package io.multipaper.shreddedpaper.threading.region;

import java.util.concurrent.atomic.AtomicLong;

final class RegionTask implements Comparable<RegionTask>, Runnable {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final RegionTaskClass taskClass;
    private final Runnable runnable;
    private final long readyTick;
    private final long sequence;
    private final long targetOwnerId;
    private final long targetOwnerEpoch;
    private final long affinityCellKey;
    private final long enqueueNanos;
    private final boolean reservedSlot;
    private final boolean emergencySlot;

    RegionTask(
            final RegionTaskClass taskClass,
            final Runnable runnable,
            final long readyTick,
            final long targetOwnerId,
            final long targetOwnerEpoch,
            final long affinityCellKey,
            final long enqueueNanos,
            final boolean reservedSlot,
            final boolean emergencySlot
    ) {
        this.taskClass = taskClass;
        this.runnable = runnable;
        this.readyTick = readyTick;
        this.sequence = SEQUENCE.getAndIncrement();
        this.targetOwnerId = targetOwnerId;
        this.targetOwnerEpoch = targetOwnerEpoch;
        this.affinityCellKey = affinityCellKey;
        this.enqueueNanos = enqueueNanos;
        this.reservedSlot = reservedSlot;
        this.emergencySlot = emergencySlot;
    }

    RegionTaskClass taskClass() {
        return this.taskClass;
    }

    long readyTick() {
        return this.readyTick;
    }

    long targetOwnerId() {
        return this.targetOwnerId;
    }

    long targetOwnerEpoch() {
        return this.targetOwnerEpoch;
    }

    long affinityCellKey() {
        return this.affinityCellKey;
    }

    long enqueueNanos() {
        return this.enqueueNanos;
    }

    boolean reservedSlot() {
        return this.reservedSlot;
    }

    boolean emergencySlot() {
        return this.emergencySlot;
    }

    RegionTask reschedule(final long readyTick) {
        return new RegionTask(
                this.taskClass,
                this.runnable,
                readyTick,
                this.targetOwnerId,
                this.targetOwnerEpoch,
                this.affinityCellKey,
                this.enqueueNanos,
                this.reservedSlot,
                this.emergencySlot
        );
    }

    @Override
    public void run() {
        this.runnable.run();
    }

    @Override
    public int compareTo(final RegionTask other) {
        final int tickCompare = Long.compare(this.readyTick, other.readyTick);
        return tickCompare != 0 ? tickCompare : Long.compare(this.sequence, other.sequence);
    }
}
