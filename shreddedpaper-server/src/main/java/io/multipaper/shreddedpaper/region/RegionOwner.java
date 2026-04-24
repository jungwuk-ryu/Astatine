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

    private final long id;
    private final RegionPos primaryCell;
    private final LongOpenHashSet cells = new LongOpenHashSet();
    private volatile List<RegionPos> cellPositionsSnapshot;
    private volatile List<RegionPos> isolationRadiusOneSnapshot;
    private volatile LevelChunkRegion region;

    private RegionOwner(final long id, final RegionPos primaryCell) {
        this.id = id;
        this.primaryCell = primaryCell;
        this.cells.add(primaryCell.longKey);
    }

    public static RegionOwner singleCell(final RegionPos cell) {
        return new RegionOwner(cell.longKey, cell);
    }

    void attachRegion(final LevelChunkRegion region) {
        this.region = region;
    }

    void detachRegion(final LevelChunkRegion region) {
        if (this.region == region) {
            this.region = null;
        }
    }

    public long id() {
        return this.id;
    }

    public RegionPos primaryCell() {
        return this.primaryCell;
    }

    public LevelChunkRegion region() {
        return this.region;
    }

    public boolean ownsCell(final RegionPos cell) {
        synchronized (this.cells) {
            return this.cells.contains(cell.longKey);
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
        synchronized (this.cells) {
            synchronized (source.cells) {
                this.cells.addAll(source.cells);
            }
            this.invalidateSnapshots();
        }
    }

    void clearTransferredCells() {
        synchronized (this.cells) {
            this.cells.clear();
            this.invalidateSnapshots();
        }
    }

    private void invalidateSnapshots() {
        this.cellPositionsSnapshot = null;
        this.isolationRadiusOneSnapshot = null;
    }

    @Override
    public String toString() {
        return "RegionOwner[id=" + this.id + ", primaryCell=" + this.primaryCell + ", cells=" + this.cellCount() + "]";
    }
}
