package io.multipaper.shreddedpaper.threading.region;

import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.region.events.CrossRegionTaskEvent;
import io.multipaper.shreddedpaper.threading.region.events.RegionQueueEvent;
import net.minecraft.server.level.ServerLevel;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.LongPredicate;

public final class RegionMailbox {

    private static final Logger LOGGER = LogUtils.getClassLogger();
    private static final RegionTaskClass[] DRAIN_ORDER = {
            RegionTaskClass.CRITICAL_SYSTEM,
            RegionTaskClass.PLAYER_ACTION,
            RegionTaskClass.OWNER_HANDOFF,
            RegionTaskClass.CHUNK_IO_LOAD,
            RegionTaskClass.CHUNK_IO_SAVE,
            RegionTaskClass.PLUGIN,
            RegionTaskClass.TRACKER_BROADCAST,
            RegionTaskClass.EXPLOSION_PHYSICS
    };
    private static final RegionTaskClass[] ALL_CLASSES = RegionTaskClass.values();

    private final ServerLevel level;
    private final String worldName;
    private final RegionPos regionPos;
    private final long ownerId;
    private final LongSupplier ownerEpochSupplier;
    private final LongPredicate ownerOwnsCell;
    private final Queue<RegionTask> transferred = new ConcurrentLinkedQueue<>();
    private final Queue<RegionTask>[] ingress;
    private final AtomicInteger[] queuedByClass;
    private final int[] capacityByClass;
    private final AtomicLong[] rejectedByClass;
    private final AtomicLong[] failedByClass;
    private final PriorityQueue<RegionTask> delayed = new PriorityQueue<>();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private final AtomicInteger transferredDepth = new AtomicInteger();
    private final QueuePressureDiagnostics criticalSystemPressure = new QueuePressureDiagnostics();
    private final QueuePressureDiagnostics transferredPressure = new QueuePressureDiagnostics();
    private volatile long currentTick;

    public RegionMailbox(
            final ServerLevel level,
            final RegionPos regionPos,
            final long ownerId,
            final LongSupplier ownerEpochSupplier,
            final LongPredicate ownerOwnsCell
    ) {
        this(level, level.getWorld().getName(), regionPos, ownerId, ownerEpochSupplier, ownerOwnsCell);
    }

    RegionMailbox(
            final String worldName,
            final RegionPos regionPos,
            final long ownerId,
            final LongSupplier ownerEpochSupplier,
            final LongPredicate ownerOwnsCell
    ) {
        this(null, worldName, regionPos, ownerId, ownerEpochSupplier, ownerOwnsCell);
    }

    private RegionMailbox(
            final ServerLevel level,
            final String worldName,
            final RegionPos regionPos,
            final long ownerId,
            final LongSupplier ownerEpochSupplier,
            final LongPredicate ownerOwnsCell
    ) {
        this.level = level;
        this.worldName = worldName;
        this.regionPos = regionPos;
        this.ownerId = ownerId;
        this.ownerEpochSupplier = ownerEpochSupplier;
        this.ownerOwnsCell = ownerOwnsCell;
        this.ingress = createQueueArray();
        this.queuedByClass = new AtomicInteger[ALL_CLASSES.length];
        this.capacityByClass = new int[ALL_CLASSES.length];
        this.rejectedByClass = new AtomicLong[ALL_CLASSES.length];
        this.failedByClass = new AtomicLong[ALL_CLASSES.length];
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        for (final RegionTaskClass taskClass : ALL_CLASSES) {
            final int index = index(taskClass);
            final int capacity = this.capacityFor(config, taskClass);
            this.capacityByClass[index] = capacity;
            this.queuedByClass[index] = new AtomicInteger();
            this.rejectedByClass[index] = new AtomicLong();
            this.failedByClass[index] = new AtomicLong();
            this.ingress[index] = new MpscArrayQueue<>(capacity);
        }
    }

    @SuppressWarnings("unchecked")
    private static Queue<RegionTask>[] createQueueArray() {
        return (Queue<RegionTask>[]) new Queue<?>[ALL_CLASSES.length];
    }

    private static int index(final RegionTaskClass taskClass) {
        return taskClass.ordinal();
    }

    private void recordQueuePressurePeak(
            final QueuePressureDiagnostics diagnostics,
            final int queued,
            final RegionTaskClass taskClass,
            final RegionPos affinityRegionPos
    ) {
        int peak;
        do {
            peak = diagnostics.peakDepth.get();
            if (queued <= peak) {
                return;
            }
        } while (!diagnostics.peakDepth.compareAndSet(peak, queued));
        diagnostics.peakProducerContext = this.producerContext(taskClass, affinityRegionPos);
    }

    private String producerContext(final RegionTaskClass taskClass, final RegionPos affinityRegionPos) {
        final LevelChunkRegion sourceRegion = ShreddedPaperChunkTicker.currentlyTickingRegion();
        if (sourceRegion == null) {
            return "thread=%s taskClass=%s affinity=[%d,%d]".formatted(
                    Thread.currentThread().getName(),
                    taskClass,
                    affinityRegionPos.x,
                    affinityRegionPos.z
            );
        }
        final RegionPos sourcePrimary = sourceRegion.getOwner().primaryCell();
        return "thread=%s source=%s[%d,%d] owner=%d taskClass=%s affinity=[%d,%d]".formatted(
                Thread.currentThread().getName(),
                sourceRegion.getLevel().getWorld().getName(),
                sourcePrimary.x,
                sourcePrimary.z,
                sourceRegion.getOwner().id(),
                taskClass,
                affinityRegionPos.x,
                affinityRegionPos.z
        );
    }

    public boolean offer(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks) {
        return this.offer(taskClass, runnable, delayTicks, this.regionPos);
    }

    public boolean offerTransferred(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks, final RegionPos affinityRegionPos) {
        final long normalizedDelayTicks = Math.max(1L, delayTicks);
        final int queuedAfter = this.reserveTransferSlot(taskClass);
        if (queuedAfter < 0) {
            this.reject(taskClass, "the transferred mailbox capacity is full");
            return false;
        }
        final int transferredQueued = this.transferredDepth.incrementAndGet();
        this.recordQueuePressurePeak(this.transferredPressure, transferredQueued, taskClass, affinityRegionPos);
        final int capacity = this.capacityByClass[index(taskClass)];
        final long readyTick = this.currentTick + normalizedDelayTicks;
        this.transferred.offer(new RegionTask(taskClass, runnable, readyTick, this.ownerId, this.ownerEpochSupplier.getAsLong(), affinityRegionPos.longKey, System.nanoTime()));
        if (this.shouldCommitCrossRegionTaskEvent(true, queuedAfter, capacity)) {
            this.commitCrossRegionTaskEvent(
                    "transferred",
                    taskClass,
                    affinityRegionPos.x,
                    affinityRegionPos.z,
                    normalizedDelayTicks,
                    "",
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE,
                    Long.MIN_VALUE,
                    Long.MIN_VALUE,
                    this.ownerEpochSupplier.getAsLong(),
                    false,
                    true,
                    false,
                    false,
                    queuedAfter,
                    capacity
            );
        }
        return true;
    }

    public boolean offerNonDropping(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks, final RegionPos affinityRegionPos) {
        if (taskClass == RegionTaskClass.CRITICAL_SYSTEM) {
            return this.offer(taskClass, runnable, delayTicks, affinityRegionPos);
        }

        final long normalizedDelayTicks = Math.max(1L, delayTicks);
        final int queuedAfterReserve = this.reserveSlot(taskClass);
        if (queuedAfterReserve < 0) {
            return this.offerTransferred(taskClass, runnable, normalizedDelayTicks, affinityRegionPos);
        }

        final long readyTick = this.currentTick + normalizedDelayTicks;
        final RegionTask task = new RegionTask(taskClass, runnable, readyTick, this.ownerId, this.ownerEpochSupplier.getAsLong(), affinityRegionPos.longKey, 0L);
        final boolean accepted = this.ingress[index(taskClass)].offer(task);
        if (accepted) {
            return true;
        }

        this.releaseSlot(taskClass);
        return this.offerTransferred(taskClass, runnable, normalizedDelayTicks, affinityRegionPos);
    }

    public boolean offer(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks, final RegionPos affinityRegionPos) {
        final long normalizedDelayTicks = Math.max(1L, delayTicks);
        final LevelChunkRegion sourceRegion = ShreddedPaperChunkTicker.currentlyTickingRegion();
        final boolean hasSourceRegion = sourceRegion != null;
        final boolean sameWorldSource = hasSourceRegion && this.level != null && this.level.equals(sourceRegion.getLevel());
        final long sourceOwnerId = hasSourceRegion ? sourceRegion.getOwner().id() : Long.MIN_VALUE;
        final boolean sameOwnerSource = sameWorldSource && sourceOwnerId == this.ownerId;
        final boolean crossRegion = hasSourceRegion && !sameOwnerSource;
        final RegionPos sourcePrimary = hasSourceRegion ? sourceRegion.getOwner().primaryCell() : null;
        final String sourceWorld = hasSourceRegion ? sourceRegion.getLevel().getWorld().getName() : "";
        final int sourceRegionX = sourcePrimary == null ? Integer.MIN_VALUE : sourcePrimary.x;
        final int sourceRegionZ = sourcePrimary == null ? Integer.MIN_VALUE : sourcePrimary.z;
        final long sourceOwnerEpoch = hasSourceRegion ? sourceRegion.getOwner().layoutEpoch() : Long.MIN_VALUE;
        final long targetOwnerEpoch = this.ownerEpochSupplier.getAsLong();
        final int capacity = this.capacityByClass[index(taskClass)];

        final int queuedAfterReserve = this.reserveSlot(taskClass);
        if (queuedAfterReserve < 0) {
            if (crossRegion) {
                this.commitCrossRegionTaskEvent(
                        "rejected",
                        taskClass,
                        affinityRegionPos.x,
                        affinityRegionPos.z,
                        normalizedDelayTicks,
                        sourceWorld,
                        sourceRegionX,
                        sourceRegionZ,
                        sourceOwnerId,
                        sourceOwnerEpoch,
                        targetOwnerEpoch,
                        hasSourceRegion,
                        crossRegion,
                        sameWorldSource,
                        sameOwnerSource,
                        capacity,
                        capacity
                );
            }
            this.reject(taskClass, "per-class capacity is full");
            return false;
        }
        if (taskClass == RegionTaskClass.CRITICAL_SYSTEM) {
            this.recordQueuePressurePeak(this.criticalSystemPressure, queuedAfterReserve, taskClass, affinityRegionPos);
        }

        final long readyTick = this.currentTick + normalizedDelayTicks;
        final RegionTask task = new RegionTask(
                taskClass,
                runnable,
                readyTick,
                this.ownerId,
                targetOwnerEpoch,
                affinityRegionPos.longKey,
                taskClass == RegionTaskClass.CRITICAL_SYSTEM ? System.nanoTime() : 0L
        );
        final boolean accepted = this.ingress[index(taskClass)].offer(task);
        if (!accepted) {
            this.releaseSlot(taskClass);
            if (crossRegion) {
                this.commitCrossRegionTaskEvent(
                        "rejected",
                        taskClass,
                        affinityRegionPos.x,
                        affinityRegionPos.z,
                        normalizedDelayTicks,
                        sourceWorld,
                        sourceRegionX,
                        sourceRegionZ,
                        sourceOwnerId,
                        sourceOwnerEpoch,
                        targetOwnerEpoch,
                        hasSourceRegion,
                        crossRegion,
                        sameWorldSource,
                        sameOwnerSource,
                        Math.max(0, queuedAfterReserve - 1),
                        capacity
                );
            }
            this.reject(taskClass, "the ingress queue is full");
        } else if (this.shouldCommitCrossRegionTaskEvent(crossRegion, queuedAfterReserve, capacity)) {
            this.commitCrossRegionTaskEvent(
                    "enqueued",
                    taskClass,
                    affinityRegionPos.x,
                    affinityRegionPos.z,
                    normalizedDelayTicks,
                    sourceWorld,
                    sourceRegionX,
                    sourceRegionZ,
                    sourceOwnerId,
                    sourceOwnerEpoch,
                    targetOwnerEpoch,
                    hasSourceRegion,
                    crossRegion,
                    sameWorldSource,
                    sameOwnerSource,
                    queuedAfterReserve,
                    capacity
            );
        }
        return accepted;
    }

    private int capacityFor(final ShreddedPaperConfiguration.Multithreading config, final RegionTaskClass taskClass) {
        final int configured = switch (taskClass) {
            case CRITICAL_SYSTEM -> config.criticalRegionMailboxCapacity;
            case PLAYER_ACTION -> config.playerActionRegionMailboxCapacity;
            case OWNER_HANDOFF -> config.ownerHandoffRegionMailboxCapacity;
            case CHUNK_IO_LOAD -> config.chunkIoLoadRegionMailboxCapacity;
            case CHUNK_IO_SAVE -> config.chunkIoSaveRegionMailboxCapacity;
            case PLUGIN -> config.pluginRegionMailboxCapacity;
            case TRACKER_BROADCAST -> config.trackerBroadcastRegionMailboxCapacity;
            case EXPLOSION_PHYSICS -> config.explosionPhysicsRegionMailboxCapacity;
        };
        return Math.max(16, configured < 0 ? config.regionMailboxCapacity : configured);
    }

    private int reserveSlot(final RegionTaskClass taskClass) {
        final AtomicInteger queued = this.queuedByClass[index(taskClass)];
        final int capacity = this.capacityByClass[index(taskClass)];
        int current;
        do {
            current = queued.get();
            if (current >= capacity) {
                return -1;
            }
        } while (!queued.compareAndSet(current, current + 1));
        return current + 1;
    }

    private boolean shouldCommitCrossRegionTaskEvent(final boolean crossRegion, final int queuedAfter, final int capacity) {
        if (!crossRegion || queuedAfter <= 0) {
            return false;
        }

        final int halfCapacity = Math.max(1, capacity / 2);
        return queuedAfter == 1
                || queuedAfter == halfCapacity
                || queuedAfter == capacity
                || queuedAfter == capacity + 1
                || (queuedAfter > capacity && ((queuedAfter - capacity) & 255) == 0);
    }

    private int reserveTransferSlot(final RegionTaskClass taskClass) {
        final AtomicInteger queued = this.queuedByClass[index(taskClass)];
        final int capacity = this.capacityByClass[index(taskClass)];
        int current;
        do {
            current = queued.get();
            if (current >= capacity) {
                return -1;
            }
        } while (!queued.compareAndSet(current, current + 1));
        return current + 1;
    }

    private void releaseSlot(final RegionTaskClass taskClass) {
        final int remaining = this.queuedByClass[index(taskClass)].decrementAndGet();
        if (remaining < 0) {
            this.queuedByClass[index(taskClass)].compareAndSet(remaining, 0);
            LOGGER.error("Region mailbox accounting underflow for {} {} {}", taskClass, this.worldName, this.regionPos);
        }
    }

    private void reject(final RegionTaskClass taskClass, final String reason) {
        final long rejectedForClass = this.rejectedByClass[index(taskClass)].incrementAndGet();
        final long rejectedTotal = this.rejected.incrementAndGet();
        if (rejectedForClass == 1L || (rejectedForClass & 255L) == 0L) {
            this.commitQueueEvent("rejected", taskClass);
            LOGGER.warn(
                    "Rejected {} region task for {} {} because {} (queued={}/{} rejectedClass={} rejectedTotal={})",
                    taskClass,
                    this.worldName,
                    this.regionPos,
                    reason,
                    this.queuedByClass[index(taskClass)].get(),
                    this.capacityByClass[index(taskClass)],
                    rejectedForClass,
                    rejectedTotal
            );
        }
    }

    public int runDue(final RegionTickBudget budget) {
        this.currentTick++;
        int ran = 0;
        do {
            final int beforeRound = ran;
            ran += this.drainTransferred(budget, 32);
            ran += this.drainDelayed(budget, 64);
            if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                return ran;
            }

            for (final RegionTaskClass taskClass : DRAIN_ORDER) {
                final int drained = this.drainIngress(taskClass, budget, this.drainQuantum(taskClass));
                ran += drained;
                if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                    return ran;
                }
            }

            ran += this.drainDelayed(budget, 64);
            if (budget == null || ran == beforeRound) {
                break;
            }
        } while (budget.canContinue(RegionWorkType.REGION_TASK));
        return ran;
    }

    private int drainTransferred(final RegionTickBudget budget, final int maxTasks) {
        int ran = 0;
        RegionTask task;
        while (ran < maxTasks && (task = this.transferred.poll()) != null) {
            this.decrementTransferredDepth();
            if (task.readyTick() > this.currentTick) {
                this.delayed.add(task);
                continue;
            }
            if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                this.delayed.add(task);
                break;
            }
            this.runTask(task);
            ran++;
        }
        return ran;
    }

    private void decrementTransferredDepth() {
        final int remaining = this.transferredDepth.decrementAndGet();
        if (remaining < 0) {
            this.transferredDepth.compareAndSet(remaining, 0);
            LOGGER.error("Transferred mailbox accounting underflow for {} {}", this.worldName, this.regionPos);
        }
    }

    private int drainDelayed(final RegionTickBudget budget, final int maxTasks) {
        int ran = 0;
        RegionTask task;
        while (ran < maxTasks && (task = this.delayed.peek()) != null && task.readyTick() <= this.currentTick) {
            if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                break;
            }
            this.delayed.poll();
            this.runTask(task);
            ran++;
        }
        return ran;
    }

    private int drainQuantum(final RegionTaskClass taskClass) {
        return switch (taskClass) {
            case CRITICAL_SYSTEM -> 64;
            case CHUNK_IO_LOAD -> 8;
            case CHUNK_IO_SAVE -> 4;
            case PLAYER_ACTION, OWNER_HANDOFF, TRACKER_BROADCAST, EXPLOSION_PHYSICS -> 32;
            case PLUGIN -> 16;
        };
    }

    private int drainIngress(final RegionTaskClass taskClass, final RegionTickBudget budget, final int maxTasks) {
        int ran = 0;
        final Queue<RegionTask> queue = this.ingress[index(taskClass)];
        RegionTask task;
        for (int remaining = maxTasks; remaining > 0 && (task = queue.poll()) != null; remaining--) {
            if (task.readyTick() > this.currentTick) {
                this.delayed.add(task);
                continue;
            }
            if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                this.delayed.add(task);
                break;
            }
            this.runTask(task);
            ran++;
        }
        return ran;
    }

    private void runTask(final RegionTask task) {
        if (this.redirectIfOwnerMoved(task)) {
            return;
        }

        this.releaseSlot(task.taskClass());
        try {
            task.run();
            this.executed.incrementAndGet();
        } catch (final Throwable throwable) {
            final long failedForClass = this.failedByClass[index(task.taskClass())].incrementAndGet();
            if (failedForClass == 1L || (failedForClass & 255L) == 0L) {
                LOGGER.error(
                        "Error while executing {} region task in {} {} (failureClass={}); further failures are sampled",
                        task.taskClass(),
                        this.worldName,
                        this.regionPos,
                        failedForClass,
                        throwable
                );
            }
        }
    }

    private boolean redirectIfOwnerMoved(final RegionTask task) {
        if (task.targetOwnerId() == this.ownerId && task.targetOwnerEpoch() == this.ownerEpochSupplier.getAsLong()) {
            return false;
        }
        if (task.targetOwnerId() == this.ownerId && this.ownerOwnsCell.test(task.affinityCellKey())) {
            return false;
        }

        this.releaseSlot(task.taskClass());
        final RegionPos affinityRegionPos = new RegionPos(task.affinityCellKey());
        if (this.level == null) {
            throw new IllegalStateException("Cannot redirect stale-owner task without a backing ServerLevel");
        }
        this.level.chunkSource.tickingRegions.scheduleTransferredTask(affinityRegionPos, task, 0L, task.taskClass());
        return true;
    }

    public boolean hasPendingTasks() {
        for (final AtomicInteger queued : this.queuedByClass) {
            if (queued.get() > 0) {
                return true;
            }
        }
        return false;
    }

    public int depth() {
        int depth = 0;
        for (final AtomicInteger queued : this.queuedByClass) {
            depth += Math.max(0, queued.get());
        }
        return depth;
    }

    public PressureDiagnostics pressureDiagnostics() {
        final long now = System.nanoTime();
        return new PressureDiagnostics(
                new QueuePressureSnapshot(
                        this.queued(RegionTaskClass.CRITICAL_SYSTEM),
                        this.criticalSystemPressure.peakDepth.get(),
                        this.oldestAgeNanos(this.ingress[index(RegionTaskClass.CRITICAL_SYSTEM)].peek(), now),
                        this.criticalSystemPressure.peakProducerContext
                ),
                new QueuePressureSnapshot(
                        Math.max(0, this.transferredDepth.get()),
                        this.transferredPressure.peakDepth.get(),
                        this.oldestAgeNanos(this.transferred.peek(), now),
                        this.transferredPressure.peakProducerContext
                )
        );
    }

    private long oldestAgeNanos(final RegionTask task, final long now) {
        if (task == null || task.enqueueNanos() <= 0L) {
            return 0L;
        }
        return Math.max(0L, now - task.enqueueNanos());
    }

    public int queued(final RegionTaskClass taskClass) {
        return Math.max(0, this.queuedByClass[index(taskClass)].get());
    }

    public int capacity(final RegionTaskClass taskClass) {
        return this.capacityByClass[index(taskClass)];
    }

    public double maxClassPressure() {
        double pressure = 0.0D;
        for (final RegionTaskClass taskClass : ALL_CLASSES) {
            pressure = Math.max(pressure, this.queued(taskClass) / (double) Math.max(1, this.capacity(taskClass)));
        }
        return pressure;
    }

    public long rejected() {
        return this.rejected.get();
    }

    public long executed() {
        return this.executed.get();
    }

    private void commitQueueEvent(final String action, final RegionTaskClass taskClass) {
        if (!RegionQueueEvent.isEventEnabled()) {
            return;
        }
        final RegionQueueEvent event = new RegionQueueEvent();
        event.world = this.worldName;
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.action = action;
        event.taskClass = taskClass.name();
        event.depth = this.depth();
        event.queuedForClass = this.queuedByClass[index(taskClass)].get();
        event.capacity = this.capacityByClass[index(taskClass)];
        event.rejected = this.rejected.get();
        event.rejectedForClass = this.rejectedByClass[index(taskClass)].get();
        event.commit();
    }

    private void commitCrossRegionTaskEvent(
            final String action,
            final RegionTaskClass taskClass,
            final int affinityRegionX,
            final int affinityRegionZ,
            final long delayTicks,
            final String sourceWorld,
            final int sourceRegionX,
            final int sourceRegionZ,
            final long sourceOwnerId,
            final long sourceOwnerEpoch,
            final long targetOwnerEpoch,
            final boolean hasSourceRegion,
            final boolean crossRegion,
            final boolean sameWorld,
            final boolean sameOwner,
            final int queuedAfter,
            final int capacity
    ) {
        if (!CrossRegionTaskEvent.isEventEnabled()) {
            return;
        }
        final CrossRegionTaskEvent event = new CrossRegionTaskEvent();
        event.world = this.worldName;
        event.sourceWorld = sourceWorld;
        event.sourceRegionX = sourceRegionX;
        event.sourceRegionZ = sourceRegionZ;
        event.sourceOwnerId = sourceOwnerId;
        event.sourceOwnerEpoch = sourceOwnerEpoch;
        event.targetRegionX = this.regionPos.x;
        event.targetRegionZ = this.regionPos.z;
        event.targetOwnerId = this.ownerId;
        event.targetOwnerEpoch = targetOwnerEpoch;
        event.taskClass = taskClass.name();
        event.affinityRegionX = affinityRegionX;
        event.affinityRegionZ = affinityRegionZ;
        event.affinityCellKey = RegionPos.asLong(affinityRegionX, affinityRegionZ);
        event.action = action;
        event.delayClock = "region-local";
        event.delayTicks = delayTicks;
        event.queuedAfter = queuedAfter;
        event.capacity = capacity;
        event.hasSourceRegion = hasSourceRegion;
        event.crossRegion = crossRegion;
        event.sameWorld = sameWorld;
        event.sameOwner = sameOwner;
        event.thread = Thread.currentThread().getName();
        event.commit();
    }

    private static final class QueuePressureDiagnostics {
        private final AtomicInteger peakDepth = new AtomicInteger();
        private volatile String peakProducerContext = "";
    }

    public record PressureDiagnostics(
            QueuePressureSnapshot criticalSystem,
            QueuePressureSnapshot transferred
    ) {
    }

    public record QueuePressureSnapshot(
            int queued,
            int peakDepth,
            long oldestAgeNanos,
            String peakProducerContext
    ) {
    }
}
