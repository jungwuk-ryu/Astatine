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
    private volatile int lastMailboxDepth;
    private volatile double lastMailboxClassPressure;
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
            final long deferredWork
    ) {
        final double mspt = tickNanos / 1.0E6D;
        final double lagMs = Math.max(0L, scheduleLagNanos) / 1.0E6D;
        this.ewmaMspt = this.ewmaMspt == 0.0D ? mspt : (this.ewmaMspt * (1.0D - EWMA_ALPHA)) + (mspt * EWMA_ALPHA);
        this.ewmaScheduleLagMs = this.ewmaScheduleLagMs == 0.0D ? lagMs : (this.ewmaScheduleLagMs * (1.0D - EWMA_ALPHA)) + (lagMs * EWMA_ALPHA);
        this.lastMailboxDepth = mailboxDepth;
        this.lastMailboxClassPressure = mailboxClassPressure;

        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        if (this.ewmaMspt >= config.quarantinedRegionMsptThreshold) {
            if (++this.quarantineStrikes >= 3) {
                this.loadClass = RegionLoadClass.QUARANTINED;
                return;
            }
        } else {
            this.quarantineStrikes = 0;
        }

        if (this.ewmaMspt >= config.degradedRegionMsptThreshold || deferredWork > 0L || mailboxClassPressure >= 0.5D) {
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
        return this.lastMailboxDepth;
    }

    public double lastMailboxClassPressure() {
        return this.lastMailboxClassPressure;
    }
}
