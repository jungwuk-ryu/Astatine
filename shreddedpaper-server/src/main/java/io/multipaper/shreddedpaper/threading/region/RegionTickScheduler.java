package io.multipaper.shreddedpaper.threading.region;

import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperTickThread;
import io.multipaper.shreddedpaper.threading.region.events.RegionTickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionTickScheduler {

    public static final long TIME_BETWEEN_TICKS_NANOS = 50_000_000L;
    private static final long MERGE_PROBE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1L);

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final AtomicLong HANDLE_IDS = new AtomicLong();
    private static volatile RegionTickScheduler global;

    private final ConcurrentHashMap<RegionKey, RegionHandle> regions = new ConcurrentHashMap<>();
    private final DelayQueue<RegionHandle> normalQueue = new DelayQueue<>();
    private final DelayQueue<RegionHandle> degradedQueue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private RegionTickScheduler() {
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        final int totalThreads = Math.max(1, ShreddedPaperTickThread.THREAD_COUNT);
        final int configuredDegraded = config.degradedRegionThreads;
        final int degradedThreads = totalThreads <= 1
                ? 0
                : Math.min(totalThreads - 1, configuredDegraded < 0 ? Math.max(1, totalThreads / 8) : Math.max(0, configuredDegraded));
        final int normalThreads = Math.max(1, totalThreads - degradedThreads);

        for (int i = 0; i < normalThreads; i++) {
            this.startWorker(false, i);
        }
        for (int i = 0; i < degradedThreads; i++) {
            this.startWorker(true, i);
        }

        LOGGER.info("Started independent region tick scheduler with {} normal worker(s) and {} degraded worker(s)", normalThreads, degradedThreads);
    }

    public static RegionTickScheduler get() {
        RegionTickScheduler scheduler = global;
        if (scheduler == null) {
            synchronized (RegionTickScheduler.class) {
                scheduler = global;
                if (scheduler == null) {
                    global = scheduler = new RegionTickScheduler();
                }
            }
        }
        return scheduler;
    }

    public static RegionTickScheduler getIfStarted() {
        return global;
    }

    public static void shutdownGlobal() {
        final RegionTickScheduler scheduler = global;
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void registerRegion(
            final ServerLevel level,
            final LevelChunkRegion region,
            final ShreddedPaperChunkTicker ticker,
            final ShreddedPaperChunkTicker.ScheduledTickContext tickContext
    ) {
        final RegionRuntimeState state = region.getRuntimeState();
        final RegionKey key = new RegionKey(level.uuid, state.ownerId());
        state.attach(region);
        this.regions.compute(key, (ignored, previous) -> {
            if (previous != null && !previous.retired.get()) {
                previous.ticker = ticker;
                previous.level = level;
                previous.nextContext.set(tickContext);
                return previous;
            }

            final long firstStart = System.nanoTime() + TIME_BETWEEN_TICKS_NANOS;
            final RegionHandle created = new RegionHandle(key, level, state, ticker, tickContext, firstStart);
            this.enqueue(created);
            return created;
        });
    }

    public List<RegionTickSnapshot> snapshots() {
        return this.regions.values().stream()
                .filter(handle -> !handle.retired.get())
                .map(RegionHandle::snapshot)
                .sorted(Comparator.comparingDouble(RegionTickSnapshot::ewmaMspt).reversed())
                .toList();
    }

    private void startWorker(final boolean degradedOnly, final int id) {
        final Thread worker = new ShreddedPaperTickThread(() -> this.workerLoop(degradedOnly), degradedOnly
                ? "ShreddedPaperRegionDegraded-%d"
                : "ShreddedPaperRegionNormal-%d");
        worker.setDaemon(true);
        worker.setUncaughtExceptionHandler((thread, throwable) -> LOGGER.error("Uncaught exception in {}", thread.getName(), throwable));
        worker.start();
        this.workers.add(worker);
    }

    private void workerLoop(final boolean degradedOnly) {
        while (this.running.get()) {
            try {
                final RegionHandle handle = degradedOnly ? this.degradedQueue.take() : this.takeNormalOrSteal();
                if (handle == null || handle.retired.get()) {
                    continue;
                }
                handle.runOneTick();
            } catch (final InterruptedException interrupted) {
                if (!this.running.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } catch (final Throwable throwable) {
                LOGGER.error("Independent region scheduler worker failed", throwable);
            }
        }
    }

    private RegionHandle takeNormalOrSteal() throws InterruptedException {
        final RegionHandle normal = this.normalQueue.poll(1L, TimeUnit.MILLISECONDS);
        if (normal != null) {
            return normal;
        }
        return this.degradedQueue.poll();
    }

    private void enqueue(final RegionHandle handle) {
        final RegionLoadClass loadClass = handle.state.overloadController().loadClass();
        if (loadClass == RegionLoadClass.QUARANTINED) {
            LOGGER.warn("Region {} {} is quarantined and will not be requeued", handle.level.getWorld().getName(), handle.state.regionPos());
            return;
        }
        if (loadClass == RegionLoadClass.DEGRADED) {
            this.degradedQueue.offer(handle);
        } else {
            this.normalQueue.offer(handle);
        }
    }

    private void shutdown() {
        if (!this.running.compareAndSet(true, false)) {
            return;
        }
        for (final Thread worker : this.workers) {
            worker.interrupt();
        }
        for (final Thread worker : this.workers) {
            try {
                worker.join(TimeUnit.SECONDS.toMillis(5L));
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        this.normalQueue.clear();
        this.degradedQueue.clear();
        this.regions.clear();
        synchronized (RegionTickScheduler.class) {
            if (global == this) {
                global = null;
            }
        }
    }

    private record RegionKey(UUID worldId, long regionKey) {
    }

    public record RegionTickSnapshot(
            String world,
            RegionPos regionPos,
            RegionLoadClass loadClass,
            double ewmaMspt,
            double ewmaScheduleLagMs,
            int mailboxDepth,
            long rejectedTasks,
            long nextStartNanos
    ) {
    }

    private final class RegionHandle implements Delayed {

        private final long id = HANDLE_IDS.getAndIncrement();
        private final RegionKey key;
        private final AtomicBoolean ticking = new AtomicBoolean();
        private final AtomicBoolean retired = new AtomicBoolean();
        private volatile ServerLevel level;
        private final RegionRuntimeState state;
        private volatile ShreddedPaperChunkTicker ticker;
        private volatile ShreddedPaperChunkTicker.ScheduledTickContext scheduledContext;
        // Latest global tick snapshot, not a queued tick input. Independent regions intentionally do not catch up missed ticks.
        private final AtomicReference<ShreddedPaperChunkTicker.ScheduledTickContext> nextContext = new AtomicReference<>();
        private volatile long scheduledStartNanos;
        private volatile long idealStartNanos;
        private volatile long nextMergeProbeNanos;
        private long lockContentionBackoffNanos = TimeUnit.MILLISECONDS.toNanos(1L);

        private RegionHandle(
                final RegionKey key,
                final ServerLevel level,
                final RegionRuntimeState state,
                final ShreddedPaperChunkTicker ticker,
                final ShreddedPaperChunkTicker.ScheduledTickContext tickContext,
                final long firstStart
        ) {
            this.key = key;
            this.level = level;
            this.state = state;
            this.ticker = ticker;
            this.scheduledContext = tickContext;
            this.scheduledStartNanos = firstStart;
            this.idealStartNanos = firstStart;
            this.nextMergeProbeNanos = firstStart + (this.id % 20L) * TIME_BETWEEN_TICKS_NANOS;
        }

        private void runOneTick() {
            if (!this.ticking.compareAndSet(false, true)) {
                return;
            }

            final LevelChunkRegion region = this.state.currentRegion();
            if (region == null) {
                RegionTickScheduler.this.regions.remove(this.key, this);
                this.retired.set(true);
                return;
            }
            final long now = System.nanoTime();
            if (now >= this.nextMergeProbeNanos) {
                this.level.chunkSource.tickingRegions.mergeNearbyOwnersQuiescent(region.getOwner(), ShreddedPaperRegionLocker.REGION_LOCK_RADIUS);
                this.nextMergeProbeNanos = now + MERGE_PROBE_INTERVAL_NANOS;
            }
            final long actualStart = System.nanoTime();
            final long scheduledStart = this.scheduledStartNanos;
            final long scheduleLag = Math.max(0L, actualStart - scheduledStart);
            final RegionTickBudget budget = this.state.overloadController().beginTick(actualStart);
            long deferred = 0L;
            Throwable failure = null;
            ShreddedPaperRegionLocker.RegionLock ownerLock = null;

            try {
                final List<RegionPos> ownerCells = region.getOwner().cellPositionsSnapshot();
                final List<RegionPos> isolationCells = region.getOwner().isolationCellPositionsSnapshot(ShreddedPaperRegionLocker.REGION_LOCK_RADIUS);
                ownerLock = this.level.chunkScheduler.getRegionLocker().internalTryTakeExactLockNow(ownerCells, isolationCells);
                if (ownerLock != null) {
                    RegionTickBudget.setCurrent(budget);
                    this.ticker.tickRegionFromIndependentScheduler(
                            this.level,
                            region,
                            budget,
                            this.scheduledContext,
                            scheduledStart
                    );
                }
            } catch (final Throwable throwable) {
                failure = throwable;
                MinecraftServer.getServer().moonrise$setChunkSystemCrash(new RuntimeException(
                        "Independent region tick failed for " + this.level.getWorld().getName() + " " + this.state.regionPos(),
                        throwable
                ));
                this.retired.set(true);
            } finally {
                deferred = budget.totalDeferred();
                RegionTickBudget.clearCurrent();
                if (ownerLock != null) {
                    ownerLock.unlock();
                }
                this.ticking.set(false);
            }

            if (ownerLock == null) {
                if (!this.retired.get()) {
                    this.scheduledStartNanos = System.nanoTime() + this.lockContentionBackoffNanos;
                    this.lockContentionBackoffNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(50L), this.lockContentionBackoffNanos << 1);
                    RegionTickScheduler.this.enqueue(this);
                }
                return;
            }
            this.lockContentionBackoffNanos = TimeUnit.MILLISECONDS.toNanos(1L);

            final long tickEnd = System.nanoTime();
            final long wallNanos = Math.max(0L, tickEnd - actualStart);
            this.state.overloadController().recordTick(wallNanos, scheduleLag, this.state.mailbox().depth(), deferred);
            this.commitTickEvent(scheduledStart, actualStart, wallNanos, scheduleLag, deferred);

            if (failure != null || this.retired.get() || region.isEmpty()) {
                RegionTickScheduler.this.regions.remove(this.key, this);
                this.retired.set(true);
                return;
            }

            final long periodsAhead = Math.max(1L, ((actualStart - this.idealStartNanos) / TIME_BETWEEN_TICKS_NANOS) + 1L);
            this.idealStartNanos += periodsAhead * TIME_BETWEEN_TICKS_NANOS;
            this.scheduledStartNanos = Math.max(tickEnd, this.idealStartNanos);
            final ShreddedPaperChunkTicker.ScheduledTickContext next = this.nextContext.getAndSet(null);
            if (next != null) {
                this.scheduledContext = next;
            }
            RegionTickScheduler.this.enqueue(this);
        }

        private RegionTickSnapshot snapshot() {
            final RegionOverloadController overload = this.state.overloadController();
            return new RegionTickSnapshot(
                    this.level.getWorld().getName(),
                    this.state.regionPos(),
                    overload.loadClass(),
                    overload.ewmaMspt(),
                    overload.ewmaScheduleLagMs(),
                    this.state.mailbox().depth(),
                    this.state.mailbox().rejected(),
                    this.scheduledStartNanos
            );
        }

        private void commitTickEvent(
                final long scheduledStart,
                final long actualStart,
                final long wallNanos,
                final long scheduleLag,
                final long deferred
        ) {
            final RegionTickEvent event = new RegionTickEvent();
            event.world = this.level.getWorld().getName();
            event.regionX = this.state.regionPos().x;
            event.regionZ = this.state.regionPos().z;
            event.loadClass = this.state.overloadController().loadClass().name();
            event.scheduledStartNanos = scheduledStart;
            event.actualStartNanos = actualStart;
            event.wallNanos = wallNanos;
            event.scheduleLagNanos = scheduleLag;
            event.mailboxDepth = this.state.mailbox().depth();
            event.deferredWork = deferred;
            event.commit();
        }

        @Override
        public long getDelay(final TimeUnit unit) {
            return unit.convert(this.scheduledStartNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(final Delayed other) {
            if (other == this) {
                return 0;
            }
            final RegionHandle handle = (RegionHandle) other;
            final int timeCompare = Long.compare(this.scheduledStartNanos, handle.scheduledStartNanos);
            return timeCompare != 0 ? timeCompare : Long.compare(this.id, handle.id);
        }
    }
}
