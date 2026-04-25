package io.multipaper.shreddedpaper.threading.region;

public enum RegionTaskClass {
    CRITICAL_SYSTEM,
    PLAYER_ACTION,
    CHUNK_IO_SAVE,
    PLUGIN,
    TRACKER_BROADCAST,
    EXPLOSION_PHYSICS
}
