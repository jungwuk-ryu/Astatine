package io.multipaper.shreddedpaper.threading.ownership;

public enum OwnershipIntent {
    LOADED_READ_OPTIONAL,
    LOADED_READ_CONSERVATIVE,
    OWNER_REQUIRED_READ,
    OWNER_REQUIRED_WRITE,
    ASYNC_PREFETCH_RESUME,
    GLOBAL_REGION,
    FALSE_POSITIVE;

    public boolean allowsLoadedOnlyRead() {
        return this == LOADED_READ_OPTIONAL || this == LOADED_READ_CONSERVATIVE;
    }
}
