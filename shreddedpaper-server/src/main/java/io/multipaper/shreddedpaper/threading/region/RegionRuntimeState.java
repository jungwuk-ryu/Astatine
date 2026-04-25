package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionOwner;
import io.multipaper.shreddedpaper.region.RegionPos;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionRuntimeState {

    private static final ConcurrentHashMap<RegionRuntimeKey, RegionRuntimeState> STATES = new ConcurrentHashMap<>();

    private final RegionRuntimeKey key;
    private final ServerLevel level;
    private final long ownerId;
    private final RegionPos regionPos;
    private final RegionMailbox mailbox;
    private final RegionOverloadController overloadController;
    private final RegionChunkIoTracker chunkIoTracker;
    private final AtomicReference<LevelChunkRegion> currentRegion = new AtomicReference<>();

    private RegionRuntimeState(final ServerLevel level, final RegionOwner owner) {
        this.key = new RegionRuntimeKey(level.uuid, owner.id());
        this.level = level;
        this.ownerId = owner.id();
        this.regionPos = owner.primaryCell();
        this.mailbox = new RegionMailbox(level, owner.primaryCell(), owner.id(), owner::layoutEpoch, cellKey -> owner.ownsCell(new RegionPos(cellKey)));
        this.overloadController = new RegionOverloadController(level, owner.primaryCell());
        this.chunkIoTracker = new RegionChunkIoTracker(level, owner.primaryCell(), this.overloadController);
    }

    public static RegionRuntimeState getOrCreate(final ServerLevel level, final RegionOwner owner) {
        final RegionRuntimeKey key = new RegionRuntimeKey(level.uuid, owner.id());
        return STATES.computeIfAbsent(key, ignored -> new RegionRuntimeState(level, owner));
    }

    public static RegionRuntimeState getOrCreate(final ServerLevel level, final RegionPos regionPos) {
        return getOrCreate(level, RegionOwner.singleCell(regionPos));
    }

    public static void removeIfIdle(final RegionRuntimeState state) {
        if (state.currentRegion.get() == null && state.isIdle()) {
            STATES.remove(state.key, state);
        }
    }

    public void attach(final LevelChunkRegion region) {
        this.currentRegion.set(region);
    }

    public void detach(final LevelChunkRegion region) {
        this.currentRegion.compareAndSet(region, null);
        removeIfIdle(this);
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

    private record RegionRuntimeKey(UUID worldId, long ownerId) {
    }
}
