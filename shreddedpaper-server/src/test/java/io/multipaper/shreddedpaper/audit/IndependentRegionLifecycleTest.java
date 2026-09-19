package io.multipaper.shreddedpaper.audit;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.ChunkTaskScheduler;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionOwner;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler;
import io.multipaper.shreddedpaper.threading.region.RegionRuntimeState;
import io.multipaper.shreddedpaper.threading.region.RegionTickScheduler;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.TickRateManager;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Normal
@Timeout(15)
class IndependentRegionLifecycleTest {
    @BeforeAll
    static void configure() throws Exception {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("region-lifecycle-test", ".yml").toFile());
    }

    @Test
    void regionCadenceFollowsTheConfiguredTickRate() throws Exception {
        try (Fixture fixture = new Fixture()) {
            for (float rate : new float[] {5.0F, 40.0F, 20.0F}) {
                fixture.clock.setTickRate(rate);
                long initial = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                fixture.schedule(initial);
                Object handle = fixture.take();
                field(handle, "idealStartNanos").setLong(handle, initial);
                fixture.run(handle);
                assertEquals(fixture.clock.nanosecondsPerTick(), field(handle, "idealStartNanos").getLong(handle) - initial);
            }
            verify(fixture.ticker, times(3)).tickRegionFromIndependentScheduler(eq(fixture.level), eq(fixture.region), any(), any(), anyLong());
        }
    }

    @Test
    void worldStopWaitsForAnActiveTickAndRejectsLaterRegistration() throws Exception {
        try (Fixture fixture = new Fixture(); var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            doAnswer(invocation -> {
                entered.countDown();
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return null;
            }).when(fixture.ticker).tickRegionFromIndependentScheduler(any(), any(), any(), any(), anyLong());
            fixture.schedule(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            Object handle = fixture.take();
            var tick = executor.submit(() -> { fixture.run(handle); return null; });
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                var stopped = executor.submit(() -> RegionTickScheduler.stopWorld(fixture.level));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!fixture.level.chunkScheduler.tickLifecycleLock().hasQueuedThreads() && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }
                assertTrue(fixture.level.chunkScheduler.tickLifecycleLock().hasQueuedThreads());
                assertFalse(stopped.isDone());
                release.countDown();
                tick.get(5, TimeUnit.SECONDS);
                stopped.get(5, TimeUnit.SECONDS);
                assertTrue(fixture.level.chunkScheduler.isIndependentTickingClosed());
                assertTrue(fixture.queued.isEmpty());
                fixture.schedule(System.nanoTime());
                assertTrue(fixture.queued.isEmpty());
                assertTrue(fixture.scheduler.snapshots().isEmpty());
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void aClosedWorldDoesNotRunOrRequeueItsRegion() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.schedule(System.nanoTime() + TimeUnit.SECONDS.toNanos(1));
            when(fixture.chunkTasks.hasShutdown()).thenReturn(true);
            fixture.run(fixture.take());
            verifyNoInteractions(fixture.ticker);
            assertTrue(fixture.queued.isEmpty());
            assertTrue(fixture.scheduler.snapshots().isEmpty());
        }
    }

    private static Field field(Object value, String name) throws Exception {
        Class<?> type = value.getClass();
        for (;;) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                type = type.getSuperclass();
                if (type == null) throw missing;
            }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final ServerLevel level = mock(ServerLevel.class);
        final ChunkTaskScheduler chunkTasks = mock(ChunkTaskScheduler.class);
        final TickRateManager clock = new TickRateManager();
        final LevelChunkRegion region = mock(LevelChunkRegion.class);
        final ShreddedPaperChunkTicker ticker = mock(ShreddedPaperChunkTicker.class);
        final RegionTickScheduler scheduler = mock(RegionTickScheduler.class, CALLS_REAL_METHODS);
        final DelayQueue<Delayed> queued = new DelayQueue<>();
        final RegionRuntimeState state;
        final RegionTickScheduler previousGlobal;

        Fixture() throws Exception {
            field(level, "uuid").set(level, UUID.randomUUID());
            field(level, "chunkScheduler").set(level, new ShreddedPaperRegionScheduler());
            when(level.moonrise$getChunkTaskScheduler()).thenReturn(chunkTasks);
            when(level.tickRateManager()).thenReturn(clock);
            CraftWorld world = mock(CraftWorld.class);
            when(world.getName()).thenReturn("audit");
            when(level.getWorld()).thenReturn(world);
            RegionOwner owner = RegionOwner.singleCell(new RegionPos(0, 0));
            Method attach = RegionOwner.class.getDeclaredMethod("attachRegion", LevelChunkRegion.class);
            attach.setAccessible(true);
            attach.invoke(owner, region);
            state = RegionRuntimeState.getOrCreate(level, owner);
            when(region.getOwner()).thenReturn(owner);
            when(region.getRuntimeState()).thenReturn(state);
            when(region.getOverloadController()).thenReturn(state.overloadController());
            field(scheduler, "regions").set(scheduler, new ConcurrentHashMap<>());
            field(scheduler, "normalQueue").set(scheduler, queued);
            field(scheduler, "degradedQueue").set(scheduler, new DelayQueue<>());
            field(scheduler, "running").set(scheduler, new AtomicBoolean(true));
            Field global = field(scheduler, "global");
            previousGlobal = (RegionTickScheduler) global.get(null);
            global.set(null, scheduler);
        }

        void schedule(long start) {
            scheduler.registerRegionForCurrentTick(level, region, ticker,
                    new ShreddedPaperChunkTicker.ScheduledTickContext(1, List.of(), null), start);
        }

        void run(Object handle) throws Exception {
            Method run = handle.getClass().getDeclaredMethod("runOneTick");
            run.setAccessible(true);
            run.invoke(handle);
        }

        Object take() {
            Delayed handle = queued.iterator().next();
            queued.remove(handle);
            return handle;
        }

        @Override
        public void close() throws Exception {
            field(scheduler, "global").set(null, previousGlobal);
            state.detach(region);
        }
    }
}
