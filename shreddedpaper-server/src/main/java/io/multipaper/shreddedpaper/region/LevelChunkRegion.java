package io.multipaper.shreddedpaper.region;

import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet;
import io.multipaper.shreddedpaper.threading.region.RegionMailbox;
import io.multipaper.shreddedpaper.threading.region.RegionOverloadController;
import io.multipaper.shreddedpaper.threading.region.RegionRuntimeState;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.multipaper.shreddedpaper.threading.region.RegionTickBudget;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LevelChunkRegion {

    private static final long NO_PREVIOUS_TICK = Long.MIN_VALUE;
    public static final long NO_CONTINUATION_CURSOR = Long.MIN_VALUE;
    private static final long TICK_STATS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final ServerLevel level;
    private final RegionOwner owner;
    private final RegionPos regionPos;
    private final List<LevelChunk> levelChunks = new ArrayList<>(RegionPos.REGION_SIZE * RegionPos.REGION_SIZE);
    private final LongOpenHashSet playerTickingChunkRequests = new LongOpenHashSet(); // ChunkPos.longKey
    private final IteratorSafeOrderedReferenceSet<Entity> tickingEntities = new IteratorSafeOrderedReferenceSet<>(); // Use IteratorSafeOrderedReferenceSet to maintain entity tick order
    private final Set<Entity> trackedEntities = new ObjectLinkedOpenHashSet<>();
    private final RegionRuntimeState runtimeState;
    private final PrioritisedTaskQueue internalTasks = new PrioritisedTaskQueue(); // Read-only tasks
    private final ObjectLinkedOpenHashSet<ServerPlayer> players = new ObjectLinkedOpenHashSet<>();
    public final LongLinkedOpenHashSet unloadQueue = new LongLinkedOpenHashSet();
    public final List<TickingBlockEntity> tickingBlockEntities = new ReferenceArrayList<>();
    public final List<TickingBlockEntity> pendingBlockEntityTickers = new ReferenceArrayList<>();
    private final ObjectOpenHashSet<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
    private final ArrayDeque<TickSample> tickSamples = new ArrayDeque<>();
    private long lastTickStatsStartNanos = NO_PREVIOUS_TICK;
    private volatile long lastAccessTick;
    private long scheduledTickCellCursor = NO_CONTINUATION_CURSOR;
    private boolean scheduledTickFluidPhase;
    private long chunkTickCursor = NO_CONTINUATION_CURSOR;
    private long entityTaskCursor = NO_CONTINUATION_CURSOR;
    private long entityTickCursor = NO_CONTINUATION_CURSOR;
    private long trackerCursor = NO_CONTINUATION_CURSOR;
    private long playerTickCursor = NO_CONTINUATION_CURSOR;
    public ArrayDeque<RedstoneTorchBlock.Toggle> redstoneUpdateInfos;

    public LevelChunkRegion(ServerLevel level, RegionOwner owner) {
        this.level = level;
        this.owner = owner;
        this.regionPos = owner.primaryCell();
        this.runtimeState = RegionRuntimeState.getOrCreate(level, owner);
        this.runtimeState.attach(this);

        this.bumpLastAccess();
    }

    public void bumpLastAccess() {
        this.lastAccessTick = this.level.levelData.getGameTime();
    }

    public synchronized void add(LevelChunk levelChunk) {
        this.levelChunks.add(levelChunk);
    }

    public synchronized void remove(LevelChunk levelChunk) {
        if (!this.levelChunks.remove(levelChunk)) {
            throw new IllegalStateException("Tried to remove a chunk that wasn't in the region: " + levelChunk.getPos());
        }
    }

    public synchronized void addPlayerTickingRequest(final ChunkPos chunkPos) {
        this.playerTickingChunkRequests.add(chunkPos.toLong());
    }

    public synchronized void removePlayerTickingRequest(final ChunkPos chunkPos) {
        this.playerTickingChunkRequests.remove(chunkPos.toLong());
    }

    public synchronized void addUnloadChunk(final ChunkPos chunkPos) {
        this.unloadQueue.add(chunkPos.toLong());
    }

    public synchronized void removeUnloadChunk(final ChunkPos chunkPos) {
        this.unloadQueue.remove(chunkPos.toLong());
    }

    public synchronized void addPendingBlockEntityTicker(final TickingBlockEntity ticker) {
        this.pendingBlockEntityTickers.add(ticker);
    }

    public boolean isPlayerTickingRequested(final ChunkPos chunkPos) {
        final RegionPos chunkRegion = chunkPos.getRegionPos();
        if (!this.owner.ownsCell(chunkRegion)) {
            throw new IllegalStateException("Chunk %s is not owned by region owner %s".formatted(chunkPos, this.owner));
        }

        return this.playerTickingChunkRequests.contains(chunkPos.toLong());
    }

    public synchronized void addTickingEntity(Entity entity) {
        if (!this.tickingEntities.add(entity)) {
            throw new IllegalStateException("Tried to add an entity that was already in the ticking list: " + entity);
        }
    }

    public synchronized void removeTickingEntity(Entity entity) {
        if (!this.tickingEntities.remove(entity)) {
            throw new IllegalStateException("Tried to remove an entity that wasn't in the ticking list: " + entity);
        }
    }

    public void forEachTickingEntity(Consumer<Entity> action) {
        IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.tickingEntities.iterator();
        try {
            while (iterator.hasNext()) {
                action.accept(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
    }

    public List<Entity> getTickingEntitiesSnapshot() {
        final List<Entity> entities = new ArrayList<>(this.tickingEntities.size());
        final IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.tickingEntities.iterator();
        try {
            while (iterator.hasNext()) {
                entities.add(iterator.next());
            }
        } finally {
            iterator.finishedIterating();
        }
        return entities;
    }

    public boolean forEachTickingEntityUntil(Predicate<Entity> action) {
        IteratorSafeOrderedReferenceSet.Iterator<Entity> iterator = this.tickingEntities.iterator();
        try {
            while (iterator.hasNext()) {
                if (!action.test(iterator.next())) {
                    return false;
                }
            }
            return true;
        } finally {
            iterator.finishedIterating();
        }
    }

    public synchronized void addTrackedEntity(Entity entity) {
        if (!this.trackedEntities.add(entity)) {
            throw new IllegalStateException("Tried to add an entity that was already tracked: " + entity);
        }
    }

    public synchronized void removeTrackedEntity(Entity entity) {
        if (!this.trackedEntities.remove(entity)) {
            throw new IllegalStateException("Tried to remove an entity that wasn't already tracked: " + entity);
        }
    }

    public synchronized void forEachTrackedEntity(Consumer<Entity> action) {
        this.trackedEntities.forEach(action);
    }

    public synchronized boolean forEachTrackedEntityUntil(Predicate<Entity> action) {
        for (final Entity entity : this.trackedEntities) {
            if (!action.test(entity)) {
                return false;
            }
        }
        return true;
    }

    public ServerLevel getLevel() {
        return this.level;
    }

    public boolean scheduleTask(Runnable task, long delay) {
        return this.scheduleTask(RegionTaskClass.CRITICAL_SYSTEM, task, delay);
    }

    public boolean scheduleTask(RegionTaskClass taskClass, Runnable task, long delay) {
        return this.runtimeState.mailbox().offer(taskClass, task, delay, this.owner.primaryCell());
    }

    public boolean scheduleTask(RegionTaskClass taskClass, Runnable task, long delay, RegionPos affinityRegionPos) {
        return this.runtimeState.mailbox().offer(taskClass, task, delay, affinityRegionPos);
    }

    public boolean scheduleTransferredTask(RegionTaskClass taskClass, Runnable task, long delay, RegionPos affinityRegionPos) {
        return this.runtimeState.mailbox().offerTransferred(taskClass, task, delay, affinityRegionPos);
    }

    public PrioritisedTaskQueue getInternalTaskQueue() {
        return this.internalTasks;
    }

    public RegionMailbox getMailbox() {
        return this.runtimeState.mailbox();
    }

    public RegionOverloadController getOverloadController() {
        return this.runtimeState.overloadController();
    }

    public RegionRuntimeState getRuntimeState() {
        return this.runtimeState;
    }

    public synchronized void addPlayer(ServerPlayer player) {
        if (!this.players.add(player)) {
            throw new IllegalStateException("Tried to add a player that was already in the region: " + player.getUUID());
        }
    }

    public synchronized void removePlayer(ServerPlayer player) {
        if (!this.players.remove(player)) {
            throw new IllegalStateException("Tried to remove a player that wasn't in the region: " + player.getUUID());
        }
    }

    public synchronized List<ServerPlayer> getPlayers() {
        return this.players.isEmpty() ? List.of() : new ObjectArrayList<>(this.players);
    }

    public synchronized List<Entity> getTrackedEntitiesSnapshot() {
        return this.trackedEntities.isEmpty() ? List.of() : new ObjectArrayList<>(this.trackedEntities);
    }

    public synchronized void addNavigationMob(Mob mob) {
        this.navigatingMobs.add(mob);
    }

    public synchronized void removeNavigationMob(Mob mob) {
        this.navigatingMobs.remove(mob);
    }

    public synchronized void collectNavigatingMobs(List<Mob> collection) {
        collection.addAll(this.navigatingMobs);
    }

    public RegionPos getRegionPos() {
        this.owner.requireSingleCell("LevelChunkRegion#getRegionPos");
        return regionPos;
    }

    public RegionOwner getOwner() {
        return this.owner;
    }

    public RegionPos getPrimaryRegionPos() {
        return this.regionPos;
    }

    public boolean isMergeQuiescent() {
        return !this.runtimeState.mailbox().hasPendingTasks()
                && this.internalTasks.getTotalTasksExecuted() >= this.internalTasks.getTotalTasksScheduled();
    }

    public void absorbFrom(final LevelChunkRegion source) {
        if (this.level != source.level) {
            throw new IllegalArgumentException("Cannot merge regions from different worlds");
        }
        if (!source.isMergeQuiescent()) {
            throw new IllegalStateException("Cannot merge non-quiescent region owner " + source.getOwner());
        }

        final List<Entity> sourceTickingEntities = new ArrayList<>();
        source.forEachTickingEntity(sourceTickingEntities::add);
        final List<ServerPlayer> sourcePlayers = source.getPlayers();

        synchronized (this) {
            synchronized (source) {
                this.levelChunks.addAll(source.levelChunks);
                this.playerTickingChunkRequests.addAll(source.playerTickingChunkRequests);
                for (final Entity entity : sourceTickingEntities) {
                    this.tickingEntities.add(entity);
                }
                this.trackedEntities.addAll(source.trackedEntities);
                this.players.addAll(sourcePlayers);
                this.unloadQueue.addAll(source.unloadQueue);
                this.tickingBlockEntities.addAll(source.tickingBlockEntities);
                this.pendingBlockEntityTickers.addAll(source.pendingBlockEntityTickers);
                this.navigatingMobs.addAll(source.navigatingMobs);
                this.blockEvents.addAll(source.blockEvents);
                if (source.redstoneUpdateInfos != null) {
                    if (this.redstoneUpdateInfos == null) {
                        this.redstoneUpdateInfos = new ArrayDeque<>();
                    }
                    this.redstoneUpdateInfos.addAll(source.redstoneUpdateInfos);
                }

                source.levelChunks.clear();
                source.playerTickingChunkRequests.clear();
                for (final Entity entity : sourceTickingEntities) {
                    source.tickingEntities.remove(entity);
                }
                source.trackedEntities.clear();
                source.players.clear();
                source.unloadQueue.clear();
                source.tickingBlockEntities.clear();
                source.pendingBlockEntityTickers.clear();
                source.navigatingMobs.clear();
                source.blockEvents.clear();
                if (source.redstoneUpdateInfos != null) {
                    source.redstoneUpdateInfos.clear();
                }
            }
        }

        for (final ServerPlayer player : sourcePlayers) {
            player.currentRegion = this;
        }
    }

    public synchronized void recordTickStats(long tickStartNanos, long tickDurationNanos) {
        tickDurationNanos = Math.max(0L, tickDurationNanos);
        pruneTickStats(tickStartNanos);
        this.tickSamples.addLast(new TickSample(tickStartNanos, tickStartNanos + tickDurationNanos, tickDurationNanos, this.lastTickStatsStartNanos));
        this.lastTickStatsStartNanos = tickStartNanos;
    }

    public synchronized TickStats getTickStats() {
        pruneTickStats(System.nanoTime());
        if (this.tickSamples.isEmpty()) {
            return null;
        }

        final long tickInterval = this.level.tickRateManager().nanosecondsPerTick();
        long totalTimeBetweenTicks = 0L;
        long totalTimeTicking = 0L;

        for (TickSample sample : this.tickSamples) {
            totalTimeTicking += sample.durationNanos();
            if (sample.previousStartNanos() == NO_PREVIOUS_TICK) {
                totalTimeBetweenTicks += Math.max(tickInterval, sample.durationNanos());
            } else {
                totalTimeBetweenTicks += Math.max(1L, sample.startNanos() - sample.previousStartNanos());
            }
        }

        final double tps = totalTimeBetweenTicks <= 0L ? 1.0E9D / (double) tickInterval : (double) this.tickSamples.size() / ((double) totalTimeBetweenTicks / 1.0E9D);
        final double mspt = (double) totalTimeTicking / (double) this.tickSamples.size() * 1.0E-6D;
        return new TickStats(tps, mspt);
    }

    private void pruneTickStats(long nowNanos) {
        TickSample first;
        while ((first = this.tickSamples.peekFirst()) != null && nowNanos - first.endNanos() > TICK_STATS_INTERVAL_NANOS) {
            this.tickSamples.removeFirst();
        }
    }

    public void forEach(Consumer<LevelChunk> consumer) {
        // This method has the chance of skipping a chunk if a chunk is removed via another thread during this iteration
        for (int i = 0; i < this.levelChunks.size(); i++) {
            try {
                LevelChunk levelChunk = this.levelChunks.get(i);
                if (levelChunk != null) {
                    consumer.accept(levelChunk);
                }
            } catch (IndexOutOfBoundsException e) {
                // Ignore - multithreaded modification
            }
        }
    }

    public boolean forEachUntil(Predicate<LevelChunk> consumer) {
        // This method has the chance of skipping a chunk if a chunk is removed via another thread during this iteration
        for (int i = 0; i < this.levelChunks.size(); i++) {
            try {
                LevelChunk levelChunk = this.levelChunks.get(i);
                if (levelChunk != null && !consumer.test(levelChunk)) {
                    return false;
                }
            } catch (IndexOutOfBoundsException e) {
                // Ignore - multithreaded modification
            }
        }
        return true;
    }

    public synchronized List<LevelChunk> getChunksSnapshot() {
        return this.levelChunks.isEmpty() ? List.of() : new ArrayList<>(this.levelChunks);
    }

    public synchronized long getScheduledTickCellCursor() {
        return this.scheduledTickCellCursor;
    }

    public synchronized boolean isScheduledTickFluidPhase() {
        return this.scheduledTickFluidPhase;
    }

    public synchronized void setScheduledTickCellCursor(final long cursor, final boolean fluidPhase) {
        this.scheduledTickCellCursor = cursor;
        this.scheduledTickFluidPhase = fluidPhase;
    }

    public synchronized void clearScheduledTickCellCursor() {
        this.scheduledTickCellCursor = NO_CONTINUATION_CURSOR;
        this.scheduledTickFluidPhase = false;
    }

    public synchronized long getChunkTickCursor() {
        return this.chunkTickCursor;
    }

    public synchronized void setChunkTickCursor(final long cursor) {
        this.chunkTickCursor = cursor;
    }

    public synchronized long getEntityTaskCursor() {
        return this.entityTaskCursor;
    }

    public synchronized void setEntityTaskCursor(final long cursor) {
        this.entityTaskCursor = cursor;
    }

    public synchronized long getEntityTickCursor() {
        return this.entityTickCursor;
    }

    public synchronized void setEntityTickCursor(final long cursor) {
        this.entityTickCursor = cursor;
    }

    public synchronized long getTrackerCursor() {
        return this.trackerCursor;
    }

    public synchronized void setTrackerCursor(final long cursor) {
        this.trackerCursor = cursor;
    }

    public synchronized long getPlayerTickCursor() {
        return this.playerTickCursor;
    }

    public synchronized void setPlayerTickCursor(final long cursor) {
        this.playerTickCursor = cursor;
    }

    public void tickTasks() {
        this.runtimeState.mailbox().runDue(RegionTickBudget.current());
    }

    public synchronized void addBlockEvent(BlockEventData blockEvent) {
        this.blockEvents.add(blockEvent);
    }

    public synchronized void addAllBlockEvents(Collection<BlockEventData> blockEvents) {
        this.blockEvents.addAll(blockEvents);
    }

    public synchronized boolean hasBlockEvents() {
        return !this.blockEvents.isEmpty();
    }

    public synchronized BlockEventData removeFirstBlockEvent() {
        return this.blockEvents.removeFirst();
    }

    public synchronized void removeBlockEventsIf(Predicate<BlockEventData> predicate) {
        this.blockEvents.removeIf(predicate);
    }

    public synchronized long removeFirstUnloadForCell(final RegionPos cell) {
        final LongIterator iterator = this.unloadQueue.iterator();
        while (iterator.hasNext()) {
            final long chunkKey = iterator.nextLong();
            if (RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)) == cell.longKey) {
                iterator.remove();
                return chunkKey;
            }
        }
        return Long.MIN_VALUE;
    }

    public synchronized Long2ObjectOpenHashMap<LongArrayList> removeUnloadsGroupedByCell(final int maxCount) {
        final Long2ObjectOpenHashMap<LongArrayList> chunksByCell = new Long2ObjectOpenHashMap<>();
        int removed = 0;
        final LongIterator iterator = this.unloadQueue.iterator();
        while (removed < maxCount && iterator.hasNext()) {
            final long chunkKey = iterator.nextLong();
            final long cellKey = RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey));
            chunksByCell.computeIfAbsent(cellKey, ignored -> new LongArrayList()).add(chunkKey);
            iterator.remove();
            removed++;
        }
        return chunksByCell;
    }

    public synchronized boolean canSplitOwner() {
        return this.isMergeQuiescent() && (this.redstoneUpdateInfos == null || this.redstoneUpdateInfos.isEmpty());
    }

    public synchronized LongOpenHashSet activeCellKeysSnapshot() {
        final LongOpenHashSet activeCells = new LongOpenHashSet();
        for (final LevelChunk levelChunk : this.levelChunks) {
            activeCells.add(RegionPos.asLongForChunk(levelChunk.getPos()));
        }
        for (final long chunkKey : this.playerTickingChunkRequests) {
            activeCells.add(RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        }

        final IteratorSafeOrderedReferenceSet.Iterator<Entity> tickingIterator = this.tickingEntities.iterator();
        try {
            while (tickingIterator.hasNext()) {
                activeCells.add(RegionPos.asLongForChunk(tickingIterator.next().chunkPosition()));
            }
        } finally {
            tickingIterator.finishedIterating();
        }

        for (final Entity entity : this.trackedEntities) {
            activeCells.add(RegionPos.asLongForChunk(entity.chunkPosition()));
        }
        for (final ServerPlayer player : this.players) {
            activeCells.add(RegionPos.asLongForChunk(player.chunkPosition()));
        }
        for (final long chunkKey : this.unloadQueue) {
            activeCells.add(RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)));
        }
        for (final TickingBlockEntity ticker : this.tickingBlockEntities) {
            activeCells.add(RegionPos.asLongForBlockPos(ticker.getPos()));
        }
        for (final TickingBlockEntity ticker : this.pendingBlockEntityTickers) {
            activeCells.add(RegionPos.asLongForBlockPos(ticker.getPos()));
        }
        for (final Mob mob : this.navigatingMobs) {
            activeCells.add(RegionPos.asLongForChunk(mob.chunkPosition()));
        }
        for (final BlockEventData blockEvent : this.blockEvents) {
            activeCells.add(RegionPos.asLongForBlockPos(blockEvent.pos()));
        }
        for (final long cellKey : this.owner.cellsSnapshot()) {
            final RegionPos cell = new RegionPos(cellKey);
            if (hasScheduledTicks(this.level.blockTicks, cell) || hasScheduledTicks(this.level.fluidTicks, cell)) {
                activeCells.add(cellKey);
            }
        }
        return activeCells;
    }

    private static boolean hasScheduledTicks(final Object ticks, final RegionPos cell) {
        return ticks instanceof LevelTicksRegionProxy<?> proxy && proxy.hasRegionData(cell);
    }

    public void extractCellsTo(final LevelChunkRegion target, final LongOpenHashSet splitCells) {
        if (this.level != target.level) {
            throw new IllegalArgumentException("Cannot split regions across worlds");
        }

        final List<Entity> tickingToMove = new ArrayList<>();
        synchronized (this) {
            final IteratorSafeOrderedReferenceSet.Iterator<Entity> tickingIterator = this.tickingEntities.iterator();
            try {
                while (tickingIterator.hasNext()) {
                    final Entity entity = tickingIterator.next();
                    if (splitCells.contains(RegionPos.asLongForChunk(entity.chunkPosition()))) {
                        tickingToMove.add(entity);
                    }
                }
            } finally {
                tickingIterator.finishedIterating();
            }

            synchronized (target) {
                for (final Iterator<LevelChunk> iterator = this.levelChunks.iterator(); iterator.hasNext();) {
                    final LevelChunk levelChunk = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForChunk(levelChunk.getPos()))) {
                        target.levelChunks.add(levelChunk);
                        iterator.remove();
                    }
                }

                for (final LongIterator iterator = this.playerTickingChunkRequests.iterator(); iterator.hasNext();) {
                    final long chunkKey = iterator.nextLong();
                    if (splitCells.contains(RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)))) {
                        target.playerTickingChunkRequests.add(chunkKey);
                        iterator.remove();
                    }
                }

                for (final Entity entity : tickingToMove) {
                    target.tickingEntities.add(entity);
                    this.tickingEntities.remove(entity);
                }

                for (final Iterator<Entity> iterator = this.trackedEntities.iterator(); iterator.hasNext();) {
                    final Entity entity = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForChunk(entity.chunkPosition()))) {
                        target.trackedEntities.add(entity);
                        iterator.remove();
                    }
                }

                for (final Iterator<ServerPlayer> iterator = this.players.iterator(); iterator.hasNext();) {
                    final ServerPlayer player = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForChunk(player.chunkPosition()))) {
                        target.players.add(player);
                        player.currentRegion = target;
                        iterator.remove();
                    }
                }

                for (final LongIterator iterator = this.unloadQueue.iterator(); iterator.hasNext();) {
                    final long chunkKey = iterator.nextLong();
                    if (splitCells.contains(RegionPos.asLongForChunk(ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey)))) {
                        target.unloadQueue.add(chunkKey);
                        iterator.remove();
                    }
                }

                for (final Iterator<TickingBlockEntity> iterator = this.tickingBlockEntities.iterator(); iterator.hasNext();) {
                    final TickingBlockEntity ticker = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForBlockPos(ticker.getPos()))) {
                        target.tickingBlockEntities.add(ticker);
                        iterator.remove();
                    }
                }
                for (final Iterator<TickingBlockEntity> iterator = this.pendingBlockEntityTickers.iterator(); iterator.hasNext();) {
                    final TickingBlockEntity ticker = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForBlockPos(ticker.getPos()))) {
                        target.pendingBlockEntityTickers.add(ticker);
                        iterator.remove();
                    }
                }

                for (final Iterator<Mob> iterator = this.navigatingMobs.iterator(); iterator.hasNext();) {
                    final Mob mob = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForChunk(mob.chunkPosition()))) {
                        target.navigatingMobs.add(mob);
                        iterator.remove();
                    }
                }

                for (final Iterator<BlockEventData> iterator = this.blockEvents.iterator(); iterator.hasNext();) {
                    final BlockEventData blockEvent = iterator.next();
                    if (splitCells.contains(RegionPos.asLongForBlockPos(blockEvent.pos()))) {
                        target.blockEvents.add(blockEvent);
                        iterator.remove();
                    }
                }
            }
        }
    }

    public boolean isEmpty() {
        return this.lastAccessTick < this.level.levelData.getGameTime() - 20
                && levelChunks.isEmpty()
                && playerTickingChunkRequests.isEmpty()
                && tickingEntities.size() == 0
                && !this.runtimeState.mailbox().hasPendingTasks()
                && internalTasks.getTotalTasksExecuted() >= internalTasks.getTotalTasksScheduled()
                && players.isEmpty()
                && unloadQueue.isEmpty()
                && tickingBlockEntities.isEmpty()
                && pendingBlockEntityTickers.isEmpty()
                && trackedEntities.isEmpty()
                && navigatingMobs.isEmpty()
                && blockEvents.isEmpty()
                ;
    }

    private record TickSample(long startNanos, long endNanos, long durationNanos, long previousStartNanos) {
    }

    public record TickStats(double tps, double mspt) {
    }
}
