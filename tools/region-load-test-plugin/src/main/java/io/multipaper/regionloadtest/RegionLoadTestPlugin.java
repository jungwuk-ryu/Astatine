package io.multipaper.regionloadtest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class RegionLoadTestPlugin extends JavaPlugin {

    public static final String MANAGED_ENTITY_TAG = "shreddedpaper_rlt";
    private static final long FIRST_TAGGED_ENTITY_PURGE_DELAY_TICKS = 40L;
    private static final long SECOND_TAGGED_ENTITY_PURGE_DELAY_TICKS = 80L;
    private static final long CLEANUP_TICKET_RELEASE_DELAY_TICKS = 140L;

    private final Map<UUID, ManagedEntity> managedEntities = new ConcurrentHashMap<>();
    private final Set<ScheduledTask> managedTasks = ConcurrentHashMap.newKeySet();
    private final Set<ManagedChunkTicket> managedChunkTickets = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cleanupRequested = new AtomicBoolean();
    private final AtomicLong batchGeneration = new AtomicLong();
    private final AtomicLong busySink = new AtomicLong();
    private final AtomicLong activeChunkBatches = new AtomicLong();

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
        this.cleanupAll(null, false);
    }

    public void trackEntity(final Entity entity) {
        entity.addScoreboardTag(MANAGED_ENTITY_TAG);
        if (this.cleanupRequested.get()) {
            this.managedEntities.put(entity.getUniqueId(), new ManagedEntity(entity));
            if (this.managedEntities.remove(entity.getUniqueId()) != null) {
                this.queueEntityRemoval(entity);
            }
            return;
        }
        this.managedEntities.put(entity.getUniqueId(), new ManagedEntity(entity));
        if (this.cleanupRequested.get() && this.managedEntities.remove(entity.getUniqueId()) != null) {
            this.queueEntityRemoval(entity);
        }
    }

    public void untrackEntity(final UUID entityId) {
        this.managedEntities.remove(entityId);
    }

    public void trackTask(final ScheduledTask task) {
        if (task != null) {
            if (this.cleanupRequested.get()) {
                task.cancel();
                return;
            }
            this.managedTasks.add(task);
            if (this.cleanupRequested.get() && this.managedTasks.remove(task)) {
                task.cancel();
            }
        }
    }

    public void untrackTask(final ScheduledTask task) {
        if (task != null) {
            this.managedTasks.remove(task);
        }
    }

    public void cleanupAll(final CommandSender sender) {
        this.cleanupAll(sender, this.isEnabled());
    }

    private void cleanupAll(final CommandSender sender, final boolean scheduleEntityRemovals) {
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
        final int pinnedEntityCleanupChunks = scheduleEntityRemovals ? this.pinEntityCleanupChunks(entities) : 0;
        for (final ManagedEntity entity : entities) {
            final UUID entityId = entity.entityId();
            if (scheduleEntityRemovals) {
                if (this.managedEntities.remove(entityId, entity)) {
                    queuedEntityRemovals++;
                    this.queueEntityRemoval(entity.entity());
                }
            } else {
                this.managedEntities.remove(entityId);
            }
        }

        int queuedPurgeTasks = 0;
        if (scheduleEntityRemovals) {
            this.scheduleTaggedEntityPurge(FIRST_TAGGED_ENTITY_PURGE_DELAY_TICKS);
            this.scheduleTaggedEntityPurge(SECOND_TAGGED_ENTITY_PURGE_DELAY_TICKS);
            queuedPurgeTasks = 2;
        }

        final int removedChunkTickets = scheduleEntityRemovals
            ? this.scheduleDeferredChunkTicketCleanup()
            : this.removeManagedChunkTickets();

        if (sender != null) {
            sender.sendMessage("RegionLoadTest cleanup queued: cancelledTasks=" + cancelledTasks
                + ", entityRemovalsQueued=" + queuedEntityRemovals
                + ", entityCleanupChunkTickets=" + pinnedEntityCleanupChunks
                + ", taggedEntityPurgeTasks=" + queuedPurgeTasks
                + ", chunkTicketsRemoved=" + removedChunkTickets);
        }
    }

    public void trackChunkTicket(final World world, final int chunkX, final int chunkZ) {
        world.addPluginChunkTicket(chunkX, chunkZ, this);
        this.managedChunkTickets.add(new ManagedChunkTicket(world, chunkX, chunkZ));
    }

    public void untrackChunkTicket(final World world, final int chunkX, final int chunkZ) {
        final ManagedChunkTicket ticket = new ManagedChunkTicket(world, chunkX, chunkZ);
        if (this.managedChunkTickets.remove(ticket)) {
            world.removePluginChunkTicket(chunkX, chunkZ, this);
        }
    }

    private int pinEntityCleanupChunks(final List<ManagedEntity> entities) {
        int pinned = 0;
        for (final ManagedEntity managedEntity : entities) {
            final Entity entity = managedEntity.entity();
            if (!entity.isValid()) {
                continue;
            }
            final World world = entity.getWorld();
            final int chunkX = entity.getLocation().getBlockX() >> 4;
            final int chunkZ = entity.getLocation().getBlockZ() >> 4;
            final ManagedChunkTicket ticket = new ManagedChunkTicket(world, chunkX, chunkZ);
            if (this.managedChunkTickets.add(ticket)) {
                world.addPluginChunkTicket(chunkX, chunkZ, this);
                pinned++;
            }
        }
        return pinned;
    }

    private void queueEntityRemoval(final Entity entity) {
        final UUID entityId = entity.getUniqueId();
        try {
            entity.getScheduler().run(this, task -> {
                try {
                    entity.remove();
                    if (!entity.isValid()) {
                        entity.removeScoreboardTag(MANAGED_ENTITY_TAG);
                    }
                } finally {
                    this.untrackEntity(entityId);
                }
            }, () -> this.untrackEntity(entityId));
        } catch (final RejectedExecutionException ignored) {
            this.untrackEntity(entityId);
        }
    }

    private int scheduleDeferredChunkTicketCleanup() {
        this.getServer().getGlobalRegionScheduler().runDelayed(this, scheduledTask -> {
            try {
                this.removeManagedChunkTickets();
            } finally {
                this.untrackTask(scheduledTask);
            }
        }, CLEANUP_TICKET_RELEASE_DELAY_TICKS);
        return 0;
    }

    private void scheduleTaggedEntityPurge(final long delayTicks) {
        this.getServer().getGlobalRegionScheduler().runDelayed(this, scheduledTask -> {
            try {
                this.dispatchTaggedEntityPurge();
            } finally {
                this.untrackTask(scheduledTask);
            }
        }, delayTicks);
    }

    private void dispatchTaggedEntityPurge() {
        final CommandSender console = this.getServer().getConsoleSender();
        this.getServer().dispatchCommand(console, "execute in minecraft:overworld run kill @e[tag=" + MANAGED_ENTITY_TAG + "]");
        this.getServer().dispatchCommand(console, "execute in minecraft:the_nether run kill @e[tag=" + MANAGED_ENTITY_TAG + "]");
        this.getServer().dispatchCommand(console, "execute in minecraft:the_end run kill @e[tag=" + MANAGED_ENTITY_TAG + "]");
    }

    private int removeManagedChunkTickets() {
        int removedChunkTickets = 0;
        for (final ManagedChunkTicket ticket : new ArrayList<>(this.managedChunkTickets)) {
            if (this.managedChunkTickets.remove(ticket)) {
                ticket.world().removePluginChunkTicket(ticket.chunkX(), ticket.chunkZ(), this);
                removedChunkTickets++;
            }
        }
        return removedChunkTickets;
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

    public void beginChunkBatch() {
        this.activeChunkBatches.incrementAndGet();
    }

    public void finishChunkBatch() {
        final long remaining = this.activeChunkBatches.decrementAndGet();
        if (remaining < 0L) {
            this.activeChunkBatches.compareAndSet(remaining, 0L);
        }
    }

    public StatusSnapshot statusSnapshot() {
        return new StatusSnapshot(
            this.batchGeneration.get(),
            this.cleanupRequested.get(),
            this.managedTasks.size(),
            this.managedEntities.size(),
            this.managedChunkTickets.size(),
            Math.max(0L, this.activeChunkBatches.get())
        );
    }

    private record ManagedEntity(UUID entityId, io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler, Entity entity) {
        private ManagedEntity(final Entity entity) {
            this(entity.getUniqueId(), entity.getScheduler(), entity);
        }
    }

    private record ManagedChunkTicket(World world, int chunkX, int chunkZ) {
    }

    public record StatusSnapshot(
        long batch,
        boolean cleanupRequested,
        int managedTasks,
        int managedEntities,
        int managedChunkTickets,
        long activeChunkBatches
    ) {
    }
}
