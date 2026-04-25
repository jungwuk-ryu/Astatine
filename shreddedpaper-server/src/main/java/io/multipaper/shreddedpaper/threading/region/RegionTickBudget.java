package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.events.RegionOverBudgetEvent;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class RegionTickBudget {

    private static final ThreadLocal<RegionTickBudget> CURRENT = new ThreadLocal<>();

    private final ServerLevel level;
    private final RegionPos regionPos;
    private final long startNanos;
    private final long deadlineNanos;
    private final Map<RegionWorkType, Long> deferred = new EnumMap<>(RegionWorkType.class);
    private final Set<RegionWorkType> deferredDrainUsed = EnumSet.noneOf(RegionWorkType.class);
    private boolean overBudgetEventCommitted;

    RegionTickBudget(final ServerLevel level, final RegionPos regionPos, final long startNanos, final long budgetMillis) {
        this.level = level;
        this.regionPos = regionPos;
        this.startNanos = startNanos;
        this.deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, budgetMillis));
    }

    public static RegionTickBudget current() {
        return CURRENT.get();
    }

    static void setCurrent(final RegionTickBudget budget) {
        CURRENT.set(budget);
    }

    static void clearCurrent() {
        CURRENT.remove();
    }

    public boolean tryConsume(final RegionWorkType type, final int cost) {
        if (System.nanoTime() <= this.deadlineNanos) {
            return true;
        }

        this.markDeferred(type, cost);
        return false;
    }

    public boolean canContinue(final RegionWorkType type) {
        return this.tryConsume(type, 1);
    }

    public boolean canDrainDeferred(final RegionWorkType type) {
        if (System.nanoTime() <= this.deadlineNanos) {
            return true;
        }

        if (this.deferredDrainUsed.add(type)) {
            this.commitOverBudgetEvent(type, 0L);
            return true;
        }

        this.markDeferred(type, 1L);
        return false;
    }

    public void markDeferred(final RegionWorkType type, final long count) {
        this.deferred.merge(type, count, Long::sum);
        if (!this.overBudgetEventCommitted) {
            this.commitOverBudgetEvent(type, count);
        }
    }

    private void commitOverBudgetEvent(final RegionWorkType type, final long deferredCount) {
        if (this.overBudgetEventCommitted) {
            return;
        }
        this.overBudgetEventCommitted = true;
        final RegionOverBudgetEvent event = new RegionOverBudgetEvent();
        event.world = this.level.getWorld().getName();
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.workType = type.name();
        event.deferred = deferredCount;
        event.elapsedNanos = System.nanoTime() - this.startNanos;
        event.commit();
    }

    public long deferred(final RegionWorkType type) {
        return this.deferred.getOrDefault(type, 0L);
    }

    public long totalDeferred() {
        long total = 0L;
        for (final long value : this.deferred.values()) {
            total += value;
        }
        return total;
    }
}
