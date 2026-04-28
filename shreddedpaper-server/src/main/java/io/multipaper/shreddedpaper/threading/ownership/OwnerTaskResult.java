package io.multipaper.shreddedpaper.threading.ownership;

public enum OwnerTaskResult {
    RAN_INLINE,
    QUEUED_TO_OWNER,
    REJECTED,
    CHUNK_UNAVAILABLE
}
