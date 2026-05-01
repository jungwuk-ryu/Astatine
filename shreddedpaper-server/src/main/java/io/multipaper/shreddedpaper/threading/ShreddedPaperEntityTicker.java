package io.multipaper.shreddedpaper.threading;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.ShreddedPaper;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionPos;

public class ShreddedPaperEntityTicker {

    public static void tickEntity(Entity entity) {
        if (!isOwnedByCurrentTickingRegion(entity)) {
            return;
        }
        ProfilerFiller profilerFiller = Profiler.get();
        ServerLevel level = (ServerLevel) entity.level();

        entity.activatedPriorityReset = false; // DivineMC - Dynamic Activation of Brain
        if (!entity.isRemoved()) {
            if (!level.tickRateManager().isEntityFrozen(entity)) {
                profilerFiller.push("checkDespawn");
                entity.checkDespawn();
                profilerFiller.pop();
                if (true) { // Paper - rewrite chunk system
                    Entity vehicle = entity.getVehicle();
                    if (vehicle != null) {
                        if (!TickThread.isTickThreadFor(vehicle)) {
                            ShreddedPaper.ensureSync(entity, vehicle, () -> tickEntity(entity));
                            return;
                        }
                        if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                            return;
                        }

                        entity.stopRiding();
                    }

                    profilerFiller.push("tick");
                    try (var ignored = level.chunkScheduler.getRegionLocker().promoteCurrentThreadLocksToWrite()) {
                        level.guardEntityTick(level::tickNonPassenger, entity);
                    }
                    profilerFiller.pop();
                }
            }
        }
    }

    /** processTrackQueue has been renamed to newTrackerTick */
    public static void processTrackQueue(Entity entity) {
        if (entity.isRemoved() || !isOwnedByCurrentTickingRegion(entity)) {
            return;
        }
        ChunkMap.TrackedEntity tracker = entity.moonrise$getTrackedEntity();
        if (tracker == null) {
            return;
        }
        final ca.spottedleaf.moonrise.patches.chunk_system.level.chunk.ChunkData chunkData = ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkData();
        if (chunkData == null) {
            return;
        }
        ((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$tick(chunkData.nearbyPlayers);
        if (((ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerTrackedEntity)tracker).moonrise$hasPlayers()
                || ((ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity)entity).moonrise$getChunkStatus().isOrAfter(FullChunkStatus.ENTITY_TICKING)) {
            tracker.serverEntity.sendChanges();
        }
    }

    private static boolean isOwnedByCurrentTickingRegion(Entity entity) {
        final LevelChunkRegion region = ShreddedPaperChunkTicker.currentlyTickingRegion();
        return region == null || (entity.level() == region.getLevel() && region.getOwner().ownsCell(RegionPos.forChunk(entity.chunkPosition())));
    }
}
