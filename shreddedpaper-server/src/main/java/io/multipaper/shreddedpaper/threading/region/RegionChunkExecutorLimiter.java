package io.multipaper.shreddedpaper.threading.region;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;
import ca.spottedleaf.concurrentutil.util.Priority;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class RegionChunkExecutorLimiter {

    private static final ThreadPoolExecutor EMERGENCY_EXECUTOR = new ThreadPoolExecutor(
            1,
            Math.max(1, Runtime.getRuntime().availableProcessors() / 8),
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8192),
            Thread.ofPlatform().daemon(true).name("ShreddedPaperChunkExecutorEmergency-", 0).factory(),
            new ThreadPoolExecutor.AbortPolicy()
    );
    private static final ScheduledThreadPoolExecutor EMERGENCY_RETRY_EXECUTOR = new ScheduledThreadPoolExecutor(
            1,
            Thread.ofPlatform().daemon(true).name("ShreddedPaperChunkExecutorEmergencyRetry", 0).factory()
    );

    static {
        EMERGENCY_EXECUTOR.allowCoreThreadTimeOut(true);
        EMERGENCY_RETRY_EXECUTOR.setRemoveOnCancelPolicy(true);
    }

    private RegionChunkExecutorLimiter() {
    }

    public static void shutdownEmergencyExecutors() {
        final long quietDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
        boolean interrupted = false;
        while (hasEmergencyExecutorWork() && System.nanoTime() < quietDeadlineNanos) {
            try {
                TimeUnit.MILLISECONDS.sleep(10L);
            } catch (final InterruptedException ignored) {
                interrupted = true;
                break;
            }
        }

        EMERGENCY_RETRY_EXECUTOR.shutdown();
        EMERGENCY_EXECUTOR.shutdown();
        try {
            if (!EMERGENCY_RETRY_EXECUTOR.awaitTermination(5L, TimeUnit.SECONDS)) {
                EMERGENCY_RETRY_EXECUTOR.shutdownNow();
            }
            if (!EMERGENCY_EXECUTOR.awaitTermination(5L, TimeUnit.SECONDS)) {
                EMERGENCY_EXECUTOR.shutdownNow();
            }
        } catch (final InterruptedException ignored) {
            interrupted = true;
            EMERGENCY_RETRY_EXECUTOR.shutdownNow();
            EMERGENCY_EXECUTOR.shutdownNow();
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasEmergencyExecutorWork() {
        return EMERGENCY_EXECUTOR.getActiveCount() > 0
                || !EMERGENCY_EXECUTOR.getQueue().isEmpty()
                || EMERGENCY_RETRY_EXECUTOR.getActiveCount() > 0
                || !EMERGENCY_RETRY_EXECUTOR.getQueue().isEmpty();
    }

    public static PrioritisedExecutor.PrioritisedTask createTask(
            final ServerLevel level,
            final int chunkX,
            final int chunkZ,
            final PrioritisedExecutor executor,
            final Runnable runnable,
            final Priority priority,
            final WorkType workType
    ) {
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking || priority == Priority.BLOCKING) {
            return executor.createTask(runnable, priority);
        }

        final RegionPos regionPos = RegionPos.forChunk(chunkX, chunkZ);
        final RegionRuntimeState state = level.chunkSource.tickingRegions.getOrCreateRuntimeStateForCell(regionPos);
        return new PermitTask(level, chunkX, chunkZ, regionPos, state.ownerId(), state.chunkIoTracker(), executor, runnable, priority, workType);
    }

    public enum WorkType {
        GENERATION("generation"),
        LOAD_DECODE("load-decode"),
        SAVE_SERIALIZE("save-serialize"),
        COMPRESSION("compression"),
        REGION_FILE_IO("region-file-io");

        private final String eventName;

        WorkType(final String eventName) {
            this.eventName = eventName;
        }
    }

    private static final class PermitTask implements PrioritisedExecutor.PrioritisedTask {

        private static final int NEW = 0;
        private static final int WAITING = 1;
        private static final int STARTED = 2;
        private static final int CANCELLED = 3;
        private static final int COMPLETED = 4;

        private final ServerLevel level;
        private final int chunkX;
        private final int chunkZ;
        private final RegionPos regionPos;
        private final WorkType workType;
        private final PrioritisedExecutor.PrioritisedTask delegate;
        private final AtomicInteger state = new AtomicInteger(NEW);
        private final AtomicBoolean permitHeld = new AtomicBoolean();
        private final AtomicBoolean overflowHeld = new AtomicBoolean();
        private final AtomicBoolean waitingHeld = new AtomicBoolean();
        private final AtomicBoolean backlogRetryOutstanding = new AtomicBoolean();
        private final AtomicBoolean emergencyRetryOutstanding = new AtomicBoolean();
        private final AtomicBoolean emergencyRetryScheduled = new AtomicBoolean();
        private final AtomicBoolean backpressureRetryOutstanding = new AtomicBoolean();
        private final Object deferredLock = new Object();
        private volatile Priority requestedPriority;
        private volatile long trackerOwnerId;
        private volatile RegionChunkIoTracker tracker;
        private volatile BacklogRetry backlogRetry;
        private volatile BackpressureRetry backpressureRetry;
        private boolean deferredOutstanding;

        private PermitTask(
                final ServerLevel level,
                final int chunkX,
                final int chunkZ,
                final RegionPos regionPos,
                final long trackerOwnerId,
                final RegionChunkIoTracker tracker,
                final PrioritisedExecutor executor,
                final Runnable runnable,
                final Priority priority,
                final WorkType workType
        ) {
            this.level = level;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.regionPos = regionPos;
            this.trackerOwnerId = trackerOwnerId;
            this.tracker = tracker;
            this.workType = workType;
            this.requestedPriority = priority;
            this.delegate = executor.createTask(() -> {
                try {
                    runnable.run();
                } finally {
                    this.completeAfterRun();
                }
            }, priority);
        }

        @Override
        public PrioritisedExecutor getExecutor() {
            return this.delegate.getExecutor();
        }

        @Override
        public boolean queue() {
            if (!this.state.compareAndSet(NEW, WAITING)) {
                return false;
            }
            return this.acquireWaitingOrBacklog(false);
        }

        @Override
        public boolean isQueued() {
            final int current = this.state.get();
            return current == WAITING || current == STARTED || this.delegate.isQueued();
        }

        @Override
        public boolean cancel() {
            for (;;) {
                final int previous = this.state.get();
                if (previous == CANCELLED || previous == COMPLETED) {
                    return false;
                }
                if (!this.state.compareAndSet(previous, CANCELLED)) {
                    continue;
                }

                this.clearDeferred();
                this.clearBacklogRetry();
                this.clearEmergencyRetry();
                this.clearBackpressureRetry();
                this.releaseWaiting();
                final boolean cancelled = this.delegate.cancel();
                if (cancelled) {
                    this.releasePermit();
                    this.releaseOverflow();
                } else if (previous == STARTED) {
                    this.state.compareAndSet(CANCELLED, STARTED);
                }
                return previous == NEW || previous == WAITING || cancelled;
            }
        }

        @Override
        public boolean execute() {
            if (!this.state.compareAndSet(NEW, WAITING)) {
                return false;
            }
            return this.acquireWaitingOrBacklog(true);
        }

        @Override
        public Priority getPriority() {
            return this.requestedPriority;
        }

        @Override
        public boolean setPriority(final Priority priority) {
            final boolean changed = this.delegate.setPriority(priority);
            if (changed) {
                this.requestedPriority = priority;
            }
            return changed;
        }

        @Override
        public boolean raisePriority(final Priority priority) {
            final boolean changed = this.delegate.raisePriority(priority);
            if (changed) {
                this.requestedPriority = Priority.max(this.requestedPriority, priority);
            }
            return changed;
        }

        @Override
        public boolean lowerPriority(final Priority priority) {
            final boolean changed = this.delegate.lowerPriority(priority);
            if (changed) {
                this.requestedPriority = Priority.min(this.requestedPriority, priority);
            }
            return changed;
        }

        @Override
        public long getSubOrder() {
            return this.delegate.getSubOrder();
        }

        @Override
        public boolean setSubOrder(final long subOrder) {
            return this.delegate.setSubOrder(subOrder);
        }

        @Override
        public boolean raiseSubOrder(final long subOrder) {
            return this.delegate.raiseSubOrder(subOrder);
        }

        @Override
        public boolean lowerSubOrder(final long subOrder) {
            return this.delegate.lowerSubOrder(subOrder);
        }

        @Override
        public long getStream() {
            return this.delegate.getStream();
        }

        @Override
        public boolean setStream(final long stream) {
            return this.delegate.setStream(stream);
        }

        @Override
        public boolean setPrioritySubOrderStream(final Priority priority, final long subOrder, final long stream) {
            final boolean changed = this.delegate.setPrioritySubOrderStream(priority, subOrder, stream);
            if (changed) {
                this.requestedPriority = priority;
            }
            return changed;
        }

        @Override
        public PrioritisedExecutor.PriorityState getPriorityState() {
            return new PrioritisedExecutor.PriorityState(this.requestedPriority, this.delegate.getSubOrder(), this.delegate.getStream());
        }

        private boolean tryStart(final boolean executeNow) {
            if (this.state.get() != WAITING) {
                return false;
            }
            if (!this.ensureWaitingOnCurrentOwner()) {
                return this.deferBacklogAdmission(executeNow);
            }

            final Priority requestedPriority = this.requestedPriority;
            final RegionChunkIoTracker.Admission admission = this.tracker.tryAcquireExecutor(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    requestedPriority
            );

            if (admission.result() == RegionChunkIoTracker.Result.DEFERRED) {
                return this.deferRetry(executeNow);
            }

            if (!this.state.compareAndSet(WAITING, STARTED)) {
                if (admission.acquiredQuota()) {
                    this.tracker.completeExecutor();
                }
                return false;
            }
            this.releaseWaiting();

            if (admission.acquiredQuota()) {
                this.permitHeld.set(true);
            }
            if (admission.priority() != requestedPriority) {
                this.delegate.setPriority(admission.priority());
            }

            final boolean started = executeNow ? this.delegate.execute() : this.delegate.queue();
            if (!started) {
                this.releasePermit();
                this.state.compareAndSet(STARTED, CANCELLED);
            }
            return started;
        }

        private boolean deferRetry(final boolean executeNow) {
            synchronized (this.deferredLock) {
                if (this.state.get() != WAITING) {
                    return false;
                }
                if (this.deferredOutstanding) {
                    return true;
                }
                this.deferredOutstanding = true;
                final boolean deferred = this.tracker.deferExecutorRetry(
                        this.chunkX,
                        this.chunkZ,
                        this.workType.eventName,
                        this.requestedPriority,
                        () -> this.retryDeferred(executeNow)
                );
                if (!deferred) {
                    this.deferredOutstanding = false;
                } else {
                    return true;
                }
            }
            return this.startOverflowOrBackpressure(executeNow);
        }

        private boolean startOverflowOrBackpressure(final boolean executeNow) {
            if (this.state.get() != WAITING) {
                return false;
            }
            if (this.tracker.tryAcquireExecutorOverflow(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    this.requestedPriority
            )) {
                return this.startOverflow(executeNow);
            }
            if (this.tracker.hasExecutorDeferredRetryCapacity()) {
                return this.deferRetry(executeNow);
            }
            if (!this.backpressureRetryOutstanding.compareAndSet(false, true)) {
                return true;
            }
            if (this.tracker.deferExecutorBackpressureRetry(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    this.requestedPriority,
                    this.backpressureRetry(executeNow)
            )) {
                return true;
            }
            throw new IllegalStateException("Internal chunk executor backpressure retry coordinator refused non-dropping engine work");
        }

        private boolean startOverflow(final boolean executeNow) {
            if (!this.state.compareAndSet(WAITING, STARTED)) {
                this.tracker.completeExecutorOverflow();
                return false;
            }

            this.releaseWaiting();
            this.overflowHeld.set(true);
            final Priority fallbackPriority = this.tracker.executorOverflowPriority(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    this.requestedPriority
            );
            if (fallbackPriority != this.requestedPriority) {
                this.delegate.setPriority(fallbackPriority);
            }

            final boolean started = executeNow ? this.delegate.execute() : this.delegate.queue();
            if (!started) {
                this.releaseOverflow();
                this.state.compareAndSet(STARTED, CANCELLED);
            }
            return started;
        }

        private void retryDeferred(final boolean executeNow) {
            this.clearDeferred();
            if (this.state.get() != WAITING) {
                return;
            }
            if (this.hasOwnerChanged()) {
                this.releaseWaiting();
                this.refreshTrackerForCurrentOwner();
                this.acquireWaitingOrBacklog(executeNow);
                return;
            }
            this.tryStart(executeNow);
        }

        private void retryBackpressured(final boolean executeNow, final BackpressureRetry retry) {
            if (this.state.get() != WAITING) {
                this.clearBackpressureRetry();
                return;
            }
            if (this.hasOwnerChanged()) {
                this.clearBackpressureRetry();
                this.refreshTrackerForCurrentOwner();
                this.startOverflowOrBackpressure(executeNow);
                return;
            }
            if (this.tracker.hasExecutorDeferredRetryCapacity()) {
                this.clearBackpressureRetry();
                this.deferRetry(executeNow);
                return;
            }
            if (this.tracker.tryAcquireExecutorOverflow(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    this.requestedPriority
            )) {
                this.clearBackpressureRetry();
                this.startOverflow(executeNow);
                return;
            }
            this.tracker.requeueExecutorBackpressureRetry(retry);
        }

        private void completeAfterRun() {
            this.releasePermit();
            this.releaseOverflow();
            this.state.set(COMPLETED);
        }

        private void releasePermit() {
            if (this.permitHeld.getAndSet(false)) {
                this.tracker.completeExecutor();
            }
        }

        private void releaseOverflow() {
            if (this.overflowHeld.getAndSet(false)) {
                this.tracker.completeExecutorOverflow();
            }
        }

        private void clearDeferred() {
            synchronized (this.deferredLock) {
                if (this.deferredOutstanding) {
                    this.deferredOutstanding = false;
                    this.tracker.executorDeferredRetryStarted();
                }
            }
        }

        private boolean acquireWaitingOrBacklog(final boolean executeNow) {
            this.refreshTrackerForCurrentOwner();
            if (this.acquireWaiting()) {
                return this.tryStart(executeNow);
            }
            return this.deferBacklogAdmission(executeNow);
        }

        private boolean deferBacklogAdmission(final boolean executeNow) {
            if (this.state.get() != WAITING) {
                return false;
            }
            if (!this.backlogRetryOutstanding.compareAndSet(false, true)) {
                return true;
            }
            if (this.tracker.deferExecutorBacklogAdmission(
                    this.chunkX,
                    this.chunkZ,
                    this.workType.eventName,
                    this.requestedPriority,
                    this.backlogRetry(executeNow)
            )) {
                return true;
            }
            this.clearBacklogRetry();
            return this.startEmergencyBacklogOverflow();
        }

        private void retryBacklogAdmission(final boolean executeNow, final BacklogRetry retry) {
            if (this.state.get() != WAITING) {
                this.clearBacklogRetry();
                return;
            }
            if (this.hasOwnerChanged()) {
                this.clearBacklogRetry();
                this.refreshTrackerForCurrentOwner();
                this.acquireWaitingOrBacklog(executeNow);
                return;
            }
            if (this.acquireWaiting()) {
                this.clearBacklogRetry();
                this.tryStart(executeNow);
                return;
            }
            this.tracker.requeueExecutorBacklogAdmission(retry);
        }

        private boolean startEmergencyBacklogOverflow() {
            if (!this.state.compareAndSet(WAITING, STARTED)) {
                return false;
            }
            this.tracker.executorBacklogEmergencyStarted(this.chunkX, this.chunkZ, this.workType.eventName, this.requestedPriority);
            try {
                EMERGENCY_EXECUTOR.execute(() -> {
                    try {
                        if (!this.delegate.execute()) {
                            this.state.compareAndSet(STARTED, CANCELLED);
                        }
                    } finally {
                        this.tracker.completeExecutorBacklogEmergency();
                    }
                });
                return true;
            } catch (final RejectedExecutionException rejected) {
                this.state.compareAndSet(STARTED, WAITING);
                try {
                    return this.deferEmergencyRetry();
                } finally {
                    this.tracker.completeExecutorBacklogEmergency();
                }
            }
        }

        private boolean deferEmergencyRetry() {
            if (this.state.get() != WAITING) {
                return false;
            }
            final boolean registered = this.emergencyRetryOutstanding.compareAndSet(false, true);
            if (registered) {
                this.tracker.executorBacklogEmergencyRetryQueued(this.chunkX, this.chunkZ, this.workType.eventName, this.requestedPriority);
            }
            try {
                if (!this.emergencyRetryScheduled.compareAndSet(false, true)) {
                    return true;
                }
                EMERGENCY_RETRY_EXECUTOR.schedule(this::retryEmergencyBacklogOverflow, 1L, TimeUnit.MILLISECONDS);
                return true;
            } catch (final RuntimeException throwable) {
                this.emergencyRetryScheduled.set(false);
                if (registered) {
                    this.clearEmergencyRetry();
                }
                throw throwable;
            }
        }

        private void retryEmergencyBacklogOverflow() {
            this.emergencyRetryScheduled.set(false);
            if (this.state.get() == WAITING) {
                this.acquireWaitingOrBacklog(false);
            }
            if (!this.emergencyRetryScheduled.get()) {
                this.clearEmergencyRetry();
            }
        }

        private void clearBacklogRetry() {
            if (this.backlogRetryOutstanding.getAndSet(false)) {
                this.tracker.executorBacklogAdmissionStarted();
            }
        }

        private void clearEmergencyRetry() {
            if (this.emergencyRetryOutstanding.getAndSet(false)) {
                this.emergencyRetryScheduled.set(false);
                this.tracker.executorBacklogEmergencyRetryStarted();
            }
        }

        private void clearBackpressureRetry() {
            if (this.backpressureRetryOutstanding.getAndSet(false)) {
                this.tracker.executorBackpressureRetryStarted();
            }
        }

        private boolean ensureWaitingOnCurrentOwner() {
            if (!this.hasOwnerChanged()) {
                return true;
            }

            this.releaseWaiting();
            this.refreshTrackerForCurrentOwner();
            return this.acquireWaiting();
        }

        private boolean hasOwnerChanged() {
            final RegionRuntimeState currentState = this.level.chunkSource.tickingRegions.runtimeStateForCellOrNull(this.regionPos);
            return currentState != null && currentState.ownerId() != this.trackerOwnerId;
        }

        private void refreshTrackerForCurrentOwner() {
            final RegionRuntimeState currentState = this.level.chunkSource.tickingRegions.runtimeStateForCellOrNull(this.regionPos);
            if (currentState == null || currentState.ownerId() == this.trackerOwnerId) {
                return;
            }

            this.tracker = currentState.chunkIoTracker();
            this.trackerOwnerId = currentState.ownerId();
        }

        private boolean acquireWaiting() {
            if (!this.tracker.tryBeginExecutorWaiting(this.chunkX, this.chunkZ, this.workType.eventName, this.requestedPriority)) {
                return false;
            }
            this.waitingHeld.set(true);
            return true;
        }

        private void releaseWaiting() {
            if (this.waitingHeld.getAndSet(false)) {
                this.tracker.endExecutorWaiting();
            }
        }

        private BacklogRetry backlogRetry(final boolean executeNow) {
            BacklogRetry retry = this.backlogRetry;
            if (retry != null) {
                return retry;
            }
            synchronized (this.deferredLock) {
                retry = this.backlogRetry;
                if (retry == null) {
                    this.backlogRetry = retry = new BacklogRetry(executeNow);
                }
            }
            return retry;
        }

        private BackpressureRetry backpressureRetry(final boolean executeNow) {
            BackpressureRetry retry = this.backpressureRetry;
            if (retry != null) {
                return retry;
            }
            synchronized (this.deferredLock) {
                retry = this.backpressureRetry;
                if (retry == null) {
                    this.backpressureRetry = retry = new BackpressureRetry(executeNow);
                }
            }
            return retry;
        }

        private final class BacklogRetry implements RegionChunkIoTracker.ExecutorBacklogRetry {

            private final boolean executeNow;
            private final AtomicBoolean queued = new AtomicBoolean();

            private BacklogRetry(final boolean executeNow) {
                this.executeNow = executeNow;
            }

            @Override
            public boolean markQueued() {
                return this.queued.compareAndSet(false, true);
            }

            @Override
            public void clearQueued() {
                this.queued.set(false);
            }

            @Override
            public void run() {
                PermitTask.this.retryBacklogAdmission(this.executeNow, this);
            }
        }

        private final class BackpressureRetry implements RegionChunkIoTracker.ExecutorBackpressureRetry {

            private final boolean executeNow;
            private final AtomicBoolean queued = new AtomicBoolean();

            private BackpressureRetry(final boolean executeNow) {
                this.executeNow = executeNow;
            }

            @Override
            public boolean markQueued() {
                return this.queued.compareAndSet(false, true);
            }

            @Override
            public void clearQueued() {
                this.queued.set(false);
            }

            @Override
            public void run() {
                PermitTask.this.retryBackpressured(this.executeNow, this);
            }
        }
    }
}
