package io.multipaper.shreddedpaper.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NetworkFlushDiagnosticsTest {

    @BeforeEach
    @AfterEach
    void resetDiagnostics() {
        NetworkFlushDiagnostics.reset();
    }

    @Test
    void snapshotCountsAllFlushQueueOutcomes() {
        NetworkFlushDiagnostics.recordDisconnectedShortCircuit();
        NetworkFlushDiagnostics.recordMainThreadResult(true);
        NetworkFlushDiagnostics.recordMainThreadResult(false);
        NetworkFlushDiagnostics.recordPendingSynchronizedResult(true);
        NetworkFlushDiagnostics.recordPendingSynchronizedResult(false);
        NetworkFlushDiagnostics.recordRejectedOffThread(false);
        NetworkFlushDiagnostics.recordRejectedOffThread(true);

        NetworkFlushDiagnostics.Snapshot snapshot = NetworkFlushDiagnostics.snapshot();

        assertEquals(1, snapshot.disconnectedShortCircuits());
        assertEquals(1, snapshot.mainThreadProcessed());
        assertEquals(1, snapshot.mainThreadBlocked());
        assertEquals(1, snapshot.pendingSynchronizedProcessed());
        assertEquals(1, snapshot.pendingSynchronizedBlocked());
        assertEquals(2, snapshot.rejectedOffThread());
        assertEquals(1, snapshot.rejectedOffThreadWithBacklog());
        assertEquals(7, snapshot.totalCalls());
    }

    @Test
    void snapshotAndResetReturnsPreviousCounts() {
        NetworkFlushDiagnostics.recordRejectedOffThread(true);

        NetworkFlushDiagnostics.Snapshot previous = NetworkFlushDiagnostics.snapshotAndReset();
        NetworkFlushDiagnostics.Snapshot current = NetworkFlushDiagnostics.snapshot();

        assertEquals(1, previous.rejectedOffThread());
        assertEquals(1, previous.rejectedOffThreadWithBacklog());
        assertEquals(0, current.totalCalls());
    }
}
