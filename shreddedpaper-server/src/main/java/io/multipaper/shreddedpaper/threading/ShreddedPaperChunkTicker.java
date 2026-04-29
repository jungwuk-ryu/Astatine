package io.multipaper.shreddedpaper.threading;

import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.common.util.WorldUtil;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.RegionTickBudget;
import io.multipaper.shreddedpaper.threading.region.RegionTickScheduler;
import io.multipaper.shreddedpaper.threading.region.RegionWorkType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.util.CraftSpawnCategory;
import org.bukkit.entity.SpawnCategory;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

public class ShreddedPaperChunkTicker {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private static final ThreadLocal<LevelChunkRegion> currentlyTickingRegion = new ThreadLocal<>();

    private final ServerChunkCache serverChunkCache;

    private final List<Entity> trackedEntitiesWorkerList = new ArrayList<>(); // Re-usable list for processing tracked entities in parallel
    public ShreddedPaperChunkTicker(ServerChunkCache serverChunkCache) {
        this.serverChunkCache = serverChunkCache;
    }

    public CompletableFuture<Void> tickChunks(final long timeInhabited, final List<MobCategory> filteredSpawningCategories, final NaturalSpawner.SpawnState spawnState) {
        ServerLevel level = this.serverChunkCache.chunkMap.level;
        final ScheduledTickContext tickContext = new ScheduledTickContext(timeInhabited, filteredSpawningCategories == null ? List.of() : filteredSpawningCategories, spawnState);
        io.papermc.paper.entity.activation.ActivationRange.activateEntities(level); // Paper - EAR // DivineMC - DAB must update priorities before ShreddedPaper region entity ticking

        if (ShreddedPaperConfiguration.get().multithreading.independentRegionTicking) {
            level.chunkSource.tickingRegions.forEach(
                    region -> {
                        if (region.getOwner().isSchedulerArmed()) {
                            RegionTickScheduler.get().registerRegion(level, region, this, tickContext);
                        }
                    }
            );
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        level.chunkSource.tickingRegions.forEach(
                region -> futures.add(this.tickRegion(level, region, timeInhabited, filteredSpawningCategories, spawnState))
        );

        CompletableFuture<Void> future = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));

        if (ShreddedPaperConfiguration.get().optimizations.processTrackQueueInParallel) future = future.thenCompose(v -> this.processTrackQueueInParallel(level));

        if (ShreddedPaperConfiguration.get().optimizations.flushQueueInParallel) future = future.thenCompose(v -> this.flushQueueInParallel(level));

        return future;
    }

    /** processTrackQueue has been renamed to newTrackerTick */
    private CompletableFuture<Void> processTrackQueueInParallel(ServerLevel level) {
        level.getChunkSource().mainThreadProcessor.managedBlock(() -> level.chunkScheduler.getRegionLocker().globalLock().tryWriteLock() != 0);
        CompletableFuture<Void> allFuture = CompletableFuture.completedFuture(null);
        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            trackedEntitiesWorkerList.clear();
            level.chunkSource.tickingRegions.forEach(
                    region -> region.forEachTrackedEntity(trackedEntitiesWorkerList::add)
            );

            List<List<Entity>> trackedEntitiesTasks = Lists.partition(trackedEntitiesWorkerList, Math.max(1, trackedEntitiesWorkerList.size() / ShreddedPaperTickThread.THREAD_COUNT / 3));
            for (List<Entity> trackedEntities : trackedEntitiesTasks) {
                if (trackedEntities.isEmpty()) continue;
                futures.add(CompletableFuture.runAsync(() -> trackedEntities.forEach(ShreddedPaperEntityTicker::processTrackQueue), ShreddedPaperTickThread.getExecutor()));
            }

            allFuture = allFuture.thenCompose(v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));
            return allFuture;
        } finally {
            allFuture.whenComplete((v, e) -> level.chunkScheduler.getRegionLocker().globalLock().tryUnlockWrite());
        }
    }

    private CompletableFuture<Void> flushQueueInParallel(ServerLevel level) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        List<List<ServerPlayer>> playersTasks = Lists.partition(new ArrayList<>(level.players()), Math.max(1, level.players().size() / ShreddedPaperTickThread.THREAD_COUNT / 3));
        for (List<ServerPlayer> players : playersTasks) {
            if (players.isEmpty()) continue;
            futures.add(CompletableFuture.runAsync(() -> players.forEach(player -> player.connection.connection.flushQueue()), ShreddedPaperTickThread.getExecutor()));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private CompletableFuture<Void> tickRegion(final ServerLevel level, final LevelChunkRegion region, final long timeInhabited, final List<MobCategory> filteredSpawningCategories, final NaturalSpawner.SpawnState spawnState) {
        return level.chunkScheduler.schedule(region.getRegionPos(), () -> this._tickRegion(level, region, timeInhabited, filteredSpawningCategories, spawnState, null)).exceptionally(e -> {
            LogUtils.getClassLogger().error("Exception ticking region {}", region.getRegionPos(), e);
            MinecraftServer.getServer().moonrise$setChunkSystemCrash(new RuntimeException("Ticking thread crash while ticking region " + region.getRegionPos(), e));
            return null;
        });
    }

    public void tickRegionFromIndependentScheduler(
            final ServerLevel level,
            final LevelChunkRegion region,
            final RegionTickBudget budget,
            final ScheduledTickContext tickContext,
            final long scheduledStartNanos
    ) {
        this._tickRegion(level, region, tickContext.timeInhabited(), tickContext.filteredSpawningCategories(), tickContext.spawnState(), budget);
    }

    public static boolean isCurrentlyTickingRegion(Level level, RegionPos regionPos) {
        LevelChunkRegion region = currentlyTickingRegion.get();
        return region != null && level.equals(region.getLevel()) && region.getOwner().ownsCell(regionPos);
    }

    public static LevelChunkRegion currentlyTickingRegion() {
        return currentlyTickingRegion.get();
    }

    private void _tickRegion(final ServerLevel level, final LevelChunkRegion region, final long timeInhabited, final List<MobCategory> filteredSpawningCategories, final NaturalSpawner.SpawnState spawnState, final RegionTickBudget budget) {
        final long tickStartNanos = System.nanoTime();
        try {
            currentlyTickingRegion.set(region);

            if (!(ShreddedPaperTickThread.isShreddedPaperTickThread())) {
                throw new IllegalStateException("Ticking region " + WorldUtil.getWorldName(level) + " " + region.getOwner() + " outside of AstatineTickThread!");
            }

            ShreddedPaperChangesBroadcaster.setAsWorkerThread();

            while (budget == null || budget.canContinue(RegionWorkType.INTERNAL_TASK)) {
                if (!region.getInternalTaskQueue().executeTask()) {
                    break;
                }
            }

            level.moonrise$getChunkTaskScheduler().chunkHolderManager.processUnloads(region);

            region.forEachTickingEntity(entity -> {
                CraftEntity bukkitEntity = entity.getBukkitEntityRaw();
                if (bukkitEntity != null && !entity.isRemoved() && !bukkitEntity.taskScheduler.isRetired() && TickThread.isTickThreadFor(entity)) {
                    bukkitEntity.taskScheduler.executeTick();
                }
            });

            region.tickTasks();
            tickPlayers(region);

            if (level.tickRateManager().runsNormally()) {
                level.handlingTickThreadLocal.set(true);

                processScheduledTicks(level, region);

                region.setChunkTickCursor(processRoundRobin(region.getChunksSnapshot(), region.getChunkTickCursor(), chunk -> chunk.getPos().toLong(), chunk -> {
                    this._tickChunk(region, level, chunk, timeInhabited, filteredSpawningCategories, spawnState);
                    return true;
                }));

                level.runBlockEvents(region);

                level.handlingTickThreadLocal.set(false);
            }

            if (region.getEntityTickCursor() == LevelChunkRegion.NO_CONTINUATION_CURSOR) {
                region.forEachTickingEntity(ShreddedPaperEntityTicker::tickEntity);
            } else {
                region.setEntityTickCursor(processRoundRobin(region.getTickingEntitiesSnapshot(), region.getEntityTickCursor(), entity -> entity.getId(), entity -> {
                    ShreddedPaperEntityTicker.tickEntity(entity);
                    return true;
                }));
            }

            if (ShreddedPaperConfiguration.get().multithreading.independentRegionTicking || !ShreddedPaperConfiguration.get().optimizations.processTrackQueueInParallel) {
                region.setTrackerCursor(processRoundRobin(region.getTrackedEntitiesSnapshot(), region.getTrackerCursor(), entity -> entity.getId(), entity -> {
                    ShreddedPaperEntityTicker.processTrackQueue(entity);
                    return true;
                }));
            }

            try (var ignored = level.chunkScheduler.getRegionLocker().promoteCurrentThreadLocksToWrite()) {
                level.tickBlockEntities(region.tickingBlockEntities, region.pendingBlockEntityTickers);
            }

            while (budget == null || budget.canContinue(RegionWorkType.INTERNAL_TASK)) {
                if (!region.getInternalTaskQueue().executeTask()) {
                    break;
                }
            }

            ShreddedPaperChangesBroadcaster.broadcastChanges(budget);

            if (region.isEmpty()) {
                level.chunkSource.tickingRegions.removeOwner(region.getOwner());
            }
        } finally {
            try {
                region.recordTickStats(tickStartNanos, System.nanoTime() - tickStartNanos);
            } finally {
                currentlyTickingRegion.remove();
            }
        }
    }

    private void _tickChunk(final LevelChunkRegion levelChunkRegion, final ServerLevel world, final LevelChunk levelChunk, final long timeInhabited, final List<MobCategory> filteredSpawningCategories, final NaturalSpawner.SpawnState spawnState) {
        if (levelChunk.moonrise$getChunkHolder().vanillaChunkHolder.hasChangesToBroadcast())
            ShreddedPaperChangesBroadcaster.add(levelChunk.moonrise$getChunkHolder().vanillaChunkHolder); // ShreddedPaper

        // ShreddedPaper start - clear chunk packet cache
        if (levelChunk.cachedChunkPacket != null && levelChunk.cachedChunkPacketLastAccessed < world.getGameTime() - ShreddedPaperConfiguration.get().optimizations.chunkPacketCaching.expireAfter) {
            levelChunk.cachedChunkPacket = null;
        }
        // ShreddedPaper end - clear chunk packet cache

        if (!levelChunk.moonrise$getChunkHolder().isEntityTickingReady()) {
            return;
        }

        if (spawnState != null && levelChunkRegion.isPlayerTickingRequested(levelChunk.getPos())) {
            this._tickSpawningChunk(world, levelChunk, timeInhabited, filteredSpawningCategories, spawnState);
        }

        final int randomTickSpeed = io.multipaper.shreddedpaper.util.ShreddedPaperLagCompensation.adjustedRandomTickSpeed(
                world,
                levelChunk.getPos().getWorldPosition(),
                world.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED)
        );
        world.tickChunk(levelChunk, randomTickSpeed);
    }

    private static void tickPlayers(final LevelChunkRegion region) {
        // Player connection ticks are latency-critical; heavy entity/block work must not starve movement validation.
        region.setPlayerTickCursor(processRoundRobin(region.getPlayers(), region.getPlayerTickCursor(), player -> player.getId(), player -> {
            ShreddedPaperPlayerTicker.tickPlayer(player);
            return true;
        }));
    }

    private void _tickSpawningChunk(final ServerLevel world, final LevelChunk levelChunk, final long timeInhabited, final List<MobCategory> filteredSpawningCategories, final NaturalSpawner.SpawnState spawnState) {
        if (!world.chunkSource.chunkMap.isChunkNearPlayer(world.chunkSource.chunkMap, levelChunk.getPos(), levelChunk)) {
            return;
        }

        world.chunkSource.tickSpawningChunk(levelChunk, timeInhabited, filteredSpawningCategories, spawnState);
    }

    private static void processScheduledTicks(final ServerLevel level, final LevelChunkRegion region) {
        final List<RegionPos> ownerCells = region.getOwner().cellPositionsSnapshot();
        final int size = ownerCells.size();
        if (size == 0) {
            region.clearScheduledTickCellCursor();
            return;
        }

        final long scheduledTickCursor = region.getScheduledTickCellCursor();
        int index = findContinuationIndex(ownerCells, scheduledTickCursor, RegionPos::toLong);
        boolean fluidPhase = scheduledTickCursor != LevelChunkRegion.NO_CONTINUATION_CURSOR
                && ownerCells.get(index).toLong() == scheduledTickCursor
                && region.isScheduledTickFluidPhase();
        final boolean scheduledTickPresenceGuard = ShreddedPaperConfiguration.get().optimizations.scheduledTickPresenceGuard;

        for (int processed = 0; processed < size; processed++) {
            final RegionPos cell = ownerCells.get(index);
            // Dynamic split/merge can leave stale scheduled tick cursors behind; never tick a cell through a read-only isolation lock.
            if (!region.getOwner().ownsCell(cell)
                    || !level.chunkScheduler.getRegionLocker().hasWriteLock(cell)
                    || !shouldProcessScheduledTicksInCell(level, cell, scheduledTickPresenceGuard)) {
                fluidPhase = false;
                index++;
                if (index == size) {
                    index = 0;
                }
                continue;
            }
            if (!fluidPhase) {
                level.blockTicks.tick(cell, level.getGameTime(), level.paperConfig().environment.maxBlockTicks, level::tickBlock);
            }

            level.fluidTicks.tick(cell, level.getGameTime(), level.paperConfig().environment.maxBlockTicks, level::tickFluid);
            fluidPhase = false;

            index++;
            if (index == size) {
                index = 0;
            }
        }

        region.clearScheduledTickCellCursor();
    }

    private static boolean shouldProcessScheduledTicksInCell(final ServerLevel level, final RegionPos cell, final boolean scheduledTickPresenceGuard) {
        if (scheduledTickPresenceGuard && !hasScheduledTickData(level, cell)) {
            return false;
        }
        return hasLoadedChunkInCell(level, cell);
    }

    private static boolean hasScheduledTickData(final ServerLevel level, final RegionPos cell) {
        return level.blockTicks.hasRegionData(cell) || level.fluidTicks.hasRegionData(cell);
    }

    private static boolean hasLoadedChunkInCell(final ServerLevel level, final RegionPos cell) {
        for (int chunkX = cell.getLowerChunkX(); chunkX <= cell.getUpperChunkX(); chunkX++) {
            for (int chunkZ = cell.getLowerChunkZ(); chunkZ <= cell.getUpperChunkZ(); chunkZ++) {
                if (level.getChunkSource().getChunkAtIfLoadedImmediately(chunkX, chunkZ) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> long processRoundRobin(final List<T> entries, final long cursor, final ToLongFunction<T> keyFunction, final Predicate<T> action) {
        final int size = entries.size();
        if (size == 0) {
            return LevelChunkRegion.NO_CONTINUATION_CURSOR;
        }

        int index = findContinuationIndex(entries, cursor, keyFunction);
        for (int processed = 0; processed < size; processed++) {
            final T entry = entries.get(index);
            if (!action.test(entry)) {
                return keyFunction.applyAsLong(entry);
            }
            index++;
            if (index == size) {
                index = 0;
            }
        }
        return LevelChunkRegion.NO_CONTINUATION_CURSOR;
    }

    private static <T> int findContinuationIndex(final List<T> entries, final long cursor, final ToLongFunction<T> keyFunction) {
        if (cursor == LevelChunkRegion.NO_CONTINUATION_CURSOR) {
            return 0;
        }

        for (int index = 0, size = entries.size(); index < size; index++) {
            if (keyFunction.applyAsLong(entries.get(index)) == cursor) {
                return index;
            }
        }
        return 0;
    }

    public static boolean willTrySpawnMobsThisTick(final ServerLevel level) {
        for (MobCategory mobCategory : NaturalSpawner.SPAWNING_CATEGORIES) {
            SpawnCategory spawnCategory = CraftSpawnCategory.toBukkit(mobCategory);
            if (CraftSpawnCategory.isValidForLimits(spawnCategory)) {
                if (level.ticksPerSpawnCategory.getLong(spawnCategory) != 0 && level.getLevelData().getGameTime() % level.ticksPerSpawnCategory.getLong(spawnCategory) == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public record ScheduledTickContext(
            long timeInhabited,
            List<MobCategory> filteredSpawningCategories,
            NaturalSpawner.SpawnState spawnState
    ) {
    }

}
