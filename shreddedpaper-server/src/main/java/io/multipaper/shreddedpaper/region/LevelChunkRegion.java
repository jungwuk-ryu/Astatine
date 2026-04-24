package io.multipaper.shreddedpaper.region;

import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import ca.spottedleaf.moonrise.common.list.IteratorSafeOrderedReferenceSet;
import io.multipaper.shreddedpaper.threading.region.RegionMailbox;
import io.multipaper.shreddedpaper.threading.region.RegionOverloadController;
import io.multipaper.shreddedpaper.threading.region.RegionRuntimeState;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import io.multipaper.shreddedpaper.threading.region.RegionTickBudget;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class LevelChunkRegion {

    private static final long NO_PREVIOUS_TICK = Long.MIN_VALUE;
    private static final long TICK_STATS_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private final ServerLevel level;
    private final RegionOwner owner;
    private final RegionPos regionPos;
    private final List<LevelChunk> levelChunks = new ArrayList<>(RegionPos.REGION_SIZE * RegionPos.REGION_SIZE);
    private final LongOpenHashSet playerTickingChunkRequests = new LongOpenHashSet(); // ChunkPos.longKey
    private final IteratorSafeOrderedReferenceSet<Entity> tickingEntities = new IteratorSafeOrderedReferenceSet<>(); // Use IteratorSafeOrderedReferenceSet to maintain entity tick order
    private final Set<Entity> trackedEntities = new ObjectOpenHashSet<>();
    private final RegionRuntimeState runtimeState;
    private final PrioritisedTaskQueue internalTasks = new PrioritisedTaskQueue(); // Read-only tasks
    private final ObjectOpenHashSet<ServerPlayer> players = new ObjectOpenHashSet<>();
    public final LongLinkedOpenHashSet unloadQueue = new LongLinkedOpenHashSet();
    public final List<TickingBlockEntity> tickingBlockEntities = new ReferenceArrayList<>();
    public final List<TickingBlockEntity> pendingBlockEntityTickers = new ReferenceArrayList<>();
    private final ObjectOpenHashSet<Mob> navigatingMobs = new ObjectOpenHashSet<>();
    private final ObjectLinkedOpenHashSet<BlockEventData> blockEvents = new ObjectLinkedOpenHashSet<>();
    private final ArrayDeque<TickSample> tickSamples = new ArrayDeque<>();
    private long lastTickStatsStartNanos = NO_PREVIOUS_TICK;
    private volatile long lastAccessTick;
    public ArrayDeque<RedstoneTorchBlock.Toggle> redstoneUpdateInfos;

    public LevelChunkRegion(ServerLevel level, RegionOwner owner) {
        owner.requireSingleCell("LevelChunkRegion construction");
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

    public boolean isPlayerTickingRequested(final ChunkPos chunkPos) {
        if (chunkPos.getRegionPos().toLong() != this.regionPos.toLong()) {
            throw new IllegalStateException("Chunk %s is not in region %s".formatted(chunkPos, this.regionPos));
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
        return this.runtimeState.mailbox().offer(taskClass, task, delay);
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

    public void tickTasks() {
        this.runtimeState.mailbox().runDue(RegionTickBudget.current());
    }

    public synchronized void addBlockEvent(BlockEventData blockEvent) {
        this.blockEvents.add(blockEvent);
    }

    public synchronized void addAllBlockEvents(Collection<BlockEventData> blockEvents) {
        this.blockEvents.addAll(blockEvents);
    }

    public boolean hasBlockEvents() {
        return !this.blockEvents.isEmpty();
    }

    public synchronized BlockEventData removeFirstBlockEvent() {
        return this.blockEvents.removeFirst();
    }

    public synchronized void removeBlockEventsIf(Predicate<BlockEventData> predicate) {
        this.blockEvents.removeIf(predicate);
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
