package io.multipaper.shreddedpaper.entity;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;

/** Reentrant scratch sets for block sweeps; never shared between active visitors. */
public final class BlockIntersectionScratch {
    private static final int MAX_RETAINED_ENTRIES = 4096;
    private static final int MAX_RETAINED_SETS = 4;
    private static final ThreadLocal<ArrayDeque<LongOpenHashSet>> FREE = ThreadLocal.withInitial(ArrayDeque::new);

    private BlockIntersectionScratch() {
    }

    public static LongOpenHashSet acquire() {
        final LongOpenHashSet set = FREE.get().pollFirst();
        return set == null ? new LongOpenHashSet() : set;
    }

    public static void release(final LongOpenHashSet set) {
        // Large teleports must not permanently enlarge every worker's scratch storage.
        if (set.size() > MAX_RETAINED_ENTRIES) {
            return;
        }
        set.clear();
        final ArrayDeque<LongOpenHashSet> free = FREE.get();
        if (free.size() < MAX_RETAINED_SETS) {
            free.addFirst(set);
        }
    }
}
