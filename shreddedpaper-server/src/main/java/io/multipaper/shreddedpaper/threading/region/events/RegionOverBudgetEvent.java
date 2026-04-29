package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;

@Category({"ShreddedPaper", "Region"})
@Label("Region Over Budget")
public final class RegionOverBudgetEvent extends Event {
    public String world;
    public int regionX;
    public int regionZ;
    public String workType;
    public long deferred;
    public long elapsedNanos;
}
