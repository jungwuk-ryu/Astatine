package io.multipaper.shreddedpaper.threading.region;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.events.ChunkRequestEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.slf4j.Logger;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionChunkIoTracker {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final long EXECUTOR_BACKPRESSURE_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(1L);
    private static final int EXECUTOR_BACKPRESSURE_DRAIN_BATCH = 64;
    private static final ScheduledExecutorService EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon(true).name("AstatineChunkExecutorBackpressureRetry", 0).factory()
    );

    private final ServerLevel level;
    private final RegionPos regionPos;
    private final RegionOverloadController overloadController;
    private final LoadAdmissionCounters loadCounters = new LoadAdmissionCounters();
    private final ExecutorAdmissionCounters executorCounters = new ExecutorAdmissionCounters();
    private final ExecutorBacklogCounters executorBacklogCounters = new ExecutorBacklogCounters();
    private final ExecutorOverflowCounters executorOverflowCounters = new ExecutorOverflowCounters();
    private final ExecutorBackpressureCounters executorBackpressureCounters = new ExecutorBackpressureCounters();
    private final ExecutorRetryQueues executorRetryQueues = new ExecutorRetryQueues();

    public RegionChunkIoTracker(
            final ServerLevel level,
            final RegionPos regionPos,
            final RegionOverloadController overloadController
    ) {
        this.level = level;
        this.regionPos = regionPos;
        this.overloadController = overloadController;
    }

    public Admission tryAcquire(
            final int chunkX,
            final int chunkZ,
            final ChunkStatus status,
            final boolean addTicket,
            final Priority requestedPriority
    ) {
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking || requestedPriority == Priority.BLOCKING) {
            return Admission.bypass(requestedPriority);
        }

        final RegionLoadClass loadClass = this.overloadController.loadClass();
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        final int cap = this.capFor(loadClass, config);

        if (addTicket && cap > 0 && this.isExecutorBacklogSaturated()) {
            final int queued = this.loadCounters.deferredRetries.incrementAndGet();
            final long count = this.loadCounters.deferred.incrementAndGet();
            if (ChunkRequestEvent.shouldCommitSample(count)) {
                this.commitEvent("async-load-deferred-executor-backlog", chunkX, chunkZ, status, count, false, this.loadCounters.inFlight.get(), cap, requestedPriority);
            }
            return Admission.deferred(queued, cap);
        }

        if (!addTicket || cap <= 0) {
            final Priority admittedPriority = this.adjustPriority(requestedPriority, loadClass, config);
            this.recordDowngradeIfNeeded(chunkX, chunkZ, status, requestedPriority, admittedPriority);
            return Admission.bypass(admittedPriority);
        }

        final int current = this.loadCounters.inFlight.incrementAndGet();
        if (current <= cap) {
            final long count = this.loadCounters.admitted.incrementAndGet();
            final Priority admittedPriority = this.adjustPriority(requestedPriority, loadClass, config);
            this.recordDowngradeIfNeeded(chunkX, chunkZ, status, requestedPriority, admittedPriority);
            if (this.shouldSamplePressure(current, cap, count)) {
                this.commitEvent("async-load-admitted", chunkX, chunkZ, status, count, false, current, cap, admittedPriority);
            }
            return Admission.acquired(admittedPriority);
        }

        this.loadCounters.inFlight.decrementAndGet();
        final int queued = this.loadCounters.deferredRetries.incrementAndGet();
        final long count = this.loadCounters.deferred.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-deferred", chunkX, chunkZ, status, count, false, current - 1, cap, requestedPriority);
        }
        return Admission.deferred(queued, cap);
    }

    public Admission tryAcquireExecutor(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority requestedPriority
    ) {
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking || requestedPriority == Priority.BLOCKING) {
            return Admission.bypass(requestedPriority);
        }

        final RegionLoadClass loadClass = this.overloadController.loadClass();
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        final int cap = this.executorCapFor(loadClass, config);

        final int current = this.executorCounters.inFlight.incrementAndGet();
        if (current <= cap) {
            final long count = this.executorCounters.admitted.incrementAndGet();
            final Priority admittedPriority = this.adjustExecutorPriority(requestedPriority, loadClass, config);
            this.recordExecutorDowngradeIfNeeded(chunkX, chunkZ, workType, requestedPriority, admittedPriority);
            if (this.shouldSamplePressure(current, cap, count)) {
                this.commitExecutorEvent("-admitted", chunkX, chunkZ, workType, count, false, current, cap, admittedPriority);
            }
            return Admission.acquired(admittedPriority);
        }

        this.executorCounters.inFlight.decrementAndGet();
        return Admission.deferred(Math.max(0, this.executorCounters.deferredRetries.get()) + 1, cap);
    }

    public boolean deferExecutorRetry(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority,
            final Runnable retry
    ) {
        final int reserve = this.executorDeferredRetryReserve();
        final int queued;
        for (;;) {
            final int current = Math.max(0, this.executorCounters.deferredRetries.get());
            if (current >= reserve) {
                return false;
            }
            if (this.executorCounters.deferredRetries.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }
        final long count = this.executorCounters.deferred.incrementAndGet();
        this.executorRetryQueues.deferredWaiters.offer(retry);

        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent("-deferred", chunkX, chunkZ, workType, count, false, this.executorCounters.inFlight.get(), this.currentExecutorCap(), priority);
        }
        this.scheduleExecutorDeferredDrain(0L);
        return true;
    }

    public boolean tryBeginExecutorWaiting(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority
    ) {
        final int cap = this.executorBacklogCap();
        for (;;) {
            final int current = Math.max(0, this.executorBacklogCounters.waitingTasks.get());
            if (current >= cap) {
                this.recordExecutorBacklogSaturated(chunkX, chunkZ, workType, priority, current + 1, cap);
                return false;
            }
            if (this.executorBacklogCounters.waitingTasks.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void recordExecutorBacklogSaturated(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority,
            final int current,
            final int cap
    ) {
        final long count = this.executorBacklogCounters.backpressure.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-backlog-backpressure",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    false,
                    current,
                    cap,
                    priority
            );
        }
        if (count == 1L || (count & 255L) == 0L) {
            LOGGER.error(
                    "Internal chunk executor backlog cap is saturated for {} {} (waitingTasks={} backlogCap={} workType={} backpressureCount={}); deferring new region chunk IO admissions until executor backlog drains",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    current,
                    cap,
                    workType,
                    count
            );
        }
    }

    public boolean deferExecutorBacklogAdmission(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority,
            final ExecutorBacklogRetry retry
    ) {
        final int cap = this.executorBacklogCap();
        final int queued;
        for (;;) {
            final int current = Math.max(0, this.executorBacklogCounters.queuedTasks.get());
            if (current >= cap) {
                return false;
            }
            if (this.executorBacklogCounters.queuedTasks.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }

        final long count = this.executorBacklogCounters.deferred.incrementAndGet();
        if (retry.markQueued()) {
            this.executorRetryQueues.backlogWaiters.offer(retry);
        }
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-backlog-deferred",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    false,
                    queued,
                    cap,
                    priority
            );
        }
        this.scheduleExecutorBacklogDrain(EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        return true;
    }

    public void requeueExecutorBacklogAdmission(final ExecutorBacklogRetry retry) {
        if (retry.markQueued()) {
            this.executorRetryQueues.backlogWaiters.offer(retry);
        }
        this.scheduleExecutorBacklogDrain(EXECUTOR_BACKPRESSURE_RETRY_NANOS);
    }

    public void executorBacklogAdmissionStarted() {
        final int remaining = this.executorBacklogCounters.queuedTasks.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogCounters.queuedTasks.compareAndSet(remaining, 0);
        }
    }

    public void executorBacklogEmergencyStarted(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority
    ) {
        final int inFlight = this.executorBacklogCounters.emergencyInFlight.incrementAndGet();
        final long count = this.executorBacklogCounters.emergency.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-backlog-emergency",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    true,
                    inFlight,
                    this.executorBacklogCap(),
                    priority
            );
        }
        if (count == 1L || (count & 255L) == 0L) {
            LOGGER.error(
                    "Internal chunk executor backlog admission queue is saturated for {} {} (waitingTasks={} backlogQueued={} backlogEmergencyInFlight={} backlogCap={} workType={} emergencyCount={}); handing one engine task to the bounded emergency executor",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    Math.max(0, this.executorBacklogCounters.waitingTasks.get()),
                    Math.max(0, this.executorBacklogCounters.queuedTasks.get()),
                    inFlight,
                    this.executorBacklogCap(),
                    workType,
                    count
            );
        }
    }

    public void completeExecutorBacklogEmergency() {
        final int remaining = this.executorBacklogCounters.emergencyInFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogCounters.emergencyInFlight.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void executorBacklogEmergencyRetryQueued(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority
    ) {
        final int queued = this.executorBacklogCounters.emergencyRetries.incrementAndGet();
        final long count = this.executorBacklogCounters.emergencyRejected.incrementAndGet();
        this.commitExecutorEvent(
                "-backlog-emergency-retry",
                chunkX,
                chunkZ,
                workType,
                count,
                true,
                queued,
                this.executorBacklogCap(),
                priority
        );
        if (count == 1L || (count & 255L) == 0L) {
            LOGGER.error(
                    "Internal chunk executor emergency executor is saturated for {} {} (emergencyRetryWaiters={} backlogEmergencyInFlight={} backlogCap={} workType={} rejectedEmergencyCount={}); deferring through detached emergency retry coordinator",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    queued,
                    Math.max(0, this.executorBacklogCounters.emergencyInFlight.get()),
                    this.executorBacklogCap(),
                    workType,
                    count
            );
        }
    }

    public void executorBacklogEmergencyRetryStarted() {
        final int remaining = this.executorBacklogCounters.emergencyRetries.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogCounters.emergencyRetries.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void endExecutorWaiting() {
        final int remaining = this.executorBacklogCounters.waitingTasks.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogCounters.waitingTasks.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void complete() {
        final int remaining = this.loadCounters.inFlight.decrementAndGet();
        if (remaining < 0) {
            this.loadCounters.inFlight.compareAndSet(remaining, 0);
        }
        this.loadCounters.completed.incrementAndGet();
    }

    public void completeExecutor() {
        final int remaining = this.executorCounters.inFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorCounters.inFlight.compareAndSet(remaining, 0);
        }
        this.executorCounters.completed.incrementAndGet();
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorDeferredDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void deferredRetryStarted() {
        final int remaining = this.loadCounters.deferredRetries.decrementAndGet();
        if (remaining < 0) {
            this.loadCounters.deferredRetries.compareAndSet(remaining, 0);
        }
    }

    public void executorDeferredRetryStarted() {
        final int remaining = this.executorCounters.deferredRetries.decrementAndGet();
        if (remaining < 0) {
            this.executorCounters.deferredRetries.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void executorBackpressureRetryStarted() {
        final int remaining = this.executorBackpressureCounters.retries.decrementAndGet();
        if (remaining < 0) {
            this.executorBackpressureCounters.retries.compareAndSet(remaining, 0);
        }
    }

    public void rejectDeferred(final int chunkX, final int chunkZ, final ChunkStatus status, final Priority priority) {
        this.deferredRetryStarted();
        final long count = this.loadCounters.rejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-rejected", chunkX, chunkZ, status, count, true, this.loadCounters.inFlight.get(), this.currentCap(), priority);
        }
    }

    public void rejectDeferredExecutor(final int chunkX, final int chunkZ, final String workType, final Priority priority) {
        this.executorDeferredRetryStarted();
        final long count = this.executorCounters.rejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-fallback",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    true,
                    this.executorCounters.inFlight.get(),
                    this.currentExecutorCap(),
                    priority
            );
        }
    }

    public Priority executorOverflowPriority(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority requestedPriority
    ) {
        final Priority priority = this.adjustExecutorPriority(
                requestedPriority,
                this.overloadController.loadClass(),
                ShreddedPaperConfiguration.get().multithreading
        );
        this.recordExecutorDowngradeIfNeeded(chunkX, chunkZ, workType, requestedPriority, priority);
        return priority;
    }

    public boolean hasExecutorDeferredRetryCapacity() {
        return Math.max(0, this.executorCounters.deferredRetries.get()) < this.executorDeferredRetryReserve();
    }

    public boolean tryAcquireExecutorOverflow(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority
    ) {
        final int cap = this.currentExecutorOverflowCap();
        if (cap <= 0) {
            return false;
        }
        final int current;
        for (;;) {
            final int observed = Math.max(0, this.executorOverflowCounters.inFlight.get());
            if (observed >= cap) {
                return false;
            }
            if (this.executorOverflowCounters.inFlight.compareAndSet(observed, observed + 1)) {
                current = observed + 1;
                break;
            }
        }

        final long admitted = this.executorOverflowCounters.admitted.incrementAndGet();
        final long fallback = this.executorCounters.rejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(fallback)) {
            this.commitExecutorEvent(
                    "-overflow-fallback",
                    chunkX,
                    chunkZ,
                    workType,
                    fallback,
                    true,
                    current,
                    cap,
                    priority
            );
        }
        if (fallback == 1L || (fallback & 255L) == 0L) {
            LOGGER.warn(
                    "Internal chunk executor wait queue reached reserve for {} {} (overflowInFlight={} overflowCap={} workType={} fallbackCount={}); admitting bounded overflow engine work",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    current,
                    cap,
                    workType,
                    admitted
            );
        }
        return true;
    }

    public void completeExecutorOverflow() {
        final int remaining = this.executorOverflowCounters.inFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorOverflowCounters.inFlight.compareAndSet(remaining, 0);
        }
        this.executorOverflowCounters.completed.incrementAndGet();
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorDeferredDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public boolean deferExecutorBackpressureRetry(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority,
            final ExecutorBackpressureRetry retry
    ) {
        final int reserve = this.executorBackpressureRetryReserve();
        final int queued;
        for (;;) {
            final int current = Math.max(0, this.executorBackpressureCounters.retries.get());
            if (this.executorBackpressureCounters.retries.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }

        final long count = this.executorOverflowCounters.backpressure.incrementAndGet();
        this.enqueueExecutorBackpressureRetry(retry, EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-overflow-backpressure",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    true,
                    queued,
                    reserve,
                    priority
            );
        }
        if (count == 1L || (count & 255L) == 0L) {
            LOGGER.warn(
                    "Internal chunk executor overflow cap is full for {} {} (backpressureWaiters={} backpressureReserve={} overflowInFlight={} overflowCap={} workType={} backpressureCount={}); deferring through bounded retry coordinator",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    queued,
                    reserve,
                    Math.max(0, this.executorOverflowCounters.inFlight.get()),
                    this.currentExecutorOverflowCap(),
                    workType,
                    count
            );
        }
        if (queued > reserve) {
            this.recordExecutorBackpressureSaturated(chunkX, chunkZ, workType, priority, queued, reserve, count);
        }
        return true;
    }

    public void requeueExecutorBackpressureRetry(final ExecutorBackpressureRetry retry) {
        this.enqueueExecutorBackpressureRetry(retry, EXECUTOR_BACKPRESSURE_RETRY_NANOS);
    }

    private void recordExecutorBackpressureSaturated(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority,
            final int queued,
            final int reserve,
            final long count
    ) {
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "-backpressure-saturated",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    false,
                    queued,
                    reserve,
                    priority
            );
        }
        if (count == 1L || (count & 255L) == 0L) {
            LOGGER.error(
                    "Internal chunk executor backpressure reserve is saturated for {} {} (backpressureWaiters={} backpressureReserve={} workType={} backpressureCount={}); preserving non-dropping engine work in the retry coordinator",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    queued,
                    reserve,
                    workType,
                    count
            );
        }
    }

    public Snapshot snapshot() {
        final int cap = this.currentCap();
        final int inFlight = Math.max(0, this.loadCounters.inFlight.get());
        final int deferred = Math.max(0, this.loadCounters.deferredRetries.get());
        final double requestPressure = cap <= 0 ? 0.0D : Math.max(inFlight / (double) cap, deferred / (double) cap);
        final int executorCap = this.currentExecutorCap();
        final int executorWaiting = Math.max(0, this.executorBacklogCounters.waitingTasks.get());
        final int executorBacklogQueued = Math.max(0, this.executorBacklogCounters.queuedTasks.get());
        final int executorBacklogEmergencyInFlight = Math.max(0, this.executorBacklogCounters.emergencyInFlight.get());
        final int executorBacklogEmergencyRetries = Math.max(0, this.executorBacklogCounters.emergencyRetries.get());
        final int executorBacklogCap = this.executorBacklogCap();
        final int executorInFlight = Math.max(0, this.executorCounters.inFlight.get());
        final int executorDeferred = Math.max(0, this.executorCounters.deferredRetries.get());
        final int executorOverflowCap = this.currentExecutorOverflowCap();
        final int executorOverflowInFlight = Math.max(0, this.executorOverflowCounters.inFlight.get());
        final int executorBackpressured = Math.max(0, this.executorBackpressureCounters.retries.get());
        final int executorBackpressureCap = this.executorBackpressureRetryReserve();
        final double executorBacklogPressure = executorBacklogCap <= 0 ? 0.0D : (executorWaiting + executorBacklogQueued) / (double) executorBacklogCap;
        final double executorPermitPressure = executorCap <= 0 ? 0.0D : Math.max(executorInFlight / (double) executorCap, executorDeferred / (double) executorCap);
        final double executorOverflowPressure = executorOverflowCap <= 0 ? 0.0D : executorOverflowInFlight / (double) executorOverflowCap;
        final double executorBackpressurePressure = executorBackpressureCap <= 0 ? 0.0D : executorBackpressured / (double) executorBackpressureCap;
        final double executorPressure = Math.max(Math.max(Math.max(executorPermitPressure, executorOverflowPressure), executorBackpressurePressure), executorBacklogPressure);
        return new Snapshot(
                inFlight,
                deferred,
                cap,
                requestPressure,
                Math.max(requestPressure, executorPressure),
                this.loadCounters.admitted.get(),
                this.loadCounters.completed.get(),
                this.loadCounters.deferred.get(),
                this.loadCounters.rejected.get(),
                this.loadCounters.downgraded.get(),
                executorWaiting,
                executorBacklogQueued,
                executorBacklogCap,
                executorBacklogPressure,
                this.executorBacklogCounters.backpressure.get(),
                this.executorBacklogCounters.deferred.get(),
                executorBacklogEmergencyInFlight,
                this.executorBacklogCounters.emergency.get(),
                executorBacklogEmergencyRetries,
                this.executorBacklogCounters.emergencyRejected.get(),
                executorInFlight,
                executorDeferred,
                executorCap,
                executorPressure,
                this.executorCounters.admitted.get(),
                this.executorCounters.completed.get(),
                this.executorCounters.deferred.get(),
                this.executorCounters.rejected.get(),
                this.executorCounters.downgraded.get(),
                executorOverflowInFlight,
                executorOverflowCap,
                executorOverflowPressure,
                this.executorOverflowCounters.admitted.get(),
                this.executorOverflowCounters.completed.get(),
                this.executorOverflowCounters.backpressure.get(),
                executorBackpressured,
                executorBackpressureCap,
                executorBackpressurePressure
        );
    }

    public boolean hasPendingWork() {
        return this.loadCounters.inFlight.get() > 0
                || this.loadCounters.deferredRetries.get() > 0
                || this.executorBacklogCounters.waitingTasks.get() > 0
                || this.executorBacklogCounters.queuedTasks.get() > 0
                || this.executorBacklogCounters.emergencyInFlight.get() > 0
                || this.executorBacklogCounters.emergencyRetries.get() > 0
                || this.executorCounters.inFlight.get() > 0
                || this.executorCounters.deferredRetries.get() > 0
                || this.executorOverflowCounters.inFlight.get() > 0
                || this.executorBackpressureCounters.retries.get() > 0;
    }

    private int currentCap() {
        return this.capFor(this.overloadController.loadClass(), ShreddedPaperConfiguration.get().multithreading);
    }

    private int currentExecutorCap() {
        return this.executorCapFor(this.overloadController.loadClass(), ShreddedPaperConfiguration.get().multithreading);
    }

    private int currentExecutorOverflowCap() {
        return Math.max(0, ShreddedPaperConfiguration.get().multithreading.chunkIoExecutorMaxOverflowPerRegion);
    }

    private int executorDeferredRetryReserve() {
        return Math.max(16, ShreddedPaperConfiguration.get().multithreading.chunkIoExecutorDeferredRetryReserve);
    }

    private int executorBackpressureRetryReserve() {
        return Math.max(16, ShreddedPaperConfiguration.get().multithreading.chunkIoExecutorBackpressureRetryReserve);
    }

    private int executorBacklogCap() {
        return Math.max(64, ShreddedPaperConfiguration.get().multithreading.chunkIoExecutorMaxBacklogPerRegion);
    }

    private boolean isExecutorBacklogSaturated() {
        return Math.max(0, this.executorBacklogCounters.waitingTasks.get()) + Math.max(0, this.executorBacklogCounters.queuedTasks.get()) >= this.executorBacklogCap();
    }

    private void scheduleExecutorBacklogDrain(final long delayNanos) {
        if (!this.executorRetryQueues.backlogDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorBacklogWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorRetryQueues.backlogDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorBacklogWaiters() {
        this.executorRetryQueues.backlogDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH && this.executorBacklogCounters.waitingTasks.get() < this.executorBacklogCap(); drained++) {
            final ExecutorBacklogRetry retry = this.executorRetryQueues.backlogWaiters.poll();
            if (retry == null) {
                return;
            }
            try {
                retry.clearQueued();
                retry.run();
            } catch (final Throwable throwable) {
                LOGGER.error("Internal chunk executor backlog retry failed for {} {}", this.level.getWorld().getName(), this.regionPos, throwable);
            }
        }
        if (!this.executorRetryQueues.backlogWaiters.isEmpty()) {
            this.scheduleExecutorBacklogDrain(this.executorBacklogCounters.waitingTasks.get() < this.executorBacklogCap() ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private void scheduleExecutorDeferredDrain(final long delayNanos) {
        if (!this.executorRetryQueues.deferredDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorDeferredWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorRetryQueues.deferredDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorDeferredWaiters() {
        this.executorRetryQueues.deferredDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH && this.executorCounters.inFlight.get() < this.currentExecutorCap(); drained++) {
            final Runnable retry = this.executorRetryQueues.deferredWaiters.poll();
            if (retry == null) {
                return;
            }
            try {
                retry.run();
            } catch (final Throwable throwable) {
                LOGGER.error("Internal chunk executor retry failed for {} {}", this.level.getWorld().getName(), this.regionPos, throwable);
            }
        }
        if (!this.executorRetryQueues.deferredWaiters.isEmpty()) {
            this.scheduleExecutorDeferredDrain(this.executorCounters.inFlight.get() < this.currentExecutorCap() ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private void scheduleExecutorBackpressureDrain(final long delayNanos) {
        if (!this.executorRetryQueues.backpressureDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorBackpressureWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorRetryQueues.backpressureDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorBackpressureWaiters() {
        this.executorRetryQueues.backpressureDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH; drained++) {
            final ExecutorBackpressureRetry retry = this.executorRetryQueues.backpressureWaiters.poll();
            if (retry == null) {
                return;
            }
            try {
                retry.clearQueued();
                retry.run();
            } catch (final Throwable throwable) {
                LOGGER.error("Internal chunk executor backpressure retry failed for {} {}", this.level.getWorld().getName(), this.regionPos, throwable);
            }
        }
        if (!this.executorRetryQueues.backpressureWaiters.isEmpty()) {
            this.scheduleExecutorBackpressureDrain((this.hasExecutorDeferredRetryCapacity() || this.hasExecutorOverflowCapacity()) ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private boolean hasExecutorOverflowCapacity() {
        final int cap = this.currentExecutorOverflowCap();
        return cap > 0 && Math.max(0, this.executorOverflowCounters.inFlight.get()) < cap;
    }

    private void enqueueExecutorBackpressureRetry(final ExecutorBackpressureRetry retry, final long delayNanos) {
        if (retry.markQueued()) {
            this.executorRetryQueues.backpressureWaiters.offer(retry);
        }
        this.scheduleExecutorBackpressureDrain(delayNanos);
    }

    private int capFor(final RegionLoadClass loadClass, final ShreddedPaperConfiguration.Multithreading config) {
        return Math.max(1, loadClass == RegionLoadClass.DEGRADED
                ? config.chunkIoLoadMaxInflightDegradedPerRegion
                : config.chunkIoLoadMaxInflightNormalPerRegion);
    }

    private int executorCapFor(final RegionLoadClass loadClass, final ShreddedPaperConfiguration.Multithreading config) {
        return Math.max(1, loadClass == RegionLoadClass.DEGRADED
                ? config.chunkIoExecutorMaxInflightDegradedPerRegion
                : config.chunkIoExecutorMaxInflightNormalPerRegion);
    }

    private Priority adjustPriority(
            final Priority requestedPriority,
            final RegionLoadClass loadClass,
            final ShreddedPaperConfiguration.Multithreading config
    ) {
        if (!config.chunkIoLoadDowngradeDegradedPriority || loadClass != RegionLoadClass.DEGRADED) {
            return requestedPriority;
        }
        if (requestedPriority == Priority.BLOCKING || requestedPriority == Priority.HIGHEST) {
            return requestedPriority;
        }
        return Priority.min(requestedPriority, Priority.LOW);
    }

    private Priority adjustExecutorPriority(
            final Priority requestedPriority,
            final RegionLoadClass loadClass,
            final ShreddedPaperConfiguration.Multithreading config
    ) {
        if (!config.chunkIoExecutorDowngradeDegradedPriority || loadClass != RegionLoadClass.DEGRADED) {
            return requestedPriority;
        }
        if (requestedPriority == Priority.BLOCKING || requestedPriority == Priority.HIGHEST) {
            return requestedPriority;
        }
        return Priority.min(requestedPriority, Priority.LOW);
    }

    private void recordDowngradeIfNeeded(
            final int chunkX,
            final int chunkZ,
            final ChunkStatus status,
            final Priority requestedPriority,
            final Priority admittedPriority
    ) {
        if (requestedPriority == admittedPriority) {
            return;
        }
        final long count = this.loadCounters.downgraded.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-downgraded", chunkX, chunkZ, status, count, false, this.loadCounters.inFlight.get(), this.currentCap(), admittedPriority);
        }
    }

    private void recordExecutorDowngradeIfNeeded(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority requestedPriority,
            final Priority admittedPriority
    ) {
        if (requestedPriority == admittedPriority) {
            return;
        }
        final long count = this.executorCounters.downgraded.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent("-downgraded", chunkX, chunkZ, workType, count, false, this.executorCounters.inFlight.get(), this.currentExecutorCap(), admittedPriority);
        }
    }

    private boolean shouldSamplePressure(final int current, final int cap, final long count) {
        if (current < Math.max(1, cap / 2)) {
            return false;
        }
        return current == cap || ChunkRequestEvent.shouldCommitSample(count);
    }

    private void commitEvent(
            final String action,
            final int chunkX,
            final int chunkZ,
            final ChunkStatus status,
            final long count,
            final boolean rejected,
            final int inFlight,
            final int cap,
            final Priority priority
    ) {
        if (!ChunkRequestEvent.shouldCommitSample(count)) {
            return;
        }
        if (!ChunkRequestEvent.isEventEnabled()) {
            return;
        }
        final ChunkRequestEvent event = new ChunkRequestEvent();
        event.world = ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.level);
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.chunkX = chunkX;
        event.chunkZ = chunkZ;
        event.status = status.toString();
        event.action = action;
        event.count = count;
        event.thread = Thread.currentThread().getName();
        event.sync = false;
        event.rejected = rejected;
        event.inFlight = inFlight;
        event.capacity = cap;
        event.priority = priority.name();
        event.commit();
    }

    private void commitExecutorEvent(
            final String actionSuffix,
            final int chunkX,
            final int chunkZ,
            final String workType,
            final long count,
            final boolean rejected,
            final int inFlight,
            final int cap,
            final Priority priority
    ) {
        if (!ChunkRequestEvent.shouldCommitSample(count)) {
            return;
        }
        if (!ChunkRequestEvent.isEventEnabled()) {
            return;
        }
        final ChunkRequestEvent event = new ChunkRequestEvent();
        event.world = ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.level);
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.chunkX = chunkX;
        event.chunkZ = chunkZ;
        event.status = "executor:" + workType;
        event.action = "executor-" + workType + actionSuffix;
        event.count = count;
        event.thread = Thread.currentThread().getName();
        event.sync = false;
        event.rejected = rejected;
        event.inFlight = inFlight;
        event.capacity = cap;
        event.priority = priority.name();
        event.commit();
    }

    private static final class LoadAdmissionCounters {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger deferredRetries = new AtomicInteger();
        private final AtomicLong admitted = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
        private final AtomicLong deferred = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final AtomicLong downgraded = new AtomicLong();
    }

    private static final class ExecutorAdmissionCounters {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicInteger deferredRetries = new AtomicInteger();
        private final AtomicLong admitted = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
        private final AtomicLong deferred = new AtomicLong();
        private final AtomicLong rejected = new AtomicLong();
        private final AtomicLong downgraded = new AtomicLong();
    }

    private static final class ExecutorBacklogCounters {
        private final AtomicInteger waitingTasks = new AtomicInteger();
        private final AtomicInteger queuedTasks = new AtomicInteger();
        private final AtomicLong backpressure = new AtomicLong();
        private final AtomicLong deferred = new AtomicLong();
        private final AtomicInteger emergencyInFlight = new AtomicInteger();
        private final AtomicLong emergency = new AtomicLong();
        private final AtomicInteger emergencyRetries = new AtomicInteger();
        private final AtomicLong emergencyRejected = new AtomicLong();
    }

    private static final class ExecutorOverflowCounters {
        private final AtomicInteger inFlight = new AtomicInteger();
        private final AtomicLong admitted = new AtomicLong();
        private final AtomicLong completed = new AtomicLong();
        private final AtomicLong backpressure = new AtomicLong();
    }

    private static final class ExecutorBackpressureCounters {
        private final AtomicInteger retries = new AtomicInteger();
    }

    private static final class ExecutorRetryQueues {
        private final Queue<Runnable> deferredWaiters = new ConcurrentLinkedQueue<>();
        private final Queue<ExecutorBacklogRetry> backlogWaiters = new ConcurrentLinkedQueue<>();
        private final Queue<ExecutorBackpressureRetry> backpressureWaiters = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean deferredDrainScheduled = new AtomicBoolean();
        private final AtomicBoolean backlogDrainScheduled = new AtomicBoolean();
        private final AtomicBoolean backpressureDrainScheduled = new AtomicBoolean();
    }

    public record Admission(Result result, Priority priority, int deferredQueued, int capacity) {
        public static Admission acquired(final Priority priority) {
            return new Admission(Result.ACQUIRED, priority, 0, 0);
        }

        public static Admission bypass(final Priority priority) {
            return new Admission(Result.BYPASS, priority, 0, 0);
        }

        public static Admission deferred(final int queued, final int capacity) {
            return new Admission(Result.DEFERRED, Priority.NORMAL, queued, capacity);
        }

        public boolean acquiredQuota() {
            return this.result == Result.ACQUIRED;
        }
    }

    public enum Result {
        ACQUIRED,
        BYPASS,
        DEFERRED
    }

    public interface ExecutorBackpressureRetry extends Runnable {
        boolean markQueued();

        void clearQueued();
    }

    public interface ExecutorBacklogRetry extends Runnable {
        boolean markQueued();

        void clearQueued();
    }

    public record Snapshot(
            int inFlight,
            int deferredRetries,
            int capacity,
            double requestPressure,
            double pressure,
            long admitted,
            long completed,
            long deferred,
            long rejected,
            long downgraded,
            int executorWaitingTasks,
            int executorBacklogQueuedTasks,
            int executorBacklogCapacity,
            double executorBacklogPressure,
            long executorBacklogBackpressure,
            long executorBacklogDeferred,
            int executorBacklogEmergencyInFlight,
            long executorBacklogEmergency,
            int executorBacklogEmergencyRetries,
            long executorBacklogEmergencyRejected,
            int executorInFlight,
            int executorDeferredRetries,
            int executorCapacity,
            double executorPressure,
            long executorAdmitted,
            long executorCompleted,
            long executorDeferred,
            long executorRejected,
            long executorDowngraded,
            int executorOverflowInFlight,
            int executorOverflowCapacity,
            double executorOverflowPressure,
            long executorOverflowAdmitted,
            long executorOverflowCompleted,
            long executorOverflowBackpressure,
            int executorBackpressuredRetries,
            int executorBackpressureCapacity,
            double executorBackpressurePressure
    ) {
    }
}
