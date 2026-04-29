package io.multipaper.shreddedpaper.region;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Dynamic ownership handle above immutable region cells.
 *
 * <p>The first migration phase keeps one owner per cell and uses the cell key as
 * the owner id. Later merge/split work can move cells between owners without
 * making {@link RegionPos} itself dynamic.</p>
 */
public final class RegionOwner {

    private static final Object CELL_LOCK_TIE_BREAKER = new Object();

    private final long id;
    private final RegionPos primaryCell;
    private final LongOpenHashSet cells = new LongOpenHashSet();
    private volatile List<RegionPos> cellPositionsSnapshot;
    private volatile List<RegionPos> isolationRadiusOneSnapshot;
    private volatile LevelChunkRegion region;
    private volatile long lastMergeNanos;
    private volatile long lastSplitNanos;
    private volatile long layoutEpoch;
    private volatile boolean schedulerArmed = true;

    private RegionOwner(final long id, final RegionPos primaryCell) {
        this.id = id;
        this.primaryCell = primaryCell;
        this.cells.add(primaryCell.longKey);
    }

    public static RegionOwner singleCell(final RegionPos cell) {
        return new RegionOwner(cell.longKey, cell);
    }

    static RegionOwner splitOwner(final RegionPos primaryCell, final LongOpenHashSet splitCells) {
        if (!splitCells.contains(primaryCell.longKey)) {
            throw new IllegalArgumentException("Split owner cells must contain the primary cell");
        }
        final RegionOwner owner = new RegionOwner(primaryCell.longKey, primaryCell);
        synchronized (owner.cells) {
            owner.cells.clear();
            owner.cells.addAll(splitCells);
            owner.invalidateSnapshots();
        }
        owner.schedulerArmed = false;
        return owner;
    }

    void attachRegion(final LevelChunkRegion region) {
        this.region = region;
        this.bumpLayoutEpoch();
    }

    void detachRegion(final LevelChunkRegion region) {
        if (this.region == region) {
            this.region = null;
            this.bumpLayoutEpoch();
        }
    }

    public long id() {
        return this.id;
    }

    public RegionPos primaryCell() {
        return this.primaryCell;
    }

    public long layoutEpoch() {
        return this.layoutEpoch;
    }

    public LevelChunkRegion region() {
        return this.region;
    }

    public boolean ownsCell(final RegionPos cell) {
        return this.ownsCellKey(cell.longKey);
    }

    public boolean ownsCellKey(final long cellKey) {
        synchronized (this.cells) {
            return this.cells.contains(cellKey);
        }
    }

    public boolean isSingleCell() {
        synchronized (this.cells) {
            return this.cells.size() == 1;
        }
    }

    public void requireSingleCell(final String usage) {
        if (!this.isSingleCell()) {
            throw new IllegalStateException(usage + " still requires a single-cell region owner: " + this);
        }
    }

    public int cellCount() {
        synchronized (this.cells) {
            return this.cells.size();
        }
    }

    public LongList cellsSnapshot() {
        synchronized (this.cells) {
            return new LongArrayList(this.cells);
        }
    }

    public List<RegionPos> cellPositionsSnapshot() {
        List<RegionPos> cached = this.cellPositionsSnapshot;
        if (cached != null) {
            return cached;
        }

        synchronized (this.cells) {
            cached = this.cellPositionsSnapshot;
            if (cached != null) {
                return cached;
            }

            final List<RegionPos> positions = new ArrayList<>(this.cells.size());
            for (final long cellKey : this.cells) {
                positions.add(new RegionPos(cellKey));
            }
            positions.sort((first, second) -> Long.compare(first.longKey, second.longKey));
            cached = List.copyOf(positions);
            this.cellPositionsSnapshot = cached;
            return cached;
        }
    }

    public List<RegionPos> isolationCellPositionsSnapshot(final int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be >= 0");
        }
        if (radius == 0) {
            return this.cellPositionsSnapshot();
        }
        if (radius == 1) {
            final List<RegionPos> cached = this.isolationRadiusOneSnapshot;
            if (cached != null) {
                return cached;
            }
        }

        synchronized (this.cells) {
            if (radius == 1) {
                final List<RegionPos> cached = this.isolationRadiusOneSnapshot;
                if (cached != null) {
                    return cached;
                }
            }

            final LongOpenHashSet isolationCells = new LongOpenHashSet(this.cells.size() * ((radius * 2 + 1) * (radius * 2 + 1)));
            for (final long cellKey : this.cells) {
                final RegionPos cell = new RegionPos(cellKey);
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        isolationCells.add(RegionPos.asLong(cell.x + x, cell.z + z));
                    }
                }
            }

            final List<RegionPos> positions = new ArrayList<>(isolationCells.size());
            for (final long cellKey : isolationCells) {
                positions.add(new RegionPos(cellKey));
            }
            final List<RegionPos> immutablePositions = List.copyOf(positions);
            if (radius == 1) {
                this.isolationRadiusOneSnapshot = immutablePositions;
            }
            return immutablePositions;
        }
    }

    void absorbCellsFrom(final RegionOwner source) {
        if (this == source) {
            return;
        }

        final int order = compareCellLockOrder(this, source);
        if (order == 0) {
            synchronized (CELL_LOCK_TIE_BREAKER) {
                this.absorbCellsFromLocked(source, this, source);
            }
        } else if (order < 0) {
            this.absorbCellsFromLocked(source, this, source);
        } else {
            this.absorbCellsFromLocked(source, source, this);
        }
        this.lastMergeNanos = System.nanoTime();
    }

    void clearTransferredCells() {
        synchronized (this.cells) {
            this.cells.clear();
            this.invalidateSnapshots();
            this.bumpLayoutEpoch();
        }
    }

    private void invalidateSnapshots() {
        this.cellPositionsSnapshot = null;
        this.isolationRadiusOneSnapshot = null;
    }

    private void absorbCellsFromLocked(final RegionOwner source, final RegionOwner first, final RegionOwner second) {
        synchronized (first.cells) {
            synchronized (second.cells) {
                this.cells.addAll(source.cells);
                this.invalidateSnapshots();
                this.bumpLayoutEpoch();
            }
        }
    }

    private static int compareCellLockOrder(final RegionOwner first, final RegionOwner second) {
        final int idOrder = Long.compare(first.id, second.id);
        if (idOrder != 0) {
            return idOrder;
        }
        return Integer.compare(System.identityHashCode(first), System.identityHashCode(second));
    }

    private void bumpLayoutEpoch() {
        this.layoutEpoch++;
    }

    void removeCells(final LongOpenHashSet removedCells) {
        synchronized (this.cells) {
            this.cells.removeAll(removedCells);
            this.cells.add(this.primaryCell.longKey);
            this.invalidateSnapshots();
            this.bumpLayoutEpoch();
        }
    }

    boolean canSplit(final long nowNanos, final long cooldownNanos) {
        return nowNanos - this.lastMergeNanos >= cooldownNanos && nowNanos - this.lastSplitNanos >= cooldownNanos;
    }

    void recordSplit(final long nowNanos) {
        this.lastSplitNanos = nowNanos;
    }

    public boolean isSchedulerArmed() {
        return this.schedulerArmed;
    }

    public void armScheduler() {
        this.schedulerArmed = true;
    }

    @Override
    public String toString() {
        return "RegionOwner[id=" + this.id + ", primaryCell=" + this.primaryCell + ", cells=" + this.cellCount() + "]";
    }
}
