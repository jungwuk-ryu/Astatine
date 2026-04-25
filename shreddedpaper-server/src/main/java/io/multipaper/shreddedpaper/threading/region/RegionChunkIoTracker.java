package io.multipaper.shreddedpaper.threading.region;

import ca.spottedleaf.concurrentutil.util.Priority;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.events.ChunkRequestEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionChunkIoTracker {

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

    public void complete() {
        final int remaining = this.inFlight.decrementAndGet();
        if (remaining < 0) {
            this.inFlight.compareAndSet(remaining, 0);
        }
        this.completed.incrementAndGet();
    }

    public void deferredRetryStarted() {
        final int remaining = this.deferredRetries.decrementAndGet();
        if (remaining < 0) {
            this.deferredRetries.compareAndSet(remaining, 0);
        }
    }

    public void rejectDeferred(final int chunkX, final int chunkZ, final ChunkStatus status, final Priority priority) {
        this.deferredRetryStarted();
        final long count = this.rejected.incrementAndGet();
        if (ChunkRequestEvent.shouldCommitSample(count)) {
            this.commitEvent("async-load-rejected", chunkX, chunkZ, status, count, true, this.inFlight.get(), this.currentCap(), priority);
        }
    }

    public Snapshot snapshot() {
        final int cap = this.currentCap();
        final int inFlight = Math.max(0, this.inFlight.get());
        final int deferred = Math.max(0, this.deferredRetries.get());
        final double pressure = cap <= 0 ? 0.0D : Math.max(inFlight / (double) cap, deferred / (double) cap);
        return new Snapshot(
                inFlight,
                deferred,
                cap,
                pressure,
                this.admitted.get(),
                this.completed.get(),
                this.deferred.get(),
                this.rejected.get(),
                this.downgraded.get()
        );
    }

    public boolean hasPendingWork() {
        return this.inFlight.get() > 0 || this.deferredRetries.get() > 0;
    }

    private int currentCap() {
        return this.capFor(this.overloadController.loadClass(), ShreddedPaperConfiguration.get().multithreading);
    }

    private int capFor(final RegionLoadClass loadClass, final ShreddedPaperConfiguration.Multithreading config) {
        return Math.max(1, loadClass == RegionLoadClass.DEGRADED
                ? config.chunkIoLoadMaxInflightDegradedPerRegion
                : config.chunkIoLoadMaxInflightNormalPerRegion);
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

    public record Snapshot(
            int inFlight,
            int deferredRetries,
            int capacity,
            double pressure,
            long admitted,
            long completed,
            long deferred,
            long rejected,
            long downgraded
    ) {
    }
}
