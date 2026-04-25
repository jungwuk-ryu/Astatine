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
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Zombie;

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
        "path",
        "tracker",
        "broadcast",
        "scheduler",
        "crossqueue",
        "syncload",
        "chunkgen",
        "chunkload",
        "scenario",
        "probe",
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
        final long commandBatch = "cleanup".equals(subcommand) ? this.plugin.currentBatch() : this.plugin.startBatch();

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
            case "path" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handlePathfindingLoad(sender, anchor, effectiveArgs, commandBatch);
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

        for (int i = 0; i < count; i++) {
            final double dx = this.offset(i, spread);
            final double dz = this.offset(i * 17, spread);
            final Location spawnHint = base.clone().add(dx, 0.0D, dz);
            final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnHint, scheduledTask -> {
                try {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        return;
                    }
                    this.spawnPathfindingMob(base, spawnHint, dx, dz, lifeTicks);
                } finally {
                    this.plugin.untrackTask(scheduledTask);
                }
            });
            this.plugin.trackTask(task);
        }

        sender.sendMessage("Queued pathfinding load: spawned=" + count + ", spread=" + spread + ", lifeTicks=" + lifeTicks);
        return true;
    }

    private void spawnPathfindingMob(
        final Location base,
        final Location spawnHint,
        final double dx,
        final double dz,
        final int lifeTicks
    ) {
        final Location spawnLocation = spawnHint.clone();
        final Zombie zombie = base.getWorld().spawn(spawnLocation, Zombie.class, mob -> {
            mob.setCanPickupItems(false);
            mob.setRemoveWhenFarAway(false);
            mob.setAdult();
            if (mob.getAttribute(Attribute.MOVEMENT_SPEED) != null) {
                Objects.requireNonNull(mob.getAttribute(Attribute.MOVEMENT_SPEED)).setBaseValue(0.35D);
            }
        });

        this.plugin.trackEntity(zombie);
        final Location destination = spawnLocation.clone().add(
            this.localStep(-dx),
            0.0D,
            this.localStep(-dz)
        );
        zombie.getPathfinder().moveTo(destination, 1.1D);

        final UUID entityId = zombie.getUniqueId();
        final AtomicReference<ScheduledTask> taskRef = new AtomicReference<>();
        final ScheduledTask removalTask = zombie.getScheduler().runDelayed(this.plugin, task -> {
            try {
                zombie.remove();
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

        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2.0D * i) / Math.max(1, count);
            final Location spawnHint = base.clone().add(Math.cos(angle) * 4.0D, 0.0D, Math.sin(angle) * 4.0D);
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
        }

        sender.sendMessage("Queued tracker flood: stands=" + count + ", ticks=" + ticks + ", distance=" + distance);
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
        for (int blockIndex = 0; blockIndex < blocksPerChunk; blockIndex++) {
            final int localX = blockIndex & 15;
            final int localZ = (blockIndex >> 4) & 15;
            final int localY = y + ((blockIndex >> 8) % maxLayers);
            world.getBlockAt((chunkX << 4) + localX, localY, (chunkZ << 4) + localZ).setType(material, false);
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
            + ", activeChunkBatches=" + status.activeChunkBatches());
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
                final double avgLagMs = totalLagNanos.get() / (double) measuredSamples / 1_000_000.0D;
                final double maxLagMs = maxLagNanos.get() == Long.MIN_VALUE ? 0.0D : maxLagNanos.get() / 1_000_000.0D;
                final double p95LagMs = measuredLags[p95Index] / 1_000_000.0D;
                this.replyLater(sender, String.format(
                    Locale.ROOT,
                    "%s finished at chunk=%d,%d: samples=%d periodTicks=%d avgLagMs=%.3f p95LagMs=%.3f maxLagMs=%.3f",
                    label,
                    probeLocation.getBlockX() >> 4,
                    probeLocation.getBlockZ() >> 4,
                    runs.get(),
                    periodTicks,
                    avgLagMs,
                    p95LagMs,
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
        sender.sendMessage("/" + label + " path <count> [spread] [lifeTicks]");
        sender.sendMessage("/" + label + " tracker <count> [ticks] [distance]");
        sender.sendMessage("/" + label + " broadcast <chunks> <blocksPerChunk> [ticks]");
        sender.sendMessage("/" + label + " scheduler <region|regionlocal|global|async> <tasks> [payloadIterations]");
        sender.sendMessage("/" + label + " crossqueue <chunkOffsetX> <tasks> [payloadIterations]");
        sender.sendMessage("/" + label + " syncload <chunkOffsetX> [attempts]");
        sender.sendMessage("/" + label + " chunkgen <radiusChunks> [urgent]");
        sender.sendMessage("/" + label + " chunkload <radiusChunks> [urgent]");
        sender.sendMessage("/" + label + " scenario <gen|load> <regions> <strideChunks> <radiusChunks> [urgent] [samples] [periodTicks]");
        sender.sendMessage("/" + label + " probe <samples> [periodTicks]");
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
