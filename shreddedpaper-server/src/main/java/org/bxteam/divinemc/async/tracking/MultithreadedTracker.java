package org.bxteam.divinemc.async.tracking;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.event.BroadcastPacketEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bxteam.divinemc.config.DivineConfig;
import org.bxteam.divinemc.util.NamedAgnosticThreadFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MultithreadedTracker {
    private static final String THREAD_PREFIX = "Async Tracker";
    private static final Logger LOGGER = LogManager.getLogger(THREAD_PREFIX);

    private static long lastWarnMillis = System.currentTimeMillis();
    public static final ThreadPoolExecutor TRACKER_EXECUTOR = DivineConfig.AsyncCategory.multithreadedEnabled ? new ThreadPoolExecutor(
        getCorePoolSize(),
        getMaxPoolSize(),
        getKeepAliveTime(), TimeUnit.SECONDS,
        getQueueImpl(),
        getThreadFactory(),
        getRejectedPolicy()
    ) : null;

    public static CompletableFuture<Void> tick(ServerLevel level) {
        final List<ChunkMap.TrackedEntity> trackers = new ArrayList<>();
        level.chunkSource.tickingRegions.forEach(region -> region.forEachTrackedEntity(entity -> {
            if (entity == null || entity.isRemoved()) return;
            final ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
            if (tracker != null) {
                trackers.add(tracker);
            }
        }));
        return sendChanges(trackers);
    }

    public static CompletableFuture<Void> sendChanges(List<ChunkMap.TrackedEntity> trackers) {
        if (TRACKER_EXECUTOR == null || TRACKER_EXECUTOR.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        if (trackers.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return sendChangesAsync(new ArrayList<>(trackers));
    }

    public static boolean requiresRegionThreadTracker() {
        return DivineConfig.AsyncCategory.multithreadedCompatModeEnabled
            || PlayerTrackEntityEvent.getHandlerList().getRegisteredListeners().length != 0
            || BroadcastPacketEvent.getHandlerList().getRegisteredListeners().length != 0;
    }

    private static CompletableFuture<Void> sendChangesAsync(List<ChunkMap.TrackedEntity> trackers) {
        final CompletableFuture<Void> future = new CompletableFuture<>();
        TRACKER_EXECUTOR.execute(() -> {
            try {
                for (final ChunkMap.TrackedEntity tracker : trackers) {
                    if (tracker == null) continue;
                    tracker.serverEntity.sendChanges();
                }
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
                LOGGER.error("Error occurred while executing async tracker task.", throwable);
            }
        });
        return future;
    }

    // Original ChunkMap#newTrackerTick of Paper
    // Just for diff usage for future update
    @SuppressWarnings("DuplicatedCode")
    private static void tickOriginal(ServerLevel level) {
        final List<Entity> trackerEntities = new ArrayList<>();
        level.chunkSource.tickingRegions.forEach(region -> region.forEachTrackedEntity(trackerEntities::add));
        for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
            final Entity entity = trackerEntities.get(i);
            final ChunkMap.TrackedEntity tracker = ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity) entity).moonrise$getTrackedEntity();
            if (tracker == null) {
                continue;
            }
            ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker).moonrise$tick(((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkData().nearbyPlayers);
            if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity) tracker).moonrise$hasPlayers()
                || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity) entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
                tracker.serverEntity.sendChanges();
            }
        }
    }

    private static int getCorePoolSize() {
        return 1;
    }

    private static int getMaxPoolSize() {
        return DivineConfig.AsyncCategory.asyncEntityTrackerMaxThreads;
    }

    private static long getKeepAliveTime() {
        return DivineConfig.AsyncCategory.asyncEntityTrackerKeepalive;
    }

    private static BlockingQueue<Runnable> getQueueImpl() {
        final int queueCapacity = DivineConfig.AsyncCategory.asyncEntityTrackerQueueSize;

        return new LinkedBlockingQueue<>(queueCapacity);
    }

    private static @NotNull ThreadFactory getThreadFactory() {
        return new NamedAgnosticThreadFactory<>(THREAD_PREFIX, TickThread::new, Thread.NORM_PRIORITY - 2);
    }

    private static @NotNull RejectedExecutionHandler getRejectedPolicy() {
        return (rejectedTask, executor) -> {
            BlockingQueue<Runnable> workQueue = executor.getQueue();

            if (!executor.isShutdown()) {
                if (!workQueue.isEmpty()) {
                    List<Runnable> pendingTasks = new ArrayList<>(workQueue.size());

                    workQueue.drainTo(pendingTasks);

                    for (Runnable pendingTask : pendingTasks) {
                        pendingTask.run();
                    }
                }

                rejectedTask.run();
            }

            if (System.currentTimeMillis() - lastWarnMillis > 30000L) {
                LOGGER.warn("Async entity tracker is busy! Tracking tasks will be done in the server thread. Increasing max-threads in DivineMC config may help.");
                lastWarnMillis = System.currentTimeMillis();
            }
        };
    }

    public static void shutdownExecutor() {
        if (TRACKER_EXECUTOR == null) {
            return;
        }

        LOGGER.info("Shutting down Async Entity Tracker executor");
        TRACKER_EXECUTOR.shutdown();
        try {
            if (!TRACKER_EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                TRACKER_EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException ex) {
            TRACKER_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static class MultithreadedTrackerThread extends Thread {
        public MultithreadedTrackerThread(Runnable runnable) {
            super(runnable);
        }
    }
}
