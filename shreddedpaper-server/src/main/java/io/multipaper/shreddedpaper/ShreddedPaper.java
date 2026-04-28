package io.multipaper.shreddedpaper;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionScheduler;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import io.multipaper.shreddedpaper.region.RegionPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ShreddedPaper {

    public static void runSync(Location location, Runnable runnable) {
        runSync(((CraftWorld) location.getWorld()).getHandle(), new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), runnable);
    }

    public static void runSync(Entity entity, Runnable runnable) {
        entity.getBukkitEntity().taskScheduler.schedule(e -> runnable.run(), null, 1);
    }

    public static void runSync(Entity entity, Consumer<Entity> consumer) {
        entity.getBukkitEntity().taskScheduler.schedule(consumer, null, 1);
    }

    public static void runSync(ServerLevel serverLevel, BlockPos blockPos, Runnable runnable) {
        runSync(serverLevel, ChunkPos.of(blockPos), runnable);
    }

    public static void runSync(ServerLevel serverLevel, ChunkPos chunkPos, Runnable runnable) {
        serverLevel.getChunkSource().tickingRegions.scheduleTask(RegionPos.forChunk(chunkPos), runnable);
    }

    public static void runSync(ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        serverLevel.chunkScheduler.scheduleOnMany(runnable, regionsForBox(box));
    }

    public static void runSync(ServerLevel serverLevel1, ChunkPos chunkPos1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        ShreddedPaperRegionScheduler.scheduleAcrossLevels(serverLevel1, RegionPos.forChunk(chunkPos1), serverLevel2, RegionPos.forChunk(chunkPos2), runnable);
    }

    public static void ensureSync(Location location, Runnable runnable) {
        ensureSync(((CraftWorld) location.getWorld()).getHandle(), new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ()), runnable);
    }

    public static void ensureSync(Entity entity, Runnable runnable) {
        if (!isSync((ServerLevel) entity.level(), entity.chunkPosition())) {
            runSync(entity, runnable);
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(Entity entity, Consumer<Entity> consumer) {
        if (!isSync((ServerLevel) entity.level(), entity.chunkPosition())) {
            runSync(entity, consumer);
        } else {
            consumer.accept(entity);
        }
    }

    public static void ensureSync(ServerLevel serverLevel, BlockPos blockPos, Runnable runnable) {
        ensureSync(serverLevel, ChunkPos.of(blockPos), runnable);
    }

    public static void ensureSync(ServerLevel serverLevel, ChunkPos chunkPos, Runnable runnable) {
        if (!isSync(serverLevel, chunkPos)) {
            runSync(serverLevel, chunkPos, runnable);
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        RegionPos[] regionPositions = regionsForBox(box);
        for (RegionPos regionPos : regionPositions) {
            if (!isSync(serverLevel, regionPos)) {
                serverLevel.chunkScheduler.scheduleOnMany(runnable, regionPositions);
                return;
            }
        }

        runnable.run();
    }

    public static void ensureSync(Entity entity, ServerLevel serverLevel, BoundingBox box, Runnable runnable) {
        final ServerLevel entityLevel = (ServerLevel) entity.level();
        if (entityLevel != serverLevel) {
            ensureSync(entity, () -> ensureSync(serverLevel, box, runnable));
            return;
        }

        final RegionPos entityRegion = RegionPos.forChunk(entity.chunkPosition());
        final RegionPos[] boxRegions = regionsForBox(box);
        if (!isSync(serverLevel, entityRegion) || !isSync(serverLevel, box)) {
            serverLevel.chunkScheduler.scheduleOnMany(
                    () -> ensureSync(entity, serverLevel, box, runnable),
                    includeRegion(entityRegion, boxRegions)
            );
            return;
        }

        runnable.run();
    }

    public static boolean isSync(ServerLevel serverLevel, BoundingBox box) {
        for (RegionPos regionPos : regionsForBox(box)) {
            if (!isSync(serverLevel, regionPos)) {
                return false;
            }
        }

        return true;
    }

    public static void ensureSync(Entity entity1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        if (!isSync((ServerLevel) entity1.level(), entity1.chunkPosition()) || !isSync(serverLevel2, chunkPos2)) {
            runSync((ServerLevel) entity1.level(), entity1.chunkPosition(), serverLevel2, chunkPos2, () -> ensureSync(entity1, serverLevel2, chunkPos2, runnable)); // Entity may have moved since, ensure still sync
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(Entity entity1, Entity entity2, Runnable runnable) {
        if (!isSync((ServerLevel) entity1.level(), entity1.chunkPosition()) || !isSync((ServerLevel) entity2.level(), entity2.chunkPosition())) {
            runSync((ServerLevel) entity1.level(), entity1.chunkPosition(), (ServerLevel) entity2.level(), entity2.chunkPosition(), () -> ensureSync(entity1, entity2, runnable)); // Entity may have moved since, ensure still sync
        } else {
            runnable.run();
        }
    }

    public static void ensureSync(ServerLevel serverLevel1, ChunkPos chunkPos1, ServerLevel serverLevel2, ChunkPos chunkPos2, Runnable runnable) {
        if (!isSync(serverLevel1, chunkPos1) || !isSync(serverLevel2, chunkPos2)) {
            runSync(serverLevel1, chunkPos1, serverLevel2, chunkPos2, runnable);
        } else {
            runnable.run();
        }
    }

    public static boolean isSync(ServerLevel serverLevel, ChunkPos chunkPos) {
        return isSync(serverLevel, RegionPos.forChunk(chunkPos));
    }

    public static boolean isSync(ServerLevel serverLevel, RegionPos regionPos) {
        return serverLevel.chunkScheduler.getRegionLocker().hasWriteLock(regionPos)
                || ShreddedPaperChunkTicker.isCurrentlyTickingRegion(serverLevel, regionPos)
                || TickThread.isShutdownThread();
    }

    private static RegionPos[] regionsForBox(BoundingBox box) {
        final int minRegionX = SectionPos.blockToSectionCoord(box.minX()) >> RegionPos.REGION_SHIFT;
        final int minRegionZ = SectionPos.blockToSectionCoord(box.minZ()) >> RegionPos.REGION_SHIFT;
        final int maxRegionX = SectionPos.blockToSectionCoord(box.maxX()) >> RegionPos.REGION_SHIFT;
        final int maxRegionZ = SectionPos.blockToSectionCoord(box.maxZ()) >> RegionPos.REGION_SHIFT;
        final List<RegionPos> regionPositions = new ArrayList<>((maxRegionX - minRegionX + 1) * (maxRegionZ - minRegionZ + 1));

        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                regionPositions.add(new RegionPos(regionX, regionZ));
            }
        }

        return regionPositions.toArray(RegionPos[]::new);
    }

    private static RegionPos[] includeRegion(RegionPos regionPos, RegionPos[] regionPositions) {
        for (RegionPos existing : regionPositions) {
            if (existing.longKey == regionPos.longKey) {
                return regionPositions;
            }
        }

        final RegionPos[] result = new RegionPos[regionPositions.length + 1];
        result[0] = regionPos;
        System.arraycopy(regionPositions, 0, result, 1, regionPositions.length);
        return result;
    }

    public static void postProcessGeneration(ServerLevel serverLevel, LevelChunk chunk) {
        try (var ignored = serverLevel.chunkScheduler.getRegionLocker().promoteCurrentThreadLocksToWrite()) {
            chunk.postProcessGeneration(serverLevel);
        }
    }

}
