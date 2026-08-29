package io.multipaper.shreddedpaper.tracking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityTrackerUpdateScratchTest {

    @Test
    void boundedSelectionMatchesFullDeterministicSort() {
        final Random random = new Random(0xA57A71E5L);

        for (int candidateCount = 0; candidateCount <= 200; candidateCount += 5) {
            final List<Candidate> candidates = new ArrayList<>(candidateCount);
            for (int id = 0; id < candidateCount; ++id) {
                final double distance = random.nextInt(20);
                final boolean bypass = random.nextInt(11) == 0;
                candidates.add(new Candidate(id, distance, bypass));
            }

            for (final int limit : new int[]{0, 1, 7, 32, 128, 256}) {
                final List<Candidate> expected = candidates.stream()
                        .sorted(Comparator.comparingDouble(Candidate::effectiveDistance).thenComparingInt(Candidate::id))
                        .limit(limit)
                        .toList();

                final EntityTrackerUpdateScratch.Scratch scratch = EntityTrackerUpdateScratch.acquire(limit);
                try {
                    for (final Candidate candidate : candidates) {
                        scratch.offer(candidate, candidate.effectiveDistance(), candidate.id());
                    }
                    scratch.finishSelection();

                    assertEquals(expected.size(), scratch.selectedSize());
                    for (int i = 0; i < expected.size(); ++i) {
                        assertSame(expected.get(i), scratch.<Candidate>selectedAt(i));
                    }
                    for (final Candidate candidate : candidates) {
                        assertEquals(expected.contains(candidate), scratch.isSelected(candidate));
                    }
                } finally {
                    EntityTrackerUpdateScratch.release(scratch);
                }
            }
        }
    }

    @Test
    void snapshotStorageIsReusedWithoutRetainingOldMembership() {
        final Candidate first = new Candidate(1, 1.0, false);
        final Candidate second = new Candidate(2, 2.0, false);

        final EntityTrackerUpdateScratch.Scratch initial = EntityTrackerUpdateScratch.acquire(1);
        try {
            initial.offer(first, first.effectiveDistance(), first.id());
            initial.finishSelection();
            initial.snapshot(List.of(first, second));
            assertEquals(2, initial.snapshotSize());
            assertSame(first, initial.<Candidate>snapshotAt(0));
            assertTrue(initial.isSelected(first));
        } finally {
            EntityTrackerUpdateScratch.release(initial);
        }

        final EntityTrackerUpdateScratch.Scratch reused = EntityTrackerUpdateScratch.acquire(1);
        try {
            assertSame(initial, reused);
            reused.offer(second, second.effectiveDistance(), second.id());
            reused.finishSelection();
            reused.snapshot(List.of(second));
            assertEquals(1, reused.snapshotSize());
            assertSame(second, reused.<Candidate>snapshotAt(0));
            assertFalse(reused.isSelected(first));
            assertTrue(reused.isSelected(second));
        } finally {
            EntityTrackerUpdateScratch.release(reused);
        }
    }

    @Test
    void nestedTrackerUpdatesUseIndependentScratchBuffers() {
        final EntityTrackerUpdateScratch.Scratch outer = EntityTrackerUpdateScratch.acquire(2);
        final EntityTrackerUpdateScratch.Scratch inner = EntityTrackerUpdateScratch.acquire(1);
        try {
            assertNotSame(outer, inner);
        } finally {
            EntityTrackerUpdateScratch.release(inner);
            EntityTrackerUpdateScratch.release(outer);
        }

        final EntityTrackerUpdateScratch.Scratch reusedOuter = EntityTrackerUpdateScratch.acquire(1);
        try {
            assertSame(outer, reusedOuter);
        } finally {
            EntityTrackerUpdateScratch.release(reusedOuter);
        }
    }

    @Test
    void rejectsInvalidLifecycleUse() {
        assertThrows(IllegalArgumentException.class, () -> EntityTrackerUpdateScratch.acquire(-1));

        final EntityTrackerUpdateScratch.Scratch scratch = EntityTrackerUpdateScratch.acquire(1);
        try {
            assertThrows(IllegalStateException.class, scratch::selectedSize);
            scratch.finishSelection();
            assertThrows(IllegalStateException.class, scratch::finishSelection);
            assertThrows(IllegalStateException.class, () -> scratch.offer(new Object(), 0.0, 0));
        } finally {
            EntityTrackerUpdateScratch.release(scratch);
        }
    }

    private record Candidate(int id, double distance, boolean bypass) {
        private double effectiveDistance() {
            return this.bypass ? 0.0 : this.distance;
        }
    }
}
