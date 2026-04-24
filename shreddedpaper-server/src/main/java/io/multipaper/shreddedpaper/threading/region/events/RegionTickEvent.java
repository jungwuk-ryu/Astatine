package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category({"ShreddedPaper", "Region"})
@Label("Region Tick")
public final class RegionTickEvent extends Event {
    public String world;
    public int regionX;
    public int regionZ;
    public String loadClass;
    public long scheduledStartNanos;
    public long actualStartNanos;
    public long wallNanos;
    public long scheduleLagNanos;
    public int mailboxDepth;
    public long deferredWork;
}
