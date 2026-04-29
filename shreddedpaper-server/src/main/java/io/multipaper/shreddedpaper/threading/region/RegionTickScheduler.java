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
    private static final long SHUTDOWN_JOIN_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);
    private static final long SHUTDOWN_JOIN_SLICE_MILLIS = 100L;

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final AtomicLong HANDLE_IDS = new AtomicLong();
    private static volatile RegionTickScheduler global;

    private final ConcurrentHashMap<RegionKey, RegionHandle> regions = new ConcurrentHashMap<>();
    private final DelayQueue<RegionHandle> normalQueue = new DelayQueue<>();
    private final DelayQueue<RegionHandle> degradedQueue = new DelayQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final boolean normalWorkersMayStealDegraded;

    private RegionTickScheduler() {
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        final int totalThreads = Math.max(1, ShreddedPaperTickThread.THREAD_COUNT);
        final int configuredDegraded = config.degradedRegionThreads;
        final int degradedThreads = totalThreads <= 1
                ? 0
                : Math.min(totalThreads - 1, configuredDegraded < 0 ? Math.max(1, totalThreads / 8) : Math.max(0, configuredDegraded));
        final int normalThreads = Math.max(1, totalThreads - degradedThreads);
        this.normalWorkersMayStealDegraded = degradedThreads == 0;

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
        this.registerRegion(level, region, ticker, tickContext, System.nanoTime() + TIME_BETWEEN_TICKS_NANOS);
    }

    public void registerRegionForCurrentTick(
            final ServerLevel level,
            final LevelChunkRegion region,
            final ShreddedPaperChunkTicker ticker,
            final ShreddedPaperChunkTicker.ScheduledTickContext tickContext,
            final long scheduledStartNanos
    ) {
        this.registerRegion(level, region, ticker, tickContext, Math.max(System.nanoTime(), scheduledStartNanos));
    }

    public void activateSplitRegionForCurrentTick(
            final ServerLevel level,
            final LevelChunkRegion region,
            final ShreddedPaperChunkTicker ticker,
            final ShreddedPaperChunkTicker.ScheduledTickContext tickContext,
            final long scheduledStartNanos
    ) {
        this.registerRegionForCurrentTick(level, region, ticker, tickContext, scheduledStartNanos);
        region.getOwner().armScheduler();
    }


    private void registerRegion(
            final ServerLevel level,
            final LevelChunkRegion region,
            final ShreddedPaperChunkTicker ticker,
            final ShreddedPaperChunkTicker.ScheduledTickContext tickContext,
            final long firstStart
    ) {
        if (!this.running.get()) {
            return;
        }
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

            final RegionHandle created = new RegionHandle(key, level, state, ticker, tickContext, firstStart);
            this.enqueue(created);
            return created;
        });
    }

    public List<RegionTickSnapshot> snapshots() {
        return this.regions.values().stream()
                .filter(handle -> !handle.retired.get())
                .map(RegionHandle::snapshot)
                .sorted(Comparator.comparingDouble(RegionTickSnapshot::sortScore).reversed())
                .toList();
    }

    public List<LongRunningRegionTick> longRunningTicks(final long thresholdNanos) {
        final long now = System.nanoTime();
        final List<LongRunningRegionTick> stuck = new ArrayList<>();
        for (final RegionHandle handle : this.regions.values()) {
            final LongRunningRegionTick tick = handle.longRunningTick(now, thresholdNanos);
            if (tick != null) {
                stuck.add(tick);
            }
        }
        stuck.sort(Comparator.comparingLong(LongRunningRegionTick::elapsedNanos).reversed());
        return stuck;
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
        if (!this.normalWorkersMayStealDegraded) {
            return this.normalQueue.take();
        }
        final RegionHandle normal = this.normalQueue.poll(1L, TimeUnit.MILLISECONDS);
        if (normal != null) {
            return normal;
        }
        return this.degradedQueue.poll();
    }

    private void enqueue(final RegionHandle handle) {
        if (!this.running.get()) {
            return;
        }
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
        final long deadline = System.nanoTime() + SHUTDOWN_JOIN_TIMEOUT_NANOS;
        boolean interrupted = false;
        for (final Thread worker : this.workers) {
            while (worker.isAlive()) {
                final long remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    break;
                }
                try {
                    worker.join(Math.max(1L, Math.min(SHUTDOWN_JOIN_SLICE_MILLIS, TimeUnit.NANOSECONDS.toMillis(remaining))));
                } catch (final InterruptedException ignored) {
                    interrupted = true;
                    break;
                }
            }
            if (interrupted || System.nanoTime() >= deadline) {
                break;
            }
        }
        final List<String> liveWorkers = this.workers.stream()
                .filter(Thread::isAlive)
                .map(thread -> thread.getName() + "/" + thread.getState())
                .toList();
        if (!liveWorkers.isEmpty()) {
            LOGGER.warn("Independent region scheduler still has live worker(s) after {} ms shutdown wait: {}",
                    TimeUnit.NANOSECONDS.toMillis(SHUTDOWN_JOIN_TIMEOUT_NANOS), liveWorkers);
        }
        RegionChunkExecutorLimiter.shutdownEmergencyExecutors();
        this.normalQueue.clear();
        this.degradedQueue.clear();
        this.regions.clear();
        synchronized (RegionTickScheduler.class) {
            if (global == this) {
                global = null;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
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
            double mailboxClassPressure,
            int chunkIoInFlight,
            int chunkIoDeferred,
            int chunkIoCapacity,
            double chunkIoPressure,
            long chunkIoDowngraded,
            long chunkIoRejected,
            int chunkIoExecutorWaiting,
            int chunkIoExecutorBacklogQueued,
            int chunkIoExecutorBacklogCapacity,
            double chunkIoExecutorBacklogPressure,
            long chunkIoExecutorBacklogBackpressure,
            long chunkIoExecutorBacklogDeferred,
            int chunkIoExecutorBacklogEmergencyInFlight,
            long chunkIoExecutorBacklogEmergency,
            int chunkIoExecutorBacklogEmergencyRetries,
            long chunkIoExecutorBacklogEmergencyRejected,
            int chunkIoExecutorInFlight,
            int chunkIoExecutorDeferred,
            int chunkIoExecutorCapacity,
            double chunkIoExecutorPressure,
            long chunkIoExecutorDowngraded,
            long chunkIoExecutorRejected,
            int chunkIoExecutorOverflowInFlight,
            int chunkIoExecutorOverflowCapacity,
            double chunkIoExecutorOverflowPressure,
            long chunkIoExecutorOverflowAdmitted,
            long chunkIoExecutorOverflowBackpressure,
            int chunkIoExecutorBackpressureWaiters,
            int chunkIoExecutorBackpressureCapacity,
            double chunkIoExecutorBackpressurePressure,
            long rejectedTasks,
            long deferredWork,
            long nextStartNanos
    ) {
        private double sortScore() {
            return Math.max(
                    Math.max(Math.max(this.ewmaMspt, this.mailboxClassPressure * 50.0D), this.deferredWork > 0L ? 50.0D : 0.0D),
                    Math.max(this.chunkIoPressure, this.chunkIoExecutorPressure) * 50.0D
            );
        }
    }

    public record LongRunningRegionTick(
            String world,
            RegionPos regionPos,
            long elapsedNanos,
            String threadName,
            long threadId
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
        private volatile Thread runningThread;
        private volatile long runningTickStartNanos;
        private List<LevelChunkRegion> pendingSplitRegions = List.of();
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

            this.runningThread = Thread.currentThread();
            this.runningTickStartNanos = System.nanoTime();
            boolean activeTickCleared = false;
            try {
                final LevelChunkRegion region = this.state.currentRegion();
                if (region == null) {
                    this.retire();
                    return;
                }
                if (this.retireIfDetached(region)) {
                    return;
                }
                final long now = System.nanoTime();
                final boolean probeRegionLayout = now >= this.nextMergeProbeNanos;
                if (probeRegionLayout) {
                    this.level.chunkSource.tickingRegions.mergeNearbyOwnersQuiescent(region.getOwner(), ShreddedPaperRegionLocker.REGION_LOCK_RADIUS);
                    if (this.retireIfDetached(region)) {
                        return;
                    }
                    if (this.pendingSplitRegions.isEmpty()) {
                        this.pendingSplitRegions = this.level.chunkSource.tickingRegions.splitDisconnectedOwnerQuiescent(region.getOwner(), ShreddedPaperRegionLocker.REGION_LOCK_RADIUS);
                        if (this.retireIfDetached(region)) {
                            return;
                        }
                    }
                    this.nextMergeProbeNanos = now + MERGE_PROBE_INTERVAL_NANOS;
                }
                final long actualStart = System.nanoTime();
                final long scheduledStart = this.scheduledStartNanos;
                final long scheduleLag = Math.max(0L, actualStart - scheduledStart);
                final RegionTickBudget budget = this.state.overloadController().beginTick(actualStart);
                long deferred = 0L;
                Throwable failure = null;
                ShreddedPaperRegionLocker.RegionLock ownerLock = null;
                boolean requeueAfterLayoutChange = false;

                try {
                    final List<RegionPos> ownerCells = region.getOwner().cellPositionsSnapshot();
                    if (ownerCells.isEmpty()) {
                        this.retire();
                        return;
                    }
                    final List<RegionPos> isolationCells = region.getOwner().isolationCellPositionsSnapshot(ShreddedPaperRegionLocker.REGION_LOCK_RADIUS);
                    if (isolationCells.isEmpty()) {
                        if (this.retireIfDetached(region) || region.getOwner().cellPositionsSnapshot().isEmpty()) {
                            this.retire();
                        } else {
                            requeueAfterLayoutChange = true;
                        }
                    } else if ((ownerLock = this.level.chunkScheduler.getRegionLocker().internalTryTakeExactLockNow(ownerCells, isolationCells)) != null) {
                        if (this.retireIfDetached(region)) {
                            return;
                        }
                        final List<RegionPos> currentOwnerCells = region.getOwner().cellPositionsSnapshot();
                        if (currentOwnerCells.isEmpty()) {
                            this.retire();
                            return;
                        }
                        if (!currentOwnerCells.equals(ownerCells)) {
                            requeueAfterLayoutChange = true;
                        } else {
                            RegionTickBudget.setCurrent(budget);
                            this.ticker.tickRegionFromIndependentScheduler(
                                    this.level,
                                    region,
                                    budget,
                                    this.scheduledContext,
                                    scheduledStart
                            );
                        }
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
                    this.runningTickStartNanos = 0L;
                    this.runningThread = null;
                    this.ticking.set(false);
                    activeTickCleared = true;
                }

                if (requeueAfterLayoutChange) {
                    this.requeueAfterOwnerLayoutChange();
                    return;
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
                final RegionChunkIoTracker.Snapshot chunkIo = this.state.chunkIoTracker().snapshot();
                this.state.overloadController().recordTick(
                        wallNanos,
                        scheduleLag,
                        this.state.mailbox().depth(),
                        this.state.mailbox().maxClassPressure(),
                        deferred,
                        chunkIo
                );
                this.commitTickEvent(scheduledStart, actualStart, wallNanos, scheduleLag, deferred, chunkIo);

                this.activatePendingSplitRegions(scheduledStart);
                if (failure != null || this.retired.get() || region.isEmpty()) {
                    this.retire();
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
            } finally {
                if (!activeTickCleared) {
                    this.runningTickStartNanos = 0L;
                    this.runningThread = null;
                    this.ticking.set(false);
                }
            }
        }

        private boolean retireIfDetached(final LevelChunkRegion region) {
            if (this.state.currentRegion() != region || region.getOwner().region() != region || region.getOwner().cellCount() == 0) {
                this.retire();
                return true;
            }
            return false;
        }

        private void retire() {
            RegionTickScheduler.this.regions.remove(this.key, this);
            this.retired.set(true);
            this.ticking.set(false);
            this.pendingSplitRegions = List.of();
        }

        private void requeueAfterOwnerLayoutChange() {
            if (this.retired.get()) {
                return;
            }
            this.scheduledStartNanos = System.nanoTime() + this.lockContentionBackoffNanos;
            this.lockContentionBackoffNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(50L), this.lockContentionBackoffNanos << 1);
            RegionTickScheduler.this.enqueue(this);
        }

        private void activatePendingSplitRegions(final long scheduledStart) {
            for (final LevelChunkRegion splitRegion : this.pendingSplitRegions) {
                RegionTickScheduler.this.activateSplitRegionForCurrentTick(this.level, splitRegion, this.ticker, this.scheduledContext, scheduledStart);
            }
            this.pendingSplitRegions = List.of();
        }

        private RegionTickSnapshot snapshot() {
            final RegionOverloadController overload = this.state.overloadController();
            final RegionChunkIoTracker.Snapshot chunkIo = this.state.chunkIoTracker().snapshot();
            return new RegionTickSnapshot(
                    this.level.getWorld().getName(),
                    this.state.regionPos(),
                    overload.loadClass(),
                    overload.ewmaMspt(),
                    overload.ewmaScheduleLagMs(),
                    this.state.mailbox().depth(),
                    this.state.mailbox().maxClassPressure(),
                    overload.lastChunkIoInFlight(),
                    overload.lastChunkIoDeferred(),
                    chunkIo.capacity(),
                    overload.lastChunkIoPressure(),
                    chunkIo.downgraded(),
                    chunkIo.rejected(),
                    chunkIo.executorWaitingTasks(),
                    chunkIo.executorBacklogQueuedTasks(),
                    chunkIo.executorBacklogCapacity(),
                    chunkIo.executorBacklogPressure(),
                    chunkIo.executorBacklogBackpressure(),
                    chunkIo.executorBacklogDeferred(),
                    chunkIo.executorBacklogEmergencyInFlight(),
                    chunkIo.executorBacklogEmergency(),
                    chunkIo.executorBacklogEmergencyRetries(),
                    chunkIo.executorBacklogEmergencyRejected(),
                    chunkIo.executorInFlight(),
                    chunkIo.executorDeferredRetries(),
                    chunkIo.executorCapacity(),
                    chunkIo.executorPressure(),
                    chunkIo.executorDowngraded(),
                    chunkIo.executorRejected(),
                    chunkIo.executorOverflowInFlight(),
                    chunkIo.executorOverflowCapacity(),
                    chunkIo.executorOverflowPressure(),
                    chunkIo.executorOverflowAdmitted(),
                    chunkIo.executorOverflowBackpressure(),
                    chunkIo.executorBackpressuredRetries(),
                    chunkIo.executorBackpressureCapacity(),
                    chunkIo.executorBackpressurePressure(),
                    this.state.mailbox().rejected(),
                    overload.lastDeferredWork(),
                    this.scheduledStartNanos
            );
        }

        private LongRunningRegionTick longRunningTick(final long nowNanos, final long thresholdNanos) {
            final long started = this.runningTickStartNanos;
            final Thread thread = this.runningThread;
            if (started <= 0L || thread == null || this.retired.get()) {
                return null;
            }

            final long elapsed = Math.max(0L, nowNanos - started);
            if (elapsed < thresholdNanos) {
                return null;
            }

            return new LongRunningRegionTick(
                    this.level.getWorld().getName(),
                    this.state.regionPos(),
                    elapsed,
                    thread.getName(),
                    thread.threadId()
            );
        }

        private void commitTickEvent(
                final long scheduledStart,
                final long actualStart,
                final long wallNanos,
                final long scheduleLag,
                final long deferred,
                final RegionChunkIoTracker.Snapshot chunkIo
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
            event.criticalSystemQueued = this.state.mailbox().queued(RegionTaskClass.CRITICAL_SYSTEM);
            event.playerActionQueued = this.state.mailbox().queued(RegionTaskClass.PLAYER_ACTION);
            event.ownerHandoffQueued = this.state.mailbox().queued(RegionTaskClass.OWNER_HANDOFF);
            event.chunkIoLoadQueued = this.state.mailbox().queued(RegionTaskClass.CHUNK_IO_LOAD);
            event.chunkIoSaveQueued = this.state.mailbox().queued(RegionTaskClass.CHUNK_IO_SAVE);
            event.chunkIoInFlight = chunkIo.inFlight();
            event.chunkIoDeferred = chunkIo.deferredRetries();
            event.chunkIoCapacity = chunkIo.capacity();
            event.chunkIoRejected = chunkIo.rejected();
            event.chunkIoDowngraded = chunkIo.downgraded();
            event.chunkIoPressure = chunkIo.requestPressure();
            event.chunkIoExecutorWaiting = chunkIo.executorWaitingTasks();
            event.chunkIoExecutorBacklogQueued = chunkIo.executorBacklogQueuedTasks();
            event.chunkIoExecutorBacklogCapacity = chunkIo.executorBacklogCapacity();
            event.chunkIoExecutorBacklogBackpressure = chunkIo.executorBacklogBackpressure();
            event.chunkIoExecutorBacklogDeferred = chunkIo.executorBacklogDeferred();
            event.chunkIoExecutorBacklogEmergencyInFlight = chunkIo.executorBacklogEmergencyInFlight();
            event.chunkIoExecutorBacklogEmergency = chunkIo.executorBacklogEmergency();
            event.chunkIoExecutorBacklogEmergencyRetries = chunkIo.executorBacklogEmergencyRetries();
            event.chunkIoExecutorBacklogEmergencyRejected = chunkIo.executorBacklogEmergencyRejected();
            event.chunkIoExecutorBacklogPressure = chunkIo.executorBacklogPressure();
            event.chunkIoExecutorInFlight = chunkIo.executorInFlight();
            event.chunkIoExecutorDeferred = chunkIo.executorDeferredRetries();
            event.chunkIoExecutorCapacity = chunkIo.executorCapacity();
            event.chunkIoExecutorRejected = chunkIo.executorRejected();
            event.chunkIoExecutorDowngraded = chunkIo.executorDowngraded();
            event.chunkIoExecutorPressure = chunkIo.executorPressure();
            event.chunkIoExecutorOverflowInFlight = chunkIo.executorOverflowInFlight();
            event.chunkIoExecutorOverflowCapacity = chunkIo.executorOverflowCapacity();
            event.chunkIoExecutorOverflowAdmitted = chunkIo.executorOverflowAdmitted();
            event.chunkIoExecutorOverflowBackpressure = chunkIo.executorOverflowBackpressure();
            event.chunkIoExecutorOverflowPressure = chunkIo.executorOverflowPressure();
            event.chunkIoExecutorBackpressureWaiters = chunkIo.executorBackpressuredRetries();
            event.chunkIoExecutorBackpressureCapacity = chunkIo.executorBackpressureCapacity();
            event.chunkIoExecutorBackpressurePressure = chunkIo.executorBackpressurePressure();
            event.pluginQueued = this.state.mailbox().queued(RegionTaskClass.PLUGIN);
            event.trackerBroadcastQueued = this.state.mailbox().queued(RegionTaskClass.TRACKER_BROADCAST);
            event.explosionPhysicsQueued = this.state.mailbox().queued(RegionTaskClass.EXPLOSION_PHYSICS);
            event.mailboxClassPressure = this.state.mailbox().maxClassPressure();
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
