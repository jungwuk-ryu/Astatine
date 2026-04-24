package org.bxteam.divinemc.util.map;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.jetbrains.annotations.NotNull;

public final class ConcurrentLongHashSet extends LongOpenHashSet {

    public ConcurrentLongHashSet() {
        super();
    }

    public ConcurrentLongHashSet(final int expected) {
        super(expected);
    }

    @Override
    public synchronized boolean add(final long key) {
        return super.add(key);
    }

    @Override
    public synchronized boolean remove(final long key) {
        return super.remove(key);
    }

    @Override
    public synchronized boolean contains(final long key) {
        return super.contains(key);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }

    @Override
    public synchronized boolean isEmpty() {
        return super.isEmpty();
    }

    @Override
    public synchronized void clear() {
        super.clear();
    }

    @Override
    public synchronized long @NotNull [] toLongArray() {
        return super.toLongArray();
    }

    @Override
    public synchronized @NotNull LongIterator iterator() {
        return LongIterators.wrap(super.toLongArray());
    }
}
