package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

import java.util.concurrent.atomic.AtomicLong;

@Name("io.multipaper.shreddedpaper.region.ChunkRequest")
@Label("Chunk Request")
@Category({"ShreddedPaper", "Region"})
public final class ChunkRequestEvent extends Event {

    private static final AtomicLong SYNC_LOAD_REJECTIONS = new AtomicLong();

    public static long recordSyncLoadRejection() {
        return SYNC_LOAD_REJECTIONS.incrementAndGet();
    }

    public static boolean shouldCommitSample(final long count) {
        return count == 1L || (count & 255L) == 0L;
    }

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

    @Label("Status")
    public String status;

    @Label("Action")
    public String action;

    @Label("Count")
    public long count;

    @Label("Thread")
    public String thread;

    @Label("Synchronous")
    public boolean sync;

    @Label("Rejected")
    public boolean rejected;
}
