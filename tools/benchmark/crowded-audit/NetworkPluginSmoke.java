package io.multipaper.audit;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import io.multipaper.shreddedpaper.network.RegionPacketFlushScope;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable validation only: never install this plugin on a production server. */
public final class NetworkPluginSmoke extends JavaPlugin implements Listener {
    private static final String CHANNEL = "astatine:perf-probe";
    private final ClientboundMoveEntityPacket[] packets = new ClientboundMoveEntityPacket[64];
    private final AtomicLong transformed = new AtomicLong();
    private final List<Long> roundTrips = new CopyOnWriteArrayList<>();

    @Override public void onEnable() {
        if (!Boolean.getBoolean("astatine.networkPluginSmoke")) throw new IllegalStateException("Disposable runner required");
        for (int i = 0; i < packets.length; i++) packets[i] = new ClientboundMoveEntityPacket.Rot(4_000_000 + i, (byte) 0, (byte) 0, true);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, (channel, player, data) -> {
            if (data.length != 8) throw new AssertionError("Invalid echo length");
            roundTrips.add(System.nanoTime() - ByteBuffer.wrap(data).getLong());
        });
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract() {
            @Override public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.ENTITY_ROTATION) return;
                final var wrapper = new WrapperPlayServerEntityRotation(event);
                if (wrapper.getEntityId() < 4_000_000 || wrapper.getEntityId() >= 4_000_064) return;
                final String name = event.getUser().getName();
                if (!name.matches("batch[0-3]")) throw new AssertionError("Unexpected test recipient");
                wrapper.setYaw((name.charAt(5) - '0') * 45.0F);
                event.markForReEncode(true);
                transformed.incrementAndGet();
            }
        });
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        final var player = event.getPlayer();
        player.getScheduler().runAtFixedRate(this, task -> {
            try (var scope = RegionPacketFlushScope.open()) {
                final var connection = ((CraftPlayer) player).getHandle().connection.connection;
                for (var packet : packets) connection.send(packet);
                player.sendPluginMessage(this, CHANNEL, ByteBuffer.allocate(8).putLong(System.nanoTime()).array());
            }
        }, null, 20, 1);
        if (player.getName().equals("batch3")) {
            final var nether = getServer().getWorld("world_nether");
            player.getScheduler().runDelayed(this, task -> player.teleportAsync(new Location(nether, 8, 100, 8)), null, 100);
            player.getScheduler().runDelayed(this, task -> player.teleportAsync(new Location(getServer().getWorld("world"), 8, -60, 8)), null, 200);
        }
    }

    @Override public void onDisable() {
        final long[] samples = roundTrips.stream().mapToLong(Long::longValue).sorted().toArray();
        if (transformed.get() < 10_000 || samples.length < 400) {
            getLogger().severe("NETWORK_PLUGIN_SMOKE_FAIL transformed=" + transformed.get() + " echoes=" + samples.length);
            return;
        }
        getLogger().info("NETWORK_PLUGIN_SMOKE_PASS transformed=" + transformed.get() + " echoes=" + samples.length
            + " rtt_p50_ms=" + samples[samples.length / 2] / 1e6
            + " rtt_p95_ms=" + samples[(int) (samples.length * .95)] / 1e6
            + " rtt_p99_ms=" + samples[(int) (samples.length * .99)] / 1e6);
    }
}
