package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category({"Astatine", "Region"})
@Label("Region Merge")
public final class RegionMergeEvent extends Event {
    public String world;
    public int targetRegionX;
    public int targetRegionZ;
    public int sourceRegionX;
    public int sourceRegionZ;
    public int targetCellsBefore;
    public int sourceCells;
    public int targetCellsAfter;
    public long durationNanos;
}
