package org.bxteam.divinemc.util.map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public final class AttributeInstanceArrayMap implements Map<Holder<Attribute>, AttributeInstance>, Cloneable {

    private int size;
    private AttributeInstance[] values = new AttributeInstance[Math.max(BuiltInRegistries.ATTRIBUTE.size(), 1)];
    private transient Set<Holder<Attribute>> keySet;
    private transient Collection<AttributeInstance> valuesView;
    private transient Set<Entry<Holder<Attribute>, AttributeInstance>> entrySet;

    public AttributeInstanceArrayMap() {
    }

    public AttributeInstanceArrayMap(final @NotNull Map<Holder<Attribute>, AttributeInstance> map) {
        this();
        this.putAll(map);
    }

    private void ensureCapacity(final int uid) {
        if (uid < this.values.length) {
            return;
        }
        int newLength = this.values.length;
        while (uid >= newLength) {
            newLength <<= 1;
        }
        this.values = Arrays.copyOf(this.values, newLength);
    }

    private void setByIndex(final int uid, final @Nullable AttributeInstance instance) {
        this.ensureCapacity(uid);
        final boolean empty = this.values[uid] == null;
        if (instance == null) {
            if (!empty) {
                this.values[uid] = null;
                --this.size;
            }
        } else {
            if (empty) {
                ++this.size;
            }
            this.values[uid] = instance;
        }
    }

    public @Nullable AttributeInstance getInstance(final int uid) {
        return uid >= 0 && uid < this.values.length ? this.values[uid] : null;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public boolean isEmpty() {
        return this.size == 0;
    }

    @Override
    public boolean containsKey(final Object key) {
        return key instanceof Holder<?> holder
            && holder.value() instanceof Attribute attribute
            && this.getInstance(attribute.uid) != null;
    }

    @Override
    public boolean containsValue(final Object value) {
        return value instanceof AttributeInstance instance
            && Objects.equals(this.getInstance(instance.getAttribute().value().uid), instance);
    }

    @Override
    public AttributeInstance get(final Object key) {
        return key instanceof Holder<?> holder && holder.value() instanceof Attribute attribute
            ? this.getInstance(attribute.uid)
            : null;
    }

    @Override
    public AttributeInstance put(final @NotNull Holder<Attribute> key, final AttributeInstance value) {
        final int uid = key.value().uid;
        final AttributeInstance previous = this.getInstance(uid);
        this.setByIndex(uid, value);
        return previous;
    }

    @Override
    public AttributeInstance remove(final Object key) {
        if (!(key instanceof Holder<?> holder) || !(holder.value() instanceof Attribute attribute)) {
            return null;
        }
        final int uid = attribute.uid;
        final AttributeInstance previous = this.getInstance(uid);
        this.setByIndex(uid, null);
        return previous;
    }

    @Override
    public void putAll(final @NotNull Map<? extends Holder<Attribute>, ? extends AttributeInstance> map) {
        for (final AttributeInstance instance : map.values()) {
            if (instance != null) {
                this.setByIndex(instance.getAttribute().value().uid, instance);
            }
        }
    }

    @Override
    public void clear() {
        Arrays.fill(this.values, null);
        this.size = 0;
    }

    @Override
    public @NotNull Set<Holder<Attribute>> keySet() {
        if (this.keySet == null) {
            this.keySet = new KeySet();
        }
        return this.keySet;
    }

    @Override
    public @NotNull Collection<AttributeInstance> values() {
        if (this.valuesView == null) {
            this.valuesView = new Values();
        }
        return this.valuesView;
    }

    @Override
    public @NotNull Set<Entry<Holder<Attribute>, AttributeInstance>> entrySet() {
        if (this.entrySet == null) {
            this.entrySet = new EntrySet();
        }
        return this.entrySet;
    }

    @Override
    public boolean equals(final Object object) {
        if (object == this) {
            return true;
        }
        if (!(object instanceof Map<?, ?> map) || map.size() != this.size()) {
            return false;
        }
        for (final Entry<?, ?> entry : map.entrySet()) {
            if (!Objects.equals(this.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 0;
        for (final Entry<Holder<Attribute>, AttributeInstance> entry : this.entrySet()) {
            hash += entry.hashCode();
        }
        return hash;
    }

    @Override
    public AttributeInstanceArrayMap clone() {
        try {
            final AttributeInstanceArrayMap clone = (AttributeInstanceArrayMap) super.clone();
            clone.values = this.values.clone();
            clone.keySet = null;
            clone.valuesView = null;
            clone.entrySet = null;
            return clone;
        } catch (final CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }

    private int findNextOccupied(final int start) {
        for (int index = start; index < this.values.length; ++index) {
            if (this.values[index] != null) {
                return index;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Attribute> holderById(final int uid) {
        return (Holder<Attribute>) BuiltInRegistries.ATTRIBUTE.get(uid).orElseThrow();
    }

    private final class KeySet extends AbstractSet<Holder<Attribute>> {
        @Override
        public @NotNull Iterator<Holder<Attribute>> iterator() {
            return new KeyIterator();
        }

        @Override
        public int size() {
            return AttributeInstanceArrayMap.this.size;
        }

        @Override
        public boolean contains(final Object object) {
            return AttributeInstanceArrayMap.this.containsKey(object);
        }
    }

    private final class KeyIterator implements Iterator<Holder<Attribute>> {
        private int currentIndex = -1;
        private int nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return this.nextIndex != -1;
        }

        @Override
        public Holder<Attribute> next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            this.currentIndex = this.nextIndex;
            this.nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(this.nextIndex + 1);
            return AttributeInstanceArrayMap.holderById(this.currentIndex);
        }

        @Override
        public void remove() {
            if (this.currentIndex == -1) {
                throw new IllegalStateException();
            }
            AttributeInstanceArrayMap.this.setByIndex(this.currentIndex, null);
            this.currentIndex = -1;
        }
    }

    private final class Values extends AbstractCollection<AttributeInstance> {
        @Override
        public @NotNull Iterator<AttributeInstance> iterator() {
            return new ValueIterator();
        }

        @Override
        public int size() {
            return AttributeInstanceArrayMap.this.size;
        }

        @Override
        public boolean contains(final Object object) {
            return AttributeInstanceArrayMap.this.containsValue(object);
        }
    }

    private final class ValueIterator implements Iterator<AttributeInstance> {
        private int currentIndex = -1;
        private int nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return this.nextIndex != -1;
        }

        @Override
        public AttributeInstance next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            this.currentIndex = this.nextIndex;
            final AttributeInstance value = AttributeInstanceArrayMap.this.values[this.nextIndex];
            this.nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(this.nextIndex + 1);
            return value;
        }

        @Override
        public void remove() {
            if (this.currentIndex == -1) {
                throw new IllegalStateException();
            }
            AttributeInstanceArrayMap.this.setByIndex(this.currentIndex, null);
            this.currentIndex = -1;
        }
    }

    private final class EntrySet extends AbstractSet<Entry<Holder<Attribute>, AttributeInstance>> {
        @Override
        public @NotNull Iterator<Entry<Holder<Attribute>, AttributeInstance>> iterator() {
            return new EntryIterator();
        }

        @Override
        public int size() {
            return AttributeInstanceArrayMap.this.size;
        }

        @Override
        public boolean contains(final Object object) {
            return object instanceof Entry<?, ?> entry
                && Objects.equals(AttributeInstanceArrayMap.this.get(entry.getKey()), entry.getValue());
        }
    }

    private final class EntryIterator implements Iterator<Entry<Holder<Attribute>, AttributeInstance>> {
        private int currentIndex = -1;
        private int nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(0);

        @Override
        public boolean hasNext() {
            return this.nextIndex != -1;
        }

        @Override
        public Entry<Holder<Attribute>, AttributeInstance> next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }
            this.currentIndex = this.nextIndex;
            final Holder<Attribute> key = AttributeInstanceArrayMap.holderById(this.currentIndex);
            final AttributeInstance value = AttributeInstanceArrayMap.this.values[this.currentIndex];
            this.nextIndex = AttributeInstanceArrayMap.this.findNextOccupied(this.nextIndex + 1);
            return new AbstractMap.SimpleEntry<>(key, value) {
                @Override
                public AttributeInstance setValue(final AttributeInstance newValue) {
                    final AttributeInstance previous = AttributeInstanceArrayMap.this.put(key, newValue);
                    super.setValue(newValue);
                    return previous;
                }
            };
        }

        @Override
        public void remove() {
            if (this.currentIndex == -1) {
                throw new IllegalStateException();
            }
            AttributeInstanceArrayMap.this.setByIndex(this.currentIndex, null);
            this.currentIndex = -1;
        }
    }
}
