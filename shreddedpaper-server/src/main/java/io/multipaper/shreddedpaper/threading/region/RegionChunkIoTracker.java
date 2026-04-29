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
            Thread.ofPlatform().daemon(true).name("ShreddedPaperChunkExecutorBackpressureRetry", 0).factory()
    );

    private final ServerLevel level;
    private final RegionPos regionPos;
    private final RegionOverloadController overloadController;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger deferredRetries = new AtomicInteger();
    private final AtomicLong admitted = new AtomicLong();
    private final AtomicLong completed = new AtomicLong();
    private final AtomicLong deferred = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong downgraded = new AtomicLong();
    private final AtomicInteger executorInFlight = new AtomicInteger();
    private final AtomicInteger executorWaitingTasks = new AtomicInteger();
    private final AtomicInteger executorDeferredRetries = new AtomicInteger();
    private final AtomicLong executorAdmitted = new AtomicLong();
    private final AtomicLong executorCompleted = new AtomicLong();
    private final AtomicLong executorDeferred = new AtomicLong();
    private final AtomicLong executorRejected = new AtomicLong();
    private final AtomicLong executorDowngraded = new AtomicLong();
    private final AtomicInteger executorOverflowInFlight = new AtomicInteger();
    private final AtomicLong executorOverflowAdmitted = new AtomicLong();
    private final AtomicLong executorOverflowCompleted = new AtomicLong();
    private final AtomicLong executorOverflowBackpressure = new AtomicLong();
    private final AtomicLong executorBacklogBackpressure = new AtomicLong();
    private final AtomicInteger executorBacklogQueuedTasks = new AtomicInteger();
    private final AtomicLong executorBacklogDeferred = new AtomicLong();
    private final AtomicInteger executorBacklogEmergencyInFlight = new AtomicInteger();
    private final AtomicLong executorBacklogEmergency = new AtomicLong();
    private final AtomicInteger executorBacklogEmergencyRetries = new AtomicInteger();
    private final AtomicLong executorBacklogEmergencyRejected = new AtomicLong();
    private final Queue<Runnable> executorDeferredWaiters = new ConcurrentLinkedQueue<>();
    private final Queue<ExecutorBacklogRetry> executorBacklogWaiters = new ConcurrentLinkedQueue<>();
    private final Queue<ExecutorBackpressureRetry> executorBackpressureWaiters = new ConcurrentLinkedQueue<>();
    private final AtomicInteger executorBackpressuredRetries = new AtomicInteger();
    private final AtomicBoolean executorDeferredDrainScheduled = new AtomicBoolean();
    private final AtomicBoolean executorBacklogDrainScheduled = new AtomicBoolean();
    private final AtomicBoolean executorBackpressureDrainScheduled = new AtomicBoolean();

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
            final int queued = this.deferredRetries.incrementAndGet();
            final long count = this.deferred.incrementAndGet();
            if (ChunkRequestEvent.shouldCommitSample(count)) {
                this.commitEvent("async-load-deferred-executor-backlog", chunkX, chunkZ, status, count, false, this.inFlight.get(), cap, requestedPriority);
            }
            return Admission.deferred(queued, cap);
        }

        if (!addTicket || cap <= 0) {
            final Priority admittedPriority = this.adjustPriority(requestedPriority, loadClass, config);
            this.recordDowngradeIfNeeded(chunkX, chunkZ, status, requestedPriority, admittedPriority);
            return Admission.bypass(admittedPriority);
        }

        final int current = this.inFlight.incrementAndGet();
        if (current <= cap) {
            final long count = this.admitted.incrementAndGet();
            final Priority admittedPriority = this.adjustPriority(requestedPriority, loadClass, config);
            this.recordDowngradeIfNeeded(chunkX, chunkZ, status, requestedPriority, admittedPriority);
            if (this.shouldSamplePressure(current, cap, count)) {
                this.commitEvent("async-load-admitted", chunkX, chunkZ, status, count, false, current, cap, admittedPriority);
            }
            return Admission.acquired(admittedPriority);
        }

        this.inFlight.decrementAndGet();
        final int queued = this.deferredRetries.incrementAndGet();
        final long count = this.deferred.incrementAndGet();
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

        final int current = this.executorInFlight.incrementAndGet();
        if (current <= cap) {
            final long count = this.executorAdmitted.incrementAndGet();
            final Priority admittedPriority = this.adjustExecutorPriority(requestedPriority, loadClass, config);
            this.recordExecutorDowngradeIfNeeded(chunkX, chunkZ, workType, requestedPriority, admittedPriority);
            if (this.shouldSamplePressure(current, cap, count)) {
                this.commitExecutorEvent("executor-" + workType + "-admitted", chunkX, chunkZ, workType, count, false, current, cap, admittedPriority);
            }
            return Admission.acquired(admittedPriority);
        }

        this.executorInFlight.decrementAndGet();
        return Admission.deferred(Math.max(0, this.executorDeferredRetries.get()) + 1, cap);
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
            final int current = Math.max(0, this.executorDeferredRetries.get());
            if (current >= reserve) {
                return false;
            }
            if (this.executorDeferredRetries.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }
        final long count = this.executorDeferred.incrementAndGet();
        this.executorDeferredWaiters.offer(retry);

        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent("executor-" + workType + "-deferred", chunkX, chunkZ, workType, count, false, this.executorInFlight.get(), this.currentExecutorCap(), priority);
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
            final int current = Math.max(0, this.executorWaitingTasks.get());
            if (current >= cap) {
                this.recordExecutorBacklogSaturated(chunkX, chunkZ, workType, priority, current + 1, cap);
                return false;
            }
            if (this.executorWaitingTasks.compareAndSet(current, current + 1)) {
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
        final long count = this.executorBacklogBackpressure.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-backlog-backpressure",
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
            final int current = Math.max(0, this.executorBacklogQueuedTasks.get());
            if (current >= cap) {
                return false;
            }
            if (this.executorBacklogQueuedTasks.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }

        final long count = this.executorBacklogDeferred.incrementAndGet();
        if (retry.markQueued()) {
            this.executorBacklogWaiters.offer(retry);
        }
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-backlog-deferred",
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
            this.executorBacklogWaiters.offer(retry);
        }
        this.scheduleExecutorBacklogDrain(EXECUTOR_BACKPRESSURE_RETRY_NANOS);
    }

    public void executorBacklogAdmissionStarted() {
        final int remaining = this.executorBacklogQueuedTasks.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogQueuedTasks.compareAndSet(remaining, 0);
        }
    }

    public void executorBacklogEmergencyStarted(
            final int chunkX,
            final int chunkZ,
            final String workType,
            final Priority priority
    ) {
        final int inFlight = this.executorBacklogEmergencyInFlight.incrementAndGet();
        final long count = this.executorBacklogEmergency.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-backlog-emergency",
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
                    Math.max(0, this.executorWaitingTasks.get()),
                    Math.max(0, this.executorBacklogQueuedTasks.get()),
                    inFlight,
                    this.executorBacklogCap(),
                    workType,
                    count
            );
        }
    }

    public void completeExecutorBacklogEmergency() {
        final int remaining = this.executorBacklogEmergencyInFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogEmergencyInFlight.compareAndSet(remaining, 0);
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
        final int queued = this.executorBacklogEmergencyRetries.incrementAndGet();
        final long count = this.executorBacklogEmergencyRejected.incrementAndGet();
        this.commitExecutorEvent(
                "executor-" + workType + "-backlog-emergency-retry",
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
                    Math.max(0, this.executorBacklogEmergencyInFlight.get()),
                    this.executorBacklogCap(),
                    workType,
                    count
            );
        }
    }

    public void executorBacklogEmergencyRetryStarted() {
        final int remaining = this.executorBacklogEmergencyRetries.decrementAndGet();
        if (remaining < 0) {
            this.executorBacklogEmergencyRetries.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void endExecutorWaiting() {
        final int remaining = this.executorWaitingTasks.decrementAndGet();
        if (remaining < 0) {
            this.executorWaitingTasks.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void complete() {
        final int remaining = this.inFlight.decrementAndGet();
        if (remaining < 0) {
            this.inFlight.compareAndSet(remaining, 0);
        }
        this.completed.incrementAndGet();
    }

    public void completeExecutor() {
        final int remaining = this.executorInFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorInFlight.compareAndSet(remaining, 0);
        }
        this.executorCompleted.incrementAndGet();
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorDeferredDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void deferredRetryStarted() {
        final int remaining = this.deferredRetries.decrementAndGet();
        if (remaining < 0) {
            this.deferredRetries.compareAndSet(remaining, 0);
        }
    }

    public void executorDeferredRetryStarted() {
        final int remaining = this.executorDeferredRetries.decrementAndGet();
        if (remaining < 0) {
            this.executorDeferredRetries.compareAndSet(remaining, 0);
        }
        this.scheduleExecutorBacklogDrain(0L);
        this.scheduleExecutorBackpressureDrain(0L);
    }

    public void executorBackpressureRetryStarted() {
        final int remaining = this.executorBackpressuredRetries.decrementAndGet();
        if (remaining < 0) {
            this.executorBackpressuredRetries.compareAndSet(remaining, 0);
        }
    }

    public void rejectDeferred(final int chunkX, final int chunkZ, final ChunkStatus status, final Priority priority) {
        this.deferredRetryStarted();
        final long count = this.rejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-rejected", chunkX, chunkZ, status, count, true, this.inFlight.get(), this.currentCap(), priority);
        }
    }

    public void rejectDeferredExecutor(final int chunkX, final int chunkZ, final String workType, final Priority priority) {
        this.executorDeferredRetryStarted();
        final long count = this.executorRejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-fallback",
                    chunkX,
                    chunkZ,
                    workType,
                    count,
                    true,
                    this.executorInFlight.get(),
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
        return Math.max(0, this.executorDeferredRetries.get()) < this.executorDeferredRetryReserve();
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
            final int observed = Math.max(0, this.executorOverflowInFlight.get());
            if (observed >= cap) {
                return false;
            }
            if (this.executorOverflowInFlight.compareAndSet(observed, observed + 1)) {
                current = observed + 1;
                break;
            }
        }

        final long admitted = this.executorOverflowAdmitted.incrementAndGet();
        final long fallback = this.executorRejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(fallback)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-overflow-fallback",
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
        final int remaining = this.executorOverflowInFlight.decrementAndGet();
        if (remaining < 0) {
            this.executorOverflowInFlight.compareAndSet(remaining, 0);
        }
        this.executorOverflowCompleted.incrementAndGet();
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
            final int current = Math.max(0, this.executorBackpressuredRetries.get());
            if (this.executorBackpressuredRetries.compareAndSet(current, current + 1)) {
                queued = current + 1;
                break;
            }
        }

        final long count = this.executorOverflowBackpressure.incrementAndGet();
        this.enqueueExecutorBackpressureRetry(retry, EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent(
                    "executor-" + workType + "-overflow-backpressure",
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
                    Math.max(0, this.executorOverflowInFlight.get()),
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
                    "executor-" + workType + "-backpressure-saturated",
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
        final int inFlight = Math.max(0, this.inFlight.get());
        final int deferred = Math.max(0, this.deferredRetries.get());
        final double requestPressure = cap <= 0 ? 0.0D : Math.max(inFlight / (double) cap, deferred / (double) cap);
        final int executorCap = this.currentExecutorCap();
        final int executorWaiting = Math.max(0, this.executorWaitingTasks.get());
        final int executorBacklogQueued = Math.max(0, this.executorBacklogQueuedTasks.get());
        final int executorBacklogEmergencyInFlight = Math.max(0, this.executorBacklogEmergencyInFlight.get());
        final int executorBacklogEmergencyRetries = Math.max(0, this.executorBacklogEmergencyRetries.get());
        final int executorBacklogCap = this.executorBacklogCap();
        final int executorInFlight = Math.max(0, this.executorInFlight.get());
        final int executorDeferred = Math.max(0, this.executorDeferredRetries.get());
        final int executorOverflowCap = this.currentExecutorOverflowCap();
        final int executorOverflowInFlight = Math.max(0, this.executorOverflowInFlight.get());
        final int executorBackpressured = Math.max(0, this.executorBackpressuredRetries.get());
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
                this.admitted.get(),
                this.completed.get(),
                this.deferred.get(),
                this.rejected.get(),
                this.downgraded.get(),
                executorWaiting,
                executorBacklogQueued,
                executorBacklogCap,
                executorBacklogPressure,
                this.executorBacklogBackpressure.get(),
                this.executorBacklogDeferred.get(),
                executorBacklogEmergencyInFlight,
                this.executorBacklogEmergency.get(),
                executorBacklogEmergencyRetries,
                this.executorBacklogEmergencyRejected.get(),
                executorInFlight,
                executorDeferred,
                executorCap,
                executorPressure,
                this.executorAdmitted.get(),
                this.executorCompleted.get(),
                this.executorDeferred.get(),
                this.executorRejected.get(),
                this.executorDowngraded.get(),
                executorOverflowInFlight,
                executorOverflowCap,
                executorOverflowPressure,
                this.executorOverflowAdmitted.get(),
                this.executorOverflowCompleted.get(),
                this.executorOverflowBackpressure.get(),
                executorBackpressured,
                executorBackpressureCap,
                executorBackpressurePressure
        );
    }

    public boolean hasPendingWork() {
        return this.inFlight.get() > 0
                || this.deferredRetries.get() > 0
                || this.executorWaitingTasks.get() > 0
                || this.executorBacklogQueuedTasks.get() > 0
                || this.executorBacklogEmergencyInFlight.get() > 0
                || this.executorBacklogEmergencyRetries.get() > 0
                || this.executorInFlight.get() > 0
                || this.executorDeferredRetries.get() > 0
                || this.executorOverflowInFlight.get() > 0
                || this.executorBackpressuredRetries.get() > 0;
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
        return Math.max(0, this.executorWaitingTasks.get()) + Math.max(0, this.executorBacklogQueuedTasks.get()) >= this.executorBacklogCap();
    }

    private void scheduleExecutorBacklogDrain(final long delayNanos) {
        if (!this.executorBacklogDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorBacklogWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorBacklogDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorBacklogWaiters() {
        this.executorBacklogDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH && this.executorWaitingTasks.get() < this.executorBacklogCap(); drained++) {
            final ExecutorBacklogRetry retry = this.executorBacklogWaiters.poll();
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
        if (!this.executorBacklogWaiters.isEmpty()) {
            this.scheduleExecutorBacklogDrain(this.executorWaitingTasks.get() < this.executorBacklogCap() ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private void scheduleExecutorDeferredDrain(final long delayNanos) {
        if (!this.executorDeferredDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorDeferredWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorDeferredDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorDeferredWaiters() {
        this.executorDeferredDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH && this.executorInFlight.get() < this.currentExecutorCap(); drained++) {
            final Runnable retry = this.executorDeferredWaiters.poll();
            if (retry == null) {
                return;
            }
            try {
                retry.run();
            } catch (final Throwable throwable) {
                LOGGER.error("Internal chunk executor retry failed for {} {}", this.level.getWorld().getName(), this.regionPos, throwable);
            }
        }
        if (!this.executorDeferredWaiters.isEmpty()) {
            this.scheduleExecutorDeferredDrain(this.executorInFlight.get() < this.currentExecutorCap() ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private void scheduleExecutorBackpressureDrain(final long delayNanos) {
        if (!this.executorBackpressureDrainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR.schedule(
                    this::drainExecutorBackpressureWaiters,
                    Math.max(0L, delayNanos),
                    TimeUnit.NANOSECONDS
            );
        } catch (final RuntimeException throwable) {
            this.executorBackpressureDrainScheduled.set(false);
            throw throwable;
        }
    }

    private void drainExecutorBackpressureWaiters() {
        this.executorBackpressureDrainScheduled.set(false);
        for (int drained = 0; drained < EXECUTOR_BACKPRESSURE_DRAIN_BATCH; drained++) {
            final ExecutorBackpressureRetry retry = this.executorBackpressureWaiters.poll();
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
        if (!this.executorBackpressureWaiters.isEmpty()) {
            this.scheduleExecutorBackpressureDrain((this.hasExecutorDeferredRetryCapacity() || this.hasExecutorOverflowCapacity()) ? 0L : EXECUTOR_BACKPRESSURE_RETRY_NANOS);
        }
    }

    private boolean hasExecutorOverflowCapacity() {
        final int cap = this.currentExecutorOverflowCap();
        return cap > 0 && Math.max(0, this.executorOverflowInFlight.get()) < cap;
    }

    private void enqueueExecutorBackpressureRetry(final ExecutorBackpressureRetry retry, final long delayNanos) {
        if (retry.markQueued()) {
            this.executorBackpressureWaiters.offer(retry);
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
        final long count = this.downgraded.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-downgraded", chunkX, chunkZ, status, count, false, this.inFlight.get(), this.currentCap(), admittedPriority);
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
        final long count = this.executorDowngraded.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitExecutorEvent("executor-" + workType + "-downgraded", chunkX, chunkZ, workType, count, false, this.executorInFlight.get(), this.currentExecutorCap(), admittedPriority);
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
            final String action,
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
        final ChunkRequestEvent event = new ChunkRequestEvent();
        event.world = ca.spottedleaf.moonrise.common.util.WorldUtil.getWorldName(this.level);
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.chunkX = chunkX;
        event.chunkZ = chunkZ;
        event.status = "executor:" + workType;
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
