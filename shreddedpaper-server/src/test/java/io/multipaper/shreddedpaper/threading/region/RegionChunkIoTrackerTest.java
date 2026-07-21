package io.multipaper.shreddedpaper.threading.region;

import ca.spottedleaf.concurrentutil.util.Priority;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionChunkIoTrackerTest {

    private final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
    private int loadNormalCap;
    private int loadDegradedCap;
    private boolean loadPriorityDowngrade;
    private int executorNormalCap;
    private int executorDegradedCap;
    private boolean executorPriorityDowngrade;
    private long degradedThreshold;
    private long quarantinedThreshold;
    private boolean independentRegionTicking;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void configureAdmissionPolicy() {
        this.loadNormalCap = this.config.chunkIoLoadMaxInflightNormalPerRegion;
        this.loadDegradedCap = this.config.chunkIoLoadMaxInflightDegradedPerRegion;
        this.loadPriorityDowngrade = this.config.chunkIoLoadDowngradeDegradedPriority;
        this.executorNormalCap = this.config.chunkIoExecutorMaxInflightNormalPerRegion;
        this.executorDegradedCap = this.config.chunkIoExecutorMaxInflightDegradedPerRegion;
        this.executorPriorityDowngrade = this.config.chunkIoExecutorDowngradeDegradedPriority;
        this.degradedThreshold = this.config.degradedRegionMsptThreshold;
        this.quarantinedThreshold = this.config.quarantinedRegionMsptThreshold;
        this.independentRegionTicking = this.config.independentRegionTicking;

        this.config.independentRegionTicking = true;
        this.config.chunkIoLoadMaxInflightNormalPerRegion = 4;
        this.config.chunkIoLoadMaxInflightDegradedPerRegion = 1;
        this.config.chunkIoLoadDowngradeDegradedPriority = true;
        this.config.chunkIoExecutorMaxInflightNormalPerRegion = 4;
        this.config.chunkIoExecutorMaxInflightDegradedPerRegion = 1;
        this.config.chunkIoExecutorDowngradeDegradedPriority = true;
        this.config.degradedRegionMsptThreshold = 75L;
        this.config.quarantinedRegionMsptThreshold = 100L;
    }

    @AfterEach
    void restoreAdmissionPolicy() {
        this.config.chunkIoLoadMaxInflightNormalPerRegion = this.loadNormalCap;
        this.config.chunkIoLoadMaxInflightDegradedPerRegion = this.loadDegradedCap;
        this.config.chunkIoLoadDowngradeDegradedPriority = this.loadPriorityDowngrade;
        this.config.chunkIoExecutorMaxInflightNormalPerRegion = this.executorNormalCap;
        this.config.chunkIoExecutorMaxInflightDegradedPerRegion = this.executorDegradedCap;
        this.config.chunkIoExecutorDowngradeDegradedPriority = this.executorPriorityDowngrade;
        this.config.degradedRegionMsptThreshold = this.degradedThreshold;
        this.config.quarantinedRegionMsptThreshold = this.quarantinedThreshold;
        this.config.independentRegionTicking = this.independentRegionTicking;
    }

    @Test
    void quarantinedRegionKeepsDegradedCapsAndPriorities() {
        final RegionPos regionPos = new RegionPos(0, 0);
        final RegionOverloadController overloadController = new RegionOverloadController(null, regionPos);
        final RegionChunkIoTracker tracker = new RegionChunkIoTracker(null, regionPos, overloadController);
        final RegionChunkIoTracker.Snapshot idleSnapshot = tracker.snapshot();

        for (int strike = 0; strike < 3; strike++) {
            overloadController.recordTick(
                    TimeUnit.MILLISECONDS.toNanos(101L),
                    0L,
                    0,
                    0.0D,
                    0L,
                    idleSnapshot
            );
        }

        assertEquals(RegionLoadClass.QUARANTINED, overloadController.loadClass());

        final RegionChunkIoTracker.Admission admittedLoad = tracker.tryAcquire(
                0,
                0,
                ChunkStatus.EMPTY,
                true,
                Priority.NORMAL
        );
        final RegionChunkIoTracker.Admission deferredLoad = tracker.tryAcquire(
                1,
                0,
                ChunkStatus.EMPTY,
                true,
                Priority.NORMAL
        );

        assertEquals(RegionChunkIoTracker.Result.ACQUIRED, admittedLoad.result());
        assertEquals(Priority.LOW, admittedLoad.priority());
        assertEquals(RegionChunkIoTracker.Result.DEFERRED, deferredLoad.result());
        assertEquals(1, deferredLoad.capacity());

        final RegionChunkIoTracker.Admission admittedExecutor = tracker.tryAcquireExecutor(0, 0, "generation", Priority.NORMAL);
        final RegionChunkIoTracker.Admission deferredExecutor = tracker.tryAcquireExecutor(1, 0, "generation", Priority.NORMAL);

        assertEquals(RegionChunkIoTracker.Result.ACQUIRED, admittedExecutor.result());
        assertEquals(Priority.LOW, admittedExecutor.priority());
        assertEquals(RegionChunkIoTracker.Result.DEFERRED, deferredExecutor.result());
        assertEquals(1, deferredExecutor.capacity());

        final RegionChunkIoTracker.Snapshot snapshot = tracker.snapshot();
        assertEquals(1, snapshot.capacity());
        assertEquals(1, snapshot.executorCapacity());
        assertEquals(1L, snapshot.downgraded());
        assertEquals(1L, snapshot.executorDowngraded());
    }
}
