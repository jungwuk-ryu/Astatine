package io.multipaper.shreddedpaper.region;

import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.multipaper.shreddedpaper.threading.region.events.RegionMergeEvent;
import io.multipaper.shreddedpaper.util.SimpleStampedLock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class LevelChunkRegionMap {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int MAX_MERGED_OWNER_CELLS = 64;

    private final ServerLevel level;
    private final SimpleStampedLock regionsLock = new SimpleStampedLock();
    private final Long2ObjectOpenHashMap<RegionOwner> ownersByCell = new Long2ObjectOpenHashMap<>(2048, 0.5f);
    private final Long2ObjectOpenHashMap<RegionOwner> ownersById = new Long2ObjectOpenHashMap<>(2048, 0.5f);

    public LevelChunkRegionMap(ServerLevel level) {
        this.level = level;
    }

    public LevelChunkRegion getOrCreate(RegionPos regionPos) {
        LevelChunkRegion levelChunkRegion = get(regionPos);

        if (levelChunkRegion != null) {
            return levelChunkRegion;
        }

        return regionsLock.write(() -> {
            RegionOwner owner = this.ownersByCell.get(regionPos.longKey);
            if (owner != null) {
                return owner.region();
            }

            owner = RegionOwner.singleCell(regionPos);
            final LevelChunkRegion created = new LevelChunkRegion(this.level, owner);
            owner.attachRegion(created);
            this.ownersByCell.put(regionPos.longKey, owner);
            this.ownersById.put(owner.id(), owner);
            return created;
        });
    }

    public LevelChunkRegion get(RegionPos regionPos) {
        return regionsLock.optimisticRead(() -> {
            final RegionOwner owner = this.ownersByCell.get(regionPos.longKey);
            final LevelChunkRegion levelChunkRegion = owner == null ? null : owner.region();
            if (levelChunkRegion != null) {
                levelChunkRegion.bumpLastAccess();
            }
            return levelChunkRegion;
        });
    }

    public void remove(RegionPos regionPos) {
        regionsLock.write(() -> {
            final RegionOwner owner = this.ownersByCell.remove(regionPos.longKey);
            final LevelChunkRegion region = owner == null ? null : owner.region();
            if (region == null) {
                return;
            }
            if (!region.isEmpty()) {
                // Guess this region has been modified by another thread, re-add it
                this.ownersByCell.put(regionPos.longKey, owner);
                this.ownersById.put(owner.id(), owner);
            } else {
                this.removeOwnerLocked(owner, region);
            }
        });
    }

    public void removeOwner(RegionOwner owner) {
        regionsLock.write(() -> {
            final RegionOwner current = this.ownersById.get(owner.id());
            if (current == null) {
                return;
            }
            final LevelChunkRegion region = current.region();
            if (region == null || !region.isEmpty()) {
                return;
            }
            this.removeOwnerLocked(current, region);
        });
    }

    public boolean mergeOwnersQuiescent(RegionPos targetCell, RegionPos sourceCell) {
        return regionsLock.write(() -> {
            if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking) {
                return false;
            }
            final RegionOwner targetOwner = this.ownersByCell.get(targetCell.longKey);
            final RegionOwner sourceOwner = this.ownersByCell.get(sourceCell.longKey);
            if (targetOwner == null || sourceOwner == null) {
                return false;
            }
            if (targetOwner == sourceOwner) {
                return true;
            }

            final LevelChunkRegion targetRegion = targetOwner.region();
            final LevelChunkRegion sourceRegion = sourceOwner.region();
            if (targetRegion == null || sourceRegion == null || !sourceRegion.isMergeQuiescent()) {
                return false;
            }
            if (targetOwner.cellCount() + sourceOwner.cellCount() > MAX_MERGED_OWNER_CELLS) {
                return false;
            }

            final long startNanos = System.nanoTime();
            final int targetCellsBefore = targetOwner.cellCount();
            final int sourceCells = sourceOwner.cellCount();
            final List<RegionPos> lockedCells = new ArrayList<>(targetOwner.cellCount() + sourceOwner.cellCount());
            lockedCells.addAll(targetOwner.cellPositionsSnapshot());
            lockedCells.addAll(sourceOwner.cellPositionsSnapshot());
            final ShreddedPaperRegionLocker.RegionLock ownerLock = this.level.chunkScheduler.getRegionLocker().internalTryTakeExactLockNow(lockedCells);
            if (ownerLock == null) {
                return false;
            }

            try {
                targetRegion.absorbFrom(sourceRegion);
                targetOwner.absorbCellsFrom(sourceOwner);
                for (final long cellKey : sourceOwner.cellsSnapshot()) {
                    this.ownersByCell.put(cellKey, targetOwner);
                }
                this.ownersById.remove(sourceOwner.id());
                sourceOwner.detachRegion(sourceRegion);
                sourceRegion.getRuntimeState().detach(sourceRegion);
                sourceOwner.clearTransferredCells();
                this.commitMergeEvent(targetOwner, sourceCell, targetCellsBefore, sourceCells, System.nanoTime() - startNanos);
            } finally {
                ownerLock.unlock();
            }
            return true;
        });
    }

    public int mergeNearbyOwnersQuiescent(final RegionOwner targetOwner, final int radius) {
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking) {
            return 0;
        }

        int merged = 0;
        final LongOpenHashSet failedCandidates = new LongOpenHashSet();
        while (true) {
            final RegionPos sourceCell = this.findNearbyMergeCandidate(targetOwner, radius, failedCandidates);
            if (sourceCell == null) {
                return merged;
            }
            if (this.mergeOwnersQuiescent(targetOwner.primaryCell(), sourceCell)) {
                merged++;
                continue;
            }
            failedCandidates.add(sourceCell.longKey);
        }
    }

    private RegionPos findNearbyMergeCandidate(final RegionOwner targetOwner, final int radius, final LongOpenHashSet failedCandidates) {
        return regionsLock.read(() -> {
            if (this.ownersById.get(targetOwner.id()) != targetOwner || targetOwner.region() == null) {
                return null;
            }

            for (final RegionPos ownedCell : targetOwner.cellPositionsSnapshot()) {
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x == 0 && z == 0) {
                            continue;
                        }
                        final long candidateKey = RegionPos.asLong(ownedCell.x + x, ownedCell.z + z);
                        if (failedCandidates.contains(candidateKey)) {
                            continue;
                        }
                        final RegionOwner candidate = this.ownersByCell.get(candidateKey);
                        if (candidate != null && candidate != targetOwner) {
                            return new RegionPos(candidateKey);
                        }
                    }
                }
            }
            return null;
        });
    }

    private void commitMergeEvent(
            final RegionOwner targetOwner,
            final RegionPos sourceCell,
            final int targetCellsBefore,
            final int sourceCells,
            final long durationNanos
    ) {
        final RegionMergeEvent event = new RegionMergeEvent();
        final RegionPos targetCell = targetOwner.primaryCell();
        event.world = this.level.getWorld().getName();
        event.targetRegionX = targetCell.x;
        event.targetRegionZ = targetCell.z;
        event.sourceRegionX = sourceCell.x;
        event.sourceRegionZ = sourceCell.z;
        event.targetCellsBefore = targetCellsBefore;
        event.sourceCells = sourceCells;
        event.targetCellsAfter = targetOwner.cellCount();
        event.durationNanos = durationNanos;
        event.commit();
    }

    private void removeOwnerLocked(RegionOwner owner, LevelChunkRegion region) {
        for (final long cellKey : owner.cellsSnapshot()) {
            this.ownersByCell.remove(cellKey, owner);
        }
        this.ownersById.remove(owner.id());
        owner.detachRegion(region);
        region.getRuntimeState().detach(region);
    }

    public void addTickingChunk(LevelChunk levelChunk) {
        getOrCreate(RegionPos.forChunk(levelChunk.getPos())).add(levelChunk);
    }

    public void removeTickingChunk(LevelChunk levelChunk) {
        getOrCreate(RegionPos.forChunk(levelChunk.getPos())).remove(levelChunk);
    }

    public void forEach(Consumer<LevelChunkRegion> consumer) {
        List<LevelChunkRegion> regionsCopy = new ArrayList<>(ownersById.size());
        regionsLock.read(() -> {
            for (final RegionOwner owner : this.ownersById.values()) {
                final LevelChunkRegion region = owner.region();
                if (region != null) {
                    regionsCopy.add(region);
                }
            }
        });
        regionsCopy.forEach(consumer);
    }

    public void addTickingEntity(Entity entity) {
        if (entity.previousTickingChunkPosRegion != null) {
            throw new IllegalStateException("Entity has already been added to a ticking list " + entity);
        }

        entity.previousTickingChunkPosRegion = entity.chunkPosition();
        getOrCreate(RegionPos.forChunk(entity.chunkPosition())).addTickingEntity(entity);
    }

    public void removeTickingEntity(Entity entity) {
        if (entity.previousTickingChunkPosRegion == null) {
            throw new IllegalStateException("Entity has not been added to a ticking list " + entity);
        }

        getOrCreate(RegionPos.forChunk(entity.previousTickingChunkPosRegion)).removeTickingEntity(entity);
        entity.previousTickingChunkPosRegion = null;
    }

    public void moveTickingEntity(Entity entity) {
        if (entity.previousTickingChunkPosRegion == null) {
            // Not ticking, ignore
            return;
        }

        ChunkPos newChunkPos = entity.chunkPosition();
        RegionPos fromRegion = RegionPos.forChunk(entity.previousTickingChunkPosRegion);
        RegionPos toRegion = RegionPos.forChunk(newChunkPos);

        if (!fromRegion.equals(toRegion)) {
            entity.previousTickingChunkPosRegion = newChunkPos;
            final LevelChunkRegion fromOwner = getOrCreate(fromRegion);
            final LevelChunkRegion toOwner = getOrCreate(toRegion);
            if (fromOwner != toOwner) {
                fromOwner.removeTickingEntity(entity);
                toOwner.addTickingEntity(entity);
            }
        }
    }

    public void addTrackedEntity(Entity entity) {
        if (entity.previousTrackedChunkPosRegion != null) {
            throw new IllegalStateException("Entity is already tracked " + entity);
        }

        entity.previousTrackedChunkPosRegion = entity.chunkPosition();
        getOrCreate(RegionPos.forChunk(entity.chunkPosition())).addTrackedEntity(entity);

        if (entity instanceof Mob mob) {
            getOrCreate(RegionPos.forChunk(entity.chunkPosition())).addNavigationMob(mob);
        }
    }

    public void removeTrackedEntity(Entity entity) {
        if (entity.previousTrackedChunkPosRegion == null) {
            throw new IllegalStateException("Entity is not being tracked " + entity);
        }

        if (entity instanceof Mob mob) {
            getOrCreate(RegionPos.forChunk(entity.chunkPosition())).removeNavigationMob(mob);
        }

        getOrCreate(RegionPos.forChunk(entity.previousTrackedChunkPosRegion)).removeTrackedEntity(entity);
        entity.previousTrackedChunkPosRegion = null;
    }

    public void moveTrackedEntity(Entity entity) {
        if (entity.previousTrackedChunkPosRegion == null) {
            // Not tracked, ignore
            return;
        }

        ChunkPos newChunkPos = entity.chunkPosition();
        RegionPos fromRegion = RegionPos.forChunk(entity.previousTrackedChunkPosRegion);
        RegionPos toRegion = RegionPos.forChunk(newChunkPos);

        if (!fromRegion.equals(toRegion)) {
            entity.previousTrackedChunkPosRegion = newChunkPos;
            final LevelChunkRegion fromOwner = getOrCreate(fromRegion);
            final LevelChunkRegion toOwner = getOrCreate(toRegion);
            if (fromOwner == toOwner) {
                return;
            }

            fromOwner.removeTrackedEntity(entity);
            toOwner.addTrackedEntity(entity);

            if (entity instanceof Mob mob) {
                fromOwner.removeNavigationMob(mob);
                toOwner.addNavigationMob(mob);
            }
        }
    }

    /**
     * Schedule a task to run on the given region's thread at the beginning of the next tick
     */
    public boolean scheduleTask(RegionPos regionPos, Runnable task) {
        return scheduleTask(regionPos, task, 0);
    }

    /**
     * Schedule a task to run on the given region's thread after a certain number of ticks
     */
    public boolean scheduleTask(RegionPos regionPos, Runnable task, long delayInTicks) {
        return scheduleTask(regionPos, task, delayInTicks, RegionTaskClass.CRITICAL_SYSTEM);
    }

    public boolean scheduleTask(RegionPos regionPos, Runnable task, long delayInTicks, RegionTaskClass taskClass) {
        return getOrCreate(regionPos).scheduleTask(taskClass, task, delayInTicks);
    }

    /**
     * Execute a task on the given region's thread at the next given opportunity.
     * These tasks must <strong>NOT</strong> modify the chunk (blocks, entities, etc). These
     * tasks must be read-only. Eg loading a chunk, saving data, sending packets, etc.
     */
    public void execute(RegionPos regionPos, Runnable task) {
        getOrCreate(regionPos).getInternalTaskQueue().queueTask(task);
    }

    /**
     * Executor that executes a task on the given region's thread at the next given opportunity.
     * These tasks must <strong>NOT</strong> modify the chunk (blocks, entities, etc). These
     * tasks must be read-only. Eg loading a chunk, saving data, sending packets, etc.
     */
    public Executor executorFor(RegionPos regionPos) {
        return runnable -> execute(regionPos, runnable);
    }

    public void addPlayer(ServerPlayer player) {
        player.previousChunkPosRegion = player.chunkPosition();
        LevelChunkRegion region = getOrCreate(RegionPos.forChunk(player.chunkPosition()));
        region.addPlayer(player);
        player.currentRegion = region;
    }

    public void removePlayer(ServerPlayer player) {
        getOrCreate(RegionPos.forChunk(player.chunkPosition())).removePlayer(player);
        player.currentRegion = null;
    }

    public void movePlayer(ServerPlayer player) {
        RegionPos fromRegion = RegionPos.forChunk(player.previousChunkPosRegion);
        RegionPos toRegion = RegionPos.forChunk(player.chunkPosition());

        if (!fromRegion.equals(toRegion)) {
            player.previousChunkPosRegion = player.chunkPosition();
            final LevelChunkRegion fromOwner = getOrCreate(fromRegion);
            final LevelChunkRegion toOwner = getOrCreate(toRegion);
            if (fromOwner != toOwner) {
                fromOwner.removePlayer(player);
                toOwner.addPlayer(player);
                player.currentRegion = toOwner;
            }
        }
    }

    public void addBlockEvent(BlockEventData blockEvent) {
        getOrCreate(RegionPos.forBlockPos(blockEvent.pos())).addBlockEvent(blockEvent);
    }

    public void forEachRegionInBoundingBox(BoundingBox box, Consumer<LevelChunkRegion> consumer) {
        RegionPos minPos = RegionPos.forBlockPos(box.minX(), box.minZ(), box.minZ());
        RegionPos maxPos = RegionPos.forBlockPos(box.maxX(), box.maxZ(), box.maxZ());

        for (int x = minPos.x; x <= maxPos.x; x++) {
            for (int z = minPos.z; z <= maxPos.z; z++) {
                LevelChunkRegion region = get(new RegionPos(x, z));
                if (region != null) {
                    consumer.accept(region);
                }
            }
        }
    }

    public List<Mob> collectRelevantNavigatingMobs(RegionPos regionPos) {
        if (!level.chunkScheduler.getRegionLocker().hasLock(regionPos) && !ShreddedPaperChunkTicker.isCurrentlyTickingRegion(this.level, regionPos)) {
            // We care about the navigating mobs in at least this region, ensure it's locked
            throw new IllegalStateException("Collecting navigating mobs outside of region's thread");
        }

        ObjectArrayList<Mob> navigatingMobs = new ObjectArrayList<>();
        final boolean independentOwner = ShreddedPaperConfiguration.get().multithreading.independentRegionTicking
                && ShreddedPaperChunkTicker.isCurrentlyTickingRegion(this.level, regionPos);

        for (int x = -ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; x <= ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; x++) {
            for (int z = -ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; z <= ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; z++) {
                RegionPos i = new RegionPos(regionPos.x + x, regionPos.z + z);

                // Only collect mobs from regions that are locked - if it's not locked, it should be too far away to matter
                if (!level.chunkScheduler.getRegionLocker().hasLock(i) && !ShreddedPaperChunkTicker.isCurrentlyTickingRegion(this.level, i)) {
                    if (!independentOwner) {
                        continue;
                    }
                    final ShreddedPaperRegionLocker.RegionLock readLock = level.chunkScheduler.getRegionLocker().internalTryTakeReadOnlyLockNow(i, 0);
                    if (readLock == null) {
                        continue;
                    }
                    try {
                        LevelChunkRegion region = get(i);
                        if (region != null) {
                            region.collectNavigatingMobs(navigatingMobs);
                        }
                    } finally {
                        readLock.unlock();
                    }
                    continue;
                }

                LevelChunkRegion region = get(i);
                if (region == null) continue;

                region.collectNavigatingMobs(navigatingMobs);
            }
        }

        return navigatingMobs;
    }
}
