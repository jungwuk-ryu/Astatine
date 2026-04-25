package io.multipaper.regionloadtest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionLoadTestCommand implements TabExecutor {

    private static final List<String> ROOT_SUBCOMMANDS = List.of(
        "help",
        "cleanup",
        "tntsingle",
        "tntspread",
        "path",
        "tracker",
        "scheduler",
        "chunkgen",
        "probe",
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
            case "scheduler" -> {
                return this.handleSchedulerFlood(sender, anchorOverride, effectiveArgs, commandBatch);
            }
            case "chunkgen" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleChunkGenerationLoad(sender, anchor, effectiveArgs, commandBatch);
            }
            case "probe" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                return this.handleProbe(sender, anchor, effectiveArgs, commandBatch);
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
            return this.filter(List.of("region", "global", "async"), args[1]);
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
                final ScheduledTask task = Bukkit.getRegionScheduler().run(this.plugin, spawnLocation, scheduledTask -> {
                    try {
                        if (this.plugin.shouldAbortBatch(commandBatch)) {
                            return;
                        }
                        this.spawnTnt(world, spawnLocation, fuseTicks);
                    } finally {
                        this.plugin.untrackTask(scheduledTask);
                    }
                });
                this.plugin.trackTask(task);
                scheduled++;
            }
        }
        return new SpawnSummary(scheduled, skipped);
    }

    private void spawnTnt(final World world, final Location spawnLocation, final int fuseTicks) {
        final Location safeLocation = spawnLocation.clone();
        safeLocation.setY(world.getHighestBlockYAt(safeLocation.getBlockX(), safeLocation.getBlockZ()) + 1.0D);
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
        spawnLocation.setY(base.getWorld().getHighestBlockYAt(spawnLocation.getBlockX(), spawnLocation.getBlockZ()) + 1.0D);
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
        resting.setY(base.getWorld().getHighestBlockYAt(resting.getBlockX(), resting.getBlockZ()) + 1.0D);
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

    private boolean handleSchedulerFlood(final CommandSender sender, final Location anchorOverride, final String[] args, final long commandBatch) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /rlt scheduler <region|global|async> <tasks> [payloadIterations=0]");
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
            case "region" -> {
                final Location anchor = this.anchorFor(sender, anchorOverride);
                if (anchor == null) {
                    return true;
                }
                final World world = anchor.getWorld();
                final int baseChunkX = anchor.getBlockX() >> 4;
                final int baseChunkZ = anchor.getBlockZ() >> 4;
                final int width = (int)Math.ceil(Math.sqrt(tasks));
                for (int i = 0; i < tasks; i++) {
                    final int dx = i % width;
                    final int dz = i / width;
                    final int chunkX = baseChunkX + dx;
                    final int chunkZ = baseChunkZ + dz;
                    this.plugin.trackTask(Bukkit.getRegionScheduler().run(this.plugin, world, chunkX, chunkZ,
                        task -> this.burnScheduled(task, payloadIterations, commandBatch)));
                }
            }
            default -> {
                sender.sendMessage("Unknown scheduler mode. Use region, global, or async.");
                return true;
            }
        }

        sender.sendMessage("Queued scheduler flood: mode=" + mode + ", tasks=" + tasks
            + ", payloadIterations=" + payloadIterations);
        return true;
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

        final World world = base.getWorld();
        final int centerChunkX = base.getBlockX() >> 4;
        final int centerChunkZ = base.getBlockZ() >> 4;
        final int total = (radius * 2 + 1) * (radius * 2 + 1);
        final AtomicInteger remaining = new AtomicInteger(total);
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger failure = new AtomicInteger();
        final long startedAt = System.nanoTime();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final int chunkX = centerChunkX + dx;
                final int chunkZ = centerChunkZ + dz;
                world.getChunkAtAsync(chunkX, chunkZ, true, urgent).whenComplete((chunk, throwable) -> {
                    if (this.plugin.shouldAbortBatch(commandBatch)) {
                        return;
                    }
                    if (throwable == null) {
                        success.incrementAndGet();
                    } else {
                        failure.incrementAndGet();
                        this.plugin.getLogger().warning("Chunk generation task failed at "
                            + chunkX + "," + chunkZ + ": " + throwable.getMessage());
                    }

                    if (remaining.decrementAndGet() == 0) {
                        final double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0D;
                        this.replyLater(sender, String.format(
                            Locale.ROOT,
                            "Chunk generation batch finished: total=%d success=%d failure=%d urgent=%s elapsedMs=%.2f",
                            total,
                            success.get(),
                            failure.get(),
                            urgent,
                            elapsedMs
                        ));
                    }
                });
            }
        }

        sender.sendMessage("Queued chunk generation load: totalChunks=" + total + ", urgent=" + urgent);
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

        final long expectedPeriodNanos = TimeUnit.MILLISECONDS.toNanos(periodTicks * 50L);
        final AtomicInteger remaining = new AtomicInteger(samples);
        final AtomicLong lastRunNanos = new AtomicLong(System.nanoTime());
        final AtomicLong maxLagNanos = new AtomicLong(Long.MIN_VALUE);
        final AtomicLong totalLagNanos = new AtomicLong();

        final ScheduledTask scheduledTask = Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, probeLocation, task -> {
            if (this.plugin.shouldAbortBatch(commandBatch)) {
                task.cancel();
                this.plugin.untrackTask(task);
                return;
            }
            final long now = System.nanoTime();
            final int current = samples - remaining.get();
            if (current > 0) {
                final long elapsed = now - lastRunNanos.getAndSet(now);
                final long lag = elapsed - expectedPeriodNanos;
                totalLagNanos.addAndGet(lag);
                maxLagNanos.accumulateAndGet(lag, Math::max);
            } else {
                lastRunNanos.set(now);
            }

            if (remaining.decrementAndGet() <= 0) {
                task.cancel();
                this.plugin.untrackTask(task);
                final int measuredSamples = Math.max(1, samples - 1);
                final double avgLagMs = totalLagNanos.get() / (double) measuredSamples / 1_000_000.0D;
                final double maxLagMs = maxLagNanos.get() == Long.MIN_VALUE ? 0.0D : maxLagNanos.get() / 1_000_000.0D;
                this.replyLater(sender, String.format(
                    Locale.ROOT,
                    "Probe finished at chunk=%d,%d: samples=%d periodTicks=%d avgLagMs=%.3f maxLagMs=%.3f",
                    probeLocation.getBlockX() >> 4,
                    probeLocation.getBlockZ() >> 4,
                    samples,
                    periodTicks,
                    avgLagMs,
                    maxLagMs
                ));
            }
        }, 1L, periodTicks);

        this.plugin.trackTask(scheduledTask);
        sender.sendMessage("Started normal-region probe at chunk=" + (probeLocation.getBlockX() >> 4)
            + "," + (probeLocation.getBlockZ() >> 4) + " for samples=" + samples + ", periodTicks=" + periodTicks);
        return true;
    }

    private void sendHelp(final CommandSender sender, final String label) {
        sender.sendMessage("RegionLoadTest commands:");
        sender.sendMessage("/" + label + " tntsingle <width> <depth> [spacing] [fuse] [regionChunks]");
        sender.sendMessage("/" + label + " tntspread <grids> <width> <depth> [spacing] [fuse] [regionChunks] [regionStride]");
        sender.sendMessage("/" + label + " path <count> [spread] [lifeTicks]");
        sender.sendMessage("/" + label + " tracker <count> [ticks] [distance]");
        sender.sendMessage("/" + label + " scheduler <region|global|async> <tasks> [payloadIterations]");
        sender.sendMessage("/" + label + " chunkgen <radiusChunks> [urgent]");
        sender.sendMessage("/" + label + " probe <samples> [periodTicks]");
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
