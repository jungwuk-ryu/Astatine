package io.multipaper.shreddedpaper;

import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import io.multipaper.shreddedpaper.region.RegionPos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class ShreddedPaper {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final int RUN_SYNC_RETRY_CAPACITY = 8192;
    private static final int RUN_SYNC_RETRY_DRAIN_LIMIT = 256;
    private static final ConcurrentLinkedQueue<RunSyncRetry> RUN_SYNC_RETRIES = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger RUN_SYNC_RETRY_DEPTH = new AtomicInteger();
    private static final AtomicLong RUN_SYNC_RETRY_REJECTIONS = new AtomicLong();
    private static final AtomicLong RUN_SYNC_RETRY_DEFERRALS = new AtomicLong();
    private static final AtomicLong RUN_SYNC_SCHEDULE_FAILURES = new AtomicLong();
    private static final ScheduledExecutorService RUN_SYNC_RETRY_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "ShreddedPaper runSync retry");
        thread.setDaemon(true);
        return thread;
    });

    static {
        RUN_SYNC_RETRY_EXECUTOR.scheduleWithFixedDelay(ShreddedPaper::drainRunSyncRetries, 10L, 10L, TimeUnit.MILLISECONDS);
    }

    public static void runSync(Location location, Runnable runnable) {
        runSync(((CraftWorld) location.getWorld()).getHandle(), new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), runnable);
    }

    public static void runSync(Entity entity, Runnable runnable) {
        runSync(entity, runnable, null);
    }

    public static boolean runSync(Entity entity, Runnable runnable, Runnable retired) {
        return entity.getBukkitEntity().taskScheduler.schedule(e -> runnable.run(), retired == null ? null : e -> retired.run(), 1);
    }

    public static void runSync(Entity entity, Consumer<Entity> consumer) {
        runSync(entity, consumer, null);
    }

    public static boolean runSync(Entity entity, Consumer<Entity> consumer, Runnable retired) {
        return entity.getBukkitEntity().taskScheduler.schedule(consumer, retired == null ? null : e -> retired.run(), 1);
    }

    public static boolean runSync(ServerLevel serverLevel, BlockPos blockPos, Runnable runnable) {
        return runSync(serverLevel, ChunkPos.of(blockPos), runnable);
    }

    public static boolean runSync(ServerLevel serverLevel, ChunkPos chunkPos, Runnable runnable) {
        if (tryScheduleRunSyncNow(serverLevel, chunkPos, runnable)) {
            return true;
        }
        return enqueueRunSyncRetry(serverLevel, chunkPos, runnable, "initial mailbox saturated");
    }

    private static boolean tryScheduleRunSyncNow(final ServerLevel serverLevel, final ChunkPos chunkPos, final Runnable runnable) {
        try {
            return scheduleRunSyncNow(serverLevel, chunkPos, runnable);
        } catch (final RuntimeException exception) {
            final long failures = RUN_SYNC_SCHEDULE_FAILURES.incrementAndGet();
            if (failures == 1L || (failures & 255L) == 0L) {
                LOGGER.warn(
                        "Failed to schedule sync task for {} {} (failures={})",
                        serverLevel.getWorld().getName(),
                        RegionPos.forChunk(chunkPos),
                        failures,
                        exception
                );
            }
            return false;
        }
    }

    private static boolean scheduleRunSyncNow(final ServerLevel serverLevel, final ChunkPos chunkPos, final Runnable runnable) {
        return serverLevel.getChunkSource().tickingRegions.scheduleTaskNonDropping(
                RegionPos.forChunk(chunkPos),
                runnable,
                0L,
                RegionTaskClass.OWNER_HANDOFF
        );
    }

    private static boolean enqueueRunSyncRetry(
            final ServerLevel serverLevel,
            final ChunkPos chunkPos,
            final Runnable runnable,
            final String reason
    ) {
        final int depth = reserveRunSyncRetrySlot();
        if (depth < 0) {
            final long rejected = RUN_SYNC_RETRY_REJECTIONS.incrementAndGet();
            if (rejected == 1L || (rejected & 255L) == 0L) {
                LOGGER.warn(
                        "Failed to enqueue sync retry for {} {} because {} and retry queue is full (rejections={})",
                        serverLevel.getWorld().getName(),
                        RegionPos.forChunk(chunkPos),
                        reason,
                        rejected
                );
            }
            return false;
        }
        RUN_SYNC_RETRIES.offer(new RunSyncRetry(serverLevel, chunkPos, runnable));
        if (depth == 1 || depth == RUN_SYNC_RETRY_CAPACITY || (depth & 255) == 0) {
            LOGGER.warn(
                    "Queued sync retry for {} {} because {} (retryDepth={}/{})",
                    serverLevel.getWorld().getName(),
                    RegionPos.forChunk(chunkPos),
                    reason,
                    depth,
                    RUN_SYNC_RETRY_CAPACITY
            );
        }
        return true;
    }

    private static int reserveRunSyncRetrySlot() {
        int current;
        do {
            current = RUN_SYNC_RETRY_DEPTH.get();
            if (current >= RUN_SYNC_RETRY_CAPACITY) {
                return -1;
            }
        } while (!RUN_SYNC_RETRY_DEPTH.compareAndSet(current, current + 1));
        return current + 1;
    }

    private static void releaseRunSyncRetrySlot() {
        final int remaining = RUN_SYNC_RETRY_DEPTH.decrementAndGet();
        if (remaining < 0) {
            RUN_SYNC_RETRY_DEPTH.compareAndSet(remaining, 0);
            LOGGER.error("runSync retry accounting underflow");
        }
    }

    private static void drainRunSyncRetries() {
        for (int i = 0; i < RUN_SYNC_RETRY_DRAIN_LIMIT; i++) {
            final RunSyncRetry retry = RUN_SYNC_RETRIES.poll();
            if (retry == null) {
                return;
            }
            if (!tryScheduleRunSyncNow(retry.level(), retry.chunkPos(), retry.runnable())) {
                requeueRunSyncRetry(retry, "retry mailbox still saturated");
                return;
            }
            releaseRunSyncRetrySlot();
        }
    }

    private static void requeueRunSyncRetry(final RunSyncRetry retry, final String reason) {
        RUN_SYNC_RETRIES.offer(retry);
        final long deferred = RUN_SYNC_RETRY_DEFERRALS.incrementAndGet();
        final int depth = RUN_SYNC_RETRY_DEPTH.get();
        if (deferred == 1L || (deferred & 255L) == 0L) {
            LOGGER.warn(
                    "Deferred sync retry for {} {} because {} (retryDepth={}/{}, deferrals={})",
                    retry.level().getWorld().getName(),
                    RegionPos.forChunk(retry.chunkPos()),
                    reason,
                    depth,
                    RUN_SYNC_RETRY_CAPACITY,
                    deferred
            );
        }
    }

    private record RunSyncRetry(ServerLevel level, ChunkPos chunkPos, Runnable runnable) {
    }

    public static void runSync(ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        serverLevel.chunkScheduler.scheduleOnMany(runnable, regionsForBox(box));
    }

    public static void runSync(ServerLevel serverLevel1, ChunkPos chunkPos1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        ShreddedPaperRegionScheduler.scheduleAcrossLevels(serverLevel1, RegionPos.forChunk(chunkPos1), serverLevel2, RegionPos.forChunk(chunkPos2), runnable);
    }

    public static void ensureSync(Location location, Runnable runnable) {
        ensureSync(((CraftWorld) location.getWorld()).getHandle(), new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), runnable);
    }

    public static void ensureSync(Entity entity, Runnable runnable) {
        if (!isSync((ServerLevel) entity.level(), entity.chunkPosition())) {
            runSync(entity, runnable);
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(Entity entity, Consumer<Entity> consumer) {
        if (!isSync((ServerLevel) entity.level(), entity.chunkPosition())) {
            runSync(entity, consumer);
        } else {
            consumer.accept(entity);
        }
    }

    public static void ensureSync(ServerLevel serverLevel, BlockPos blockPos, Runnable runnable) {
        ensureSync(serverLevel, ChunkPos.of(blockPos), runnable);
    }

    public static void ensureSync(ServerLevel serverLevel, ChunkPos chunkPos, Runnable runnable) {
        if (!isSync(serverLevel, chunkPos)) {
            runSync(serverLevel, chunkPos, runnable);
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        RegionPos[] regionPositions = regionsForBox(box);
        for (RegionPos regionPos : regionPositions) {
            if (!isSync(serverLevel, regionPos)) {
                serverLevel.chunkScheduler.scheduleOnMany(runnable, regionPositions);
                return;
            }
        }

        runnable.run();
    }

    public static void ensureSync(Entity entity, ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        final ServerLevel entityLevel = (ServerLevel) entity.level();
        if (entityLevel != serverLevel) {
            final RegionPos entityRegion = RegionPos.forChunk(entity.chunkPosition());
            final RegionPos[] boxRegions = regionsForBox(box);
            if (!isSync(entityLevel, entityRegion) || !isSync(serverLevel, box)) {
                ShreddedPaperRegionScheduler.scheduleAcrossLevels(
                        entityLevel,
                        new RegionPos[]{entityRegion},
                        serverLevel,
                        boxRegions,
                        () -> ensureSync(entity, serverLevel, box, runnable)
                );
                return;
            }

            runnable.run();
            return;
        }

        final RegionPos entityRegion = RegionPos.forChunk(entity.chunkPosition());
        final RegionPos[] boxRegions = regionsForBox(box);
        if (!isSync(serverLevel, entityRegion) || !isSync(serverLevel, box)) {
            serverLevel.chunkScheduler.scheduleOnMany(
                    () -> ensureSync(entity, serverLevel, box, runnable),
                    includeRegion(entityRegion, boxRegions)
            );
            return;
        }

        runnable.run();
    }

    public static boolean isSync(ServerLevel serverLevel, BoundingBox box) {
        for (RegionPos regionPos : regionsForBox(box)) {
            if (!isSync(serverLevel, regionPos)) {
                return false;
            }
        }

        return true;
    }

    public static void ensureSync(Entity entity1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        if (!isSync((ServerLevel) entity1.level(), entity1.chunkPosition()) || !isSync(serverLevel2, chunkPos2)) {
            runSync((ServerLevel) entity1.level(), entity1.chunkPosition(), serverLevel2, chunkPos2, () -> ensureSync(entity1, serverLevel2, chunkPos2, runnable)); // Entity may have moved since, ensure still sync
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(Entity entity1, Entity entity2, Runnable runnable) {
        if (!isSync((ServerLevel) entity1.level(), entity1.chunkPosition()) || !isSync((ServerLevel) entity2.level(), entity2.chunkPosition())) {
            runSync((ServerLevel) entity1.level(), entity1.chunkPosition(), (ServerLevel) entity2.level(), entity2.chunkPosition(), () -> ensureSync(entity1, entity2, runnable)); // Entity may have moved since, ensure still sync
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(ServerLevel serverLevel1, ChunkPos chunkPos1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        if (!isSync(serverLevel1, chunkPos1) || !isSync(serverLevel2, chunkPos2)) {
            runSync(serverLevel1, chunkPos1, serverLevel2, chunkPos2, runnable);
        } else {
            runnable.run();
        }
    }

    public static boolean isSync(ServerLevel serverLevel, ChunkPos chunkPos) {
        return isSync(serverLevel, RegionPos.forChunk(chunkPos));
    }

    public static boolean isSync(ServerLevel serverLevel, RegionPos regionPos) {
        return serverLevel.chunkScheduler.getRegionLocker().hasWriteLock(regionPos)
                || ShreddedPaperChunkTicker.isCurrentlyTickingRegion(serverLevel, regionPos)
                || TickThread.isShutdownThread();
    }

    private static RegionPos[] regionsForBox(BoundingBox box) {
        final int minRegionX = SectionPos.blockToSectionCoord(box.minX()) >> RegionPos.REGION_SHIFT;
        final int minRegionZ = SectionPos.blockToSectionCoord(box.minZ()) >> RegionPos.REGION_SHIFT;
        final int maxRegionX = SectionPos.blockToSectionCoord(box.maxX()) >> RegionPos.REGION_SHIFT;
        final int maxRegionZ = SectionPos.blockToSectionCoord(box.maxZ()) >> RegionPos.REGION_SHIFT;
        final List<RegionPos> regionPositions = new ArrayList<>((maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1));

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                regionPositions.add(new RegionPos(regionX, regionZ));
            }
        }

        return regionPositions.toArray(RegionPos[]::new);
    }

    private static RegionPos[] includeRegion(RegionPos regionPos, RegionPos[] regionPositions) {
        for (RegionPos existing : regionPositions) {
            if (existing.longKey == regionPos.longKey) {
                return regionPositions;
            }
        }

        final RegionPos[] result = new RegionPos[regionPositions.length + 1];
        result[0] = regionPos;
        System.arraycopy(regionPositions, 0, result, 1, regionPositions.length);
        return result;
    }

    public static void postProcessGeneration(ServerLevel serverLevel, LevelChunk chunk) {
        try (var ignored = serverLevel.chunkScheduler.getRegionLocker().promoteCurrentThreadLocksToWrite()) {
            chunk.postProcessGeneration(serverLevel);
        }
    }

}
