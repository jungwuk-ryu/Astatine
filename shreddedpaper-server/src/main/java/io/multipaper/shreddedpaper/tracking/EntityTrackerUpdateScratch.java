package io.multipaper.shreddedpaper.tracking;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Per-thread, re-entrant scratch storage for high-density entity tracker updates.
 *
 * <p>The bounded selection keeps the best {@code K} candidates in a max heap,
 * then heap-sorts only those retained candidates. This avoids allocating and
 * sorting one wrapper object for every nearby player. Snapshot storage is also
 * reused so the normal visibility validation path does not allocate one array
 * per tracked entity per tick.</p>
 */
public final class EntityTrackerUpdateScratch {

    private static final ThreadLocal<ScratchPool> LOCAL_POOL = ThreadLocal.withInitial(ScratchPool::new);

    private EntityTrackerUpdateScratch() {
    }

    public static Scratch acquire(final int selectionLimit) {
        if (selectionLimit < 0) {
            throw new IllegalArgumentException("selectionLimit must be non-negative");
        }
        return LOCAL_POOL.get().acquire(selectionLimit);
    }

    public static void release(final Scratch scratch) {
        LOCAL_POOL.get().release(Objects.requireNonNull(scratch, "scratch"));
    }

    public static final class Scratch {

        private Object[] selectedValues = new Object[0];
        private double[] selectedPriorities = new double[0];
        private int[] selectedTieBreakers = new int[0];
        private final ReferenceOpenHashSet<Object> selectedMembership = new ReferenceOpenHashSet<>();
        private int selectionLimit;
        private int selectedSize;
        private boolean selectionFinished;

        private Object[] snapshotValues = new Object[0];
        private int snapshotSize;

        private Scratch() {
        }

        public void offer(final Object value, final double priority, final int tieBreaker) {
            Objects.requireNonNull(value, "value");
            if (this.selectionFinished) {
                throw new IllegalStateException("selection is already finished");
            }
            if (this.selectionLimit == 0) {
                return;
            }

            if (this.selectedSize < this.selectionLimit) {
                this.ensureSelectionCapacity(this.selectedSize + 1);
                final int index = this.selectedSize++;
                this.setSelection(index, value, priority, tieBreaker);
                this.siftUp(index);
                return;
            }

            if (compare(priority, tieBreaker, this.selectedPriorities[0], this.selectedTieBreakers[0]) < 0) {
                this.setSelection(0, value, priority, tieBreaker);
                this.siftDown(0, this.selectedSize);
            }
        }

        public void finishSelection() {
            if (this.selectionFinished) {
                throw new IllegalStateException("selection is already finished");
            }

            for (int end = this.selectedSize - 1; end > 0; --end) {
                this.swapSelection(0, end);
                this.siftDown(0, end);
            }
            for (int i = 0; i < this.selectedSize; ++i) {
                this.selectedMembership.add(this.selectedValues[i]);
            }
            this.selectionFinished = true;
        }

        public int selectedSize() {
            this.requireFinishedSelection();
            return this.selectedSize;
        }

        @SuppressWarnings("unchecked")
        public <T> T selectedAt(final int index) {
            this.requireFinishedSelection();
            Objects.checkIndex(index, this.selectedSize);
            return (T) this.selectedValues[index];
        }

        public boolean isSelected(final Object value) {
            this.requireFinishedSelection();
            return this.selectedMembership.contains(value);
        }

        public void snapshot(final Iterable<?> values) {
            Objects.requireNonNull(values, "values");
            this.clearSnapshot();
            for (final Object value : values) {
                this.ensureSnapshotCapacity(this.snapshotSize + 1);
                this.snapshotValues[this.snapshotSize++] = value;
            }
        }

        public int snapshotSize() {
            return this.snapshotSize;
        }

        @SuppressWarnings("unchecked")
        public <T> T snapshotAt(final int index) {
            Objects.checkIndex(index, this.snapshotSize);
            return (T) this.snapshotValues[index];
        }

        private void reset(final int selectionLimit) {
            this.selectionLimit = selectionLimit;
            this.selectedSize = 0;
            this.selectionFinished = false;
            this.selectedMembership.clear();
            this.snapshotSize = 0;
        }

        private void clear() {
            Arrays.fill(this.selectedValues, 0, this.selectedSize, null);
            this.selectedMembership.clear();
            this.selectedSize = 0;
            this.selectionLimit = 0;
            this.selectionFinished = false;
            this.clearSnapshot();
        }

        private void clearSnapshot() {
            Arrays.fill(this.snapshotValues, 0, this.snapshotSize, null);
            this.snapshotSize = 0;
        }

        private void requireFinishedSelection() {
            if (!this.selectionFinished) {
                throw new IllegalStateException("selection has not been finished");
            }
        }

        private void ensureSelectionCapacity(final int required) {
            if (required <= this.selectedValues.length) {
                return;
            }
            final int capacity = growCapacity(this.selectedValues.length, required, this.selectionLimit);
            this.selectedValues = Arrays.copyOf(this.selectedValues, capacity);
            this.selectedPriorities = Arrays.copyOf(this.selectedPriorities, capacity);
            this.selectedTieBreakers = Arrays.copyOf(this.selectedTieBreakers, capacity);
        }

        private void ensureSnapshotCapacity(final int required) {
            if (required <= this.snapshotValues.length) {
                return;
            }
            final int capacity = growCapacity(this.snapshotValues.length, required, Integer.MAX_VALUE);
            this.snapshotValues = Arrays.copyOf(this.snapshotValues, capacity);
        }

        private static int growCapacity(final int current, final int required, final int maximum) {
            final long doubled = Math.max(4L, (long) current << 1);
            return (int) Math.min(maximum, Math.max(required, doubled));
        }

        private void setSelection(final int index, final Object value, final double priority, final int tieBreaker) {
            this.selectedValues[index] = value;
            this.selectedPriorities[index] = priority;
            this.selectedTieBreakers[index] = tieBreaker;
        }

        private void siftUp(int index) {
            while (index > 0) {
                final int parent = (index - 1) >>> 1;
                if (this.compareSelection(parent, index) >= 0) {
                    return;
                }
                this.swapSelection(parent, index);
                index = parent;
            }
        }

        private void siftDown(int index, final int size) {
            while (true) {
                final int left = (index << 1) + 1;
                if (left >= size) {
                    return;
                }
                final int right = left + 1;
                final int worst = right < size && this.compareSelection(right, left) > 0 ? right : left;
                if (this.compareSelection(index, worst) >= 0) {
                    return;
                }
                this.swapSelection(index, worst);
                index = worst;
            }
        }

        private int compareSelection(final int left, final int right) {
            return compare(
                    this.selectedPriorities[left],
                    this.selectedTieBreakers[left],
                    this.selectedPriorities[right],
                    this.selectedTieBreakers[right]
            );
        }

        private void swapSelection(final int left, final int right) {
            final Object value = this.selectedValues[left];
            this.selectedValues[left] = this.selectedValues[right];
            this.selectedValues[right] = value;

            final double priority = this.selectedPriorities[left];
            this.selectedPriorities[left] = this.selectedPriorities[right];
            this.selectedPriorities[right] = priority;

            final int tieBreaker = this.selectedTieBreakers[left];
            this.selectedTieBreakers[left] = this.selectedTieBreakers[right];
            this.selectedTieBreakers[right] = tieBreaker;
        }

        private static int compare(final double leftPriority, final int leftTieBreaker, final double rightPriority, final int rightTieBreaker) {
            final int priorityComparison = Double.compare(leftPriority, rightPriority);
            return priorityComparison != 0 ? priorityComparison : Integer.compare(leftTieBreaker, rightTieBreaker);
        }
    }

    private static final class ScratchPool {

        private final ArrayList<Scratch> scratches = new ArrayList<>(1);
        private int depth;

        private Scratch acquire(final int selectionLimit) {
            if (this.depth == this.scratches.size()) {
                this.scratches.add(new Scratch());
            }
            final Scratch scratch = this.scratches.get(this.depth++);
            scratch.reset(selectionLimit);
            return scratch;
        }

        private void release(final Scratch scratch) {
            if (this.depth == 0 || this.scratches.get(this.depth - 1) != scratch) {
                throw new IllegalStateException("tracker scratch must be released by its acquiring thread in LIFO order");
            }
            scratch.clear();
            --this.depth;
        }
    }
}
