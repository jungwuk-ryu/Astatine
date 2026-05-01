package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import net.minecraft.server.level.ServerLevel;

public final class RegionOverloadController {

    private static final double EWMA_ALPHA = 0.15D;

    private final ServerLevel level;
    private final RegionPos regionPos;
    private volatile RegionLoadClass loadClass = RegionLoadClass.NORMAL;
    private volatile double ewmaMspt;
    private volatile double ewmaScheduleLagMs;
    private volatile boolean degradedRegionBossBarVisible;
    private volatile DiagnosticSnapshot diagnostics = DiagnosticSnapshot.EMPTY;
    private int quarantineStrikes;

    public RegionOverloadController(final ServerLevel level, final RegionPos regionPos) {
        this.level = level;
        this.regionPos = regionPos;
    }

    public RegionTickBudget beginTick(final long startNanos) {
        return new RegionTickBudget(
                this.level,
                this.regionPos,
                startNanos,
                ShreddedPaperConfiguration.get().multithreading.regionTickBudgetMs
        );
    }

    public void recordTick(
            final long tickNanos,
            final long scheduleLagNanos,
            final int mailboxDepth,
            final double mailboxClassPressure,
            final long deferredWork,
            final RegionChunkIoTracker.Snapshot chunkIo
    ) {
        final double mspt = tickNanos / 1.0E6D;
        final double lagMs = Math.max(0L, scheduleLagNanos) / 1.0E6D;
        this.ewmaMspt = this.ewmaMspt == 0.0D ? mspt : (this.ewmaMspt * (1.0D - EWMA_ALPHA)) + (mspt * EWMA_ALPHA);
        this.ewmaScheduleLagMs = this.ewmaScheduleLagMs == 0.0D ? lagMs : (this.ewmaScheduleLagMs * (1.0D - EWMA_ALPHA)) + (lagMs * EWMA_ALPHA);
        this.diagnostics = new DiagnosticSnapshot(
                mailboxDepth,
                mailboxClassPressure,
                chunkIo.requestPressure(),
                chunkIo.inFlight(),
                chunkIo.deferredRetries(),
                deferredWork
        );

        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        if (this.ewmaMspt >= config.quarantinedRegionMsptThreshold) {
            if (++this.quarantineStrikes >= 3) {
                this.loadClass = RegionLoadClass.QUARANTINED;
                return;
            }
        } else {
            this.quarantineStrikes = 0;
        }

        if (this.ewmaMspt >= config.degradedRegionMsptThreshold
                || deferredWork > 0L
                || mailboxClassPressure >= 0.5D
                || chunkIo.requestPressure() >= 0.5D
                || chunkIo.deferredRetries() > 0
                || chunkIo.executorWaitingTasks() + chunkIo.executorBacklogQueuedTasks() > chunkIo.executorCapacity()
                || chunkIo.executorDeferredRetries() > 0
                || chunkIo.executorOverflowInFlight() > 0
                || chunkIo.executorBacklogEmergencyInFlight() > 0
                || chunkIo.executorBacklogEmergencyRetries() > 0
                || chunkIo.executorBackpressuredRetries() > 0) {
            this.loadClass = RegionLoadClass.DEGRADED;
        } else {
            this.loadClass = RegionLoadClass.NORMAL;
        }
    }

    public RegionLoadClass loadClass() {
        return this.loadClass;
    }

    public double ewmaMspt() {
        return this.ewmaMspt;
    }

    public double ewmaScheduleLagMs() {
        return this.ewmaScheduleLagMs;
    }

    public int lastMailboxDepth() {
        return this.diagnostics.lastMailboxDepth();
    }

    public double lastMailboxClassPressure() {
        return this.diagnostics.lastMailboxClassPressure();
    }

    public double lastChunkIoPressure() {
        return this.diagnostics.lastChunkIoPressure();
    }

    public int lastChunkIoInFlight() {
        return this.diagnostics.lastChunkIoInFlight();
    }

    public int lastChunkIoDeferred() {
        return this.diagnostics.lastChunkIoDeferred();
    }

    public long lastDeferredWork() {
        return this.diagnostics.lastDeferredWork();
    }

    public DiagnosticSnapshot diagnostics() {
        return this.diagnostics;
    }

    public boolean degradedRegionBossBarVisible() {
        return this.degradedRegionBossBarVisible;
    }

    public void degradedRegionBossBarVisible(final boolean visible) {
        this.degradedRegionBossBarVisible = visible;
    }

    /**
     * Display-only observations are published as one immutable volatile snapshot.
     * Control decisions still read the current recordTick inputs and EWMA fields directly,
     * so diagnostics may be slightly stale without delaying degrade/quarantine transitions.
     */
    public record DiagnosticSnapshot(
            int lastMailboxDepth,
            double lastMailboxClassPressure,
            double lastChunkIoPressure,
            int lastChunkIoInFlight,
            int lastChunkIoDeferred,
            long lastDeferredWork
    ) {
        private static final DiagnosticSnapshot EMPTY = new DiagnosticSnapshot(0, 0.0D, 0.0D, 0, 0, 0L);
    }
}
