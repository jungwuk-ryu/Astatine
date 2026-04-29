package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionOwner;
import io.multipaper.shreddedpaper.region.RegionPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionRuntimeState {

    private static final ConcurrentHashMap<RegionRuntimeKey, RegionRuntimeState> STATES = new ConcurrentHashMap<>();
    private static final AtomicLongArray CREATED_BY_REASON = new AtomicLongArray(CreationReason.values().length);
    private static final AtomicLongArray REMOVED_BY_REASON = new AtomicLongArray(RemovalReason.values().length);

    private final RegionRuntimeKey key;
    private final ServerLevel level;
    private final long ownerId;
    private final RegionPos regionPos;
    private final RegionMailbox mailbox;
    private final RegionOverloadController overloadController;
    private final RegionChunkIoTracker chunkIoTracker;
    private final AtomicReference<LevelChunkRegion> currentRegion = new AtomicReference<>();

    private RegionRuntimeState(final ServerLevel level, final RegionOwner owner, final CreationReason reason) {
        this.key = new RegionRuntimeKey(level.uuid, owner.id());
        this.level = level;
        this.ownerId = owner.id();
        this.regionPos = owner.primaryCell();
        this.mailbox = new RegionMailbox(level, owner.primaryCell(), owner.id(), owner::layoutEpoch, owner::ownsCellKey);
        this.overloadController = new RegionOverloadController(level, owner.primaryCell());
        this.chunkIoTracker = new RegionChunkIoTracker(level, owner.primaryCell(), this.overloadController);
        CREATED_BY_REASON.incrementAndGet(reason.ordinal());
    }

    public static RegionRuntimeState getOrCreate(final ServerLevel level, final RegionOwner owner) {
        return getOrCreate(level, owner, CreationReason.REGION);
    }

    public static RegionRuntimeState getOrCreate(final ServerLevel level, final RegionOwner owner, final CreationReason reason) {
        final RegionRuntimeKey key = new RegionRuntimeKey(level.uuid, owner.id());
        return STATES.computeIfAbsent(key, ignored -> new RegionRuntimeState(level, owner, reason));
    }

    public static RegionRuntimeState getOrCreate(final ServerLevel level, final RegionPos regionPos) {
        return getOrCreate(level, RegionOwner.singleCell(regionPos), CreationReason.CELL_LOOKUP);
    }

    public static void removeIfIdle(final RegionRuntimeState state) {
        removeIfIdle(state, RemovalReason.DETACHED_IDLE);
    }

    public static boolean removeIfIdle(final RegionRuntimeState state, final RemovalReason reason) {
        if (state.currentRegion.get() == null && state.isIdle()) {
            final boolean removed = STATES.remove(state.key, state);
            if (removed) {
                REMOVED_BY_REASON.incrementAndGet(reason.ordinal());
            }
            return removed;
        }
        return false;
    }

    public void attach(final LevelChunkRegion region) {
        this.currentRegion.set(region);
    }

    public void detach(final LevelChunkRegion region) {
        this.detach(region, RemovalReason.DETACHED_IDLE);
    }

    public void detach(final LevelChunkRegion region, final RemovalReason reason) {
        this.currentRegion.compareAndSet(region, null);
        removeIfIdle(this, reason);
    }

    public LevelChunkRegion currentRegion() {
        return this.currentRegion.get();
    }

    public ServerLevel level() {
        return this.level;
    }

    public long ownerId() {
        return this.ownerId;
    }

    public RegionPos regionPos() {
        return this.regionPos;
    }

    public RegionMailbox mailbox() {
        return this.mailbox;
    }

    public RegionOverloadController overloadController() {
        return this.overloadController;
    }

    public RegionChunkIoTracker chunkIoTracker() {
        return this.chunkIoTracker;
    }

    public boolean isIdle() {
        return !this.mailbox.hasPendingTasks() && !this.chunkIoTracker.hasPendingWork();
    }

    public static RuntimeStateDiagnostics diagnostics() {
        int totalStates = 0;
        int attachedStates = 0;
        int detachedPendingStates = 0;
        int orphanStates = 0;

        for (final RegionRuntimeState state : STATES.values()) {
            totalStates++;
            if (state.currentRegion.get() != null) {
                attachedStates++;
            } else if (state.isIdle()) {
                orphanStates++;
            } else {
                detachedPendingStates++;
            }
        }

        return new RuntimeStateDiagnostics(
                totalStates,
                attachedStates,
                detachedPendingStates,
                orphanStates,
                CREATED_BY_REASON.get(CreationReason.REGION.ordinal()),
                CREATED_BY_REASON.get(CreationReason.SPLIT.ordinal()),
                CREATED_BY_REASON.get(CreationReason.CELL_LOOKUP.ordinal()),
                REMOVED_BY_REASON.get(RemovalReason.EMPTY_REGION.ordinal()),
                REMOVED_BY_REASON.get(RemovalReason.MERGED_REGION.ordinal()),
                REMOVED_BY_REASON.get(RemovalReason.DETACHED_IDLE.ordinal())
        );
    }

    public static LiveStateDiagnostics liveDiagnostics(final ServerLevel level) {
        int totalStates = 0;
        int attachedStates = 0;
        int detachedPendingStates = 0;
        int orphanStates = 0;

        for (final RegionRuntimeState state : STATES.values()) {
            if (state.level != level) {
                continue;
            }
            totalStates++;
            if (state.currentRegion.get() != null) {
                attachedStates++;
            } else if (state.isIdle()) {
                orphanStates++;
            } else {
                detachedPendingStates++;
            }
        }

        return new LiveStateDiagnostics(
                totalStates,
                attachedStates,
                detachedPendingStates,
                orphanStates
        );
    }

    public enum CreationReason {
        REGION,
        SPLIT,
        CELL_LOOKUP
    }

    public enum RemovalReason {
        EMPTY_REGION,
        MERGED_REGION,
        DETACHED_IDLE
    }

    public record RuntimeStateDiagnostics(
            int totalStates,
            int attachedStates,
            int detachedPendingStates,
            int orphanStates,
            long createdRegions,
            long createdSplits,
            long createdCellLookups,
            long removedEmptyRegions,
            long removedMergedRegions,
            long removedDetachedIdle
    ) {
    }

    public record LiveStateDiagnostics(
            int totalStates,
            int attachedStates,
            int detachedPendingStates,
            int orphanStates
    ) {
    }

    private record RegionRuntimeKey(UUID worldId, long ownerId) {
    }
}
