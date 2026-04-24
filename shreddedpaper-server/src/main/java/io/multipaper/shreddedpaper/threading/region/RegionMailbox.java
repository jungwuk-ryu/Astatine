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
import java.util.concurrent.atomic.LongAdder;

public final class RegionMailbox {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final ServerLevel level;
    private final RegionPos regionPos;
    private final Map<RegionTaskClass, Queue<RegionTask>> ingress = new EnumMap<>(RegionTaskClass.class);
    private final PriorityQueue<RegionTask> delayed = new PriorityQueue<>();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder executed = new LongAdder();
    private volatile long currentTick;

    public RegionMailbox(final ServerLevel level, final RegionPos regionPos) {
        this.level = level;
        this.regionPos = regionPos;
        final ShreddedPaperConfiguration.Multithreading config = ShreddedPaperConfiguration.get().multithreading;
        for (final RegionTaskClass taskClass : RegionTaskClass.values()) {
            if (taskClass == RegionTaskClass.CRITICAL_SYSTEM) {
                this.ingress.put(taskClass, new ConcurrentLinkedQueue<>());
            } else {
                this.ingress.put(taskClass, new MpscArrayQueue<>(Math.max(16, config.regionMailboxCapacity)));
            }
        }
    }

    public boolean offer(final RegionTaskClass taskClass, final Runnable runnable, final long delayTicks) {
        final long readyTick = this.currentTick + Math.max(1L, delayTicks);
        final RegionTask task = new RegionTask(taskClass, runnable, readyTick);
        final boolean accepted = this.ingress.get(taskClass).offer(task);
        if (!accepted) {
            this.rejected.increment();
            this.commitQueueEvent("rejected", taskClass, this.depth());
            LOGGER.warn(
                    "Rejected {} region task for {} {} because the bounded mailbox is full",
                    taskClass,
                    this.level.getWorld().getName(),
                    this.regionPos
            );
        }
        return accepted;
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
        try {
            task.run();
            this.executed.increment();
        } catch (final Throwable throwable) {
            LOGGER.error("Error while executing {} region task in {} {}", task.taskClass(), this.level.getWorld().getName(), this.regionPos, throwable);
        }
    }

    public boolean hasPendingTasks() {
        if (!this.delayed.isEmpty()) {
            return true;
        }
        for (final Queue<RegionTask> queue : this.ingress.values()) {
            if (!queue.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public int depth() {
        int depth = this.delayed.size();
        for (final Queue<RegionTask> queue : this.ingress.values()) {
            depth += queue.size();
        }
        return depth;
    }

    public long rejected() {
        return this.rejected.sum();
    }

    public long executed() {
        return this.executed.sum();
    }

    private void commitQueueEvent(final String action, final RegionTaskClass taskClass, final int depth) {
        final RegionQueueEvent event = new RegionQueueEvent();
        event.world = this.level.getWorld().getName();
        event.regionX = this.regionPos.x;
        event.regionZ = this.regionPos.z;
        event.action = action;
        event.taskClass = taskClass.name();
        event.depth = depth;
        event.rejected = this.rejected.sum();
        event.commit();
    }
}
