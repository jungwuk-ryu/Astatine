package io.multipaper.shreddedpaper.audit;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import io.multipaper.shreddedpaper.region.LevelChunkRegionMap;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.region.ShreddedPaperRegionSchedulerApiImpl;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.bxteam.divinemc.config.DivineConfig;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.plugin.Plugin;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Normal
class RegionSchedulerApiRegressionTest {
    @BeforeAll
    static void configure() throws IOException {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("astatine-bug-audit", ".yml").toFile());
    }

    @AfterEach
    void restoreConfig() {
        DivineConfig.syncFromShreddedPaper(new ShreddedPaperConfiguration());
    }

    @Test
    void worldCloseWaitsForAnInFlightTaskRegistration() throws Exception {
        SchedulerFixture fixture = schedulerFixture();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var lifecycle = fixture.world.getHandle().chunkScheduler.tickLifecycleLock();
        var regions = fixture.world.getHandle().getChunkSource().tickingRegions;
        when(regions.scheduleTask(any(RegionPos.class), any(Runnable.class), anyLong(), eq(RegionTaskClass.PLUGIN)))
                .thenAnswer(call -> {
                    entered.countDown();
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    fixture.queued.set(call.getArgument(1));
                    return true;
                });
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var admitted = executor.submit(() -> fixture.api.run(fixture.plugin, fixture.world, 0, 0, ignored -> {}));
            try {
                assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
                var close = executor.submit(() -> {
                    lifecycle.writeLock().lock();
                    try {
                        fixture.world.getHandle().chunkScheduler.closeIndependentTicking();
                    } finally {
                        lifecycle.writeLock().unlock();
                    }
                });
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                while (!lifecycle.hasQueuedThreads() && System.nanoTime() < deadline) Thread.onSpinWait();
                assertTrue(lifecycle.hasQueuedThreads(), "world close must wait for admitted work");
                assertFalse(close.isDone());
                release.countDown();
                assertNotNull(admitted.get(5, java.util.concurrent.TimeUnit.SECONDS));
                close.get(5, java.util.concurrent.TimeUnit.SECONDS);
                assertThrows(java.util.concurrent.RejectedExecutionException.class,
                        () -> fixture.api.run(fixture.plugin, fixture.world, 0, 0, ignored -> {}));
                verify(regions, times(1)).scheduleTask(any(RegionPos.class), any(Runnable.class), anyLong(), eq(RegionTaskClass.PLUGIN));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void unloadedWorldRejectsNewTasksWithoutEnqueuing() throws Exception {
        SchedulerFixture fixture = schedulerFixture();
        var scheduler = fixture.world.getHandle().chunkScheduler;
        var lock = scheduler.tickLifecycleLock().writeLock();
        lock.lock();
        try {
            scheduler.closeIndependentTicking();
        } finally {
            lock.unlock();
        }
        assertThrows(java.util.concurrent.RejectedExecutionException.class,
                () -> fixture.api.run(fixture.plugin, fixture.world, 0, 0, ignored -> {}));
        assertNull(fixture.queued.get());
    }

    @Test
    void runningOneShotCannotBeCancelledAndFinishesNormally() throws Exception {
        SchedulerFixture fixture = schedulerFixture();
        AtomicReference<ScheduledTask.CancelledState> first = new AtomicReference<>();
        AtomicReference<ScheduledTask.CancelledState> second = new AtomicReference<>();
        ScheduledTask task = fixture.api.run(fixture.plugin, fixture.world, 0, 0, running -> {
            first.set(running.cancel());
            second.set(running.cancel());
        });
        fixture.queued.get().run();
        assertEquals(ScheduledTask.CancelledState.RUNNING, first.get());
        assertEquals(ScheduledTask.CancelledState.RUNNING, second.get());
        assertEquals(ScheduledTask.ExecutionState.FINISHED, task.getExecutionState());
        assertFalse(task.isCancelled());
        assertFalse(task.isRepeatingTask());
    }

    @Test
    void fixedRateRejectsNonPositivePeriodsWithoutEnqueuing() throws Exception {
        SchedulerFixture fixture = schedulerFixture();
        for (long period : new long[] {Long.MIN_VALUE, -2, -1, 0}) {
            assertThrows(IllegalArgumentException.class, () -> fixture.api.runAtFixedRate(
                    fixture.plugin, fixture.world, 0, 0, ignored -> {}, 1, period));
        }
        assertNull(fixture.queued.get());
    }

    @Test
    void cancellingARepeatingCallbackPreventsItsNextRun() throws Exception {
        SchedulerFixture fixture = schedulerFixture();
        ScheduledTask task = fixture.api.runAtFixedRate(fixture.plugin, fixture.world, 0, 0, running -> {
            assertEquals(ScheduledTask.CancelledState.NEXT_RUNS_CANCELLED, running.cancel());
            assertEquals(ScheduledTask.CancelledState.NEXT_RUNS_CANCELLED_ALREADY, running.cancel());
        }, 1, 2);
        fixture.queued.getAndSet(null).run();
        assertEquals(ScheduledTask.ExecutionState.CANCELLED, task.getExecutionState());
        assertNull(fixture.queued.get());
    }

    static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static SchedulerFixture schedulerFixture() throws Exception {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        CraftWorld world = mock(CraftWorld.class);
        ServerLevel level = mock(ServerLevel.class);
        setField(level, "chunkScheduler", new io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler());
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        LevelChunkRegionMap regions = mock(LevelChunkRegionMap.class);
        when(world.getHandle()).thenReturn(level);
        when(level.getChunkSource()).thenReturn(chunks);
        setField(chunks, "tickingRegions", regions);
        AtomicReference<Runnable> queued = new AtomicReference<>();
        when(regions.scheduleTask(any(RegionPos.class), any(Runnable.class), anyLong(), eq(RegionTaskClass.PLUGIN)))
                .thenAnswer(call -> { queued.set(call.getArgument(1)); return true; });
        return new SchedulerFixture(new ShreddedPaperRegionSchedulerApiImpl(), plugin, world, queued);
    }

    private record SchedulerFixture(ShreddedPaperRegionSchedulerApiImpl api, Plugin plugin, CraftWorld world,
                                    AtomicReference<Runnable> queued) {
    }
}
