package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.region.events.RegionLockWaitEvent;

/** One summary per contended acquisition, preserving the deadline across retries. */
final class RegionLockWait {
    private RegionLockWaitEvent pending;

    synchronized void failed(final String world, final RegionPos region, final long scheduled, final long now,
                             final ShreddedPaperRegionLocker locker, final long[] isolationCells) {
        if (this.pending == null) {
            if (!RegionLockWaitEvent.isEventEnabled()) return;
            final var event = new RegionLockWaitEvent();
            event.world = world;
            event.regionX = region.x;
            event.regionZ = region.z;
            event.originalScheduledNanos = scheduled;
            event.firstFailedNanos = now;
            final var blocker = locker.observedConflictingLock(isolationCells);
            event.observedBlocker = blocker == null
                ? (locker.globalLock().isWriteLocked() ? "global-write-lock" : "released-before-observation")
                : blocker.owner().getName();
            if (blocker != null) {
                event.blockerX = blocker.regionPos().x;
                event.blockerZ = blocker.regionPos().z;
            }
            this.pending = event;
        }
        this.pending.failedAttempts++;
    }

    synchronized void finish(final long now, final String outcome) {
        final var event = this.pending;
        if (event == null) return;
        this.pending = null;
        event.waitNanos = Math.max(0L, now - event.firstFailedNanos);
        event.overdueNanos = Math.max(0L, now - event.originalScheduledNanos);
        event.outcome = outcome;
        event.commit();
    }
}
