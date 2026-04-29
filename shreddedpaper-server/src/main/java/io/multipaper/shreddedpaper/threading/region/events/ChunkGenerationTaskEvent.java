package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

@Name("io.multipaper.shreddedpaper.region.ChunkGenerationTask")
@Label("Chunk Generation Task")
@Category({"ShreddedPaper", "Region"})
public final class ChunkGenerationTaskEvent extends Event {

    @Label("World")
    public String world;

    @Label("Region X")
    public int regionX;

    @Label("Region Z")
    public int regionZ;

    @Label("Chunk X")
    public int chunkX;

    @Label("Chunk Z")
    public int chunkZ;

    @Label("Work Type")
    public String workType;

    @Label("Admission Path")
    public String admissionPath;

    @Label("Thread")
    public String thread;

    @Label("Requested Priority")
    public String requestedPriority;

    @Label("Admitted Priority")
    public String admittedPriority;

    @Label("Owner At Admission")
    public long ownerAtAdmission;

    @Label("Owner At Execution")
    public long ownerAtExecution;

    @Label("Queue Wait Nanos")
    public long queueWaitNanos;

    @Label("Run Nanos")
    public long runNanos;

    @Label("Permit Held Nanos")
    public long permitHeldNanos;
}
