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

    private static final ThreadLocal<BroadcastState> broadcastStateThreadLocal = new ThreadLocal<>();

    public static void setAsWorkerThread() {
        if (broadcastStateThreadLocal.get() == null) {
            broadcastStateThreadLocal.set(new BroadcastState());
        }
    }

    public static void add(ChunkHolder chunkHolder) {
        BroadcastState state = broadcastStateThreadLocal.get();
        if (state != null) {
            state.pending.add(chunkHolder);
        }
    }

    public static void remove(ChunkHolder chunkHolder) {
        BroadcastState state = broadcastStateThreadLocal.get();
        if (state != null) {
            state.pending.remove(chunkHolder);
        }
    }

    public static void broadcastChanges() {
        broadcastChanges(RegionTickBudget.current());
    }

    public static void broadcastChanges(RegionTickBudget budget) {
        final BroadcastState state = broadcastStateThreadLocal.get();
        if (state == null) {
            return;
        }
        if (state.broadcasting) {
            broadcastChanges(state.pending, budget);
            return;
        }
        broadcastChanges(state, budget);
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
            drainBroadcasts(needsChangeBroadcasting, copy, budget);
        }
    }

    private static void broadcastChanges(final BroadcastState state, final RegionTickBudget budget) {
        if (state.pending.isEmpty()) {
            return;
        }

        state.processing.clear();
        state.processing.addAll(state.pending);
        state.pending.clear();
        state.broadcasting = true;
        try {
            drainBroadcasts(state.pending, state.processing, budget);
        } finally {
            state.processing.clear();
            state.broadcasting = false;
        }
    }

    private static void drainBroadcasts(final ReferenceOpenHashSet<ChunkHolder> pending, final ReferenceOpenHashSet<ChunkHolder> processing, final RegionTickBudget budget) {
        boolean deferred = false;
        for (ChunkHolder holder : processing) {
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
                pending.add(holder);
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
                pending.add(holder);
            }
        }
    }

    private static final class BroadcastState {
        private final ReferenceOpenHashSet<ChunkHolder> pending = new ReferenceOpenHashSet<>();
        private final ReferenceOpenHashSet<ChunkHolder> processing = new ReferenceOpenHashSet<>();
        private boolean broadcasting;
    }
}
