package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category({"ShreddedPaper", "Region"})
@Label("Region Split")
public final class RegionSplitEvent extends Event {
    public String world;
    public int sourceRegionX;
    public int sourceRegionZ;
    public int newRegionX;
    public int newRegionZ;
    public int movedCells;
    public int sourceCellsAfter;
    public long durationNanos;
}
