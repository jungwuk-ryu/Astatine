package org.bxteam.divinemc.util.map;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class AttributeInstanceSet extends AbstractSet<AttributeInstance> {

    public final IntSet inner = new IntArraySet();
    public final AttributeInstanceArrayMap map;

    public AttributeInstanceSet(final AttributeInstanceArrayMap map) {
        this.map = map;
    }

    @Override
    public boolean add(final AttributeInstance instance) {
        return this.inner.add(instance.getAttribute().value().uid);
    }

    @Override
    public boolean remove(final Object object) {
        return object instanceof AttributeInstance instance && this.inner.remove(instance.getAttribute().value().uid);
    }

    @Override
    public boolean contains(final Object object) {
        return object instanceof AttributeInstance instance && this.inner.contains(instance.getAttribute().value().uid);
    }

    @Override
    public @NotNull Iterator<AttributeInstance> iterator() {
        return new SnapshotIterator(this.inner.toIntArray(), this.map);
    }

    @Override
    public int size() {
        return this.inner.size();
    }

    @Override
    public boolean isEmpty() {
        return this.inner.isEmpty();
    }

    @Override
    public void clear() {
        this.inner.clear();
    }

    @Override
    public AttributeInstance @NotNull [] toArray() {
        final int[] snapshot = this.inner.toIntArray();
        final AttributeInstance[] array = new AttributeInstance[snapshot.length];
        for (int i = 0; i < snapshot.length; ++i) {
            array[i] = this.map.getInstance(snapshot[i]);
        }
        return array;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T @NotNull [] toArray(final T @NotNull [] input) {
        final AttributeInstance[] snapshot = this.toArray();
        final T[] result = input.length < snapshot.length
            ? (T[]) Array.newInstance(input.getClass().getComponentType(), snapshot.length)
            : input;
        System.arraycopy(snapshot, 0, result, 0, snapshot.length);
        if (result.length > snapshot.length) {
            result[snapshot.length] = null;
        }
        return result;
    }

    private static final class SnapshotIterator implements Iterator<AttributeInstance> {
        private final int[] snapshot;
        private final AttributeInstanceArrayMap map;
        private int index;

        private SnapshotIterator(final int[] snapshot, final AttributeInstanceArrayMap map) {
            this.snapshot = snapshot;
            this.map = map;
        }

        @Override
        public boolean hasNext() {
            return this.index < this.snapshot.length;
        }

        @Override
        public AttributeInstance next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            return this.map.getInstance(this.snapshot[this.index++]);
        }
    }
}
