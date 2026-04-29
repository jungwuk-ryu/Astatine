package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;

import java.util.concurrent.atomic.AtomicLong;

@Category({"Astatine", "Region"})
@Label("Region Scheduler")
public final class RegionSchedulerEvent extends Event {

    private static final EventType EVENT_TYPE = EventType.getEventType(RegionSchedulerEvent.class);
    private static final AtomicLong SAMPLES = new AtomicLong();

    public static boolean isEventEnabled() {
        return EVENT_TYPE.isEnabled();
    }

    public static long nextSampleCount() {
        return SAMPLES.incrementAndGet();
    }

    public static boolean shouldCommitSample(final long count) {
        return count > 0L && (count == 1L || (count & 255L) == 0L);
    }

    public long sampleCount;
    public String workerLane;
    public String sourceQueue;
    public String world;
    public int regionX;
    public int regionZ;
    public String loadClass;
    public long scheduledStartNanos;
    public long dequeueNanos;
    public long workerWaitNanos;
    public long workerBusyNanos;
    public long wakeupLatencyNanos;
    public int normalQueueDepth;
    public int degradedQueueDepth;
    public long degradedBacklogAgeNanos;
}
