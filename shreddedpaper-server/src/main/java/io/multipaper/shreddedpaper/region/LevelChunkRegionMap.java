package io.multipaper.shreddedpaper.region;

import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import io.multipaper.shreddedpaper.util.SimpleStampedLock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class LevelChunkRegionMap {

    private static final Logger LOGGER = LogUtils.getClassLogger();

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
            owner.requireSingleCell("LevelChunkRegionMap#remove");
            if (!region.isEmpty()) {
                // Guess this region has been modified by another thread, re-add it
                this.ownersByCell.put(regionPos.longKey, owner);
                this.ownersById.put(owner.id(), owner);
            } else {
                this.ownersById.remove(owner.id());
                owner.detachRegion(region);
                region.getRuntimeState().detach(region);
            }
        });
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
            getOrCreate(fromRegion).removeTickingEntity(entity);
            getOrCreate(toRegion).addTickingEntity(entity);
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
            getOrCreate(fromRegion).removeTrackedEntity(entity);
            getOrCreate(toRegion).addTrackedEntity(entity);

            if (entity instanceof Mob mob) {
                getOrCreate(fromRegion).removeNavigationMob(mob);
                getOrCreate(toRegion).addNavigationMob(mob);
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
            getOrCreate(fromRegion).removePlayer(player);
            LevelChunkRegion region = getOrCreate(toRegion);
            region.addPlayer(player);
            player.currentRegion = region;
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
