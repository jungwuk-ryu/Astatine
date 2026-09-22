package io.multipaper.shreddedpaper.network;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Label;
import jdk.jfr.Period;

/** Opt-in counters avoid per-packet JFR events and their profiling distortion. */
public final class NetworkBatchMetrics {
    private static final boolean ENABLED = Boolean.getBoolean("astatine.packet-batch-diagnostics");
    private static final LongAdder scheduled = new LongAdder();
    private static final LongAdder drained = new LongAdder();
    private static final LongAdder packets = new LongAdder();
    private static final LongAdder queueNanos = new LongAdder();
    private static final AtomicLong maxQueueNanos = new AtomicLong();

    static {
        if (ENABLED) FlightRecorder.addPeriodicEvent(Summary.class, () -> {
            final Summary event = new Summary();
            if (!event.isEnabled()) return;
            event.scheduledBatches = scheduled.sum();
            event.drainedBatches = drained.sum();
            event.drainedPackets = packets.sum();
            event.totalQueueNanos = queueNanos.sum();
            event.maxQueueNanos = maxQueueNanos.getAndSet(0L);
            event.commit();
        });
    }

    private NetworkBatchMetrics() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    static void scheduledBatch() {
        if (ENABLED) scheduled.increment();
    }

    static void drainedBatch(final int count, final long enqueued) {
        if (!ENABLED) return;
        final long delay = Math.max(0L, System.nanoTime() - enqueued);
        drained.increment();
        packets.add(count);
        queueNanos.add(delay);
        maxQueueNanos.accumulateAndGet(delay, Math::max);
    }

    @Category({"Astatine", "Network"})
    @Label("Ordered packet batch counters")
    @Period("1 s")
    public static final class Summary extends Event {
        public long scheduledBatches;
        public long drainedBatches;
        public long drainedPackets;
        public long totalQueueNanos;
        public long maxQueueNanos;
    }
}
