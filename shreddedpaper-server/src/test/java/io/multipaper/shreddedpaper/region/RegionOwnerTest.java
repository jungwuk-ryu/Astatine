package io.multipaper.shreddedpaper.region;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionOwnerTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void absorbCellsFromKeepsCellSetAndInvalidatesSnapshot() {
        final RegionOwner target = RegionOwner.singleCell(new RegionPos(1, 2));
        final RegionOwner source = RegionOwner.singleCell(new RegionPos(-3, 4));
        final var cachedBeforeAbsorb = target.cellPositionsSnapshot();
        final long[] packedBeforeAbsorb = target.internalSortedCellKeysSnapshot();
        final long[] isolationBeforeAbsorb = target.internalSortedIsolationCellKeysSnapshot(1);

        target.absorbCellsFrom(source);

        final var cells = target.cellPositionsSnapshot();
        assertNotSame(cachedBeforeAbsorb, cells);
        assertNotSame(packedBeforeAbsorb, target.internalSortedCellKeysSnapshot());
        assertNotSame(isolationBeforeAbsorb, target.internalSortedIsolationCellKeysSnapshot(1));
        assertArrayEquals(sorted(RegionPos.asLong(1, 2), RegionPos.asLong(-3, 4)), target.internalSortedCellKeysSnapshot());
        assertEquals(
                Set.of(RegionPos.asLong(1, 2), RegionPos.asLong(-3, 4)),
                cells.stream().map(RegionPos::toLong).collect(Collectors.toSet())
        );
        assertEquals(2, target.cellCount());
        assertTrue(target.layoutEpoch() > 0L);
    }

    @Test
    void publicPackedSnapshotsAreDefensiveCopies() {
        final RegionOwner owner = RegionOwner.singleCell(new RegionPos(7, 8));
        final long expectedKey = RegionPos.asLong(7, 8);
        final long[] exposedCells = owner.sortedCellKeysSnapshot();
        final long[] exposedIsolation = owner.sortedIsolationCellKeysSnapshot(0);

        exposedCells[0] = RegionPos.asLong(99, 99);
        exposedIsolation[0] = RegionPos.asLong(100, 100);

        assertArrayEquals(new long[] {expectedKey}, owner.internalSortedCellKeysSnapshot());
        assertArrayEquals(new long[] {expectedKey}, owner.internalSortedIsolationCellKeysSnapshot(0));
    }

    @Test
    void packedIsolationSnapshotMatchesObjectSnapshotSet() {
        final LongOpenHashSet cells = new LongOpenHashSet();
        cells.add(RegionPos.asLong(0, 0));
        cells.add(RegionPos.asLong(1, 0));
        final RegionOwner owner = RegionOwner.splitOwner(new RegionPos(0, 0), cells);

        final long[] packedIsolation = owner.sortedIsolationCellKeysSnapshot(1);
        final long[] objectIsolation = owner.isolationCellPositionsSnapshot(1).stream()
                .mapToLong(RegionPos::toLong)
                .sorted()
                .toArray();

        assertArrayEquals(objectIsolation, packedIsolation);
        assertTrue(isStrictlySorted(packedIsolation));
    }

    @Test
    void packedExactLockOverloadPreservesWriteAndIsolationSemantics() {
        final ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();
        final RegionPos write = new RegionPos(2, 3);
        final RegionPos readOnly = new RegionPos(3, 3);
        final long[] writeKeys = sorted(write.longKey);
        final long[] isolationKeys = sorted(write.longKey, readOnly.longKey);

        final ShreddedPaperRegionLocker.RegionLock lock = locker.internalTryTakeExactLockNow(writeKeys, isolationKeys);
        assertNotNull(lock);
        try {
            assertTrue(locker.hasLock(write));
            assertTrue(locker.hasLock(readOnly));
            assertTrue(locker.hasWriteLock(write));
            assertFalse(locker.hasWriteLock(readOnly));
        } finally {
            lock.unlock();
        }

        assertFalse(locker.hasLock(write));
        assertFalse(locker.hasLock(readOnly));
    }

    @Test
    void absorbCellsFromSelfIsDefensiveNoOp() {
        final RegionOwner owner = RegionOwner.singleCell(new RegionPos(5, 6));
        final long layoutEpoch = owner.layoutEpoch();
        final var cachedBeforeAbsorb = owner.cellPositionsSnapshot();

        owner.absorbCellsFrom(owner);

        assertEquals(layoutEpoch, owner.layoutEpoch());
        assertEquals(cachedBeforeAbsorb, owner.cellPositionsSnapshot());
    }

    @Test
    void oppositeAbsorbCallsDoNotDeadlockCellMonitors() throws InterruptedException {
        final RegionOwner first = RegionOwner.singleCell(new RegionPos(10, 11));
        final RegionOwner second = RegionOwner.singleCell(new RegionPos(12, 13));
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final Thread left = new Thread(() -> absorbAfterStart(first, second, start, failure), "region-owner-test-left");
        final Thread right = new Thread(() -> absorbAfterStart(second, first, start, failure), "region-owner-test-right");
        left.setDaemon(true);
        right.setDaemon(true);
        left.start();
        right.start();
        start.countDown();

        join(left);
        join(right);

        assertFalse(left.isAlive(), "left absorb call did not complete");
        assertFalse(right.isAlive(), "right absorb call did not complete");
        assertNull(failure.get());
        assertEquals(2, first.cellCount());
        assertEquals(2, second.cellCount());
    }

    private static void join(final Thread thread) throws InterruptedException {
        thread.join(1_000L);
    }

    private static void absorbAfterStart(
            final RegionOwner target,
            final RegionOwner source,
            final CountDownLatch start,
            final AtomicReference<Throwable> failure
    ) {
        try {
            start.await();
            target.absorbCellsFrom(source);
        } catch (final Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static boolean isStrictlySorted(final long[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] >= values[i]) {
                return false;
            }
        }
        return true;
    }

    private static long[] sorted(final long... values) {
        Arrays.sort(values);
        return values;
    }
}
