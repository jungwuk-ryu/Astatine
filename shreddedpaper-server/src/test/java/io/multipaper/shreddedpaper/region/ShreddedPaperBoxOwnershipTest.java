package io.multipaper.shreddedpaper.region;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.ShreddedPaper;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@AllFeatures
class ShreddedPaperBoxOwnershipTest {
    private static final int BLOCKS_PER_REGION = RegionPos.REGION_SIZE * 16;
    private final ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();
    private final ShreddedPaperRegionScheduler scheduler = mock(ShreddedPaperRegionScheduler.class);
    private final ServerLevel level = mock(ServerLevel.class);
    private ThreadLocal<LevelChunkRegion> ticking;
    private LevelChunkRegion previous;
    private MockedStatic<TickThread> tickThread;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws ReflectiveOperationException {
        // Unit tests have no live MinecraftServer for the shutdown-thread lookup.
        this.tickThread = mockStatic(TickThread.class);
        final Field field = ShreddedPaperChunkTicker.class.getDeclaredField("currentlyTickingRegion");
        field.setAccessible(true);
        this.ticking = (ThreadLocal<LevelChunkRegion>) field.get(null);
        this.previous = this.ticking.get();
        this.ticking.remove();
        when(this.scheduler.getRegionLocker()).thenReturn(this.locker);
        final Field schedulerField = ServerLevel.class.getDeclaredField("chunkScheduler");
        schedulerField.setAccessible(true);
        schedulerField.set(this.level, this.scheduler);
    }

    @AfterEach
    void restore() {
        this.locker.releaseCurrentThreadLocks();
        if (this.previous == null) this.ticking.remove();
        else this.ticking.set(this.previous);
        this.tickThread.close();
    }

    @Test
    void currentOwnerAllowsInteriorAndExactInclusiveBoundary() {
        own(this.level, new RegionPos(0, 0));
        assertTrue(ShreddedPaper.isSync(this.level, new BoundingBox(0, -200, 0, BLOCKS_PER_REGION - 1, 400, BLOCKS_PER_REGION - 1)));
        assertFalse(ShreddedPaper.isSync(this.level, new BoundingBox(0, 0, 0, BLOCKS_PER_REGION, 0, 0)));
        assertFalse(ShreddedPaper.isSync(this.level, new BoundingBox(-1, 0, 0, 0, 0, 0)));
    }

    @Test
    void negativeCoordinatesUseFloorDivisionOnBothAxes() {
        own(this.level, new RegionPos(-1, -1));
        assertTrue(ShreddedPaper.isSync(this.level, new BoundingBox(-BLOCKS_PER_REGION, 0, -BLOCKS_PER_REGION, -1, 0, -1)));
        assertFalse(ShreddedPaper.isSync(this.level, new BoundingBox(-BLOCKS_PER_REGION - 1, 0, -1, -1, 0, -1)));
        assertFalse(ShreddedPaper.isSync(this.level, new BoundingBox(-1, 0, -1, -1, 0, 0)));
    }

    @Test
    void everyCellIsCheckedIncludingHolesInMergedOwners() {
        final RegionOwner owner = own(this.level, new RegionPos(-1, -1));
        for (int x = -1; x <= 1; ++x) for (int z = -1; z <= 1; ++z) {
            if ((x != -1 || z != -1) && (x != 0 || z != 0)) owner.absorbCellsFrom(RegionOwner.singleCell(new RegionPos(x, z)));
        }
        final BoundingBox box = new BoundingBox(-1, 0, -1, BLOCKS_PER_REGION, 0, BLOCKS_PER_REGION);
        assertFalse(ShreddedPaper.isSync(this.level, box));
        owner.absorbCellsFrom(RegionOwner.singleCell(new RegionPos(0, 0)));
        assertTrue(ShreddedPaper.isSync(this.level, box));
    }

    @Test
    void regionOwnershipFromAnotherWorldDoesNotAuthorizeAccess() {
        own(mock(ServerLevel.class), new RegionPos(0, 0));
        assertFalse(ShreddedPaper.isSync(this.level, boxAt(0, 0)));
        final var lock = this.locker.internalTryTakeExactLockNow(List.of(new RegionPos(0, 0)));
        assertNotNull(lock);
        try { assertTrue(ShreddedPaper.isSync(this.level, boxAt(0, 0))); }
        finally { lock.unlock(); }
        assertFalse(ShreddedPaper.isSync(this.level, boxAt(0, 0)));
    }

    @Test
    void readOnlyLocksRequirePromotionAndLoseWriteAccessAfterScope() {
        final var lock = this.locker.internalTryTakeExactReadOnlyLockNow(List.of(new RegionPos(0, 0)));
        assertNotNull(lock);
        try {
            assertFalse(ShreddedPaper.isSync(this.level, boxAt(0, 0)));
            try (var ignored = this.locker.promoteCurrentThreadLocksToWrite()) {
                assertTrue(ShreddedPaper.isSync(this.level, boxAt(0, 0)));
            }
            assertFalse(ShreddedPaper.isSync(this.level, boxAt(0, 0)));
        } finally { lock.unlock(); }
    }

    @Test
    void ownerAndExplicitNeighborWriteLocksCanCoverOneBoxTogether() {
        own(this.level, new RegionPos(0, 0));
        final BoundingBox box = new BoundingBox(0, 0, 0, BLOCKS_PER_REGION, 0, 0);
        final var lock = this.locker.internalTryTakeExactLockNow(List.of(new RegionPos(1, 0)));
        assertNotNull(lock);
        try { assertTrue(ShreddedPaper.isSync(this.level, box)); }
        finally { lock.unlock(); }
        assertFalse(ShreddedPaper.isSync(this.level, box));
    }

    @Test
    void hugeUnownedQueryStopsAtFirstCellWithoutMaterializingWorldSizedArray() {
        assertFalse(ShreddedPaper.isSync(this.level, new BoundingBox(Integer.MIN_VALUE, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, Integer.MAX_VALUE)));
    }

    @Test
    void shutdownThreadRetainsAccessWithoutOwnerOrLocks() {
        this.tickThread.when(TickThread::isShutdownThread).thenReturn(true);
        assertTrue(ShreddedPaper.isSync(this.level, new BoundingBox(-BLOCKS_PER_REGION, 0, -BLOCKS_PER_REGION, BLOCKS_PER_REGION, 0, BLOCKS_PER_REGION)));
    }

    @Test
    void ensureSyncRunsInlineOrSchedulesEveryCellInOriginalOrder() {
        own(this.level, new RegionPos(0, 0));
        final AtomicInteger calls = new AtomicInteger();
        final Runnable task = calls::incrementAndGet;
        ShreddedPaper.ensureSync(this.level, boxAt(0, 0), task);
        assertEquals(1, calls.get());
        verify(this.scheduler, never()).scheduleOnMany(any(), any(RegionPos[].class));
        ShreddedPaper.ensureSync(this.level, new BoundingBox(-1, 0, -1, 0, 0, 0), task);
        assertEquals(1, calls.get());
        final ArgumentCaptor<RegionPos[]> positions = ArgumentCaptor.forClass(RegionPos[].class);
        verify(this.scheduler).scheduleOnMany(same(task), positions.capture());
        assertArrayEquals(new RegionPos[]{new RegionPos(-1, -1), new RegionPos(-1, 0), new RegionPos(0, -1), new RegionPos(0, 0)}, positions.getValue());
    }

    @Test
    void entityOutsideAnOwnedBoxIsStillIncludedWhenScheduling() {
        own(this.level, new RegionPos(0, 0));
        final Entity entity = mock(Entity.class);
        when(entity.level()).thenReturn(this.level);
        when(entity.chunkPosition()).thenReturn(new ChunkPos(RegionPos.REGION_SIZE, 0));
        final AtomicInteger calls = new AtomicInteger();
        ShreddedPaper.ensureSync(entity, this.level, boxAt(0, 0), calls::incrementAndGet);
        assertEquals(0, calls.get());
        final ArgumentCaptor<RegionPos[]> positions = ArgumentCaptor.forClass(RegionPos[].class);
        verify(this.scheduler).scheduleOnMany(any(), positions.capture());
        assertEquals(Set.of(new RegionPos(0, 0), new RegionPos(1, 0)), Arrays.stream(positions.getValue()).collect(Collectors.toSet()));
    }

    private RegionOwner own(ServerLevel world, RegionPos position) {
        final LevelChunkRegion region = mock(LevelChunkRegion.class);
        final RegionOwner owner = RegionOwner.singleCell(position);
        when(region.getLevel()).thenReturn(world);
        when(region.getOwner()).thenReturn(owner);
        this.ticking.set(region);
        return owner;
    }

    private static BoundingBox boxAt(int regionX, int regionZ) {
        return new BoundingBox(regionX * BLOCKS_PER_REGION, 0, regionZ * BLOCKS_PER_REGION, regionX * BLOCKS_PER_REGION, 0, regionZ * BLOCKS_PER_REGION);
    }
}
