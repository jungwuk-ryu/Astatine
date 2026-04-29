package io.multipaper.regionloadtest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class RegionLoadTestCommand implements TabExecutor {

    private static final List<String> ROOT_SUBCOMMANDS = List.of(
        "help",
        "cleanup",
        "tntsingle",
        "tntspread",
        "villagers",
        "path",
        "boundary",
        "redstone",
        "lighting",
        "mobfarm",
        "pvp",
        "vehicles",
        "tracker",
        "broadcast",
        "scheduler",
        "watchdogstall",
        "crossqueue",
        "syncload",
        "chunkgen",
        "chunkload",
        "scenario",
        "probe",
        "playercheck",
        "status",
        "at"
    );

    private final RegionLoadTestPlugin plugin;

    public RegionLoadTestCommand(final RegionLoadTestPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        final CommandSender sender,
        final Command command,
        final String label,
        final String[] args
    ) {
        if (args.length == 0 || equalsAny(args[0], "help", "?")) {
            this.sendHelp(sender, label);
            return true;
        }

        Location anchorOverride = null;
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        String[] effectiveArgs = args;
        if ("at".equals(subcommand)) {
            if (args.length < 6) {
                sender.sendMessage("Usage: /" + label + " at <world> <x> <y> <z> <subcommand> [args...]");
                return true;
            }
            final World world = Bukkit.getWorld(args[1]);
            final Double x = this.parseDouble(sender, args[2], "x");
            final Double y = this.parseDouble(sender, args[3], "y");
            final Double z = this.parseDouble(sender, args[4], "z");
            if (world == null) {
                sender.sendMessage("Unknown world: " + args[1]);
                return true;
            }
            if (x == null || y == null || z == null) {
                return true;
            }
            anchorOverride = new Location(world, x, y, z);
            subcommand = args[5].toLowerCase(Locale.ROOT);
            effectiveArgs = new String[args.length - 5];
            effectiveArgs[0] = subcommand;
            System.arraycopy(args, 6, effectiveArgs, 1, args.length - 6);
        }
        final long commandBatch = this.opensNewBatch(subcommand) ? this.plugin.startBatch() : this.plugin.currentBatch();

        switch (subcommand) {
            case "cleanup" -> {
                this.plugin.cleanupAll(sender);
                return true;
            }
            case "tntsingle" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleSingleRegionTnt(sender, anchor, effectiveArgs, commandBatch);
            }
            case "tntspread" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleDistributedTnt(sender, anchor, effectiveArgs, commandBatch);
            }
            case "villagers" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleVillagerLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "path" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handlePathfindingLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "boundary", "redstone" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleRedstoneBoundaryLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "lighting" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleLightingLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "mobfarm" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleMobFarmLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "pvp" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handlePvpProjectileLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "vehicles" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleVehiclePassengerLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "tracker" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleTrackerFlood(sender, anchor, effectiveArgs, commandBatch);
            }
            case "broadcast" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleBroadcastFlood(sender, anchor, effectiveArgs, commandBatch);
            }
            case "scheduler" -> {
                return this.handleSchedulerFlood(sender, anchorOverride, effectiveArgs, commandBatch);
            }
            case "watchdogstall" -> {
                return this.handleWatchdogStall(sender, anchorOverride, effectiveArgs);
            }
            case "crossqueue" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleCrossRegionQueueProbe(sender, anchor, effectiveArgs, commandBatch);
            }
            case "syncload" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleSyncLoadGuardProbe(sender, anchor, effectiveArgs, commandBatch);
            }
            case "chunkgen" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleChunkGenerationLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "chunkload" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleChunkLoadOnly(sender, anchor, effectiveArgs, commandBatch);
            }
            case "scenario" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleScenario(sender, anchor, effectiveArgs, commandBatch);
            }
            case "probe" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleProbe(sender, anchor, effectiveArgs, commandBatch);
            }
            case "playercheck" -> {
                return this.handlePlayerCheck(sender, effectiveArgs);
            }
            case "status" -> {
                return this.handleStatus(sender);
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Use /" + label + " help");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(
        final CommandSender sender,
        final Command command,
        final String alias,
        final String[] args
    ) {
        if (args.length == 1) {
            return this.filter(ROOT_SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && equalsAny(args[0], "scheduler")) {
            return this.filter(List.of("region", "regionlocal", "global", "async"), args[1]);
        }
        if (args.length == 2 && equalsAny(args[0], "watchdogstall")) {
            return this.filter(List.of("global", "region"), args[1]);
        }
        if (args.length == 4 && equalsAny(args[0], "watchdogstall")) {
            return this.filter(List.of("interruptible", "uninterruptible"), args[3]);
        }
        return Collections.emptyList();
    }

    private boolean handleSingleRegionTnt(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt tntsingle <width> <depth> [spacing=4] [fuse=80] [regionChunks=8]");
            return true;
        }

        final Integer width = this.parseInt(sender, args[1], "width");
        final Integer depth = this.parseInt(sender, args[2], "depth");
        final Integer spacing = args.length >= 4 ? this.parseInt(sender, args[3], "spacing") : 4;
        final Integer fuseTicks = args.length >= 5 ? this.parseInt(sender, args[4], "fuse") : 80;
        final Integer regionChunks = args.length >= 6 ? this.parseInt(sender, args[5], "regionChunks") : 8;
        if (width == null || depth == null || spacing == null || fuseTicks == null || regionChunks == null) {
            return true;
        }
        if (width < 1 || depth < 1 || spacing < 1 || fuseTicks < 1 || regionChunks < 1) {
            sender.sendMessage("All numeric arguments must be positive.");
            return true;
        }

        final LogicalRegion logicalRegion = LogicalRegion.fromChunk(base.getBlockX() >> 4, base.getBlockZ() >> 4, regionChunks);
        final SpawnSummary summary = this.spawnTntGrid(base.getWorld(), base, width, depth, spacing, fuseTicks, logicalRegion, commandBatch);

        sender.sendMessage("Queued single-region TNT grid: scheduled=" + summary.scheduled()
            + ", skippedOutsideRegion=" + summary.skipped());
        return true;
    }

    private boolean handleDistributedTnt(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 4) {
            sender.sendMessage("Usage: /rlt tntspread <grids> <width> <depth> [spacing=4] [fuse=80] [regionChunks=8] [regionStride=2]");
            return true;
        }

        final Integer grids = this.parseInt(sender, args[1], "grids");
        final Integer width = this.parseInt(sender, args[2], "width");
        final Integer depth = this.parseInt(sender, args[3], "depth");
        final Integer spacing = args.length >= 5 ? this.parseInt(sender, args[4], "spacing") : 4;
        final Integer fuseTicks = args.length >= 6 ? this.parseInt(sender, args[5], "fuse") : 80;
        final Integer regionChunks = args.length >= 7 ? this.parseInt(sender, args[6], "regionChunks") : 8;
        final Integer regionStride = args.length >= 8 ? this.parseInt(sender, args[7], "regionStride") : 2;
        if (grids == null || width == null || depth == null || spacing == null || fuseTicks == null
            || regionChunks == null || regionStride == null) {
            return true;
        }
        if (grids < 1 || width < 1 || depth < 1 || spacing < 1 || fuseTicks < 1 || regionChunks < 1 || regionStride < 1) {
            sender.sendMessage("All numeric arguments must be positive.");
            return true;
        }

        final LogicalRegion baseRegion = LogicalRegion.fromChunk(base.getBlockX() >> 4, base.getBlockZ() >> 4, regionChunks);
        int totalScheduled = 0;
        int totalSkipped = 0;
        for (int i = 0; i < grids; i++) {
            final int startChunkX = baseRegion.startChunkX() + i * regionChunks * regionStride;
            final int centerChunkX = startChunkX + regionChunks / 2;
            final int centerChunkZ = baseRegion.startChunkZ() + regionChunks / 2;
            final Location anchor = new Location(
                base.getWorld(),
                centerChunkX * 16.0 + 8.0,
                base.getY(),
                centerChunkZ * 16.0 + 8.0,
                base.getYaw(),
                base.getPitch()
            );

            final SpawnSummary summary = this.spawnTntGrid(
                anchor.getWorld(),
                anchor,
                width,
                depth,
                spacing,
                fuseTicks,
                new LogicalRegion(startChunkX, baseRegion.startChunkZ(), regionChunks),
                commandBatch
            );
            totalScheduled += summary.scheduled();
            totalSkipped += summary.skipped();
        }

        sender.sendMessage("Queued distributed TNT grids: grids=" + grids + ", scheduled=" + totalScheduled
            + ", skippedOutsideRegion=" + totalSkipped);
        return true;
    }

    private SpawnSummary spawnTntGrid(
        final World world,
        final Location anchor,
        final int width,
        final int depth,
        final int spacing,
        final int fuseTicks,
        final LogicalRegion logicalRegion,
        final long commandBatch
    ) {
        int scheduled = 0;
        int skipped = 0;
        final Map<Long, List<Location>> locationsByChunk = new HashMap<>();
        final double x0 = anchor.getX() - ((width - 1) * spacing) / 2.0D;
        final double z0 = anchor.getZ() - ((depth - 1) * spacing) / 2.0D;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                final double targetX = x0 + x * spacing;
                final double targetZ = z0 + z * spacing;
                final int chunkX = ((int)Math.floor(targetX)) >> 4;
                final int chunkZ = ((int)Math.floor(targetZ)) >> 4;
                if (!logicalRegion.contains(chunkX, chunkZ)) {
                    skipped++;
                    continue;
                }

                final Location spawnLocation = new Location(world, targetX, anchor.getY(), targetZ);
                locationsByChunk.computeIfAbsent(chunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(spawnLocation);
                scheduled++;
            }
        }
        for (final Map.Entry<Long, List<Location>> entry : locationsByChunk.entrySet()) {
            final int chunkX = unpackChunkX(entry.getKey());
            final int chunkZ = unpackChunkZ(entry.getKey());
            final List<Location> chunkLocations = List.copyOf(entry.getValue());
            final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ, scheduledTask -> {
                try {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        return;
                    }
                    for (final Location spawnLocation : chunkLocations) {
                        this.spawnTnt(world, spawnLocation, fuseTicks);
                    }
                } finally {
                    this.plugin.untrackTask(scheduledTask);
                }
            });
            this.plugin.trackTask(task);
        }
        return new SpawnSummary(scheduled, skipped);
    }

    private static long chunkKey(final int chunkX, final int chunkZ) {
        return ((long)chunkX << 32) ^ (chunkZ & 0xFFFF_FFFFL);
    }

    private static int unpackChunkX(final long key) {
        return (int)(key >> 32);
    }

    private static int unpackChunkZ(final long key) {
        return (int)key;
    }

    private void spawnTnt(final World world, final Location spawnLocation, final int fuseTicks) {
        final Location safeLocation = spawnLocation.clone();
        world.spawn(safeLocation, TNTPrimed.class, tnt -> {
            tnt.setFuseTicks(fuseTicks);
            tnt.setYield(4.0F);
            tnt.setIsIncendiary(false);
        });
    }

    private boolean handleVillagerLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt villagers <count> [spread=24] [lifeTicks=1200]");
            return true;
        }

        final Integer count = this.parseInt(sender, args[1], "count");
        final Integer spread = args.length >= 3 ? this.parseInt(sender, args[2], "spread") : 24;
        final Integer lifeTicks = args.length >= 4 ? this.parseInt(sender, args[3], "lifeTicks") : 1200;
        if (count == null || spread == null || lifeTicks == null) {
            return true;
        }
        if (count < 1 || spread < 1 || lifeTicks < 1) {
            sender.sendMessage("All numeric arguments must be positive.");
            return true;
        }

        for (int i = 0; i < count; i++) {
            final double dx = this.offset(i, spread);
            final double dz = this.offset(i * 17, spread);
            final Location spawnHint = base.clone().add(dx, 0.0D, dz);
            final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnHint, scheduledTask -> {
                try {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        return;
                    }
                    this.spawnVillager(spawnHint, lifeTicks);
                } finally {
                    this.plugin.untrackTask(scheduledTask);
                }
            });
            this.plugin.trackTask(task);
        }

        sender.sendMessage("Queued villager load: spawned=" + count + ", spread=" + spread + ", lifeTicks=" + lifeTicks);
        return true;
    }

    private void spawnVillager(final Location spawnHint, final int lifeTicks) {
        final Location spawnLocation = this.surfaceSpawnLocation(spawnHint);
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.setBlockTypeIfLoaded(world, spawnLocation.getBlockX(), spawnLocation.getBlockY() - 1, spawnLocation.getBlockZ(), Material.STONE, false)) {
            return;
        }
        final Villager villager = spawnLocation.getWorld().spawn(spawnLocation, Villager.class, entity -> {
            entity.setRemoveWhenFarAway(false);
            entity.setAdult();
            entity.setProfession(Villager.Profession.FARMER);
        });

        this.plugin.trackEntity(villager);
        final UUID entityId = villager.getUniqueId();
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        final ScheduledTask removalTask = villager.getScheduler().runDelayed(this.plugin, task -> {
            try {
                villager.remove();
            } finally {
                this.plugin.untrackTask(task);
                this.plugin.untrackEntity(entityId);
            }
        }, () -> {
            this.plugin.untrackTask(taskRef.get());
            this.plugin.untrackEntity(entityId);
        }, lifeTicks);
        taskRef.set(removalTask);
        this.plugin.trackTask(removalTask);
    }

    private boolean handlePathfindingLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt path <count> [spread=24] [lifeTicks=600]");
            return true;
        }

        final Integer count = this.parseInt(sender, args[1], "count");
        final Integer spread = args.length >= 3 ? this.parseInt(sender, args[2], "spread") : 24;
        final Integer lifeTicks = args.length >= 4 ? this.parseInt(sender, args[3], "lifeTicks") : 600;
        if (count == null || spread == null || lifeTicks == null) {
            return true;
        }
        if (count < 1 || spread < 1 || lifeTicks < 1) {
            sender.sendMessage("All numeric arguments must be positive.");
            return true;
        }

        final AtomicInteger queuedTasks = new AtomicInteger();
        final AtomicInteger completedTasks = new AtomicInteger();
        final AtomicInteger rejectedTasks = new AtomicInteger();
        final AtomicInteger spawned = new AtomicInteger();
        final AtomicInteger skippedUnloaded = new AtomicInteger();
        final AtomicInteger pathStarted = new AtomicInteger();
        final AtomicInteger crossChunkMoves = new AtomicInteger();
        final AtomicInteger progressed = new AtomicInteger();
        final AtomicInteger arrived = new AtomicInteger();
        final AtomicInteger removed = new AtomicInteger();
        for (int i = 0; i < count; i++) {
            final double dx = this.offset(i, spread);
            final double dz = this.offset(i * 17, spread);
            final Location spawnHint = base.clone().add(dx, 0.0D, dz);
            try {
                final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnHint, scheduledTask -> {
                    try {
                        if (this.plugin.shouldAbortBatch(commandBatch)) {
                            return;
                        }
                        this.spawnPathfindingMob(base, spawnHint, dx, dz, lifeTicks, spawned, skippedUnloaded,
                            pathStarted, crossChunkMoves, progressed, arrived, removed);
                    } finally {
                        completedTasks.incrementAndGet();
                        this.plugin.untrackTask(scheduledTask);
                    }
                });
                this.plugin.trackTask(task);
                queuedTasks.incrementAndGet();
            } catch (final RejectedExecutionException rejectedExecutionException) {
                rejectedTasks.incrementAndGet();
            }
        }

        final int summaryDelayTicks = Math.max(40, Math.min(lifeTicks, 800));
        this.plugin.trackTask(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
            try {
                this.replyLater(sender, "pathfinding load finished: requested=" + count
                    + ", spread=" + spread + ", lifeTicks=" + lifeTicks
                    + ", queued=" + queuedTasks.get() + ", completed=" + completedTasks.get()
                    + ", rejected=" + rejectedTasks.get() + ", spawned=" + spawned.get()
                    + ", skippedUnloaded=" + skippedUnloaded.get() + ", pathStarted=" + pathStarted.get()
                    + ", crossChunkMoves=" + crossChunkMoves.get() + ", progressed=" + progressed.get()
                    + ", arrived=" + arrived.get()
                    + ", removed=" + removed.get());
            } finally {
                this.plugin.untrackTask(task);
            }
        }, summaryDelayTicks));

        sender.sendMessage("Queued pathfinding load: requested=" + count + ", spread=" + spread
            + ", lifeTicks=" + lifeTicks + ", queued=" + queuedTasks.get() + ", rejected=" + rejectedTasks.get());
        return true;
    }

    private void spawnPathfindingMob(
        final Location base,
        final Location spawnHint,
        final double dx,
        final double dz,
        final int lifeTicks,
        final AtomicInteger spawned,
        final AtomicInteger skippedUnloaded,
        final AtomicInteger pathStarted,
        final AtomicInteger crossChunkMoves,
        final AtomicInteger progressed,
        final AtomicInteger arrived,
        final AtomicInteger removed
    ) {
        final Location spawnLocation = this.surfaceSpawnLocation(spawnHint);
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.setBlockTypeIfLoaded(world, spawnLocation.getBlockX(), spawnLocation.getBlockY() - 1, spawnLocation.getBlockZ(), Material.STONE, false)) {
            skippedUnloaded.incrementAndGet();
            return;
        }
        final Zombie zombie = base.getWorld().spawn(spawnLocation, Zombie.class, mob -> {
            mob.setCanPickupItems(false);
            mob.setRemoveWhenFarAway(false);
            mob.setAdult();
            if (mob.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                Objects.requireNonNull(mob.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.35D);
            }
        });

        spawned.incrementAndGet();
        this.plugin.trackEntity(zombie);
        final Location destination = spawnLocation.clone().add(
            dx == 0.0D ? 24.0D : Math.copySign(Math.max(24.0D, Math.abs(dx)), -dx),
            0.0D,
            dz == 0.0D ? 0.0D : Math.copySign(Math.max(24.0D, Math.abs(dz)), -dz)
        );
        if (!world.isChunkLoaded(destination.getBlockX() >> 4, destination.getBlockZ() >> 4)) {
            skippedUnloaded.incrementAndGet();
            zombie.remove();
            this.plugin.untrackEntity(zombie.getUniqueId());
            return;
        }
        zombie.getPathfinder().moveTo(destination, 1.1D);
        pathStarted.incrementAndGet();

        final UUID entityId = zombie.getUniqueId();
        final AtomicBoolean crossedChunk = new AtomicBoolean();
        final AtomicBoolean reachedDestination = new AtomicBoolean();
        final AtomicInteger monitorTicks = new AtomicInteger(Math.max(1, Math.min(lifeTicks, 240)));
        final AtomicReference<ScheduledTask> monitorRef = new AtomicReference<>();
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        final int startChunkX = spawnLocation.getBlockX() >> 4;
        final int startChunkZ = spawnLocation.getBlockZ() >> 4;
        final double initialDistanceSquared = spawnLocation.distanceSquared(destination);
        final double progressDistanceSquared = Math.max(9.0D, initialDistanceSquared * 0.64D);
        final AtomicBoolean madeProgress = new AtomicBoolean();
        try {
            final ScheduledTask monitorTask = zombie.getScheduler().runAtFixedRate(this.plugin, task -> {
                if (!zombie.isValid() || monitorTicks.addAndGet(-10) < 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    return;
                }
                final Location current = zombie.getLocation();
                if (!crossedChunk.get()
                    && ((current.getBlockX() >> 4) != startChunkX || (current.getBlockZ() >> 4) != startChunkZ)) {
                    if (crossedChunk.compareAndSet(false, true)) {
                        crossChunkMoves.incrementAndGet();
                    }
                }
                if (!madeProgress.get() && current.distanceSquared(destination) <= progressDistanceSquared) {
                    if (madeProgress.compareAndSet(false, true)) {
                        progressed.incrementAndGet();
                    }
                }
                if (!reachedDestination.get() && current.distanceSquared(destination) <= 9.0D) {
                    if (reachedDestination.compareAndSet(false, true)) {
                        arrived.incrementAndGet();
                    }
                }
            }, () -> this.plugin.untrackTask(monitorRef.get()), 10L, 10L);
            monitorRef.set(monitorTask);
            this.plugin.trackTask(monitorTask);
        } catch (final RejectedExecutionException rejectedExecutionException) {
            // The spawn itself is still useful load; the summary will expose lack of movement evidence.
        }
        final ScheduledTask removalTask = zombie.getScheduler().runDelayed(this.plugin, task -> {
            try {
                final ScheduledTask monitorTask = monitorRef.get();
                if (monitorTask != null) {
                    monitorTask.cancel();
                    this.plugin.untrackTask(monitorTask);
                }
                zombie.remove();
                removed.incrementAndGet();
            } finally {
                this.plugin.untrackTask(task);
                this.plugin.untrackEntity(entityId);
            }
        }, () -> {
            this.plugin.untrackTask(monitorRef.get());
            this.plugin.untrackTask(taskRef.get());
            this.plugin.untrackEntity(entityId);
        }, lifeTicks);
        taskRef.set(removalTask);
        this.plugin.trackTask(removalTask);
    }

    private boolean handleRedstoneBoundaryLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt redstone <lanes> [ticks=1200] [length=64] [periodTicks=1]");
            return true;
        }

        final Integer lanes = this.parseInt(sender, args[1], "lanes");
        final Integer ticks = args.length >= 3 ? this.parseInt(sender, args[2], "ticks") : 1200;
        final Integer length = args.length >= 4 ? this.parseInt(sender, args[3], "length") : 64;
        final Integer periodTicks = args.length >= 5 ? this.parseInt(sender, args[4], "periodTicks") : 1;
        if (lanes == null || ticks == null || length == null || periodTicks == null) {
            return true;
        }
        if (lanes < 1 || lanes > 32 || ticks < 1 || length < 1 || length > 192 || periodTicks < 1) {
            sender.sendMessage("lanes must be 1..32, ticks positive, length 1..192, periodTicks positive.");
            return true;
        }

        final World world = Objects.requireNonNull(base.getWorld());
        final int baseX = base.getBlockX();
        final int baseY = Math.max(world.getMinHeight() + 2, Math.min(world.getMaxHeight() - 2, base.getBlockY()));
        final int baseZ = base.getBlockZ();
        final AtomicInteger expectedTasks = new AtomicInteger(Integer.MAX_VALUE);
        final AtomicInteger completedTasks = new AtomicInteger();
        final AtomicInteger pulseWrites = new AtomicInteger();
        final AtomicInteger skippedUnloaded = new AtomicInteger();
        final AtomicInteger rejectedTasks = new AtomicInteger();
        int queued = 0;
        for (int lane = 0; lane < lanes; lane++) {
            final int z = baseZ + lane - lanes / 2;
            for (int step = 0; step < length; step++) {
                final int x = baseX - length / 2 + step;
                final Location cell = new Location(world, x, baseY, z);
                if (this.queueRedstonePulse(cell, ticks, periodTicks, commandBatch, lane + step,
                    expectedTasks, completedTasks, pulseWrites, skippedUnloaded, sender,
                    lanes, length, rejectedTasks)) {
                    queued++;
                } else {
                    rejectedTasks.incrementAndGet();
                }
            }
        }
        expectedTasks.set(queued);
        if (queued == 0) {
            this.replyLater(sender, "redstone boundary load finished: lanes=" + lanes
                + ", length=" + length + ", ticks=" + ticks + ", periodTicks=" + periodTicks
                + ", pulseTasks=0, completed=0, rejected=" + rejectedTasks.get()
                + ", writes=0, skippedUnloaded=0");
        }

        sender.sendMessage("Queued redstone boundary load: lanes=" + lanes
            + ", length=" + length + ", ticks=" + ticks + ", periodTicks=" + periodTicks
            + ", pulseTasks=" + queued + ", rejected=" + rejectedTasks.get());
        return true;
    }

    private boolean queueRedstonePulse(
        final Location cell,
        final int ticks,
        final int periodTicks,
        final long commandBatch,
        final int phaseOffset,
        final AtomicInteger expectedTasks,
        final AtomicInteger completedTasks,
        final AtomicInteger pulseWrites,
        final AtomicInteger skippedUnloaded,
        final CommandSender sender,
        final int lanes,
        final int length,
        final AtomicInteger rejectedTasks
    ) {
        final World world = Objects.requireNonNull(cell.getWorld());
        final int x = cell.getBlockX();
        final int y = cell.getBlockY();
        final int z = cell.getBlockZ();
        final AtomicInteger remainingTicks = new AtomicInteger(ticks);
        final AtomicInteger phase = new AtomicInteger(phaseOffset);
        try {
            final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, cell, task -> {
                if (this.plugin.shouldAbortBatch(commandBatch) || remainingTicks.addAndGet(-periodTicks) < 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    this.finishRedstonePulse(expectedTasks, completedTasks, pulseWrites, skippedUnloaded, sender,
                        lanes, length, ticks, periodTicks, rejectedTasks);
                    return;
                }
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    skippedUnloaded.incrementAndGet();
                    return;
                }

                if (!this.setBlockTypeIfLoaded(world, x, y - 1, z, Material.STONE, false)) {
                    skippedUnloaded.incrementAndGet();
                    return;
                }
                final Material material = switch (Math.floorMod(phase.getAndIncrement(), 10)) {
                    case 0, 1 -> Material.REDSTONE_BLOCK;
                    case 2, 3 -> Material.REDSTONE_WIRE;
                    case 4 -> Material.REPEATER;
                    case 5 -> Material.OBSERVER;
                    case 6 -> Material.PISTON;
                    case 7 -> Material.REDSTONE_TORCH;
                    default -> Material.AIR;
                };
                if (this.setBlockTypeIfLoaded(world, x, y, z, material, true)) {
                    pulseWrites.incrementAndGet();
                } else {
                    skippedUnloaded.incrementAndGet();
                }
            }, 1L, periodTicks);
            this.plugin.trackTask(scheduledTask);
            return true;
        } catch (final RejectedExecutionException rejectedExecutionException) {
            return false;
        }
    }

    private void finishRedstonePulse(
        final AtomicInteger expectedTasks,
        final AtomicInteger completedTasks,
        final AtomicInteger pulseWrites,
        final AtomicInteger skippedUnloaded,
        final CommandSender sender,
        final int lanes,
        final int length,
        final int ticks,
        final int periodTicks,
        final AtomicInteger rejectedTasks
    ) {
        final int completed = completedTasks.incrementAndGet();
        final int expected = expectedTasks.get();
        if (completed == expected) {
            this.replyLater(sender, "redstone boundary load finished: lanes=" + lanes
                + ", length=" + length + ", ticks=" + ticks + ", periodTicks=" + periodTicks
                + ", pulseTasks=" + expected + ", completed=" + completed + ", rejected=" + rejectedTasks.get()
                + ", writes=" + pulseWrites.get() + ", skippedUnloaded=" + skippedUnloaded.get());
        }
    }

    private boolean handleLightingLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt lighting <cells> [ticks=160] [periodTicks=2]");
            return true;
        }

        final Integer cells = this.parseInt(sender, args[1], "cells");
        final Integer ticks = args.length >= 3 ? this.parseInt(sender, args[2], "ticks") : 160;
        final Integer periodTicks = args.length >= 4 ? this.parseInt(sender, args[3], "periodTicks") : 2;
        if (cells == null || ticks == null || periodTicks == null) {
            return true;
        }
        if (cells < 1 || cells > 128 || ticks < 1 || periodTicks < 1) {
            sender.sendMessage("cells must be 1..128, ticks and periodTicks must be positive.");
            return true;
        }

        final World world = Objects.requireNonNull(base.getWorld());
        final int baseX = base.getBlockX() - cells / 2;
        final int baseY = Math.max(world.getMinHeight() + 3, Math.min(world.getMaxHeight() - 3, base.getBlockY()));
        final int baseZ = base.getBlockZ();
        final AtomicInteger queued = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger lightReads = new AtomicInteger();
        final AtomicInteger brightSamples = new AtomicInteger();
        final AtomicInteger darkSamples = new AtomicInteger();
        final AtomicInteger skippedUnloaded = new AtomicInteger();
        for (int i = 0; i < cells; i++) {
            final Location cell = new Location(world, baseX + i, baseY, baseZ + (i & 1));
            final AtomicInteger remaining = new AtomicInteger(ticks);
            try {
                final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, cell, task -> {
                    if (this.plugin.shouldAbortBatch(commandBatch) || remaining.addAndGet(-periodTicks) < 0) {
                        task.cancel();
                        this.plugin.untrackTask(task);
                        return;
                    }
                    final int x = cell.getBlockX();
                    final int y = cell.getBlockY();
                    final int z = cell.getBlockZ();
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        skippedUnloaded.incrementAndGet();
                        return;
                    }
                    this.setBlockTypeIfLoaded(world, x, y - 1, z, Material.STONE, false);
                    final boolean bright = (remaining.get() & 4) == 0;
                    if (this.setBlockTypeIfLoaded(world, x, y, z, bright ? Material.SEA_LANTERN : Material.AIR, true)) {
                        writes.incrementAndGet();
                        final int light = world.getBlockAt(x, y, z).getLightLevel();
                        lightReads.incrementAndGet();
                        if (bright && light > 0) {
                            brightSamples.incrementAndGet();
                        }
                        if (!bright && light == 0) {
                            darkSamples.incrementAndGet();
                        }
                    } else {
                        skippedUnloaded.incrementAndGet();
                    }
                }, 1L, periodTicks);
                this.plugin.trackTask(scheduledTask);
                queued.incrementAndGet();
            } catch (final RejectedExecutionException rejectedExecutionException) {
                rejected.incrementAndGet();
            }
        }

        final int summaryDelayTicks = Math.max(20, ticks + periodTicks + 2);
        this.plugin.trackTask(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
            try {
                this.replyLater(sender, "lighting load finished: cells=" + cells
                    + ", ticks=" + ticks
                    + ", periodTicks=" + periodTicks
                    + ", queued=" + queued.get()
                    + ", rejected=" + rejected.get()
                    + ", writes=" + writes.get()
                    + ", lightReads=" + lightReads.get()
                    + ", brightSamples=" + brightSamples.get()
                    + ", darkSamples=" + darkSamples.get()
                    + ", skippedUnloaded=" + skippedUnloaded.get());
            } finally {
                this.plugin.untrackTask(task);
            }
        }, summaryDelayTicks));

        sender.sendMessage("Queued lighting load: cells=" + cells
            + ", ticks=" + ticks
            + ", periodTicks=" + periodTicks
            + ", queued=" + queued.get()
            + ", rejected=" + rejected.get());
        return true;
    }

    private boolean handleMobFarmLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt mobfarm <spawners> [mobsPerWave=3] [lifeTicks=1200] [periodTicks=20] [spread=32]");
            return true;
        }

        final Integer spawners = this.parseInt(sender, args[1], "spawners");
        final Integer mobsPerWave = args.length >= 3 ? this.parseInt(sender, args[2], "mobsPerWave") : 3;
        final Integer lifeTicks = args.length >= 4 ? this.parseInt(sender, args[3], "lifeTicks") : 1200;
        final Integer periodTicks = args.length >= 5 ? this.parseInt(sender, args[4], "periodTicks") : 20;
        final Integer spread = args.length >= 6 ? this.parseInt(sender, args[5], "spread") : 32;
        if (spawners == null || mobsPerWave == null || lifeTicks == null || periodTicks == null || spread == null) {
            return true;
        }
        if (spawners < 1 || spawners > 64 || mobsPerWave < 1 || mobsPerWave > 12
            || lifeTicks < 1 || periodTicks < 1 || spread < 1 || spread > 192) {
            sender.sendMessage("spawners must be 1..64, mobsPerWave 1..12, lifeTicks/periodTicks positive, spread 1..192.");
            return true;
        }

        int queued = 0;
        int rejected = 0;
        for (int i = 0; i < spawners; i++) {
            final double dx = this.offset(i * 13, spread);
            final double dz = this.offset(i * 29, spread);
            final Location spawnHint = base.clone().add(dx, 0.0D, dz);
            if (this.queueMobFarmSpawner(base, spawnHint, mobsPerWave, lifeTicks, periodTicks, commandBatch, i)) {
                queued++;
            } else {
                rejected++;
            }
        }

        sender.sendMessage("Queued mob farm load: spawners=" + spawners
            + ", mobsPerWave=" + mobsPerWave + ", lifeTicks=" + lifeTicks
            + ", periodTicks=" + periodTicks + ", spread=" + spread
            + ", queued=" + queued + ", rejected=" + rejected);
        return true;
    }

    private boolean queueMobFarmSpawner(
        final Location base,
        final Location spawnHint,
        final int mobsPerWave,
        final int lifeTicks,
        final int periodTicks,
        final long commandBatch,
        final int spawnerIndex
    ) {
        final Location spawnLocation = this.surfaceSpawnLocation(spawnHint);
        final AtomicInteger remainingTicks = new AtomicInteger(lifeTicks);
        final AtomicInteger wave = new AtomicInteger();
        try {
            final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, spawnLocation, task -> {
                if (this.plugin.shouldAbortBatch(commandBatch) || remainingTicks.addAndGet(-periodTicks) < 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    return;
                }

                final World world = Objects.requireNonNull(spawnLocation.getWorld());
                if (!this.setBlockTypeIfLoaded(world, spawnLocation.getBlockX(), spawnLocation.getBlockY() - 1, spawnLocation.getBlockZ(), Material.STONE, false)) {
                    return;
                }
                final int currentWave = wave.getAndIncrement();
                for (int i = 0; i < mobsPerWave; i++) {
                    this.spawnMobFarmEntity(base, spawnLocation.clone().add((i % 3) - 1.0D, 0.0D, i / 3.0D), lifeTicks, spawnerIndex + currentWave + i);
                }
                if ((currentWave & 1) == 0) {
                    this.spawnFarmDrops(spawnLocation, lifeTicks);
                }
            }, 1L, periodTicks);
            this.plugin.trackTask(scheduledTask);
            return true;
        } catch (final RejectedExecutionException rejectedExecutionException) {
            return false;
        }
    }

    private void spawnMobFarmEntity(final Location base, final Location spawnLocation, final int lifeTicks, final int selector) {
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.isChunkLoaded(spawnLocation)) {
            return;
        }
        final Entity entity = switch (Math.floorMod(selector, 4)) {
            case 0 -> world.spawn(spawnLocation, Zombie.class, mob -> {
                mob.setAdult();
                mob.setCanPickupItems(false);
                mob.setRemoveWhenFarAway(false);
                if (mob.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                    Objects.requireNonNull(mob.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.42D);
                }
            });
            case 1 -> world.spawn(spawnLocation, Skeleton.class, mob -> {
                mob.setRemoveWhenFarAway(false);
                mob.setCanPickupItems(false);
            });
            case 2 -> world.spawn(spawnLocation, Creeper.class, mob -> {
                mob.setRemoveWhenFarAway(false);
                mob.setExplosionRadius(2);
            });
            default -> world.spawn(spawnLocation, Slime.class, mob -> {
                mob.setRemoveWhenFarAway(false);
                mob.setSize(2);
            });
        };
        final Vector towardBase = new Vector(
            base.getX() - spawnLocation.getX(),
            0.15D,
            base.getZ() - spawnLocation.getZ()
        );
        if (towardBase.lengthSquared() > 0.001D) {
            entity.setVelocity(towardBase.normalize().multiply(0.35D));
        }
        this.trackAndRemove(entity, lifeTicks);
    }

    private void spawnFarmDrops(final Location spawnLocation, final int lifeTicks) {
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.isChunkLoaded(spawnLocation)) {
            return;
        }
        final Item item = world.dropItem(spawnLocation.clone().add(0.0D, 0.4D, 0.0D), new ItemStack(Material.ROTTEN_FLESH, 1));
        item.setPickupDelay(20);
        this.trackAndRemove(item, Math.min(lifeTicks, 600));
        final ExperienceOrb orb = world.spawn(spawnLocation.clone().add(0.0D, 0.8D, 0.0D), ExperienceOrb.class, entity -> entity.setExperience(1));
        this.trackAndRemove(orb, Math.min(lifeTicks, 600));
    }

    private boolean handlePvpProjectileLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt pvp <launchers> [projectilesPerBurst=4] [lifeTicks=600] [periodTicks=8] [spread=48] [tntEvery=4]");
            return true;
        }

        final Integer launchers = this.parseInt(sender, args[1], "launchers");
        final Integer projectilesPerBurst = args.length >= 3 ? this.parseInt(sender, args[2], "projectilesPerBurst") : 4;
        final Integer lifeTicks = args.length >= 4 ? this.parseInt(sender, args[3], "lifeTicks") : 600;
        final Integer periodTicks = args.length >= 5 ? this.parseInt(sender, args[4], "periodTicks") : 8;
        final Integer spread = args.length >= 6 ? this.parseInt(sender, args[5], "spread") : 48;
        final Integer tntEvery = args.length >= 7 ? this.parseInt(sender, args[6], "tntEvery") : 4;
        if (launchers == null || projectilesPerBurst == null || lifeTicks == null || periodTicks == null || spread == null || tntEvery == null) {
            return true;
        }
        if (launchers < 1 || launchers > 64 || projectilesPerBurst < 1 || projectilesPerBurst > 16
            || lifeTicks < 1 || periodTicks < 1 || spread < 4 || spread > 256 || tntEvery < 0) {
            sender.sendMessage("launchers 1..64, projectilesPerBurst 1..16, lifeTicks/periodTicks positive, spread 4..256, tntEvery >= 0.");
            return true;
        }

        int queued = 0;
        int rejected = 0;
        for (int i = 0; i < launchers; i++) {
            final double angle = (Math.PI * 2.0D * i) / Math.max(1, launchers);
            final Location launcher = base.clone().add(Math.cos(angle) * spread, 2.0D, Math.sin(angle) * spread);
            if (this.queueProjectileLauncher(base, launcher, projectilesPerBurst, lifeTicks, periodTicks, tntEvery, commandBatch, i)) {
                queued++;
            } else {
                rejected++;
            }
        }

        sender.sendMessage("Queued PVP projectile load: launchers=" + launchers
            + ", projectilesPerBurst=" + projectilesPerBurst + ", lifeTicks=" + lifeTicks
            + ", periodTicks=" + periodTicks + ", spread=" + spread + ", tntEvery=" + tntEvery
            + ", queued=" + queued + ", rejected=" + rejected);
        return true;
    }

    private boolean queueProjectileLauncher(
        final Location target,
        final Location launcher,
        final int projectilesPerBurst,
        final int lifeTicks,
        final int periodTicks,
        final int tntEvery,
        final long commandBatch,
        final int launcherIndex
    ) {
        final AtomicInteger remainingTicks = new AtomicInteger(lifeTicks);
        final AtomicInteger burst = new AtomicInteger();
        try {
            final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, launcher, task -> {
                if (this.plugin.shouldAbortBatch(commandBatch) || remainingTicks.addAndGet(-periodTicks) < 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    return;
                }

                final World world = Objects.requireNonNull(launcher.getWorld());
                if (!this.setBlockTypeIfLoaded(world, launcher.getBlockX(), launcher.getBlockY() - 1, launcher.getBlockZ(), Material.STONE, false)) {
                    return;
                }
                final int currentBurst = burst.getAndIncrement();
                for (int i = 0; i < projectilesPerBurst; i++) {
                    final Location origin = launcher.clone().add(0.0D, 1.2D + (i % 2) * 0.2D, 0.0D);
                    final Vector direction = new Vector(
                        target.getX() - origin.getX(),
                        target.getY() + 1.0D - origin.getY(),
                        target.getZ() - origin.getZ()
                    );
                    if (direction.lengthSquared() < 0.001D) {
                        continue;
                    }
                    final Arrow arrow = world.spawnArrow(origin, direction.normalize(), 2.4F, 7.5F);
                    arrow.setCritical(true);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setKnockbackStrength(1);
                    this.trackAndRemove(arrow, Math.min(lifeTicks, 400));
                }
                if (tntEvery > 0 && currentBurst % tntEvery == launcherIndex % tntEvery) {
                    this.spawnChaosTnt(launcher.clone().add(0.0D, 0.8D, 0.0D), target, Math.min(80, Math.max(30, periodTicks * 6)));
                }
            }, 1L, periodTicks);
            this.plugin.trackTask(scheduledTask);
            return true;
        } catch (final RejectedExecutionException rejectedExecutionException) {
            return false;
        }
    }

    private void spawnChaosTnt(final Location spawnLocation, final Location target, final int fuseTicks) {
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.isChunkLoaded(spawnLocation)) {
            return;
        }
        final TNTPrimed tnt = world.spawn(spawnLocation, TNTPrimed.class, entity -> {
            entity.setFuseTicks(fuseTicks);
            entity.setYield(2.0F);
            entity.setIsIncendiary(false);
        });
        final Vector direction = new Vector(
            target.getX() - spawnLocation.getX(),
            0.35D,
            target.getZ() - spawnLocation.getZ()
        );
        if (direction.lengthSquared() > 0.001D) {
            tnt.setVelocity(direction.normalize().multiply(0.45D));
        }
        this.trackAndRemove(tnt, fuseTicks + 100);
    }

    private boolean handleVehiclePassengerLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt vehicles <count> [lifeTicks=1200] [spread=48]");
            return true;
        }

        final Integer count = this.parseInt(sender, args[1], "count");
        final Integer lifeTicks = args.length >= 3 ? this.parseInt(sender, args[2], "lifeTicks") : 1200;
        final Integer spread = args.length >= 4 ? this.parseInt(sender, args[3], "spread") : 48;
        if (count == null || lifeTicks == null || spread == null) {
            return true;
        }
        if (count < 1 || count > 128 || lifeTicks < 1 || spread < 1 || spread > 256) {
            sender.sendMessage("count must be 1..128, lifeTicks positive, spread 1..256.");
            return true;
        }

        final AtomicInteger queuedTasks = new AtomicInteger();
        final AtomicInteger completedTasks = new AtomicInteger();
        final AtomicInteger rejectedTasks = new AtomicInteger();
        final AtomicInteger spawned = new AtomicInteger();
        final AtomicInteger skippedUnloaded = new AtomicInteger();
        final AtomicInteger mounted = new AtomicInteger();
        final AtomicInteger crossChunkMoves = new AtomicInteger();
        final AtomicInteger passengerRetained = new AtomicInteger();
        final AtomicInteger removed = new AtomicInteger();
        for (int i = 0; i < count; i++) {
            final int index = i;
            final double dx = this.offset(i * 7, spread);
            final double dz = this.offset(i * 19, spread);
            final Location spawnHint = base.clone().add(dx, 0.0D, dz);
            try {
                final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnHint, scheduledTask -> {
                    try {
                        if (!this.plugin.shouldAbortBatch(commandBatch)) {
                            this.spawnVehiclePassenger(base, spawnHint, lifeTicks, index, spawned, skippedUnloaded,
                                mounted, crossChunkMoves, passengerRetained, removed);
                        }
                    } finally {
                        completedTasks.incrementAndGet();
                        this.plugin.untrackTask(scheduledTask);
                    }
                });
                this.plugin.trackTask(task);
                queuedTasks.incrementAndGet();
            } catch (final RejectedExecutionException rejectedExecutionException) {
                rejectedTasks.incrementAndGet();
            }
        }

        final int summaryDelayTicks = Math.max(40, lifeTicks + 20);
        this.plugin.trackTask(Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task -> {
            try {
                this.replyLater(sender, "vehicle/passenger load finished: requested=" + count
                    + ", lifeTicks=" + lifeTicks + ", spread=" + spread
                    + ", queued=" + queuedTasks.get() + ", completed=" + completedTasks.get()
                    + ", rejected=" + rejectedTasks.get() + ", spawned=" + spawned.get()
                    + ", skippedUnloaded=" + skippedUnloaded.get() + ", mounted=" + mounted.get()
                    + ", crossChunkMoves=" + crossChunkMoves.get() + ", passengerRetained=" + passengerRetained.get()
                    + ", removed=" + removed.get());
            } finally {
                this.plugin.untrackTask(task);
            }
        }, summaryDelayTicks));

        sender.sendMessage("Queued vehicle/passenger load: count=" + count
            + ", lifeTicks=" + lifeTicks + ", spread=" + spread
            + ", queued=" + queuedTasks.get() + ", rejected=" + rejectedTasks.get());
        return true;
    }

    private void spawnVehiclePassenger(
        final Location base,
        final Location spawnHint,
        final int lifeTicks,
        final int index,
        final AtomicInteger spawned,
        final AtomicInteger skippedUnloaded,
        final AtomicInteger mounted,
        final AtomicInteger crossChunkMoves,
        final AtomicInteger passengerRetained,
        final AtomicInteger removed
    ) {
        final Location spawnLocation = this.surfaceSpawnLocation(spawnHint);
        final World world = Objects.requireNonNull(spawnLocation.getWorld());
        if (!this.setBlockTypeIfLoaded(world, spawnLocation.getBlockX(), spawnLocation.getBlockY() - 1, spawnLocation.getBlockZ(), Material.REDSTONE_BLOCK, false)
            || !this.setBlockTypeIfLoaded(world, spawnLocation.getBlockX(), spawnLocation.getBlockY(), spawnLocation.getBlockZ(), Material.POWERED_RAIL, false)) {
            skippedUnloaded.incrementAndGet();
            return;
        }
        final Minecart cart = world.spawn(spawnLocation.clone().add(0.0D, 0.2D, 0.0D), Minecart.class);
        final Skeleton passenger = world.spawn(spawnLocation.clone().add(0.0D, 0.4D, 0.0D), Skeleton.class, entity -> {
            entity.setRemoveWhenFarAway(false);
            entity.setCanPickupItems(false);
        });
        spawned.incrementAndGet();
        if (cart.addPassenger(passenger)) {
            mounted.incrementAndGet();
        }
        this.trackAndRemove(cart, lifeTicks, removed);
        this.trackAndRemove(passenger, lifeTicks, removed);

        final AtomicInteger remainingTicks = new AtomicInteger(lifeTicks);
        final AtomicBoolean crossedChunk = new AtomicBoolean();
        final AtomicBoolean retainedPassenger = new AtomicBoolean();
        final int startChunkX = spawnLocation.getBlockX() >> 4;
        final int startChunkZ = spawnLocation.getBlockZ() >> 4;
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        try {
            final ScheduledTask motionTask = cart.getScheduler().runAtFixedRate(this.plugin, task -> {
                if (!cart.isValid() || remainingTicks.addAndGet(-2) < 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    return;
                }
                final Location current = cart.getLocation();
                if (!crossedChunk.get()
                    && ((current.getBlockX() >> 4) != startChunkX || (current.getBlockZ() >> 4) != startChunkZ)) {
                    if (crossedChunk.compareAndSet(false, true)) {
                        crossChunkMoves.incrementAndGet();
                    }
                }
                if (!retainedPassenger.get() && remainingTicks.get() <= lifeTicks - 40
                    && cart.getPassengers().contains(passenger)) {
                    if (retainedPassenger.compareAndSet(false, true)) {
                        passengerRetained.incrementAndGet();
                    }
                }
                final double xDirection = Math.copySign(1.0D, base.getX() - cart.getLocation().getX() + ((index & 1) == 0 ? 0.5D : -0.5D));
                final double zDirection = Math.copySign(0.25D, base.getZ() - cart.getLocation().getZ());
                cart.setVelocity(new Vector(xDirection * 0.55D, 0.0D, zDirection));
            }, () -> this.plugin.untrackTask(taskRef.get()), 1L, 2L);
            taskRef.set(motionTask);
            this.plugin.trackTask(motionTask);
        } catch (final RejectedExecutionException rejectedExecutionException) {
            cart.remove();
            passenger.remove();
            removed.addAndGet(2);
        }
    }

    private void trackAndRemove(final Entity entity, final int lifeTicks) {
        this.trackAndRemove(entity, lifeTicks, null);
    }

    private void trackAndRemove(final Entity entity, final int lifeTicks, final AtomicInteger removed) {
        this.plugin.trackEntity(entity);
        final UUID entityId = entity.getUniqueId();
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        try {
            final ScheduledTask removalTask = entity.getScheduler().runDelayed(this.plugin, task -> {
                try {
                    if (entity.isValid()) {
                        entity.remove();
                    }
                    if (removed != null) {
                        removed.incrementAndGet();
                    }
                } finally {
                    this.plugin.untrackTask(task);
                    this.plugin.untrackEntity(entityId);
                }
            }, () -> {
                this.plugin.untrackTask(taskRef.get());
                this.plugin.untrackEntity(entityId);
            }, Math.max(1, lifeTicks));
            taskRef.set(removalTask);
            this.plugin.trackTask(removalTask);
        } catch (final RejectedExecutionException rejectedExecutionException) {
            if (entity.isValid()) {
                entity.remove();
            }
            if (removed != null) {
                removed.incrementAndGet();
            }
            this.plugin.untrackEntity(entityId);
        }
    }

    private Location surfaceSpawnLocation(final Location hint) {
        final World world = Objects.requireNonNull(hint.getWorld());
        final int blockX = hint.getBlockX();
        final int blockZ = hint.getBlockZ();
        final int y = Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight() + 1, hint.getBlockY()));
        return new Location(world, blockX + 0.5D, y, blockZ + 0.5D, hint.getYaw(), hint.getPitch());
    }

    private boolean isChunkLoaded(final Location location) {
        final World world = Objects.requireNonNull(location.getWorld());
        return world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private boolean setBlockTypeIfLoaded(
        final World world,
        final int x,
        final int y,
        final int z,
        final Material material,
        final boolean applyPhysics
    ) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }
        world.getBlockAt(x, y, z).setType(material, applyPhysics);
        return true;
    }

    private boolean handleTrackerFlood(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt tracker <count> [ticks=200] [distance=3]");
            return true;
        }

        final Integer count = this.parseInt(sender, args[1], "count");
        final Integer ticks = args.length >= 3 ? this.parseInt(sender, args[2], "ticks") : 200;
        final Double distance = args.length >= 4 ? this.parseDouble(sender, args[3], "distance") : 3.0D;
        if (count == null || ticks == null || distance == null) {
            return true;
        }
        if (count < 1 || ticks < 1 || distance <= 0.0D) {
            sender.sendMessage("Count and ticks must be positive, distance must be greater than zero.");
            return true;
        }

        int queued = 0;
        int rejected = 0;
        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0D * i) / Math.max(1, count);
            final Location spawnHint = base.clone().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
            try {
                final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnHint, scheduledTask -> {
                    try {
                        if (this.plugin.shouldAbortBatch(commandBatch)) {
                            return;
                        }
                        this.spawnTrackerStand(base, spawnHint, angle, distance, ticks);
                    } finally {
                        this.plugin.untrackTask(scheduledTask);
                    }
                });
                this.plugin.trackTask(task);
                queued++;
            } catch (final RejectedExecutionException rejectedExecutionException) {
                rejected++;
            }
        }

        sender.sendMessage("Queued tracker flood: stands=" + count + ", ticks=" + ticks
            + ", distance=" + distance + ", queued=" + queued + ", rejected=" + rejected);
        return true;
    }

    private void spawnTrackerStand(
        final Location base,
        final Location spawnHint,
        final double angle,
        final double distance,
        final int ticks
    ) {
        final Location resting = spawnHint.clone();
        final Location shifted = resting.clone().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
        if (!this.isChunkLoaded(resting)) {
            return;
        }
        final ArmorStand stand = base.getWorld().spawn(resting, ArmorStand.class, entity -> {
            entity.setMarker(true);
            entity.setSmall(true);
            entity.setVisible(false);
            entity.setGravity(false);
            entity.setInvulnerable(true);
            entity.setCanMove(false);
            entity.customName(null);
        });

        final UUID entityId = stand.getUniqueId();
        this.plugin.trackEntity(stand);
        final AtomicInteger remaining = new AtomicInteger(ticks);
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        try {
            final ScheduledTask scheduledTask = stand.getScheduler().runAtFixedRate(this.plugin, task -> {
                if (!stand.isValid()) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    this.plugin.untrackEntity(entityId);
                    return;
                }
                if (remaining.getAndDecrement() <= 0) {
                    task.cancel();
                    this.plugin.untrackTask(task);
                    try {
                        stand.remove();
                    } finally {
                        this.plugin.untrackEntity(entityId);
                    }
                    return;
                }

                final Location target = (remaining.get() & 1) == 0 ? resting : shifted;
                stand.teleportAsync(target);
            }, () -> {
                this.plugin.untrackTask(taskRef.get());
                this.plugin.untrackEntity(entityId);
            }, 1L, 1L);
            taskRef.set(scheduledTask);
            this.plugin.trackTask(scheduledTask);
        } catch (final RejectedExecutionException rejectedExecutionException) {
            stand.remove();
            this.plugin.untrackEntity(entityId);
        }
    }

    private boolean handleBroadcastFlood(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt broadcast <chunks=1..64> <blocksPerChunk=1..4096> [ticks=200]");
            return true;
        }

        final Integer chunks = this.parseInt(sender, args[1], "chunks");
        final Integer blocksPerChunk = this.parseInt(sender, args[2], "blocksPerChunk");
        final Integer ticks = args.length >= 4 ? this.parseInt(sender, args[3], "ticks") : 200;
        if (chunks == null || blocksPerChunk == null || ticks == null) {
            return true;
        }
        if (chunks < 1 || chunks > 64 || blocksPerChunk < 1 || blocksPerChunk > 4096 || ticks < 1) {
            sender.sendMessage("chunks must be 1..64, blocksPerChunk must be 1..4096, and ticks must be positive.");
            return true;
        }

        final World world = Objects.requireNonNull(base.getWorld());
        final int anchorChunkX = base.getBlockX() >> 4;
        final int anchorChunkZ = base.getBlockZ() >> 4;
        final int regionMinChunkX = Math.floorDiv(anchorChunkX, 8) * 8;
        final int regionMinChunkZ = Math.floorDiv(anchorChunkZ, 8) * 8;
        final int y = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, base.getBlockY()));
        final List<int[]> targetChunks = new ArrayList<>(chunks);
        for (int chunkIndex = 0; chunkIndex < chunks; chunkIndex++) {
            final int chunkX = regionMinChunkX + (chunkIndex & 7);
            final int chunkZ = regionMinChunkZ + (chunkIndex >> 3);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                this.releaseBroadcastChunkTickets(world, targetChunks);
                sender.sendMessage("Broadcast target chunk is not loaded: " + chunkX + "," + chunkZ
                    + ". Run /rlt chunkgen for this area first.");
                return true;
            }
            this.plugin.trackChunkTicket(world, chunkX, chunkZ);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                this.plugin.untrackChunkTicket(world, chunkX, chunkZ);
                this.releaseBroadcastChunkTickets(world, targetChunks);
                sender.sendMessage("Broadcast target chunk unloaded before its plugin ticket was installed: " + chunkX + "," + chunkZ);
                return true;
            }
            targetChunks.add(new int[] {chunkX, chunkZ});
        }

        int queuedTasks = 0;
        final List<ScheduledTask> scheduledTasks = new ArrayList<>(targetChunks.size());
        for (final int[] targetChunk : targetChunks) {
            final int chunkX = targetChunk[0];
            final int chunkZ = targetChunk[1];
            final Location taskLocation = new Location(world, (chunkX << 4) + 8.0D, y, (chunkZ << 4) + 8.0D);
            final AtomicInteger remaining = new AtomicInteger(ticks);
            final AtomicInteger phase = new AtomicInteger();
            try {
                final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, taskLocation, task -> {
                    if (this.plugin.shouldAbortBatch(commandBatch) || !world.isChunkLoaded(chunkX, chunkZ)) {
                        task.cancel();
                        this.plugin.untrackTask(task);
                        this.plugin.untrackChunkTicket(world, chunkX, chunkZ);
                        return;
                    }
                    if (remaining.getAndDecrement() <= 0) {
                        task.cancel();
                        this.plugin.untrackTask(task);
                        this.plugin.untrackChunkTicket(world, chunkX, chunkZ);
                        return;
                    }
                    this.applyBroadcastBlockChanges(world, chunkX, chunkZ, y, blocksPerChunk, phase.getAndIncrement());
                }, 1L, 1L);
                this.plugin.trackTask(scheduledTask);
                scheduledTasks.add(scheduledTask);
                queuedTasks++;
            } catch (final RuntimeException schedulingFailure) {
                for (final ScheduledTask scheduledTask : scheduledTasks) {
                    scheduledTask.cancel();
                    this.plugin.untrackTask(scheduledTask);
                }
                this.releaseBroadcastChunkTickets(world, targetChunks);
                sender.sendMessage("Broadcast scheduling failed after queueing " + queuedTasks
                    + " task(s): " + schedulingFailure.getClass().getSimpleName());
                return true;
            }
        }

        sender.sendMessage("Queued broadcast flood: chunks=" + chunks + ", blocksPerChunk=" + blocksPerChunk
            + ", ticks=" + ticks + ", queuedTasks=" + queuedTasks + ", regionMinChunk=" + regionMinChunkX + "," + regionMinChunkZ);
        return true;
    }

    private void releaseBroadcastChunkTickets(final World world, final List<int[]> targetChunks) {
        for (final int[] targetChunk : targetChunks) {
            this.plugin.untrackChunkTicket(world, targetChunk[0], targetChunk[1]);
        }
    }

    private void applyBroadcastBlockChanges(
        final World world,
        final int chunkX,
        final int chunkZ,
        final int y,
        final int blocksPerChunk,
        final int phase
    ) {
        final Material material = (phase & 1) == 0 ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE;
        final int maxLayers = Math.max(1, world.getMaxHeight() - y - 1);
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (int blockIndex = 0; blockIndex < blocksPerChunk; blockIndex++) {
            final int localX = blockIndex & 15;
            final int localZ = (blockIndex >> 4) & 15;
            final int localY = y + ((blockIndex >> 8) % maxLayers);
            this.setBlockTypeIfLoaded(world, (chunkX << 4) + localX, localY, (chunkZ << 4) + localZ, material, false);
        }
    }

    private boolean handleSchedulerFlood(final CommandSender sender, final Location anchorOverride, final String[] args, final long commandBatch) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt scheduler <region|regionlocal|global|async> <tasks> [payloadIterations=0]");
            return true;
        }

        final String mode = args[1].toLowerCase(Locale.ROOT);
        final Integer tasks = this.parseInt(sender, args[2], "tasks");
        final Integer payloadIterations = args.length >= 4 ? this.parseInt(sender, args[3], "payloadIterations") : 0;
        if (tasks == null || payloadIterations == null) {
            return true;
        }
        if (tasks < 1 || payloadIterations < 0) {
            sender.sendMessage("tasks must be positive and payloadIterations must be zero or greater.");
            return true;
        }

        switch (mode) {
            case "global" -> {
                for (int i = 0; i < tasks; i++) {
                    this.plugin.trackTask(Bukkit.getGlobalRegionScheduler().run(this.plugin,
                        task -> this.burnScheduled(task, payloadIterations, commandBatch)));
                }
            }
            case "async" -> {
                for (int i = 0; i < tasks; i++) {
                    this.plugin.trackTask(Bukkit.getAsyncScheduler().runNow(this.plugin,
                        task -> this.burnScheduled(task, payloadIterations, commandBatch)));
                }
            }
            case "region", "regionlocal" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                final World world = anchor.getWorld();
                final int baseChunkX = anchor.getBlockX() >> 4;
                final int baseChunkZ = anchor.getBlockZ() >> 4;
                final int width = (int)Math.ceil(Math.sqrt(tasks));
                int queued = 0;
                int rejected = 0;
                for (int i = 0; i < tasks; i++) {
                    final int chunkX;
                    final int chunkZ;
                    if ("regionlocal".equals(mode)) {
                        chunkX = baseChunkX;
                        chunkZ = baseChunkZ;
                    } else {
                        final int dx = i % width;
                        final int dz = i / width;
                        chunkX = baseChunkX + dx;
                        chunkZ = baseChunkZ + dz;
                    }
                    try {
                        this.plugin.trackTask(Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ,
                            task -> this.burnScheduled(task, payloadIterations, commandBatch)));
                        queued++;
                    } catch (final RejectedExecutionException rejectedExecutionException) {
                        rejected++;
                    }
                }
                sender.sendMessage("Queued scheduler flood: mode=" + mode + ", tasks=" + tasks
                    + ", queued=" + queued + ", rejected=" + rejected
                    + ", payloadIterations=" + payloadIterations);
                return true;
            }
            default -> {
                sender.sendMessage("Unknown scheduler mode. Use region, regionlocal, global, or async.");
                return true;
            }
        }

        sender.sendMessage("Queued scheduler flood: mode=" + mode + ", tasks=" + tasks
            + ", payloadIterations=" + payloadIterations);
        return true;
    }

    private boolean handleWatchdogStall(final CommandSender sender, final Location anchorOverride, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt watchdogstall <global|region> <millis> [interruptible|uninterruptible]");
            return true;
        }

        final String mode = args[1].toLowerCase(Locale.ROOT);
        final Integer millis = this.parseInt(sender, args[2], "millis");
        if (millis == null) {
            return true;
        }
        if (millis < 1 || millis > 120_000) {
            sender.sendMessage("millis must be 1..120000.");
            return true;
        }
        final String stallStyle = args.length >= 4 ? args[3].toLowerCase(Locale.ROOT) : "interruptible";
        final boolean interruptible;
        if ("interruptible".equals(stallStyle)) {
            interruptible = true;
        } else if ("uninterruptible".equals(stallStyle)) {
            interruptible = false;
        } else {
            sender.sendMessage("Unknown watchdogstall style. Use interruptible or uninterruptible.");
            return true;
        }

        switch (mode) {
            case "global" -> {
                final ScheduledTask task = Bukkit.getGlobalRegionScheduler().run(this.plugin,
                    scheduledTask -> this.blockForWatchdog(scheduledTask, millis, interruptible));
                this.plugin.trackTask(task);
            }
            case "region" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, anchor,
                    scheduledTask -> this.blockForWatchdog(scheduledTask, millis, interruptible));
                this.plugin.trackTask(task);
            }
            default -> {
                sender.sendMessage("Unknown watchdogstall mode. Use global or region.");
                return true;
            }
        }

        sender.sendMessage("Queued watchdog stall: mode=" + mode + ", millis=" + millis + ", style=" + stallStyle);
        return true;
    }

    private void blockForWatchdog(final ScheduledTask task, final int millis, final boolean interruptible) {
        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        boolean interrupted = false;
        try {
            while (true) {
                final long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return;
                }
                try {
                    TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(250L)));
                } catch (final InterruptedException interruptedException) {
                    interrupted = true;
                    if (interruptible) {
                        return;
                    }
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            this.plugin.untrackTask(task);
        }
    }

    private boolean handleCrossRegionQueueProbe(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt crossqueue <chunkOffsetX> <tasks> [payloadIterations=0]");
            return true;
        }

        final Integer chunkOffsetX = this.parseInt(sender, args[1], "chunkOffsetX");
        final Integer tasks = this.parseInt(sender, args[2], "tasks");
        final Integer payloadIterations = args.length >= 4 ? this.parseInt(sender, args[3], "payloadIterations") : 0;
        if (chunkOffsetX == null || tasks == null || payloadIterations == null) {
            return true;
        }
        if (tasks < 1 || payloadIterations < 0) {
            sender.sendMessage("tasks must be positive and payloadIterations must be zero or greater.");
            return true;
        }

        final World world = Objects.requireNonNull(base.getWorld());
        final int sourceChunkX = base.getBlockX() >> 4;
        final int sourceChunkZ = base.getBlockZ() >> 4;
        final int targetChunkX = sourceChunkX + chunkOffsetX;
        final int targetChunkZ = sourceChunkZ;
        final AtomicInteger remaining = new AtomicInteger(tasks);
        final AtomicInteger queued = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();
        final AtomicInteger executed = new AtomicInteger();
        final AtomicInteger failed = new AtomicInteger();
        final long startedAt = System.nanoTime();

        try {
            final ScheduledTask sourceTask = Bukkit.getRegionScheduler().run(this.plugin, base, task -> {
                try {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        remaining.set(0);
                        this.replyLater(sender, "Cross-region queue probe aborted before target scheduling.");
                        return;
                    }
                    for (int i = 0; i < tasks; i++) {
                        try {
                            final ScheduledTask targetTask = Bukkit.getRegionScheduler().run(this.plugin, world, targetChunkX, targetChunkZ, target -> {
                                try {
                                    if (!this.plugin.shouldAbortBatch(commandBatch)) {
                                        this.plugin.burnCpu(payloadIterations);
                                        executed.incrementAndGet();
                                    }
                                } catch (final Throwable throwable) {
                                    failed.incrementAndGet();
                                    this.plugin.getLogger().warning("Cross-region target task failed: "
                                        + throwable.getClass().getName() + ": " + throwable.getMessage());
                                } finally {
                                    this.plugin.untrackTask(target);
                                    this.finishCrossRegionQueueProbeIfDone(
                                        sender,
                                        remaining,
                                        sourceChunkX,
                                        sourceChunkZ,
                                        targetChunkX,
                                        targetChunkZ,
                                        tasks,
                                        payloadIterations,
                                        startedAt,
                                        queued,
                                        rejected,
                                        executed,
                                        failed
                                    );
                                }
                            });
                            this.plugin.trackTask(targetTask);
                            queued.incrementAndGet();
                        } catch (final RejectedExecutionException rejectedExecutionException) {
                            rejected.incrementAndGet();
                            this.finishCrossRegionQueueProbeIfDone(
                                sender,
                                remaining,
                                sourceChunkX,
                                sourceChunkZ,
                                targetChunkX,
                                targetChunkZ,
                                tasks,
                                payloadIterations,
                                startedAt,
                                queued,
                                rejected,
                                executed,
                                failed
                            );
                        }
                    }
                } finally {
                    this.plugin.untrackTask(task);
                }
            });
            this.plugin.trackTask(sourceTask);
        } catch (final RuntimeException schedulingFailure) {
            sender.sendMessage("Could not schedule cross-region queue source task: "
                + schedulingFailure.getClass().getSimpleName() + ": " + schedulingFailure.getMessage());
            return true;
        }

        sender.sendMessage("Queued cross-region queue probe source: sourceChunk=" + sourceChunkX + "," + sourceChunkZ
            + ", targetChunk=" + targetChunkX + "," + targetChunkZ
            + ", tasks=" + tasks
            + ", payloadIterations=" + payloadIterations);
        return true;
    }

    private void finishCrossRegionQueueProbeIfDone(
        final CommandSender sender,
        final AtomicInteger remaining,
        final int sourceChunkX,
        final int sourceChunkZ,
        final int targetChunkX,
        final int targetChunkZ,
        final int tasks,
        final int payloadIterations,
        final long startedAt,
        final AtomicInteger queued,
        final AtomicInteger rejected,
        final AtomicInteger executed,
        final AtomicInteger failed
    ) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        final double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
        this.replyLater(sender, String.format(
            Locale.ROOT,
            "Cross-region queue probe finished: sourceChunk=%d,%d targetChunk=%d,%d tasks=%d queued=%d rejected=%d executed=%d failed=%d payloadIterations=%d elapsedMs=%.2f",
            sourceChunkX,
            sourceChunkZ,
            targetChunkX,
            targetChunkZ,
            tasks,
            queued.get(),
            rejected.get(),
            executed.get(),
            failed.get(),
            payloadIterations,
            elapsedMs
        ));
    }

    private void burnScheduled(final ScheduledTask task, final int payloadIterations, final long commandBatch) {
        try {
            if (!this.plugin.shouldAbortBatch(commandBatch)) {
                this.plugin.burnCpu(payloadIterations);
            }
        } finally {
            this.plugin.untrackTask(task);
        }
    }

    private boolean handleSyncLoadGuardProbe(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt syncload <chunkOffsetX> [attempts=1]");
            return true;
        }

        final Integer chunkOffsetX = this.parseInt(sender, args[1], "chunkOffsetX");
        final Integer attempts = args.length >= 3 ? this.parseInt(sender, args[2], "attempts") : 1;
        if (chunkOffsetX == null || attempts == null) {
            return true;
        }
        if (attempts < 1) {
            sender.sendMessage("attempts must be positive.");
            return true;
        }

        final World world = base.getWorld();
        final int sourceChunkX = base.getBlockX() >> 4;
        final int sourceChunkZ = base.getBlockZ() >> 4;
        final int targetChunkX = sourceChunkX + chunkOffsetX;
        final int targetChunkZ = sourceChunkZ;
        final int targetBlockX = (targetChunkX << 4) + 8;
        final int targetBlockZ = (targetChunkZ << 4) + 8;
        final AtomicInteger remaining = new AtomicInteger(attempts);
        final AtomicInteger guardRejections = new AtomicInteger();
        final AtomicInteger unexpectedSuccess = new AtomicInteger();
        final AtomicInteger unexpectedFailure = new AtomicInteger();
        final boolean loadedBefore = world.isChunkLoaded(targetChunkX, targetChunkZ);

        int queued = 0;
        for (int i = 0; i < attempts; i++) {
            try {
                final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().run(this.plugin, base, task -> {
                    try {
                        if (this.plugin.shouldAbortBatch(commandBatch)) {
                            return;
                        }
                        world.getHighestBlockYAt(targetBlockX, targetBlockZ);
                        unexpectedSuccess.incrementAndGet();
                    } catch (final IllegalStateException exception) {
                        if (exception.getMessage() != null && exception.getMessage().contains("Synchronous chunk load is not allowed")) {
                            guardRejections.incrementAndGet();
                        } else {
                            unexpectedFailure.incrementAndGet();
                            this.plugin.getLogger().warning("Unexpected IllegalStateException from syncload probe: " + exception.getMessage());
                        }
                    } catch (final Throwable throwable) {
                        unexpectedFailure.incrementAndGet();
                        this.plugin.getLogger().warning("Unexpected throwable from syncload probe: " + throwable.getClass().getName()
                            + ": " + throwable.getMessage());
                    } finally {
                        this.plugin.untrackTask(task);
                        this.finishSyncLoadGuardProbeIfDone(sender, remaining, sourceChunkX, sourceChunkZ, targetChunkX, targetChunkZ,
                            loadedBefore, attempts, guardRejections, unexpectedSuccess, unexpectedFailure);
                    }
                });
                this.plugin.trackTask(scheduledTask);
                queued++;
            } catch (final RuntimeException schedulingFailure) {
                unexpectedFailure.incrementAndGet();
                this.plugin.getLogger().warning("Could not schedule syncload probe task: " + schedulingFailure.getMessage());
                this.finishSyncLoadGuardProbeIfDone(sender, remaining, sourceChunkX, sourceChunkZ, targetChunkX, targetChunkZ,
                    loadedBefore, attempts, guardRejections, unexpectedSuccess, unexpectedFailure);
            }
        }

        sender.sendMessage("Queued sync-load guard probe: sourceChunk=" + sourceChunkX + "," + sourceChunkZ
            + ", targetChunk=" + targetChunkX + "," + targetChunkZ
            + ", loadedBefore=" + loadedBefore
            + ", attempts=" + attempts
            + ", queued=" + queued);
        return true;
    }

    private void finishSyncLoadGuardProbeIfDone(
        final CommandSender sender,
        final AtomicInteger remaining,
        final int sourceChunkX,
        final int sourceChunkZ,
        final int targetChunkX,
        final int targetChunkZ,
        final boolean loadedBefore,
        final int attempts,
        final AtomicInteger guardRejections,
        final AtomicInteger unexpectedSuccess,
        final AtomicInteger unexpectedFailure
    ) {
        if (remaining.decrementAndGet() != 0) {
            return;
        }
        this.replyLater(sender, "Sync-load guard probe finished: sourceChunk=" + sourceChunkX + "," + sourceChunkZ
            + ", targetChunk=" + targetChunkX + "," + targetChunkZ
            + ", loadedBefore=" + loadedBefore
            + ", attempts=" + attempts
            + ", guardRejections=" + guardRejections.get()
            + ", unexpectedSuccess=" + unexpectedSuccess.get()
            + ", unexpectedFailure=" + unexpectedFailure.get());
    }

    private boolean handleChunkGenerationLoad(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt chunkgen <radiusChunks> [urgent=false]");
            return true;
        }

        final Integer radius = this.parseInt(sender, args[1], "radiusChunks");
        final Boolean urgent = args.length >= 3 ? this.parseBoolean(sender, args[2], "urgent") : Boolean.FALSE;
        if (radius == null || urgent == null) {
            return true;
        }
        if (radius < 0) {
            sender.sendMessage("radiusChunks must be zero or greater.");
            return true;
        }

        this.startChunkBatch(sender, base, radius, true, urgent, commandBatch, "chunk generation");
        return true;
    }

    private boolean handleChunkLoadOnly(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt chunkload <radiusChunks> [urgent=false]");
            return true;
        }

        final Integer radius = this.parseInt(sender, args[1], "radiusChunks");
        final Boolean urgent = args.length >= 3 ? this.parseBoolean(sender, args[2], "urgent") : Boolean.FALSE;
        if (radius == null || urgent == null) {
            return true;
        }
        if (radius < 0) {
            sender.sendMessage("radiusChunks must be zero or greater.");
            return true;
        }

        this.startChunkBatch(sender, base, radius, false, urgent, commandBatch, "chunk load-only");
        return true;
    }

    private boolean handleScenario(final CommandSender sender, final Location base, final String[] args, final long commandBatch) {
        if (args.length < 5) {
            sender.sendMessage("Usage: /rlt scenario <gen|load> <regions> <strideChunks> <radiusChunks> [urgent=false] [samples=1200] [periodTicks=1]");
            return true;
        }

        final String mode = args[1].toLowerCase(Locale.ROOT);
        final boolean generate;
        if ("gen".equals(mode) || "chunkgen".equals(mode)) {
            generate = true;
        } else if ("load".equals(mode) || "chunkload".equals(mode)) {
            generate = false;
        } else {
            sender.sendMessage("mode must be gen or load.");
            return true;
        }

        final Integer regions = this.parseInt(sender, args[2], "regions");
        final Integer strideChunks = this.parseInt(sender, args[3], "strideChunks");
        final Integer radius = this.parseInt(sender, args[4], "radiusChunks");
        final Boolean urgent = args.length >= 6 ? this.parseBoolean(sender, args[5], "urgent") : Boolean.FALSE;
        final Integer samples = args.length >= 7 ? this.parseInt(sender, args[6], "samples") : 1200;
        final Integer periodTicks = args.length >= 8 ? this.parseInt(sender, args[7], "periodTicks") : 1;
        if (regions == null || strideChunks == null || radius == null || urgent == null || samples == null || periodTicks == null) {
            return true;
        }
        if (regions < 1 || strideChunks < 1 || radius < 0 || samples < 2 || periodTicks < 1) {
            sender.sendMessage("regions/strideChunks must be positive, radiusChunks >= 0, samples >= 2, periodTicks >= 1.");
            return true;
        }

        final AtomicInteger remainingScenarioBatches = new AtomicInteger(regions);
        this.startProbe(sender, base, samples, periodTicks, commandBatch, "scenario control",
            () -> remainingScenarioBatches.get() > 0);
        for (int i = 1; i <= regions; i++) {
            final Location loadAnchor = base.clone().add((double) strideChunks * 16.0D * i, 0.0D, 0.0D);
            this.startChunkBatch(sender, loadAnchor, radius, generate, urgent, commandBatch,
                "scenario " + (generate ? "chunk generation" : "chunk load-only") + " region=" + i,
                remainingScenarioBatches::decrementAndGet);
        }
        sender.sendMessage("Started scenario: mode=" + mode + ", regions=" + regions + ", strideChunks=" + strideChunks
            + ", radiusChunks=" + radius + ", urgent=" + urgent + ", minSamples=" + samples
            + ", periodTicks=" + periodTicks + ", probeExtendsUntilBatchesFinish=true");
        return true;
    }

    private boolean handleProbe(final CommandSender sender, final Location probeLocation, final String[] args, final long commandBatch) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /rlt probe <samples> [periodTicks=1]");
            return true;
        }

        final Integer samples = this.parseInt(sender, args[1], "samples");
        final Integer periodTicks = args.length >= 3 ? this.parseInt(sender, args[2], "periodTicks") : 1;
        if (samples == null || periodTicks == null) {
            return true;
        }
        if (samples < 2 || periodTicks < 1) {
            sender.sendMessage("samples must be at least 2 and periodTicks must be positive.");
            return true;
        }

        this.startProbe(sender, probeLocation, samples, periodTicks, commandBatch, "probe");
        return true;
    }

    private boolean handleStatus(final CommandSender sender) {
        final RegionLoadTestPlugin.StatusSnapshot status = this.plugin.statusSnapshot();
        sender.sendMessage("RegionLoadTest status: batch=" + status.batch()
            + ", cleanupRequested=" + status.cleanupRequested()
            + ", managedTasks=" + status.managedTasks()
            + ", managedEntities=" + status.managedEntities()
            + ", managedChunkTickets=" + status.managedChunkTickets()
            + ", cleanupPurgeTasks=" + status.cleanupPurgeTasks()
            + ", activeChunkBatches=" + status.activeChunkBatches());
        return true;
    }

    private boolean handlePlayerCheck(final CommandSender sender, final String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt playercheck <name> <present|absent> [world]");
            return true;
        }

        final String playerName = args[1];
        final String mode = args[2].toLowerCase(Locale.ROOT);
        final String expectedWorld = args.length >= 4 ? args[3] : null;
        if (!"present".equals(mode) && !"absent".equals(mode)) {
            sender.sendMessage("Usage: /rlt playercheck <name> <present|absent> [world]");
            return true;
        }

        final List<String> matches = new ArrayList<>();
        final List<String> worldMatches = new ArrayList<>();
        for (final Player player : Bukkit.getOnlinePlayers()) {
            if (!player.getName().equalsIgnoreCase(playerName)) {
                continue;
            }
            final String worldName = player.getWorld().getName();
            final String worldKey = player.getWorld().getKey().asString();
            final String descriptor = player.getName() + "@" + worldName + "(" + worldKey + ")";
            matches.add(descriptor);
            if (expectedWorld == null
                || expectedWorld.equalsIgnoreCase(worldName)
                || expectedWorld.equalsIgnoreCase(worldKey)) {
                worldMatches.add(descriptor);
            }
        }

        final boolean ok;
        if ("present".equals(mode)) {
            ok = expectedWorld == null ? matches.size() == 1 : matches.size() == 1 && worldMatches.size() == 1;
        } else {
            ok = expectedWorld == null ? matches.isEmpty() : worldMatches.isEmpty();
        }

        final String details = "name=" + playerName
            + ", mode=" + mode
            + ", expectedWorld=" + (expectedWorld == null ? "(any)" : expectedWorld)
            + ", totalMatches=" + matches.size()
            + ", worldMatches=" + worldMatches.size()
            + ", matches=" + (matches.isEmpty() ? "(none)" : String.join(";", matches));
        if (ok) {
            sender.sendMessage("RLT playercheck ok: " + details);
        } else {
            sender.sendMessage("RLT playercheck failed: " + details);
        }
        return true;
    }

    private void startChunkBatch(
        final CommandSender sender,
        final Location base,
        final int radius,
        final boolean generate,
        final boolean urgent,
        final long commandBatch,
        final String label
    ) {
        this.startChunkBatch(sender, base, radius, generate, urgent, commandBatch, label, null);
    }

    private void startChunkBatch(
        final CommandSender sender,
        final Location base,
        final int radius,
        final boolean generate,
        final boolean urgent,
        final long commandBatch,
        final String label,
        final Runnable onFinish
    ) {
        final World world = base.getWorld();
        final int centerChunkX = base.getBlockX() >> 4;
        final int centerChunkZ = base.getBlockZ() >> 4;
        final int total = (radius * 2 + 1) * (radius * 2 + 1);
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger nullResult = new AtomicInteger();
        final AtomicInteger failure = new AtomicInteger();
        final long startedAt = System.nanoTime();
        this.plugin.beginChunkBatch();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int chunkX = centerChunkX + dx;
                final int chunkZ = centerChunkZ + dz;
                world.getChunkAtAsync(chunkX, chunkZ, generate, urgent).whenComplete((chunk, throwable) -> {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        if (remaining.decrementAndGet() == 0) {
                            this.finishChunkBatch(onFinish);
                        }
                        return;
                    }
                    if (throwable == null && chunk != null) {
                        success.incrementAndGet();
                    } else if (throwable == null) {
                        nullResult.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                        this.plugin.getLogger().warning(label + " failed at "
                            + chunkX + "," + chunkZ + ": " + throwable.getMessage());
                    }

                    if (remaining.decrementAndGet() == 0) {
                        this.finishChunkBatch(onFinish);
                        final double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
                        this.replyLater(sender, String.format(
                            Locale.ROOT,
                            "%s batch finished: centerChunk=%d,%d total=%d success=%d null=%d failure=%d generate=%s urgent=%s elapsedMs=%.2f",
                            label,
                            centerChunkX,
                            centerChunkZ,
                            total,
                            success.get(),
                            nullResult.get(),
                            failure.get(),
                            generate,
                            urgent,
                            elapsedMs
                        ));
                    }
                });
            }
        }

        sender.sendMessage("Queued " + label + ": centerChunk=" + centerChunkX + "," + centerChunkZ
            + ", totalChunks=" + total + ", generate=" + generate + ", urgent=" + urgent);
    }

    private void finishChunkBatch(final Runnable onFinish) {
        this.plugin.finishChunkBatch();
        if (onFinish != null) {
            onFinish.run();
        }
    }

    private void startProbe(
        final CommandSender sender,
        final Location probeLocation,
        final int samples,
        final int periodTicks,
        final long commandBatch,
        final String label
    ) {
        this.startProbe(sender, probeLocation, samples, periodTicks, commandBatch, label, () -> false);
    }

    private void startProbe(
        final CommandSender sender,
        final Location probeLocation,
        final int samples,
        final int periodTicks,
        final long commandBatch,
        final String label,
        final BooleanSupplier continueAfterMinSamples
    ) {
        final long expectedPeriodNanos = TimeUnit.MILLISECONDS.toNanos(periodTicks * 50L);
        final AtomicInteger runs = new AtomicInteger();
        final AtomicLong lastRunNanos = new AtomicLong(System.nanoTime());
        final AtomicLong maxLagNanos = new AtomicLong(Long.MIN_VALUE);
        final AtomicLong totalLagNanos = new AtomicLong();
        final List<Long> lagSamples = new ArrayList<>(Math.max(1, samples - 1));

        final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, probeLocation, task -> {
            if (this.plugin.shouldAbortBatch(commandBatch)) {
                task.cancel();
                this.plugin.untrackTask(task);
                return;
            }
            final long now = System.nanoTime();
            final int current = runs.getAndIncrement();
            if (current > 0) {
                final long elapsed = now - lastRunNanos.getAndSet(now);
                final long lag = elapsed - expectedPeriodNanos;
                totalLagNanos.addAndGet(lag);
                maxLagNanos.accumulateAndGet(lag, Math::max);
                lagSamples.add(lag);
            } else {
                lastRunNanos.set(now);
            }

            if (runs.get() >= samples && !continueAfterMinSamples.getAsBoolean()) {
                task.cancel();
                this.plugin.untrackTask(task);
                final int measuredSamples = Math.max(1, lagSamples.size());
                final long[] measuredLags = new long[measuredSamples];
                for (int i = 0; i < lagSamples.size(); i++) {
                    measuredLags[i] = lagSamples.get(i);
                }
                Arrays.sort(measuredLags);
                final int p95Index = Math.min(measuredSamples - 1, Math.max(0, (int)Math.ceil(measuredSamples * 0.95D) - 1));
                final int p99Index = Math.min(measuredSamples - 1, Math.max(0, (int)Math.ceil(measuredSamples * 0.99D) - 1));
                final double avgLagMs = totalLagNanos.get() / (double) measuredSamples / 1_000_000.0D;
                final double maxLagMs = maxLagNanos.get() == Long.MIN_VALUE ? 0.0D : maxLagNanos.get() / 1_000_000.0D;
                final double p95LagMs = measuredLags[p95Index] / 1_000_000.0D;
                final double p99LagMs = measuredLags[p99Index] / 1_000_000.0D;
                this.replyLater(sender, String.format(
                    Locale.ROOT,
                    "%s finished at chunk=%d,%d: samples=%d periodTicks=%d avgLagMs=%.3f p95LagMs=%.3f p99LagMs=%.3f maxLagMs=%.3f",
                    label,
                    probeLocation.getBlockX() >> 4,
                    probeLocation.getBlockZ() >> 4,
                    runs.get(),
                    periodTicks,
                    avgLagMs,
                    p95LagMs,
                    p99LagMs,
                    maxLagMs
                ));
            }
        }, 1L, periodTicks);

        this.plugin.trackTask(scheduledTask);
        sender.sendMessage("Started " + label + " at chunk=" + (probeLocation.getBlockX() >> 4)
            + "," + (probeLocation.getBlockZ() >> 4) + " for samples=" + samples + ", periodTicks=" + periodTicks);
    }

    private void sendHelp(final CommandSender sender, final String label) {
        sender.sendMessage("RegionLoadTest commands:");
        sender.sendMessage("/" + label + " tntsingle <width> <depth> [spacing] [fuse] [regionChunks]");
        sender.sendMessage("/" + label + " tntspread <grids> <width> <depth> [spacing] [fuse] [regionChunks] [regionStride]");
        sender.sendMessage("/" + label + " villagers <count> [spread] [lifeTicks]");
        sender.sendMessage("/" + label + " path <count> [spread] [lifeTicks]");
        sender.sendMessage("/" + label + " redstone <lanes> [ticks] [length] [periodTicks]");
        sender.sendMessage("/" + label + " boundary <lanes> [ticks] [length] [periodTicks]");
        sender.sendMessage("/" + label + " lighting <cells> [ticks] [periodTicks]");
        sender.sendMessage("/" + label + " mobfarm <spawners> [mobsPerWave] [lifeTicks] [periodTicks] [spread]");
        sender.sendMessage("/" + label + " pvp <launchers> [projectilesPerBurst] [lifeTicks] [periodTicks] [spread] [tntEvery]");
        sender.sendMessage("/" + label + " vehicles <count> [lifeTicks] [spread]");
        sender.sendMessage("/" + label + " tracker <count> [ticks] [distance]");
        sender.sendMessage("/" + label + " broadcast <chunks> <blocksPerChunk> [ticks]");
        sender.sendMessage("/" + label + " scheduler <region|regionlocal|global|async> <tasks> [payloadIterations]");
        sender.sendMessage("/" + label + " watchdogstall <global|region> <millis> [interruptible|uninterruptible]");
        sender.sendMessage("/" + label + " crossqueue <chunkOffsetX> <tasks> [payloadIterations]");
        sender.sendMessage("/" + label + " syncload <chunkOffsetX> [attempts]");
        sender.sendMessage("/" + label + " chunkgen <radiusChunks> [urgent]");
        sender.sendMessage("/" + label + " chunkload <radiusChunks> [urgent]");
        sender.sendMessage("/" + label + " scenario <gen|load> <regions> <strideChunks> <radiusChunks> [urgent] [samples] [periodTicks]");
        sender.sendMessage("/" + label + " probe <samples> [periodTicks]");
        sender.sendMessage("/" + label + " playercheck <name> <present|absent> [world]");
        sender.sendMessage("/" + label + " status");
        sender.sendMessage("/" + label + " at <world> <x> <y> <z> <subcommand> [args...]");
        sender.sendMessage("/" + label + " cleanup");
    }

    private Location anchorFor(final CommandSender sender, final Location override) {
        if (override != null) {
            return override.clone();
        }
        if (sender instanceof Player player) {
            return player.getLocation().clone();
        }
        sender.sendMessage("This subcommand must be run by a player or with /rlt at <world> <x> <y> <z> <subcommand> [args...]");
        return null;
    }

    private boolean opensNewBatch(final String subcommand) {
        return !equalsAny(subcommand, "cleanup", "status", "playercheck", "help", "?");
    }

    private void replyLater(final CommandSender sender, final String message) {
        this.plugin.getLogger().info(message);
        if (sender instanceof Player player) {
            player.getScheduler().run(this.plugin, task -> player.sendMessage(message), () -> {});
            return;
        }
        Bukkit.getGlobalRegionScheduler().execute(this.plugin, () -> sender.sendMessage(message));
    }

    private List<String> filter(final List<String> source, final String token) {
        final String lower = token.toLowerCase(Locale.ROOT);
        final List<String> matches = new ArrayList<>();
        for (final String entry : source) {
            if (entry.startsWith(lower)) {
                matches.add(entry);
            }
        }
        return matches;
    }

    private boolean equalsAny(final String value, final String... options) {
        for (final String option : options) {
            if (option.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private Integer parseInt(final CommandSender sender, final String raw, final String name) {
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Invalid " + name + ": " + raw);
            return null;
        }
    }

    private Double parseDouble(final CommandSender sender, final String raw, final String name) {
        try {
            return Double.parseDouble(raw);
        } catch (final NumberFormatException ex) {
            sender.sendMessage("Invalid " + name + ": " + raw);
            return null;
        }
    }

    private Boolean parseBoolean(final CommandSender sender, final String raw, final String name) {
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        sender.sendMessage("Invalid " + name + ": " + raw + " (expected true or false)");
        return null;
    }

    private double offset(final int seed, final int bound) {
        final int normalized = Math.floorMod(seed * 31, bound * 2 + 1) - bound;
        return normalized;
    }

    private double localStep(final double value) {
        if (value == 0.0D) {
            return 0.0D;
        }
        return Math.copySign(Math.min(Math.abs(value), 3.0D), value);
    }

    private record SpawnSummary(int scheduled, int skipped) {
    }

    private record LogicalRegion(int startChunkX, int startChunkZ, int regionChunks) {
        static LogicalRegion fromChunk(final int chunkX, final int chunkZ, final int regionChunks) {
            return new LogicalRegion(
                Math.floorDiv(chunkX, regionChunks) * regionChunks,
                Math.floorDiv(chunkZ, regionChunks) * regionChunks,
                regionChunks
            );
        }

        boolean contains(final int chunkX, final int chunkZ) {
            return chunkX >= this.startChunkX
                && chunkX < this.startChunkX + this.regionChunks
                && chunkZ >= this.startChunkZ
                && chunkZ < this.startChunkZ + this.regionChunks;
        }
    }
}
