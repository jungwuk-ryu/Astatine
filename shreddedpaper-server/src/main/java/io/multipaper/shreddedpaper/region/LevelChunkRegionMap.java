package io.multipaper.shreddedpaper.region;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.multipaper.shreddedpaper.threading.region.RegionRuntimeState;
import io.multipaper.shreddedpaper.threading.region.events.RegionMergeEvent;
import io.multipaper.shreddedpaper.threading.region.events.RegionSplitEvent;
import io.multipaper.shreddedpaper.util.DegradedRegionBossBar;
import io.multipaper.shreddedpaper.util.SimpleStampedLock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class LevelChunkRegionMap {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int MAX_MERGED_OWNER_CELLS = 64;
    private static final int MAX_SPLITS_PER_PROBE = 1;
    private static final int MAIN_THREAD_INTERNAL_TASK_SCAN_LIMIT = 64;
    private static final long SPLIT_COOLDOWN_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(10L);

    private final ServerLevel level;
    private final SimpleStampedLock regionsLock = new SimpleStampedLock();
    private final Long2ObjectOpenHashMap<RegionOwner> ownersByCell = new Long2ObjectOpenHashMap<>(2048, 0.5f);
    private final Long2ObjectOpenHashMap<RegionOwner> ownersById = new Long2ObjectOpenHashMap<>(2048, 0.5f);
    private final AtomicInteger mainThreadInternalTaskScanCursor = new AtomicInteger();
    private volatile List<LevelChunkRegion> regionsSnapshot = List.of();

    public LevelChunkRegionMap(ServerLevel level) {
        this.level = level;
    }

    public LevelChunkRegion getOrCreate(RegionPos regionPos) {
        final LevelChunkRegion levelChunkRegion = get(regionPos);

        if (levelChunkRegion != null) {
            return levelChunkRegion;
        }

        return regionsLock.write(() -> {
            return this.getOrCreateRegionLocked(regionPos);
        });
    }

    private LevelChunkRegion getExistingRegionLocked(final RegionPos regionPos) {
        final RegionOwner owner = this.ownersByCell.get(regionPos.longKey);
        final LevelChunkRegion region = owner == null ? null : owner.region();
        if (region != null) {
            region.bumpLastAccess();
        }
        return region;
    }

    private LevelChunkRegion getOrCreateRegionLocked(final RegionPos regionPos) {
        return this.getOrCreateRegionLocked(regionPos, RegionRuntimeState.CreationReason.REGION);
    }

    private LevelChunkRegion getOrCreateRegionLocked(final RegionPos regionPos, final RegionRuntimeState.CreationReason creationReason) {
        final LevelChunkRegion existing = this.getExistingRegionLocked(regionPos);
        if (existing != null) {
            return existing;
        }

        final RegionOwner owner = RegionOwner.singleCell(regionPos);
        final LevelChunkRegion created = new LevelChunkRegion(this.level, owner, creationReason);
        owner.attachRegion(created);
        this.ownersByCell.put(regionPos.longKey, owner);
        this.ownersById.put(owner.id(), owner);
        this.invalidateRegionsSnapshot();
        return created;
    }

    private void acceptRegionForCell(final RegionPos regionPos, final Consumer<LevelChunkRegion> action) {
        final boolean accepted = this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            if (region == null) {
                return false;
            }
            action.accept(region);
            return true;
        });
        if (accepted) {
            return;
        }

        this.regionsLock.write(() -> action.accept(this.getOrCreateRegionLocked(regionPos)));
    }

    private void acceptExistingRegionForCell(final RegionPos regionPos, final Consumer<LevelChunkRegion> action) {
        this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            if (region != null) {
                action.accept(region);
            }
        });
    }

    private <T> T applyRegionForCell(final RegionPos regionPos, final Function<LevelChunkRegion, T> action) {
        final RegionActionResult<T> result = this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            if (region == null) {
                return RegionActionResult.missing();
            }
            return RegionActionResult.found(action.apply(region));
        });
        if (result.found()) {
            return result.value();
        }

        return this.regionsLock.write(() -> action.apply(this.getOrCreateRegionLocked(regionPos)));
    }

    private void acceptRegionsForCells(final RegionPos firstPos, final RegionPos secondPos, final BiConsumer<LevelChunkRegion, LevelChunkRegion> action) {
        final boolean accepted = this.regionsLock.read(() -> {
            final LevelChunkRegion first = this.getExistingRegionLocked(firstPos);
            final LevelChunkRegion second = this.getExistingRegionLocked(secondPos);
            if (first == null || second == null) {
                return false;
            }
            action.accept(first, second);
            return true;
        });
        if (accepted) {
            return;
        }

        this.regionsLock.write(() -> action.accept(this.getOrCreateRegionLocked(firstPos), this.getOrCreateRegionLocked(secondPos)));
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
                if (!sourceRegion.isMergeQuiescent() || !targetRegion.absorbFrom(sourceRegion)) {
                    return false;
                }
                targetOwner.absorbCellsFrom(sourceOwner);
                for (final long cellKey : sourceOwner.cellsSnapshot()) {
                    this.ownersByCell.put(cellKey, targetOwner);
                }
                this.ownersById.remove(sourceOwner.id());
                this.invalidateRegionsSnapshot();
                sourceOwner.detachRegion(sourceRegion);
                sourceRegion.getRuntimeState().detach(sourceRegion, RegionRuntimeState.RemovalReason.MERGED_REGION);
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

    public List<LevelChunkRegion> splitDisconnectedOwnerQuiescent(final RegionOwner owner, final int isolationRadius) {
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking) {
            return List.of();
        }

        final long nowNanos = System.nanoTime();
        if (!owner.canSplit(nowNanos, SPLIT_COOLDOWN_NANOS)) {
            return List.of();
        }

        return regionsLock.write(() -> {
            final RegionOwner current = this.ownersById.get(owner.id());
            if (current != owner || owner.cellCount() <= 1) {
                return List.of();
            }
            final LevelChunkRegion sourceRegion = owner.region();
            if (sourceRegion == null || !sourceRegion.canSplitOwner()) {
                return List.of();
            }

            final ShreddedPaperRegionLocker.RegionLock ownerLock = this.level.chunkScheduler.getRegionLocker().internalTryTakeExactLockNow(
                    owner.cellPositionsSnapshot(),
                    owner.isolationCellPositionsSnapshot(isolationRadius)
            );
            if (ownerLock == null) {
                return List.of();
            }

            try {
                if (!sourceRegion.canSplitOwner()) {
                    return List.of();
                }

                final LongOpenHashSet ownedCells = new LongOpenHashSet(owner.cellsSnapshot());
                final LongOpenHashSet activeCells = sourceRegion.activeCellKeysSnapshot();
                retainOnly(activeCells, ownedCells);
                if (activeCells.isEmpty() || !activeCells.contains(owner.primaryCell().longKey)) {
                    return List.of();
                }

                this.removeInactiveCells(owner, ownedCells, activeCells);

                final List<LongOpenHashSet> components = connectedComponents(activeCells);
                if (components.size() <= 1) {
                    return List.of();
                }

                final int keepIndex = selectComponentToKeep(owner.primaryCell().longKey, components);
                final List<LevelChunkRegion> splitRegions = new ArrayList<>();
                int splits = 0;
                for (int i = 0; i < components.size(); i++) {
                    if (i == keepIndex) {
                        continue;
                    }
                    final LongOpenHashSet splitCells = components.get(i);
                    if (splitCells.isEmpty()) {
                        continue;
                    }
                    splitRegions.add(this.splitCellsToNewOwner(owner, sourceRegion, splitCells, nowNanos));
                    splits++;
                    if (splits >= MAX_SPLITS_PER_PROBE) {
                        break;
                    }
                }
                if (splits != 0) {
                    owner.recordSplit(nowNanos);
                }
                return List.copyOf(splitRegions);
            } finally {
                ownerLock.unlock();
            }
        });
    }

    private void removeInactiveCells(final RegionOwner owner, final LongOpenHashSet ownedCells, final LongOpenHashSet activeCells) {
        final LongOpenHashSet inactiveCells = new LongOpenHashSet(ownedCells);
        inactiveCells.removeAll(activeCells);
        inactiveCells.remove(owner.primaryCell().longKey);
        if (inactiveCells.isEmpty()) {
            return;
        }

        for (final long cellKey : inactiveCells) {
            this.ownersByCell.remove(cellKey, owner);
        }
        owner.removeCells(inactiveCells);
    }

    private LevelChunkRegion splitCellsToNewOwner(
            final RegionOwner sourceOwner,
            final LevelChunkRegion sourceRegion,
            final LongOpenHashSet splitCells,
            final long nowNanos
    ) {
        final long startNanos = System.nanoTime();
        final RegionPos newPrimary = new RegionPos(splitCells.iterator().nextLong());
        final RegionOwner splitOwner = RegionOwner.splitOwner(newPrimary, splitCells);
        final LevelChunkRegion splitRegion = new LevelChunkRegion(this.level, splitOwner, RegionRuntimeState.CreationReason.SPLIT);
        splitOwner.attachRegion(splitRegion);
        splitOwner.recordSplit(nowNanos);

        sourceRegion.extractCellsTo(splitRegion, splitCells);
        sourceOwner.removeCells(splitCells);

        this.ownersById.put(splitOwner.id(), splitOwner);
        this.invalidateRegionsSnapshot();
        for (final long cellKey : splitCells) {
            this.ownersByCell.put(cellKey, splitOwner);
        }
        this.commitSplitEvent(sourceOwner, splitOwner, splitCells.size(), System.nanoTime() - startNanos);
        return splitRegion;
    }

    private void commitSplitEvent(final RegionOwner sourceOwner, final RegionOwner splitOwner, final int movedCells, final long durationNanos) {
        final RegionSplitEvent event = new RegionSplitEvent();
        final RegionPos sourceCell = sourceOwner.primaryCell();
        final RegionPos splitCell = splitOwner.primaryCell();
        event.world = this.level.getWorld().getName();
        event.sourceRegionX = sourceCell.x;
        event.sourceRegionZ = sourceCell.z;
        event.newRegionX = splitCell.x;
        event.newRegionZ = splitCell.z;
        event.movedCells = movedCells;
        event.sourceCellsAfter = sourceOwner.cellCount();
        event.durationNanos = durationNanos;
        event.commit();
    }

    private static void retainOnly(final LongOpenHashSet cells, final LongOpenHashSet allowedCells) {
        for (final LongIterator iterator = cells.iterator(); iterator.hasNext();) {
            if (!allowedCells.contains(iterator.nextLong())) {
                iterator.remove();
            }
        }
    }

    private static List<LongOpenHashSet> connectedComponents(final LongOpenHashSet cells) {
        final List<LongOpenHashSet> components = new ArrayList<>();
        final LongOpenHashSet remaining = new LongOpenHashSet(cells);
        final LongArrayList queue = new LongArrayList();
        while (!remaining.isEmpty()) {
            final long start = remaining.iterator().nextLong();
            remaining.remove(start);
            queue.clear();
            queue.add(start);
            final LongOpenHashSet component = new LongOpenHashSet();
            component.add(start);

            for (int index = 0; index < queue.size(); index++) {
                final long cellKey = queue.getLong(index);
                final int x = (int) cellKey;
                final int z = (int) (cellKey >> 32);
                addNeighborIfPresent(RegionPos.asLong(x + 1, z), remaining, component, queue);
                addNeighborIfPresent(RegionPos.asLong(x - 1, z), remaining, component, queue);
                addNeighborIfPresent(RegionPos.asLong(x, z + 1), remaining, component, queue);
                addNeighborIfPresent(RegionPos.asLong(x, z - 1), remaining, component, queue);
            }

            components.add(component);
        }
        return components;
    }

    private static void addNeighborIfPresent(
            final long neighbor,
            final LongOpenHashSet remaining,
            final LongOpenHashSet component,
            final LongArrayList queue
    ) {
        if (remaining.remove(neighbor)) {
            component.add(neighbor);
            queue.add(neighbor);
        }
    }

    private static int selectComponentToKeep(final long primaryCell, final List<LongOpenHashSet> components) {
        int largestIndex = 0;
        int largestSize = -1;
        for (int i = 0; i < components.size(); i++) {
            final LongOpenHashSet component = components.get(i);
            if (component.contains(primaryCell)) {
                return i;
            }
            if (component.size() > largestSize) {
                largestIndex = i;
                largestSize = component.size();
            }
        }
        return largestIndex;
    }

    private void removeOwnerLocked(RegionOwner owner, LevelChunkRegion region) {
        for (final long cellKey : owner.cellsSnapshot()) {
            this.ownersByCell.remove(cellKey, owner);
        }
        this.ownersById.remove(owner.id());
        this.invalidateRegionsSnapshot();
        owner.detachRegion(region);
        region.getRuntimeState().detach(region, RegionRuntimeState.RemovalReason.EMPTY_REGION);
    }

    public void addTickingChunk(LevelChunk levelChunk) {
        this.acceptRegionForCell(RegionPos.forChunk(levelChunk.getPos()), region -> region.add(levelChunk));
    }

    public void removeTickingChunk(LevelChunk levelChunk) {
        this.acceptRegionForCell(RegionPos.forChunk(levelChunk.getPos()), region -> region.remove(levelChunk));
    }

    public void forEach(Consumer<LevelChunkRegion> consumer) {
        this.regionsSnapshot().forEach(consumer);
    }

    private List<LevelChunkRegion> regionsSnapshot() {
        return this.regionsSnapshot;
    }

    public boolean pollMainThreadInternalTask(final ShreddedPaperRegionLocker regionLocker, final boolean shutdown) {
        final List<LevelChunkRegion> snapshot = this.regionsSnapshot();
        final int size = snapshot.size();
        if (size == 0) {
            return false;
        }

        final int limit = shutdown ? size : Math.min(size, MAIN_THREAD_INTERNAL_TASK_SCAN_LIMIT);
        final int start = Math.floorMod(this.mainThreadInternalTaskScanCursor.getAndAdd(limit), size);
        boolean executed = false;
        for (int offset = 0; offset < limit; offset++) {
            final LevelChunkRegion region = snapshot.get((start + offset) % size);
            if (shutdown) {
                executed |= region.getInternalTaskQueue().executeTask();
                continue;
            }

            if (region.getInternalTaskQueue().hasNoScheduledTasks()) {
                continue;
            }

            final RegionOwner owner = region.getOwner();
            final ShreddedPaperRegionLocker.RegionLock lock = regionLocker.internalTryTakeExactLockNow(
                    owner.internalSortedCellKeysSnapshot(),
                    owner.internalSortedIsolationCellKeysSnapshot(ShreddedPaperRegionLocker.REGION_LOCK_RADIUS)
            );
            if (lock != null) {
                try {
                    executed |= region.getInternalTaskQueue().executeTask();
                } finally {
                    lock.unlock();
                }
            }
        }
        return executed;
    }

    private void invalidateRegionsSnapshot() {
        final List<LevelChunkRegion> regions = new ArrayList<>(ownersById.size());
        for (final RegionOwner owner : this.ownersById.values()) {
            final LevelChunkRegion region = owner.region();
            if (region != null) {
                regions.add(region);
            }
        }
        this.regionsSnapshot = List.copyOf(regions);
    }

    public void addTickingEntity(Entity entity) {
        if (entity.previousTickingChunkPosRegion != null) {
            throw new IllegalStateException("Entity has already been added to a ticking list " + entity);
        }

        entity.previousTickingChunkPosRegion = entity.chunkPosition();
        this.acceptRegionForCell(RegionPos.forChunk(entity.chunkPosition()), region -> region.addTickingEntity(entity));
    }

    public void removeTickingEntity(Entity entity) {
        if (entity.previousTickingChunkPosRegion == null) {
            throw new IllegalStateException("Entity has not been added to a ticking list " + entity);
        }

        this.acceptRegionForCell(RegionPos.forChunk(entity.previousTickingChunkPosRegion), region -> region.removeTickingEntity(entity));
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
            this.acceptRegionsForCells(fromRegion, toRegion, (fromOwner, toOwner) -> {
                if (fromOwner != toOwner) {
                    fromOwner.removeTickingEntity(entity);
                    toOwner.addTickingEntity(entity);
                }
            });
        }
    }

    public void addTrackedEntity(Entity entity) {
        if (entity.previousTrackedChunkPosRegion != null) {
            throw new IllegalStateException("Entity is already tracked " + entity);
        }

        entity.previousTrackedChunkPosRegion = entity.chunkPosition();
        this.acceptRegionForCell(RegionPos.forChunk(entity.chunkPosition()), region -> {
            region.addTrackedEntity(entity);
            if (entity instanceof Mob mob) {
                region.addNavigationMob(mob);
            }
        });
    }

    public void removeTrackedEntity(Entity entity) {
        if (entity.previousTrackedChunkPosRegion == null) {
            throw new IllegalStateException("Entity is not being tracked " + entity);
        }

        if (entity instanceof Mob mob) {
            this.acceptRegionForCell(RegionPos.forChunk(entity.chunkPosition()), region -> region.removeNavigationMob(mob));
        }

        this.acceptRegionForCell(RegionPos.forChunk(entity.previousTrackedChunkPosRegion), region -> region.removeTrackedEntity(entity));
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
            this.acceptRegionsForCells(fromRegion, toRegion, (fromOwner, toOwner) -> {
                if (fromOwner == toOwner) {
                    return;
                }

                fromOwner.removeTrackedEntity(entity);
                toOwner.addTrackedEntity(entity);

                if (entity instanceof Mob mob) {
                    fromOwner.removeNavigationMob(mob);
                    toOwner.addNavigationMob(mob);
                }
            });
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
        return this.applyRegionForCell(regionPos, region -> {
            final boolean scheduled = region.scheduleTask(taskClass, task, delayInTicks, regionPos);
            if (scheduled) {
                region.getOwner().armScheduler();
            }
            return scheduled;
        });
    }

    public boolean scheduleTaskNonDropping(RegionPos regionPos, Runnable task, long delayInTicks, RegionTaskClass taskClass) {
        return this.applyRegionForCell(regionPos, region -> {
            final boolean scheduled = region.scheduleTaskNonDropping(taskClass, task, delayInTicks, regionPos);
            if (scheduled) {
                region.getOwner().armScheduler();
            }
            return scheduled;
        });
    }

    public boolean scheduleTaskIfSchedulerArmed(RegionPos regionPos, Runnable task, long delayInTicks, RegionTaskClass taskClass) {
        return this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            return region != null && region.getOwner().isSchedulerArmed() && region.scheduleTask(taskClass, task, delayInTicks, regionPos);
        });
    }

    public boolean isSchedulerArmed(final RegionPos regionPos) {
        return this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            return region != null && region.getOwner().isSchedulerArmed();
        });
    }

    public boolean scheduleTaskIfRegionExists(RegionPos regionPos, Runnable task, long delayInTicks, RegionTaskClass taskClass) {
        return this.regionsLock.read(() -> {
            final LevelChunkRegion region = this.getExistingRegionLocked(regionPos);
            return region != null && region.scheduleTask(taskClass, task, delayInTicks, regionPos);
        });
    }

    public long ownerIdForCellOr(RegionPos regionPos, long missingValue) {
        return this.regionsLock.read(() -> {
            final RegionOwner owner = this.ownersByCell.get(regionPos.longKey);
            return owner == null ? missingValue : owner.id();
        });
    }

    public RegionRuntimeState runtimeStateForCellOrNull(RegionPos regionPos) {
        return this.regionsLock.read(() -> {
            final RegionOwner owner = this.ownersByCell.get(regionPos.longKey);
            final LevelChunkRegion region = owner == null ? null : owner.region();
            return region == null ? null : region.getRuntimeState();
        });
    }

    public RegionRuntimeState getOrCreateRuntimeStateForCell(RegionPos regionPos) {
        return this.regionsLock.write(() -> {
            final LevelChunkRegion region = this.getOrCreateRegionLocked(regionPos, RegionRuntimeState.CreationReason.CELL_LOOKUP);
            return region.getRuntimeState();
        });
    }

    public RuntimeStateParity runtimeStateParity() {
        return this.regionsLock.read(() -> new RuntimeStateParity(
                this.ownersById.size(),
                RegionRuntimeState.liveDiagnostics(this.level)
        ));
    }

    public boolean scheduleTransferredTask(RegionPos regionPos, Runnable task, long delayInTicks, RegionTaskClass taskClass) {
        return this.applyRegionForCell(regionPos, region -> {
            final boolean scheduled = region.scheduleTransferredTask(taskClass, task, delayInTicks, regionPos);
            if (scheduled) {
                region.getOwner().armScheduler();
            }
            return scheduled;
        });
    }

    public PrioritisedExecutor.PrioritisedTask createInternalTask(final RegionPos regionPos, final Runnable task, final Priority priority) {
        return this.applyRegionForCell(regionPos, region -> region.getInternalTaskQueue().createTask(task, priority));
    }

    public PrioritisedExecutor.PrioritisedTask queueInternalTask(final RegionPos regionPos, final Runnable task, final Priority priority) {
        return this.applyRegionForCell(regionPos, region -> {
            region.getOwner().armScheduler();
            return region.getInternalTaskQueue().queueTask(task, priority);
        });
    }

    /**
     * Execute a task on the given region's thread at the next given opportunity.
     * These tasks must <strong>NOT</strong> modify the chunk (blocks, entities, etc). These
     * tasks must be read-only. Eg loading a chunk, saving data, sending packets, etc.
     */
    public void execute(RegionPos regionPos, Runnable task) {
        this.acceptRegionForCell(regionPos, region -> {
            region.getOwner().armScheduler();
            region.getInternalTaskQueue().queueTask(task);
        });
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
        this.acceptRegionForCell(RegionPos.forChunk(player.chunkPosition()), region -> {
            region.addPlayer(player);
            player.currentRegion = region;
            DegradedRegionBossBar.updateCurrentRegion(player);
        });
    }

    public void removePlayer(ServerPlayer player) {
        final ChunkPos previousChunk = player.previousChunkPosRegion != null ? player.previousChunkPosRegion : player.chunkPosition();
        this.acceptExistingRegionForCell(RegionPos.forChunk(previousChunk), region -> region.removePlayerIfPresent(player));
        if (player.currentRegion != null && player.currentRegion.getLevel() == this.level) {
            player.currentRegion = null;
        }
        player.previousChunkPosRegion = null;
        DegradedRegionBossBar.hide(player);
    }

    public void movePlayer(ServerPlayer player) {
        RegionPos fromRegion = RegionPos.forChunk(player.previousChunkPosRegion);
        RegionPos toRegion = RegionPos.forChunk(player.chunkPosition());

        if (!fromRegion.equals(toRegion)) {
            player.previousChunkPosRegion = player.chunkPosition();
            this.acceptRegionsForCells(fromRegion, toRegion, (fromOwner, toOwner) -> {
                if (fromOwner != toOwner) {
                    fromOwner.removePlayer(player);
                    toOwner.addPlayer(player);
                    player.currentRegion = toOwner;
                    DegradedRegionBossBar.updateCurrentRegion(player);
                }
            });
        }
    }

    public void reconcilePlayerIfNeeded(ServerPlayer player) {
        if (player.connection.player != player
                || player.connection.processedDisconnect
                || player.isRemoved()
                || !player.valid
                || player.level() != this.level) {
            return;
        }

        final ChunkPos currentChunk = player.chunkPosition();
        final RegionPos currentCell = RegionPos.forChunk(currentChunk);
        final LevelChunkRegion currentRegion = player.currentRegion;
        if (currentRegion != null
                && currentRegion.getLevel() == this.level
                && currentRegion.getOwner().ownsCell(currentCell)
                && currentRegion.containsPlayer(player)) {
            currentRegion.getOwner().armScheduler();
            player.previousChunkPosRegion = currentChunk;
            return;
        }

        this.reconcilePlayer(currentChunk, currentCell, player);
    }

    private void reconcilePlayer(final ChunkPos currentChunk, final RegionPos currentCell, final ServerPlayer player) {
        this.regionsLock.write(() -> {
            final LevelChunkRegion expectedRegion = this.getOrCreateRegionLocked(currentCell);
            final LevelChunkRegion currentRegion = player.currentRegion != null && player.currentRegion.getLevel() == this.level
                    ? player.currentRegion
                    : null;
            final ChunkPos previousChunk = player.previousChunkPosRegion;
            final LevelChunkRegion previousRegion = previousChunk == null
                    ? null
                    : this.getExistingRegionLocked(RegionPos.forChunk(previousChunk));

            if (currentRegion == expectedRegion && expectedRegion.containsPlayer(player)) {
                expectedRegion.getOwner().armScheduler();
                player.previousChunkPosRegion = currentChunk;
                return;
            }

            if (previousRegion != null && previousRegion != expectedRegion) {
                previousRegion.removePlayerIfPresent(player);
            }
            if (currentRegion != null && currentRegion != expectedRegion && currentRegion != previousRegion) {
                currentRegion.removePlayerIfPresent(player);
            }

            expectedRegion.addPlayerIfAbsent(player);
            expectedRegion.getOwner().armScheduler();
            player.currentRegion = expectedRegion;
            player.previousChunkPosRegion = currentChunk;
        });
    }

    public void addBlockEvent(BlockEventData blockEvent) {
        this.acceptRegionForCell(RegionPos.forBlockPos(blockEvent.pos()), region -> region.addBlockEvent(blockEvent));
    }

    public void addPlayerTickingRequest(final ChunkPos chunkPos) {
        this.acceptRegionForCell(RegionPos.forChunk(chunkPos), region -> region.addPlayerTickingRequest(chunkPos));
    }

    public void removePlayerTickingRequest(final ChunkPos chunkPos) {
        this.acceptExistingRegionForCell(RegionPos.forChunk(chunkPos), region -> region.removePlayerTickingRequest(chunkPos));
    }

    public void addUnloadChunk(final ChunkPos chunkPos) {
        this.acceptRegionForCell(RegionPos.forChunk(chunkPos), region -> region.addUnloadChunk(chunkPos));
    }

    public void removeUnloadChunk(final ChunkPos chunkPos) {
        this.acceptExistingRegionForCell(RegionPos.forChunk(chunkPos), region -> region.removeUnloadChunk(chunkPos));
    }

    public void addPendingBlockEntityTicker(final TickingBlockEntity ticker) {
        this.acceptRegionForCell(RegionPos.forBlockPos(ticker.getPos()), region -> region.addPendingBlockEntityTicker(ticker));
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

    public record RuntimeStateParity(int activeOwners, RegionRuntimeState.LiveStateDiagnostics runtimeStates) {
        public boolean attachedStateCountMatchesOwners() {
            return this.activeOwners == this.runtimeStates.attachedStates();
        }
    }

    private record RegionActionResult<T>(boolean found, T value) {
        private static <T> RegionActionResult<T> found(final T value) {
            return new RegionActionResult<>(true, value);
        }

        private static <T> RegionActionResult<T> missing() {
            return new RegionActionResult<>(false, null);
        }
    }
}
