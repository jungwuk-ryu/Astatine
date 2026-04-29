package io.multipaper.shreddedpaper.threading.region.events;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.EventType;
import jdk.jfr.Label;

@Category({"Astatine", "Region"})
@Label("Region Tick")
public final class RegionTickEvent extends Event {

    private static final EventType EVENT_TYPE = EventType.getEventType(RegionTickEvent.class);

    public static boolean isEventEnabled() {
        return EVENT_TYPE.isEnabled();
    }

    public String world;
    public int regionX;
    public int regionZ;
    public String loadClass;
    public long scheduledStartNanos;
    public long actualStartNanos;
    public long wallNanos;
    public long scheduleLagNanos;
    public int mailboxDepth;
    public int criticalSystemQueued;
    public int criticalSystemPeakQueued;
    public long criticalSystemOldestAgeNanos;
    public String criticalSystemPeakProducer;
    public int transferredQueued;
    public int transferredPeakQueued;
    public long transferredOldestAgeNanos;
    public String transferredPeakProducer;
    public int playerActionQueued;
    public int ownerHandoffQueued;
    public int chunkIoLoadQueued;
    public int chunkIoSaveQueued;
    public int chunkIoInFlight;
    public int chunkIoDeferred;
    public int chunkIoCapacity;
    public long chunkIoRejected;
    public long chunkIoDowngraded;
    public double chunkIoPressure;
    public int chunkIoExecutorWaiting;
    public int chunkIoExecutorBacklogQueued;
    public int chunkIoExecutorBacklogCapacity;
    public long chunkIoExecutorBacklogBackpressure;
    public long chunkIoExecutorBacklogDeferred;
    public int chunkIoExecutorBacklogEmergencyInFlight;
    public long chunkIoExecutorBacklogEmergency;
    public int chunkIoExecutorBacklogEmergencyRetries;
    public long chunkIoExecutorBacklogEmergencyRejected;
    public double chunkIoExecutorBacklogPressure;
    public int chunkIoExecutorInFlight;
    public int chunkIoExecutorDeferred;
    public int chunkIoExecutorCapacity;
    public long chunkIoExecutorRejected;
    public long chunkIoExecutorDowngraded;
    public double chunkIoExecutorPressure;
    public int chunkIoExecutorOverflowInFlight;
    public int chunkIoExecutorOverflowCapacity;
    public long chunkIoExecutorOverflowAdmitted;
    public long chunkIoExecutorOverflowBackpressure;
    public double chunkIoExecutorOverflowPressure;
    public int chunkIoExecutorBackpressureWaiters;
    public int chunkIoExecutorBackpressureCapacity;
    public double chunkIoExecutorBackpressurePressure;
    public int pluginQueued;
    public int trackerBroadcastQueued;
    public int explosionPhysicsQueued;
    public double mailboxClassPressure;
    public long deferredWork;
}
