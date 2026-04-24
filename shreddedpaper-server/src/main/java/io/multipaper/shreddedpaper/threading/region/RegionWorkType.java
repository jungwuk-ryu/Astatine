package io.multipaper.shreddedpaper.threading.region;

public enum RegionWorkType {
    INTERNAL_TASK,
    REGION_TASK,
    BLOCK_TICK,
    FLUID_TICK,
    CHUNK_TICK,
    ENTITY_TICK,
    TRACKER,
    BLOCK_ENTITY,
    PLAYER,
    BROADCAST,
    EXPLOSION,
    CHUNK_IO
}
