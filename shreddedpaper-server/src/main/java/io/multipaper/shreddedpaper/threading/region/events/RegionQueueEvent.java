package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category({"ShreddedPaper", "Region"})
@Label("Region Queue")
public final class RegionQueueEvent extends Event {
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
