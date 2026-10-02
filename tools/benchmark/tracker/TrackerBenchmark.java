package io.multipaper.benchmark;

import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader.PlayerChunkLoaderData;
import com.mojang.authlib.GameProfile;
import com.sun.management.ThreadMXBean;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.plugin.java.JavaPlugin;

/** Times the installed server's real tracker with detached entities; no mocked or copied tracker loop. */
public final class TrackerBenchmark extends JavaPlugin {
    private static volatile long sink;
    private final AtomicBoolean running = new AtomicBoolean();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final int count = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        final int hidden = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        if (count < 2 || count > 1000 || hidden < 0 || hidden > 1 || !getServer().getOnlinePlayers().isEmpty()) {
            sender.sendMessage("Use trackerbench <2..1000 players> <0..1 hidden per viewer> on an empty isolated server.");
            return true;
        }
        if (!this.running.compareAndSet(false, true)) return true;
        final var world = getServer().getWorlds().getFirst();
        getServer().getRegionScheduler().execute(this, world, 0, 0, () -> {
            try {
                if (command.getName().equals("ownershipbench")) OwnershipBenchmark.run(this, ((CraftWorld) world).getHandle(), count);
                else benchmark(((CraftWorld) world).getHandle(), count, hidden);
            } catch (Throwable exception) {
                getLogger().log(java.util.logging.Level.SEVERE, "TRACKER_BENCH_FAILED", exception);
            } finally {
                this.running.set(false);
            }
        });
        sender.sendMessage("Tracker benchmark queued.");
        return true;
    }

    @SuppressWarnings("unchecked")
    private void benchmark(ServerLevel level, int count, int hidden) throws Exception {
        final ServerPlayer[] players = new ServerPlayer[count];
        final ChunkMap.TrackedEntity[] trackers = new ChunkMap.TrackedEntity[count];
        final NearbyPlayers nearby = new NearbyPlayers(level);
        final NearbyPlayers.TrackedChunk chunk = new NearbyPlayers.TrackedChunk(0L, nearby);
        final var visibilityField = CraftPlayer.class.getDeclaredField("invertedVisibilityEntities");
        visibilityField.setAccessible(true);
        final var sendDistanceField = PlayerChunkLoaderData.class.getDeclaredField("lastSendDistance");
        sendDistanceField.setAccessible(true);
        for (int i = 0; i < count; ++i) {
            final var profile = new GameProfile(new UUID(12345, i + 1), "tracker" + i);
            final var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
            player.setPos(7 + (i % 13) / 6.5, -60, 7 + (i % 17) / 8.5);
            new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND), player,
                    CommonListenerCookie.createInitial(profile, false));
            final PlayerChunkLoaderData loader = new PlayerChunkLoaderData(level, player);
            // Detached loaders have not run add(), so initialize the applied view distance.
            sendDistanceField.setInt(loader, 2);
            loader.getSentChunksRaw().add(0L);
            player.moonrise$setChunkLoader(loader);
            players[i] = player;
            chunk.addPlayer(player, NearbyPlayers.NearbyMapType.VIEW_DISTANCE);
            trackers[i] = level.getChunkSource().chunkMap.new TrackedEntity(player, 64, 1, false);
        }
        for (int viewer = 0; viewer < count; ++viewer) {
            if (hidden != 0) {
                ((Map<UUID, Object>) visibilityField.get(players[viewer].getBukkitEntity()))
                        .put(players[(viewer + 1) % count].getUUID(), Set.of());
            }
            for (int target = 0; target < count; ++target) {
                if (viewer != target && (hidden == 0 || target != (viewer + 1) % count)) {
                    trackers[target].seenBy.add(players[viewer].connection);
                }
            }
        }
        final int previousLimit = ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity;
        final long previousFrequency = ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency;
        final ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isCurrentThreadCpuTimeSupported()) {
            throw new IllegalStateException("Thread allocation and CPU accounting are required");
        }
        bean.setThreadAllocatedMemoryEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
        try {
            ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity = count;
            ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency = 20;
            final int rounds = Math.max(2, 1_000_000 / (count * count));
            for (int i = 0; i < 20; ++i) sample(trackers, chunk, rounds, bean);
            final List<Sample> samples = new ArrayList<>();
            for (int i = 0; i < 9; ++i) {
                samples.add(sample(trackers, chunk, rounds, bean));
                // Full membership checks are outside the timed loops.
                for (int target = 0; target < count; ++target) {
                    if (trackers[target].seenBy.size() != count - 1 - hidden) throw new IllegalStateException("Unexpected viewer count");
                    for (int viewer = 0; viewer < count; ++viewer) {
                        final boolean expected = viewer != target && (hidden == 0 || target != (viewer + 1) % count);
                        if (trackers[target].seenBy.contains(players[viewer].connection) != expected) {
                            throw new IllegalStateException("Viewer membership changed");
                        }
                    }
                }
            }
            final String result = String.format(Locale.ROOT,
                    "{\"players\":%d,\"hiddenPerViewer\":%d,\"roundsPerSample\":%d,\"viewDistance\":2,\"layout\":\"clustered\",\"cpuNsPerTracker\":%.3f,\"wallNsPerTracker\":%.3f,\"bytesPerTracker\":%.3f,\"samples\":%s}",
                    count, hidden, rounds, median(samples.stream().mapToDouble(Sample::cpuNs).toArray()),
                    median(samples.stream().mapToDouble(Sample::wallNs).toArray()),
                    median(samples.stream().mapToDouble(Sample::bytes).toArray()), samples);
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("tracker-" + count + "-" + hidden + ".json"), result + "\n");
            getLogger().info("TRACKER_BENCH_DONE " + result);
        } finally {
            ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity = previousLimit;
            ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency = previousFrequency;
            for (ServerPlayer player : players) player.getTextFilter().leave();
        }
    }

    private static Sample sample(ChunkMap.TrackedEntity[] trackers, NearbyPlayers.TrackedChunk chunk, int rounds, ThreadMXBean bean) {
        final long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        final long cpu = bean.getCurrentThreadCpuTime();
        final long wall = System.nanoTime();
        for (int round = 0; round < rounds; ++round) {
            long viewers = 0;
            for (ChunkMap.TrackedEntity tracker : trackers) {
                tracker.moonrise$tick(chunk);
                viewers += tracker.seenBy.size();
            }
            sink = viewers;
        }
        final long elapsed = System.nanoTime() - wall;
        final long cpuElapsed = bean.getCurrentThreadCpuTime() - cpu;
        final long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
        final double ticks = (double) rounds * trackers.length;
        return new Sample(cpuElapsed / ticks, elapsed / ticks, bytes / ticks);
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private record Sample(double cpuNs, double wallNs, double bytes) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "{\"cpuNs\":%.3f,\"wallNs\":%.3f,\"bytes\":%.3f}", this.cpuNs, this.wallNs, this.bytes);
        }
    }
}
