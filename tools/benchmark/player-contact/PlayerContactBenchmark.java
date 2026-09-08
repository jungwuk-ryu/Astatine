package io.multipaper.benchmark;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import com.mojang.authlib.GameProfile;
import com.sun.management.ThreadMXBean;
import io.multipaper.shreddedpaper.entity.PlayerTouchQuery;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs the real section query with normally constructed entities, without sockets or mocks. */
public final class PlayerContactBenchmark extends JavaPlugin {
    private static final AABB BOX = new AABB(7, 64, 7, 9, 66, 9);
    private static volatile long sink;
    private static volatile List<Entity> querySink;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final int players = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        final int contacts = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        if (players < 1 || players > 2000 || contacts < 0 || contacts > 100 || !this.running.compareAndSet(false, true)) {
            sender.sendMessage("Use contactbench <1..2000 players> <0..100 items>; one run at a time.");
            return true;
        }
        final var world = getServer().getWorlds().getFirst();
        getServer().getRegionScheduler().execute(this, world, 0, 0, () -> {
            try {
                runBenchmark(((CraftWorld) world).getHandle(), players, contacts);
            } catch (Exception exception) {
                getLogger().log(java.util.logging.Level.SEVERE, "CONTACT_BENCH_FAILED", exception);
            } finally {
                this.running.set(false);
            }
        });
        sender.sendMessage("Contact query benchmark queued; results go to plugins/PlayerContactBenchmark.");
        return true;
    }

    private void runBenchmark(ServerLevel level, int playerCount, int contactCount) throws Exception {
        final ChunkEntitySlices slices = new ChunkEntitySlices(level, 0, 0, FullChunkStatus.ENTITY_TICKING, null, -4, 19);
        for (int i = 0; i < playerCount; ++i) {
            final ServerPlayer player = new ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "query" + i), ClientInformation.createDefault());
            player.setBoundingBox(BOX);
            slices.addEntity(player, 4);
        }
        for (int i = 0; i < contactCount; ++i) {
            final ItemEntity item = new ItemEntity(level, 8, 64, 8, new ItemStack(Items.DIAMOND));
            item.setBoundingBox(BOX);
            slices.addEntity(item, 4);
        }
        final List<Entity> expected = query(slices, EntitySelector.NO_SPECTATORS);
        if (expected.size() != playerCount + contactCount) {
            throw new AssertionError("Incomplete baseline population: " + expected.size());
        }
        expected.removeIf(entity -> !PlayerTouchQuery.isCandidate(entity));
        if (!expected.equals(query(slices, PlayerTouchQuery.PREDICATE))) {
            throw new AssertionError("Contact membership or order changed");
        }

        final ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) {
            throw new IllegalStateException("Thread allocation accounting is unavailable");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        final int queries = 2000;
        for (int warmup = 0; warmup < 10; ++warmup) {
            sample(slices, EntitySelector.NO_SPECTATORS, queries, bean);
            sample(slices, PlayerTouchQuery.PREDICATE, queries, bean);
        }
        final List<Sample> baseline = new ArrayList<>();
        final List<Sample> indexed = new ArrayList<>();
        for (int repeat = 0; repeat < 9; ++repeat) {
            if ((repeat & 1) == 0) {
                baseline.add(sample(slices, EntitySelector.NO_SPECTATORS, queries, bean));
                indexed.add(sample(slices, PlayerTouchQuery.PREDICATE, queries, bean));
            } else {
                indexed.add(sample(slices, PlayerTouchQuery.PREDICATE, queries, bean));
                baseline.add(sample(slices, EntitySelector.NO_SPECTATORS, queries, bean));
            }
        }
        final String result = String.format(Locale.ROOT,
                "{\"players\":%d,\"items\":%d,\"queriesPerSample\":%d,\"baselineNs\":%.3f,\"indexedNs\":%.3f,\"baselineBytes\":%.3f,\"indexedBytes\":%.3f,\"baselineSamples\":%s,\"indexedSamples\":%s}",
                playerCount, contactCount, queries, median(baseline, false), median(indexed, false),
                median(baseline, true), median(indexed, true), json(baseline), json(indexed));
        Files.createDirectories(getDataFolder().toPath());
        Files.writeString(getDataFolder().toPath().resolve("contacts-" + playerCount + "-" + contactCount + ".json"), result + "\n");
        getLogger().info("CONTACT_BENCH " + result);
        if (contactCount == 0) {
            benchmarkVisibility(slices, playerCount, bean);
        }
    }

    @SuppressWarnings("unchecked")
    private void benchmarkVisibility(ChunkEntitySlices slices, int count, ThreadMXBean bean) throws Exception {
        final CraftPlayer[] players = query(slices, EntitySelector.NO_SPECTATORS).stream()
                .map(entity -> ((ServerPlayer) entity).getBukkitEntity()).toArray(CraftPlayer[]::new);
        final Map<UUID, Object>[] maps = new Map[count];
        final var field = CraftPlayer.class.getDeclaredField("invertedVisibilityEntities");
        field.setAccessible(true);
        for (int i = 0; i < count; ++i) maps[i] = (Map<UUID, Object>) field.get(players[i]);
        for (int overrides : new int[]{0, 1}) {
            for (int i = 0; i < count; ++i) {
                maps[i].clear();
                if (overrides > 0) maps[i].put(players[(i + 1) % count].getUniqueId(), Set.of());
            }
            final int loops = Math.max(1, 500_000 / (count * count));
            for (int warm = 0; warm < 10; ++warm) {
                visibilitySample(players, maps, false, loops, bean);
                final long expected = sink;
                visibilitySample(players, maps, true, loops, bean);
                if (sink != expected) throw new AssertionError("Visibility results changed");
            }
            final List<Sample> baseline = new ArrayList<>(), optimized = new ArrayList<>();
            for (int repeat = 0; repeat < 9; ++repeat) {
                if ((repeat & 1) == 0) {
                    baseline.add(visibilitySample(players, maps, false, loops, bean));
                    optimized.add(visibilitySample(players, maps, true, loops, bean));
                } else {
                    optimized.add(visibilitySample(players, maps, true, loops, bean));
                    baseline.add(visibilitySample(players, maps, false, loops, bean));
                }
            }
            final String result = String.format(Locale.ROOT,
                    "{\"players\":%d,\"overridesPerViewer\":%d,\"checksPerSample\":%d,\"baselineNs\":%.3f,\"optimizedNs\":%.3f,\"baselineSamples\":%s,\"optimizedSamples\":%s}",
                    count, overrides, count * count * loops, median(baseline, false), median(optimized, false), json(baseline), json(optimized));
            Files.writeString(getDataFolder().toPath().resolve("visibility-" + count + "-" + overrides + ".json"), result + "\n");
            getLogger().info("VISIBILITY_BENCH " + result);
        }
        getLogger().info("BENCH_DONE players=" + count);
    }

    private static Sample visibilitySample(CraftPlayer[] players, Map<UUID, Object>[] maps, boolean optimized, int loops, ThreadMXBean bean) {
        final long thread = Thread.currentThread().threadId(), allocated = bean.getThreadAllocatedBytes(thread);
        final long start = System.nanoTime();
        long visible = 0;
        for (int loop = 0; loop < loops; ++loop) {
            for (int viewer = 0; viewer < players.length; ++viewer) {
                for (CraftPlayer target : players) {
                    final boolean canSee = optimized ? players[viewer].canSee((org.bukkit.entity.Entity) target)
                            : ShreddedPaperConfiguration.get().optimizations.disableVanishApi || players[viewer].equals(target)
                            || target.isVisibleByDefault() ^ maps[viewer].containsKey(target.getUniqueId());
                    if (canSee) ++visible;
                }
            }
        }
        final long elapsed = System.nanoTime() - start, bytes = bean.getThreadAllocatedBytes(thread) - allocated;
        sink = visible;
        final double checks = (double) loops * players.length * players.length;
        return new Sample(elapsed / checks, bytes / checks);
    }

    private static Sample sample(ChunkEntitySlices slices, Predicate<Entity> predicate, int queries, ThreadMXBean bean) {
        final long thread = Thread.currentThread().threadId();
        final long allocated = bean.getThreadAllocatedBytes(thread);
        final long start = System.nanoTime();
        long count = 0;
        for (int i = 0; i < queries; ++i) {
            final List<Entity> result = query(slices, predicate);
            count += result.size();
            querySink = result; // Keep empty queries and their returned lists observable to the JIT.
        }
        final long elapsed = System.nanoTime() - start;
        final long bytes = bean.getThreadAllocatedBytes(thread) - allocated;
        sink = count;
        return new Sample((double) elapsed / queries, (double) bytes / queries);
    }

    private static List<Entity> query(ChunkEntitySlices slices, Predicate<Entity> predicate) {
        final List<Entity> result = new ArrayList<>();
        slices.getEntities((Entity) null, BOX, result, predicate);
        return result;
    }

    private static double median(List<Sample> samples, boolean allocation) {
        final double[] values = samples.stream().mapToDouble(sample -> allocation ? sample.bytes : sample.nanos).toArray();
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private static String json(List<Sample> samples) {
        return samples.stream().map(sample -> String.format(Locale.ROOT,
                "{\"ns\":%.3f,\"bytes\":%.3f}", sample.nanos, sample.bytes)).collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private record Sample(double nanos, double bytes) {
    }
}
