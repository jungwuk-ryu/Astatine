package io.multipaper.shreddedpaper.audit;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.LevelTicksRegionProxy;
import io.multipaper.shreddedpaper.region.RegionOwner;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler;
import io.papermc.paper.configuration.WorldConfiguration;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.bxteam.divinemc.config.DivineConfig;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Normal
class FluidTickLimitRegressionTest {
    @BeforeAll
    static void configure() throws IOException {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("astatine-bug-audit", ".yml").toFile());
    }

    @AfterEach
    void restoreConfig() {
        DivineConfig.syncFromShreddedPaper(new ShreddedPaperConfiguration());
    }

    @Test
    void fluidTickDispatchUsesTheConfiguredFluidLimit() throws Exception {
        ServerLevel level = mock(ServerLevel.class);
        ServerChunkCache chunks = mock(ServerChunkCache.class);
        when(level.getChunkSource()).thenReturn(chunks);
        when(chunks.getChunkAtIfLoadedImmediately(anyInt(), anyInt())).thenReturn(mock(LevelChunk.class));
        ShreddedPaperRegionScheduler scheduler = new ShreddedPaperRegionScheduler();
        setField(level, "chunkScheduler", scheduler);
        RegionPos pos = new RegionPos(0, 0);
        LevelChunkRegion region = mock(LevelChunkRegion.class);
        when(region.getOwner()).thenReturn(RegionOwner.singleCell(pos));
        when(region.getScheduledTickCellCursor()).thenReturn(LevelChunkRegion.NO_CONTINUATION_CURSOR);
        WorldConfiguration config = mock(WorldConfiguration.class);
        config.environment = mock(WorldConfiguration.Environment.class);
        config.environment.maxBlockTicks = 17;
        config.environment.maxFluidTicks = 3;
        when(level.paperConfig()).thenReturn(config);
        LevelTicksRegionProxy blocks = mock(LevelTicksRegionProxy.class);
        LevelTicksRegionProxy fluids = mock(LevelTicksRegionProxy.class);
        when(blocks.hasRegionData(pos)).thenReturn(true);
        setField(level, "blockTicks", blocks);
        setField(level, "fluidTicks", fluids);
        Method dispatch = ShreddedPaperChunkTicker.class.getDeclaredMethod("processScheduledTicks", ServerLevel.class, LevelChunkRegion.class);
        dispatch.setAccessible(true);
        var lock = scheduler.getRegionLocker().tryTakeLockNow(pos);
        assertNotNull(lock);
        try {
            dispatch.invoke(null, level, region);
        } finally {
            lock.unlock();
        }
        verify(fluids).tick(eq(pos), anyLong(), eq(3), any());
        verify(fluids, never()).tick(eq(pos), anyLong(), eq(17), any());
    }

    static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
