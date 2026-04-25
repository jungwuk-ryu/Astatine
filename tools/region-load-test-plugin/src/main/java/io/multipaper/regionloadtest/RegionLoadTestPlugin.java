package io.multipaper.regionloadtest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionLoadTestPlugin extends JavaPlugin {

    private final Map<UUID, ManagedEntity> managedEntities = new ConcurrentHashMap<>();
    private final Set<ScheduledTask> managedTasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cleanupRequested = new AtomicBoolean();
    private final AtomicLong batchGeneration = new AtomicLong();
    private final AtomicLong busySink = new AtomicLong();

    @Override
    public void onEnable() {
        final RegionLoadTestCommand command = new RegionLoadTestCommand(this);
        if (this.getCommand("regionloadtest") == null) {
            throw new IllegalStateException("regionloadtest command missing from plugin.yml");
        }
        this.getCommand("regionloadtest").setExecutor(command);
        this.getCommand("regionloadtest").setTabCompleter(command);
    }

    @Override
    public void onDisable() {
        this.cleanupAll(null);
    }

    public void trackEntity(final Entity entity) {
        this.managedEntities.put(entity.getUniqueId(), new ManagedEntity(entity));
    }

    public void untrackEntity(final UUID entityId) {
        this.managedEntities.remove(entityId);
    }

    public void trackTask(final ScheduledTask task) {
        if (task != null) {
            this.managedTasks.add(task);
        }
    }

    public void untrackTask(final ScheduledTask task) {
        if (task != null) {
            this.managedTasks.remove(task);
        }
    }

    public void cleanupAll(final CommandSender sender) {
        this.cleanupRequested.set(true);
        this.batchGeneration.incrementAndGet();
        this.getServer().getGlobalRegionScheduler().cancelTasks(this);
        this.getServer().getAsyncScheduler().cancelTasks(this);

        int cancelledTasks = 0;
        for (final ScheduledTask task : new ArrayList<>(this.managedTasks)) {
            if (task == null) {
                continue;
            }
            task.cancel();
            this.managedTasks.remove(task);
            cancelledTasks++;
        }

        int queuedEntityRemovals = 0;
        final List<ManagedEntity> entities = new ArrayList<>(this.managedEntities.values());
        for (final ManagedEntity entity : entities) {
            final UUID entityId = entity.entityId();
            queuedEntityRemovals++;
            final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
            final ScheduledTask scheduledTask = entity.scheduler().run(this, task -> {
                try {
                    entity.entity().remove();
                } finally {
                    this.untrackTask(task);
                    this.managedEntities.remove(entityId);
                }
            }, () -> {
                this.untrackTask(taskRef.get());
                this.managedEntities.remove(entityId);
            });
            taskRef.set(scheduledTask);
            if (scheduledTask == null) {
                this.managedEntities.remove(entityId);
            } else {
                this.trackTask(scheduledTask);
            }
        }

        if (sender != null) {
            sender.sendMessage("RegionLoadTest cleanup queued: cancelledTasks=" + cancelledTasks
                + ", entityRemovalsQueued=" + queuedEntityRemovals);
        }
    }

    public long startBatch() {
        // Cleanup is the only batch boundary; load and probe commands often run together.
        this.cleanupRequested.set(false);
        return this.batchGeneration.get();
    }

    public long currentBatch() {
        return this.batchGeneration.get();
    }

    public boolean shouldAbortBatch(final long batchId) {
        return this.cleanupRequested.get() || this.batchGeneration.get() != batchId;
    }

    public void burnCpu(final int iterations) {
        long value = this.busySink.get();
        for (int i = 0; i < iterations; i++) {
            value = value * 1664525L + 1013904223L + i;
        }
        this.busySink.lazySet(value);
    }

    private record ManagedEntity(UUID entityId, io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler, Entity entity) {
        private ManagedEntity(final Entity entity) {
            this(entity.getUniqueId(), entity.getScheduler(), entity);
        }
    }
}
