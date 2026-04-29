package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionOverloadControllerTest {

    @Test
    void diagnosticSnapshotPublishesDisplayOnlyFields() {
        final RegionOverloadController controller = controller();

        controller.recordTick(
                TimeUnit.MILLISECONDS.toNanos(10L),
                TimeUnit.MILLISECONDS.toNanos(2L),
                7,
                0.25D,
                3L,
                chunkIo(4, 5, 8, 0.50D)
        );

        final RegionOverloadController.DiagnosticSnapshot diagnostics = controller.diagnostics();
        assertEquals(7, diagnostics.lastMailboxDepth());
        assertEquals(0.25D, diagnostics.lastMailboxClassPressure());
        assertEquals(0.50D, diagnostics.lastChunkIoPressure());
        assertEquals(4, diagnostics.lastChunkIoInFlight());
        assertEquals(5, diagnostics.lastChunkIoDeferred());
        assertEquals(3L, diagnostics.lastDeferredWork());
        assertEquals(RegionLoadClass.DEGRADED, controller.loadClass());
    }

    @Test
    void loadClassTransitionStillUsesCurrentTickInputs() {
        final RegionOverloadController degraded = controller();
        degraded.recordTick(TimeUnit.MILLISECONDS.toNanos(76L), 0L, 0, 0.0D, 0L, chunkIo(0, 0, 8, 0.0D));
        assertEquals(RegionLoadClass.DEGRADED, degraded.loadClass());

        final RegionOverloadController quarantined = controller();
        for (int i = 0; i < 3; i++) {
            quarantined.recordTick(TimeUnit.MILLISECONDS.toNanos(5_001L), 0L, 0, 0.0D, 0L, chunkIo(0, 0, 8, 0.0D));
        }
        assertEquals(RegionLoadClass.QUARANTINED, quarantined.loadClass());
    }

    private static RegionOverloadController controller() {
        return new RegionOverloadController(null, new RegionPos(0, 0));
    }

    private static RegionChunkIoTracker.Snapshot chunkIo(
            final int inFlight,
            final int deferredRetries,
            final int capacity,
            final double requestPressure
    ) {
        return new RegionChunkIoTracker.Snapshot(
                inFlight,
                deferredRetries,
                capacity,
                requestPressure,
                requestPressure,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                0,
                64,
                0.0D,
                0L,
                0L,
                0,
                0L,
                0,
                0L,
                0,
                0,
                8,
                0.0D,
                0L,
                0L,
                0L,
                0L,
                0L,
                0,
                64,
                0.0D,
                0L,
                0L,
                0L,
                0,
                16,
                0.0D
        );
    }
}
