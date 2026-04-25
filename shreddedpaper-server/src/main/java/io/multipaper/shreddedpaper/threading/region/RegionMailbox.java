package io.multipaper.shreddedpaper.threading.region;

import com.mojang.logging.LogUtils;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.region.events.RegionQueueEvent;
import net.minecraft.server.level.ServerLevel;
import org.jctools.queues.MpscArrayQueue;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionMailbox {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final ServerLevel level;
    private final RegionPos regionPos;
    private final Map<RegionTaskClass, Queue<RegionTask>> ingress = new EnumMap<>(RegionTaskClass.class);
    private final Map<RegionTaskClass, AtomicInteger> queuedByClass = new EnumMap<>(RegionTaskClass.class);
    private final Map<RegionTaskClass, Integer> capacityByClass = new EnumMap<>(RegionTaskClass.class);
    private final Map<RegionTaskClass, AtomicLong> rejectedByClass = new EnumMap<>(RegionTaskClass.class);
    private final Map<RegionTaskClass, AtomicLong> failedByClass = new EnumMap<>(RegionTaskClass.class);
    private final PriorityQueue<RegionTask> delayed = new PriorityQueue<>();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private volatile long currentTick;

    public RegionMailbox(final ServerLevel level, final RegionPos regionPos) {
        this.level = level;
        this.regionPos = regionPos;
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        for (final RegionTaskClass taskClass : RegionTaskClass.values()) {
            final int capacity = this.capacityFor(config, taskClass);
            this.capacityByClass.put(taskClass, capacity);
            this.queuedByClass.put(taskClass, new AtomicInteger());
            this.rejectedByClass.put(taskClass, new AtomicLong());
            this.failedByClass.put(taskClass, new AtomicLong());
            this.ingress.put(taskClass, taskClass == RegionTaskClass.CRITICAL_SYSTEM
                    ? new ConcurrentLinkedQueue<>()
                    : new MpscArrayQueue<>(capacity));
        }
    }

    public boolean offer(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks) {
        if (!this.reserveSlot(taskClass)) {
            this.reject(taskClass, "per-class capacity is full");
            return false;
        }

        final long readyTick = this.currentTick + Math.max(1L, delayTicks);
        final RegionTask task = new RegionTask(taskClass, runnable, readyTick);
        final boolean accepted = this.ingress.get(taskClass).offer(task);
        if (!accepted) {
            this.releaseSlot(taskClass);
            this.reject(taskClass, "the ingress queue is full");
        }
        return accepted;
    }

    private int capacityFor(final ShreddedPaperConfiguration.Multithreading config, final RegionTaskClass taskClass) {
        final int configured = switch (taskClass) {
            case CRITICAL_SYSTEM -> config.criticalRegionMailboxCapacity;
            case PLAYER_ACTION -> config.playerActionRegionMailboxCapacity;
            case PLUGIN -> config.pluginRegionMailboxCapacity;
            case TRACKER_BROADCAST -> config.trackerBroadcastRegionMailboxCapacity;
            case EXPLOSION_PHYSICS -> config.explosionPhysicsRegionMailboxCapacity;
        };
        return Math.max(16, configured < 0 ? config.regionMailboxCapacity : configured);
    }

    private boolean reserveSlot(final RegionTaskClass taskClass) {
        final AtomicInteger queued = this.queuedByClass.get(taskClass);
        final int capacity = this.capacityByClass.get(taskClass);
        if (taskClass == RegionTaskClass.CRITICAL_SYSTEM) {
            final int current = queued.incrementAndGet();
            if (current > capacity) {
                this.recordCriticalOverReserve(current, capacity);
            }
            return true;
        }

        int current;
        do {
            current = queued.get();
            if (current >= capacity) {
                return false;
            }
        } while (!queued.compareAndSet(current, current + 1));
        return true;
    }

    private void releaseSlot(final RegionTaskClass taskClass) {
        final int remaining = this.queuedByClass.get(taskClass).decrementAndGet();
        if (remaining < 0) {
            this.queuedByClass.get(taskClass).compareAndSet(remaining, 0);
            LOGGER.error("Region mailbox accounting underflow for {} {} {}", taskClass, this.level.getWorld().getName(), this.regionPos);
        }
    }

    private void reject(final RegionTaskClass taskClass, final String reason) {
        final long rejectedForClass = this.rejectedByClass.get(taskClass).incrementAndGet();
        final long rejectedTotal = this.rejected.incrementAndGet();
        if (rejectedForClass == 1L || (rejectedForClass & 255L) == 0L) {
            this.commitQueueEvent("rejected", taskClass, this.depth());
            LOGGER.warn(
                    "Rejected {} region task for {} {} because {} (queued={}/{} rejectedClass={} rejectedTotal={})",
                    taskClass,
                    this.level.getWorld().getName(),
                    this.regionPos,
                    reason,
                    this.queuedByClass.get(taskClass).get(),
                    this.capacityByClass.get(taskClass),
                    rejectedForClass,
                    rejectedTotal
            );
        }
    }

    private void recordCriticalOverReserve(final int queued, final int capacity) {
        final int overReserve = queued - capacity;
        if (overReserve == 1 || (overReserve & 255) == 0) {
            this.commitQueueEvent("critical-over-reserve", RegionTaskClass.CRITICAL_SYSTEM, this.depth());
            LOGGER.warn(
                    "Critical region mailbox reserve exceeded for {} {} (queued={} reserve={}); critical work remains non-dropping",
                    this.level.getWorld().getName(),
                    this.regionPos,
                    queued,
                    capacity
            );
        }
    }

    public int runDue(final RegionTickBudget budget) {
        this.currentTick++;
        int ran = 0;
        for (final RegionTaskClass taskClass : RegionTaskClass.values()) {
            ran += this.drainIngress(taskClass, budget);
        }

        RegionTask task;
        while ((task = this.delayed.peek()) != null && task.readyTick() <= this.currentTick) {
            if (budget != null && !budget.canContinue(RegionWorkType.REGION_TASK)) {
                break;
            }
            this.delayed.poll();
            this.runTask(task);
            ran++;
        }
        return ran;
    }

    private int drainIngress(final RegionTaskClass taskClass, final RegionTickBudget budget) {
        int ran = 0;
        final Queue<RegionTask> queue = this.ingress.get(taskClass);
        RegionTask task;
        for (int remaining = queue.size(); remaining > 0 && (task = queue.poll()) != null; remaining--) {
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
        this.releaseSlot(task.taskClass());
        try {
            task.run();
            this.executed.incrementAndGet();
        } catch (final Throwable throwable) {
            final long failedForClass = this.failedByClass.get(task.taskClass()).incrementAndGet();
            if (failedForClass == 1L || (failedForClass & 255L) == 0L) {
                LOGGER.error(
                        "Error while executing {} region task in {} {} (failureClass={}); further failures are sampled",
                        task.taskClass(),
                        this.level.getWorld().getName(),
                        this.regionPos,
                        failedForClass,
                        throwable
                );
            }
        }
    }

    public boolean hasPendingTasks() {
        for (final AtomicInteger queued : this.queuedByClass.values()) {
            if (queued.get() > 0) {
                return true;
            }
        }
        return false;
    }

    public int depth() {
        int depth = 0;
        for (final AtomicInteger queued : this.queuedByClass.values()) {
            depth += Math.max(0, queued.get());
        }
        return depth;
    }

    public long rejected() {
        return this.rejected.get();
    }

    public long executed() {
        return this.executed.get();
    }

    private void commitQueueEvent(final String action, final RegionTaskClass taskClass, final int depth) {
        final RegionQueueEvent event = new RegionQueueEvent();
        event.world = this.level.getWorld().getName();
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.action = action;
        event.taskClass = taskClass.name();
        event.depth = depth;
        event.queuedForClass = this.queuedByClass.get(taskClass).get();
        event.capacity = this.capacityByClass.get(taskClass);
        event.rejected = this.rejected.get();
        event.rejectedForClass = this.rejectedByClass.get(taskClass).get();
        event.commit();
    }
}
