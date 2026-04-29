package io.multipaper.shreddedpaper.region;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.threading.ShreddedPaperTickThread;
import io.multipaper.shreddedpaper.util.SimpleStampedLock;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;

import java.util.ArrayList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongPredicate;

public class LevelTicksRegionProxy<T> extends LevelTicks<T> {

    private final LongPredicate tickingFutureReadyPredicate;
    private final Long2ObjectMap<LevelTicks<T>> regions = new Long2ObjectOpenHashMap<>();
    private final SimpleStampedLock regionsLock = new SimpleStampedLock();

    public LevelTicksRegionProxy(LongPredicate tickingFutureReadyPredicate) {
        super(tickingFutureReadyPredicate);
        this.tickingFutureReadyPredicate = tickingFutureReadyPredicate;
    }

    private LevelTicks<T> createRegionLevelTicks() {
        return new LevelTicks<>(tickingFutureReadyPredicate);
    }

    public Optional<LevelTicks<T>> get(BlockPos pos) {
        return Optional.ofNullable(getNullable(pos));
    }

    public Optional<LevelTicks<T>> get(ChunkPos pos) {
        return Optional.ofNullable(getNullable(pos));
    }

    public Optional<LevelTicks<T>> get(RegionPos pos) {
        return Optional.ofNullable(getNullable(pos));
    }

    private LevelTicks<T> getNullable(BlockPos pos) {
        return getNullable(RegionPos.forBlockPos(pos));
    }

    private LevelTicks<T> getNullable(ChunkPos pos) {
        return getNullable(RegionPos.forChunk(pos));
    }

    private LevelTicks<T> getNullable(RegionPos pos) {
        return regionsLock.optimisticRead(() -> regions.get(pos.longKey));
    }

    public boolean hasRegionData(RegionPos pos) {
        final LevelTicks<T> region = getNullable(pos);
        return region != null && !region.isEmpty();
    }

    public static boolean canScheduleFromCurrentThread(ServerLevel level, BlockPos pos) {
        final RegionPos targetRegion = RegionPos.forBlockPos(pos);
        if (TickThread.isTickThreadFor(level, pos)) {
            return true;
        }

        return ShreddedPaperConfiguration.get().multithreading.independentRegionTicking
                && ShreddedPaperTickThread.isShreddedPaperTickThread()
                && level.chunkScheduler.getRegionLocker().hasWriteLock(targetRegion);
    }

    public static boolean scheduleOnOwningRegionIfNeeded(ServerLevel level, BlockPos pos, Runnable task) {
        final RegionPos targetRegion = RegionPos.forBlockPos(pos);
        if (!ShreddedPaperConfiguration.get().multithreading.independentRegionTicking
                || !ShreddedPaperTickThread.isShreddedPaperTickThread()
                || !level.chunkScheduler.getRegionLocker().hasLock(targetRegion)) {
            return false;
        }

        level.chunkScheduler.schedule(targetRegion, task);
        return true;
    }

    public void addContainer(ChunkPos pos, LevelChunkTicks<T> scheduler) {
        final RegionPos regionPos = RegionPos.forChunk(pos);
        LevelTicks<T> region = getNullable(regionPos);
        if (region == null) {
            region = regionsLock.write(() -> regions.computeIfAbsent(regionPos.longKey, k -> createRegionLevelTicks()));
        }
        region.addContainer(pos, scheduler);
    }

    public void removeContainer(ChunkPos pos) {
        final RegionPos regionPos = RegionPos.forChunk(pos);
        final LevelTicks<T> region = getNullable(regionPos);
        if (region != null) {
            region.removeContainer(pos);

            if (region.isEmpty()) {
                regionsLock.write(() -> regions.remove(regionPos.longKey));
            }
        }
    }

    @Override
    public void schedule(ScheduledTick<T> orderedTick) {
        final LevelTicks<T> region = getNullable(orderedTick.pos());
        if (region == null) {
            throw new IllegalArgumentException("Chunk not loaded: " + orderedTick.pos());
        }
        region.schedule(orderedTick);
    }

    public void tick(RegionPos regionPos, long time, int maxTicks, BiConsumer<BlockPos, T> ticker) {
        final LevelTicks<T> region = getNullable(regionPos);
        if (region != null) {
            region.tick(time, maxTicks, ticker);
        }
    }

    @Override
    public void tick(long time, int maxTicks, BiConsumer<BlockPos, T> ticker) {
        // Do nothing
    }

    @Override
    public boolean hasScheduledTick(BlockPos pos, T type) {
        final LevelTicks<T> region = getNullable(pos);
        return region != null && region.hasScheduledTick(pos, type);
    }

    @Override
    public boolean willTickThisTick(BlockPos pos, T type) {
        final LevelTicks<T> region = getNullable(pos);
        return region != null && region.willTickThisTick(pos, type);
    }

    @Override
    public void clearArea(BoundingBox box) {
        for (LevelTicks<T> region : getRegionsInArea(box)) {
            region.clearArea(box);
        }
    }

    @Override
    public void copyArea(BoundingBox box, Vec3i offset) {
        copyAreaFrom(this, box, offset);
    }

    @Override
    public void copyAreaFrom(LevelTicks<T> scheduler, BoundingBox box, Vec3i offset) {
        List<ScheduledTick<T>> ticks = new ArrayList<>();
        scheduler.shreddedpaper$collectTicksInArea(box, ticks);
        LongSummaryStatistics subTickOrder = ticks.stream().mapToLong(ScheduledTick::subTickOrder).summaryStatistics();
        long min = subTickOrder.getMin();
        long max = subTickOrder.getMax();
        ticks.forEach(tick -> schedule(new ScheduledTick<>(
            tick.type(),
            tick.pos().offset(offset),
            tick.triggerTick(),
            tick.priority(),
            tick.subTickOrder() - min + max + 1L
        )));
    }

    @Override
    public void shreddedpaper$collectTicksInArea(BoundingBox area, List<ScheduledTick<T>> ticks) {
        for (LevelTicks<T> region : getRegionsInArea(area)) {
            region.shreddedpaper$collectTicksInArea(area, ticks);
        }
    }

    @Override
    public int count() {
        int count = 0;
        for (LevelTicks<T> region : getAllRegions()) {
            count += region.count();
        }
        return count;
    }

    private List<LevelTicks<T>> getAllRegions() {
        return regionsLock.read(() -> new ArrayList<>(regions.values()));
    }

    private List<LevelTicks<T>> getRegionsInArea(BoundingBox area) {
        int minRegionX = SectionPos.posToSectionCoord(area.minX()) >> RegionPos.REGION_SHIFT;
        int minRegionZ = SectionPos.posToSectionCoord(area.minZ()) >> RegionPos.REGION_SHIFT;
        int maxRegionX = SectionPos.posToSectionCoord(area.maxX()) >> RegionPos.REGION_SHIFT;
        int maxRegionZ = SectionPos.posToSectionCoord(area.maxZ()) >> RegionPos.REGION_SHIFT;

        return regionsLock.read(() -> {
            List<LevelTicks<T>> result = new ArrayList<>();
            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
                for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                    LevelTicks<T> region = regions.get(RegionPos.asLong(regionX, regionZ));
                    if (region != null) {
                        result.add(region);
                    }
                }
            }
            return result;
        });
    }
}
