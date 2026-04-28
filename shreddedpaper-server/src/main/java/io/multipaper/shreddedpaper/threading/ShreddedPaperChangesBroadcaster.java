package io.multipaper.shreddedpaper.threading;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.RegionTickBudget;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.multipaper.shreddedpaper.threading.region.RegionWorkType;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public class ShreddedPaperChangesBroadcaster {

    private static final ThreadLocal<ReferenceOpenHashSet<ChunkHolder>> needsChangeBroadcastingThreadLocal = new ThreadLocal<>();

    public static void setAsWorkerThread() {
        if (needsChangeBroadcastingThreadLocal.get() == null) {
            needsChangeBroadcastingThreadLocal.set(new ReferenceOpenHashSet<>());
        }
    }

    public static void add(ChunkHolder chunkHolder) {
        ReferenceOpenHashSet<ChunkHolder> needsChangeBroadcasting = needsChangeBroadcastingThreadLocal.get();
        if (needsChangeBroadcasting != null) {
            needsChangeBroadcasting.add(chunkHolder);
        }
    }

    public static void remove(ChunkHolder chunkHolder) {
        ReferenceOpenHashSet<ChunkHolder> needsChangeBroadcasting = needsChangeBroadcastingThreadLocal.get();
        if (needsChangeBroadcasting != null) {
            needsChangeBroadcasting.remove(chunkHolder);
        }
    }

    public static void broadcastChanges() {
        broadcastChanges(RegionTickBudget.current());
    }

    public static void broadcastChanges(RegionTickBudget budget) {
        broadcastChanges(needsChangeBroadcastingThreadLocal.get(), budget);
    }

    public static void broadcastChanges(ReferenceOpenHashSet<ChunkHolder> needsChangeBroadcasting) {
        broadcastChanges(needsChangeBroadcasting, RegionTickBudget.current());
    }

    public static void broadcastChanges(ReferenceOpenHashSet<ChunkHolder> needsChangeBroadcasting, RegionTickBudget budget) {
        if (needsChangeBroadcasting == null) {
            return;
        }

        if (!needsChangeBroadcasting.isEmpty()) {
            ReferenceOpenHashSet<ChunkHolder> copy = needsChangeBroadcasting.clone();
            needsChangeBroadcasting.clear();
            boolean deferred = false;
            for (ChunkHolder holder : copy) {
                if (!TickThread.isTickThreadFor(holder.moonrise$getRealChunkHolder().world, holder.getPos())) {
                    final ServerLevel targetLevel = holder.moonrise$getRealChunkHolder().world;
                    final RegionPos targetRegion = RegionPos.forChunk(holder.getPos());
                    final Runnable broadcastTask = () -> {
                        ShreddedPaperChangesBroadcaster.add(holder);
                        ShreddedPaperChangesBroadcaster.broadcastChanges();
                    };
                    targetLevel.getChunkSource().tickingRegions.scheduleTaskNonDropping(targetRegion, broadcastTask, 1L, RegionTaskClass.TRACKER_BROADCAST);
                    continue;
                }

                if (deferred || (budget != null && !budget.canContinue(RegionWorkType.BROADCAST))) {
                    needsChangeBroadcasting.add(holder);
                    deferred = true;
                    continue;
                }

                final LevelChunk chunk = holder.getFullChunkNowUnchecked();
                if (chunk == null) {
                    // A holder can remain queued after teleport/unload churn downgrades the full chunk.
                    // The holder keeps its dirty flags, and it will be queued again when the chunk is ready to send.
                    continue;
                }

                holder.broadcastChanges(chunk);
                if (holder.hasChangesToBroadcast()) {
                    // I DON'T want to KNOW what DUMB plugins might be doing.
                    needsChangeBroadcasting.add(holder);
                }
            }
        }
    }
}
