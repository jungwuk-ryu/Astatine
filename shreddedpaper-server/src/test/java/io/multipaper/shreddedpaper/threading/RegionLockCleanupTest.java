package io.multipaper.shreddedpaper.threading;

import io.multipaper.shreddedpaper.region.RegionPos;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@AllFeatures
class RegionLockCleanupTest {
    @Test
    void cleanupPreservesForeignLocksAndReleasesOwnLeakedLock() throws Exception {
        final var locker = new ShreddedPaperRegionLocker();
        final CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread other = new Thread(() -> {
            ShreddedPaperRegionLocker.RegionLock lock = null;
            try {
                lock = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(20, 20)});
                assertNotNull(lock);
                ready.countDown();
                assertTrue(release.await(10, TimeUnit.SECONDS));
                assertTrue(locker.hasWriteLock(new RegionPos(20, 20)));
            } catch (Throwable t) { failure.set(t); ready.countDown(); }
            finally { if (lock != null) lock.unlock(); }
        });
        other.start();
        try {
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            assertEquals(0, locker.releaseCurrentThreadLocks());
            var own = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(0, 0)});
            assertNotNull(own);
            assertEquals(1, locker.releaseCurrentThreadLocks());
            own.unlock(); // Cleanup and subsequent finally-unlock remain idempotent.
            assertEquals(0, locker.releaseCurrentThreadLocks());
            assertEquals(1, locker.getAllLockedRegions().size());
        } finally { release.countDown(); other.join(5000); }
        assertFalse(other.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertFalse(locker.areAnyLocked());
    }

    @Test
    void missingActiveRegistryStillTriggersMapRecovery() throws Exception {
        final var locker = new ShreddedPaperRegionLocker();
        final var lock = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(0, 0), RegionPos.asLong(1, 0)});
        assertNotNull(lock);
        // Simulate corrupted bookkeeping; the ownership counter is deliberately independent.
        final Field field = ShreddedPaperRegionLocker.class.getDeclaredField("activeReadLocks");
        field.setAccessible(true);
        ((Set<?>) ((ThreadLocal<?>) field.get(locker)).get()).clear();
        assertEquals(2, locker.releaseCurrentThreadLocks());
        assertFalse(locker.areAnyLocked());
        lock.unlock(); // Also releases the global read stamp, which is owned by this handle.
        assertEquals(0, locker.releaseCurrentThreadLocks());
        final var next = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(0, 0)});
        assertNotNull(next);
        next.unlock();
        assertEquals(0, locker.releaseCurrentThreadLocks());
    }

    @Test
    void nestedLocksDoNotDoubleCountAlreadyOwnedCells() {
        final var locker = new ShreddedPaperRegionLocker();
        final var first = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(0, 0)});
        final var nested = locker.internalTryTakeExactLockNow(new long[]{RegionPos.asLong(0, 0)});
        assertNotNull(first);
        assertNotNull(nested);
        nested.unlock();
        assertTrue(locker.hasWriteLock(new RegionPos(0, 0)));
        first.unlock();
        assertEquals(0, locker.releaseCurrentThreadLocks());
        assertFalse(locker.areAnyLocked());
    }
}
