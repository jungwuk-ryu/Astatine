package io.multipaper.shreddedpaper.region;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionOwnerTest {

    @Test
    void absorbCellsFromKeepsCellSetAndInvalidatesSnapshot() {
        final RegionOwner target = RegionOwner.singleCell(new RegionPos(1, 2));
        final RegionOwner source = RegionOwner.singleCell(new RegionPos(-3, 4));
        final var cachedBeforeAbsorb = target.cellPositionsSnapshot();

        target.absorbCellsFrom(source);

        final var cells = target.cellPositionsSnapshot();
        assertNotSame(cachedBeforeAbsorb, cells);
        assertEquals(
                Set.of(RegionPos.asLong(1, 2), RegionPos.asLong(-3, 4)),
                cells.stream().map(RegionPos::toLong).collect(Collectors.toSet())
        );
        assertEquals(2, target.cellCount());
        assertTrue(target.layoutEpoch() > 0L);
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
}
