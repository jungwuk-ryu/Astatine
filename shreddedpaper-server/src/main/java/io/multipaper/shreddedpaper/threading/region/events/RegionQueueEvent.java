package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;

@Category({"Astatine", "Region"})
@Label("Region Queue")
public final class RegionQueueEvent extends Event {

    private static final EventType EVENT_TYPE = EventType.getEventType(RegionQueueEvent.class);

    public static boolean isEventEnabled() {
        return EVENT_TYPE.isEnabled();
    }

    public String world;
    public int regionX;
    public int regionZ;
    public String action;
    public String taskClass;
    public int depth;
    public int queuedForClass;
    public int capacity;
    public long rejected;
    public long rejectedForClass;
}
