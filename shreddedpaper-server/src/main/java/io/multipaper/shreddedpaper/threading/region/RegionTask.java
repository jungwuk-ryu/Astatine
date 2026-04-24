package io.multipaper.shreddedpaper.threading.region;

import java.util.concurrent.atomic.AtomicLong;

final class RegionTask implements Comparable<RegionTask>, Runnable {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final RegionTaskClass taskClass;
    private final Runnable runnable;
    private final long readyTick;
    private final long sequence;

    RegionTask(final RegionTaskClass taskClass, final Runnable runnable, final long readyTick) {
        this.taskClass = taskClass;
        this.runnable = runnable;
        this.readyTick = readyTick;
        this.sequence = SEQUENCE.getAndIncrement();
    }

    RegionTaskClass taskClass() {
        return this.taskClass;
    }

    long readyTick() {
        return this.readyTick;
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
