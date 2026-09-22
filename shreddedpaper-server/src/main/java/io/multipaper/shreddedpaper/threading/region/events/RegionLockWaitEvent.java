package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

@Category({"Astatine", "Region"})
@Label("Region lock retry interval")
@StackTrace(false)
public final class RegionLockWaitEvent extends Event {
    private static final EventType TYPE = EventType.getEventType(RegionLockWaitEvent.class);

    public static boolean isEventEnabled() { return TYPE.isEnabled(); }

    public String world;
    public int regionX;
    public int regionZ;
    public long originalScheduledNanos;
    public long firstFailedNanos;
    public long waitNanos;
    public long overdueNanos;
    public int failedAttempts;
    public String outcome;
    // A diagnostic snapshot after the failed try-lock, not a stable ownership claim.
    public String observedBlocker;
    public int blockerX;
    public int blockerZ;
}
