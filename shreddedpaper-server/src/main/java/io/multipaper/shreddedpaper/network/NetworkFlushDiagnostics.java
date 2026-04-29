package io.multipaper.shreddedpaper.network;

import java.util.concurrent.atomic.LongAdder;

public final class NetworkFlushDiagnostics {

    private static final LongAdder disconnectedShortCircuits = new LongAdder();
    private static final LongAdder mainThreadProcessed = new LongAdder();
    private static final LongAdder mainThreadBlocked = new LongAdder();
    private static final LongAdder pendingSynchronizedProcessed = new LongAdder();
    private static final LongAdder pendingSynchronizedBlocked = new LongAdder();
    private static final LongAdder rejectedOffThread = new LongAdder();
    private static final LongAdder rejectedOffThreadWithBacklog = new LongAdder();

    private NetworkFlushDiagnostics() {
    }

    public static void recordDisconnectedShortCircuit() {
        disconnectedShortCircuits.increment();
    }

    public static void recordMainThreadResult(boolean processed) {
        if (processed) {
            mainThreadProcessed.increment();
        } else {
            mainThreadBlocked.increment();
        }
    }

    public static void recordPendingSynchronizedResult(boolean processed) {
        if (processed) {
            pendingSynchronizedProcessed.increment();
        } else {
            pendingSynchronizedBlocked.increment();
        }
    }

    public static void recordRejectedOffThread(boolean hasBacklog) {
        rejectedOffThread.increment();
        if (hasBacklog) {
            rejectedOffThreadWithBacklog.increment();
        }
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            disconnectedShortCircuits.sum(),
            mainThreadProcessed.sum(),
            mainThreadBlocked.sum(),
            pendingSynchronizedProcessed.sum(),
            pendingSynchronizedBlocked.sum(),
            rejectedOffThread.sum(),
            rejectedOffThreadWithBacklog.sum()
        );
    }

    public static Snapshot snapshotAndReset() {
        Snapshot snapshot = snapshot();
        reset();
        return snapshot;
    }

    public static void reset() {
        disconnectedShortCircuits.reset();
        mainThreadProcessed.reset();
        mainThreadBlocked.reset();
        pendingSynchronizedProcessed.reset();
        pendingSynchronizedBlocked.reset();
        rejectedOffThread.reset();
        rejectedOffThreadWithBacklog.reset();
    }

    public record Snapshot(
        long disconnectedShortCircuits,
        long mainThreadProcessed,
        long mainThreadBlocked,
        long pendingSynchronizedProcessed,
        long pendingSynchronizedBlocked,
        long rejectedOffThread,
        long rejectedOffThreadWithBacklog
    ) {
        public long totalCalls() {
            return disconnectedShortCircuits
                + mainThreadProcessed
                + mainThreadBlocked
                + pendingSynchronizedProcessed
                + pendingSynchronizedBlocked
                + rejectedOffThread;
        }
    }
}
