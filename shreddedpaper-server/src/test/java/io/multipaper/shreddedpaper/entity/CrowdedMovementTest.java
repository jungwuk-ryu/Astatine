package io.multipaper.shreddedpaper.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.InsideBlockEffectType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@AllFeatures
class CrowdedMovementTest {
    private static final Vec3 FROM = new Vec3(-2.5, 63, -1.5);
    private static final Vec3 TO = new Vec3(2.5, 64, 1.5);
    private static final AABB BOX = new AABB(2.2, 64, 1.2, 2.8, 65.8, 1.8);

    private static List<String> trace() {
        final List<String> result = new ArrayList<>();
        assertTrue(BlockGetter.forEachBlockIntersectedBetween(FROM, TO, BOX, (pos, step) -> {
            result.add(pos.asLong() + ":" + step);
            return true;
        }));
        return result;
    }

    @Test
    void nestedSweepCannotClearOrContaminateOuterVisitedBlocks() {
        final List<String> expected = trace();
        final List<String> actual = new ArrayList<>();
        final AtomicBoolean nested = new AtomicBoolean();
        assertTrue(BlockGetter.forEachBlockIntersectedBetween(FROM, TO, BOX, (pos, step) -> {
            actual.add(pos.asLong() + ":" + step);
            if (nested.compareAndSet(false, true)) {
                assertEquals(expected, trace());
                assertTrue(BlockGetter.forEachBlockIntersectedBetween(TO, FROM, BOX.move(-5, -1, -3), (p, s) -> true));
            }
            return true;
        }));
        assertEquals(expected, actual);
        assertEquals(actual.size(), new HashSet<>(actual.stream().map(s -> s.split(":")[0]).toList()).size());
    }

    @Test
    void abortedAndThrowingVisitorsDoNotPoisonLaterSweeps() {
        final List<String> expected = trace();
        assertFalse(BlockGetter.forEachBlockIntersectedBetween(FROM, TO, BOX, (pos, step) -> false));
        assertEquals(expected, trace());
        assertThrows(IllegalStateException.class, () -> BlockGetter.forEachBlockIntersectedBetween(FROM, TO, BOX,
            (pos, step) -> { throw new IllegalStateException("visitor failure"); }));
        assertEquals(expected, trace());
    }

    @Test
    void effectOrderAndStepBoundariesRemainIntact() {
        final var collector = new InsideBlockEffectApplier.StepBasedCollector();
        final Entity entity = mock(Entity.class);
        when(entity.isAlive()).thenReturn(true);
        final List<String> effects = new ArrayList<>();
        doAnswer(call -> { effects.add("freeze"); return null; }).when(entity).setIsInPowderSnow(true);
        collector.advanceStep(0, BlockPos.ZERO);
        collector.runBefore(InsideBlockEffectType.FREEZE, ignored -> effects.add("before0"));
        collector.apply(InsideBlockEffectType.FREEZE);
        collector.apply(InsideBlockEffectType.FREEZE); // Each effect type runs at most once per step.
        collector.runAfter(InsideBlockEffectType.FREEZE, ignored -> effects.add("after0"));
        collector.advanceStep(1, new BlockPos(1, 0, 0));
        collector.runBefore(InsideBlockEffectType.FREEZE, ignored -> effects.add("before1"));
        collector.apply(InsideBlockEffectType.FREEZE);
        collector.runAfter(InsideBlockEffectType.FREEZE, ignored -> effects.add("after1"));
        collector.applyAndClear(entity);
        assertEquals(List.of("before0", "freeze", "after0", "before1", "freeze", "after1"), effects);
        collector.applyAndClear(null); // An empty collector must not dereference the entity.
        assertEquals(6, effects.size());
    }

    @Test
    void effectProcessingStopsWhenAnEarlierEffectKillsTheEntity() {
        final var collector = new InsideBlockEffectApplier.StepBasedCollector();
        final Entity entity = mock(Entity.class);
        when(entity.isAlive()).thenReturn(true, false);
        final List<String> effects = new ArrayList<>();
        collector.runBefore(InsideBlockEffectType.FREEZE, ignored -> effects.add("first"));
        collector.runAfter(InsideBlockEffectType.FREEZE, ignored -> effects.add("later"));
        collector.applyAndClear(entity);
        assertEquals(List.of("first"), effects);
        collector.applyAndClear(null);
    }
}
