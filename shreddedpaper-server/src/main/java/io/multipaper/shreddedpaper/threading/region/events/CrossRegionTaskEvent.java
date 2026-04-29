package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("io.multipaper.shreddedpaper.region.CrossRegionTask")
@Category({"Astatine", "Region"})
@Label("Cross Region Task")
public final class CrossRegionTaskEvent extends Event {

    private static final EventType EVENT_TYPE = EventType.getEventType(CrossRegionTaskEvent.class);

    public static boolean isEventEnabled() {
        return EVENT_TYPE.isEnabled();
    }

    public String world;
    public String sourceWorld;
    public int sourceRegionX;
    public int sourceRegionZ;
    public long sourceOwnerId;
    public long sourceOwnerEpoch;
    public int targetRegionX;
    public int targetRegionZ;
    public long targetOwnerId;
    public long targetOwnerEpoch;
    public int affinityRegionX;
    public int affinityRegionZ;
    public long affinityCellKey;
    public String taskClass;
    public String action;
    public String delayClock;
    public long delayTicks;
    public int queuedAfter;
    public int capacity;
    public boolean hasSourceRegion;
    public boolean crossRegion;
    public boolean sameWorld;
    public boolean sameOwner;
    public String thread;
}
