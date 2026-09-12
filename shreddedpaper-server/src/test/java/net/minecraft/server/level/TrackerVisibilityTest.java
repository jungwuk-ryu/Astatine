package net.minecraft.server.level;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import com.google.common.collect.ImmutableList;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.papermc.paper.configuration.WorldConfiguration;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.debug.LevelDebugSynchronizers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.spigotmc.AsyncCatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@AllFeatures
class TrackerVisibilityTest {
    private final ReferenceList<ServerPlayer> candidates = new ReferenceList<>(new ServerPlayer[0]);
    private final AtomicLong candidateVersion = new AtomicLong();
    private final AtomicLong gameTime = new AtomicLong(1);
    private ChunkMap map;
    private Entity entity;
    private ChunkMap.TrackedEntity tracker;
    private ServerEntity synchronizer;
    private NearbyPlayers.TrackedChunk chunk;
    private WorldConfiguration paperConfig;
    private MockedStatic<AsyncCatcher> asyncCatcher;
    private int previousLimit;
    private long previousFrequency;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        this.previousLimit = ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity;
        this.previousFrequency = ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency;
        ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity = 500;
        ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency = 20;
        // These tests exercise tracker behavior on one thread, without a running region scheduler.
        this.asyncCatcher = mockStatic(AsyncCatcher.class);
        final ServerLevel level = mock(ServerLevel.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        when(server.getScaledTrackingDistance(anyInt())).thenAnswer(call -> call.getArgument(0));
        when(level.getServer()).thenReturn(server);
        when(level.getGameTime()).thenAnswer(call -> this.gameTime.get());
        when(level.debugSynchronizers()).thenReturn(mock(LevelDebugSynchronizers.class));
        this.paperConfig = mock(WorldConfiguration.class);
        this.paperConfig.entities = mock(WorldConfiguration.Entities.class);
        this.paperConfig.entities.trackingRangeY = mock(WorldConfiguration.Entities.TrackingRangeY.class);
        when(level.paperConfig()).thenReturn(this.paperConfig);
        this.map = mock(ChunkMap.class);
        setField(this.map, "level", level);
        when(this.map.getPlayerViewDistance(any())).thenReturn(8);
        when(this.map.isChunkTracked(any(), anyInt(), anyInt())).thenReturn(true);
        this.entity = mock(Entity.class);
        when(this.entity.position()).thenReturn(Vec3.ZERO);
        when(this.entity.trackingPosition()).thenReturn(Vec3.ZERO);
        when(this.entity.blockPosition()).thenReturn(BlockPos.ZERO);
        when(this.entity.chunkPosition()).thenReturn(new ChunkPos(0, 0));
        when(this.entity.getEntityData()).thenReturn(mock(SynchedEntityData.class));
        when(this.entity.getPassengers()).thenReturn(ImmutableList.of());
        when(this.entity.getBukkitEntity()).thenReturn(mock(CraftEntity.class));
        when(this.entity.broadcastToPlayer(any())).thenReturn(true);
        this.tracker = this.map.new TrackedEntity(this.entity, 64, 1, false);
        this.synchronizer = mock(ServerEntity.class);
        setField(this.tracker, "serverEntity", this.synchronizer);
        this.chunk = mock(NearbyPlayers.TrackedChunk.class);
        when(this.chunk.getPlayers(NearbyPlayers.NearbyMapType.VIEW_DISTANCE)).thenReturn(this.candidates);
        when(this.chunk.getUpdateCount()).thenAnswer(call -> this.candidateVersion.get());
    }

    @AfterEach
    void restore() {
        ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity = this.previousLimit;
        ShreddedPaperConfiguration.get().optimizations.trackerFullUpdateFrequency = this.previousFrequency;
        if (this.asyncCatcher != null) this.asyncCatcher.close();
    }

    @Test
    void stableViewersAreStillValidatedOnceOnEveryTickIncludingFullUpdates() {
        final ServerPlayer first = viewer(1);
        final ServerPlayer second = viewer(2);
        tick();
        clearInvocations(this.entity, this.synchronizer);
        for (int i = 0; i < 40; ++i) tick();
        verify(this.entity, times(40)).broadcastToPlayer(first);
        verify(this.entity, times(40)).broadcastToPlayer(second);
        assertEquals(Set.of(first.connection, second.connection), this.tracker.seenBy);
        verify(this.synchronizer, times(0)).addPairing(any());
        verify(this.synchronizer, times(0)).removePairing(any());
    }

    @Test
    void visibilityAndBroadcastChangesTakeEffectWithoutCandidateChanges() {
        final ServerPlayer viewer = viewer(1);
        tick();
        when(viewer.getBukkitEntity().canSee(any(org.bukkit.entity.Entity.class))).thenReturn(false);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(viewer.getBukkitEntity().canSee(any(org.bukkit.entity.Entity.class))).thenReturn(true);
        tick();
        assertTrue(this.tracker.seenBy.contains(viewer.connection));
        when(this.entity.broadcastToPlayer(viewer)).thenReturn(false);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(this.entity.broadcastToPlayer(viewer)).thenReturn(true);
        tick();
        assertTrue(this.tracker.seenBy.contains(viewer.connection));
    }

    @Test
    void movementViewDistanceVerticalRangeAndSentChunksRemainLive() {
        final ServerPlayer viewer = viewer(1);
        tick();
        when(viewer.getX()).thenReturn(65.0);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(this.entity.getX()).thenReturn(64.0);
        tick();
        assertTrue(this.tracker.seenBy.contains(viewer.connection));
        when(this.map.getPlayerViewDistance(viewer)).thenReturn(0);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(this.map.getPlayerViewDistance(viewer)).thenReturn(8);
        tick();
        this.paperConfig.entities.trackingRangeY.enabled = true;
        when(this.paperConfig.entities.trackingRangeY.get(this.entity, -1)).thenReturn(8);
        when(viewer.getY()).thenReturn(9.0);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(viewer.getY()).thenReturn(8.0);
        tick();
        assertTrue(this.tracker.seenBy.contains(viewer.connection));
        when(this.map.isChunkTracked(eq(viewer), anyInt(), anyInt())).thenReturn(false);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        when(this.map.isChunkTracked(eq(viewer), anyInt(), anyInt())).thenReturn(true);
        tick();
        assertTrue(this.tracker.seenBy.contains(viewer.connection));
    }

    @Test
    void departedCandidatesAreRemovedEvenWhenPopulationSizeStaysTheSame() {
        final ServerPlayer departed = viewer(1);
        tick();
        this.candidates.remove(departed);
        final ServerPlayer arrived = viewer(2);
        tick();
        assertEquals(Set.of(arrived.connection), this.tracker.seenBy);
        verify(this.synchronizer).removePairing(departed);
        this.tracker.moonrise$tick(null);
        assertTrue(this.tracker.seenBy.isEmpty());
        tick();
        assertEquals(Set.of(arrived.connection), this.tracker.seenBy);
        when(this.chunk.getPlayers(NearbyPlayers.NearbyMapType.VIEW_DISTANCE)).thenReturn(null);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
    }

    @Test
    void viewerAddedOutsideTheCandidateListIsPrunedWithAnUnchangedChunkStamp() {
        final ServerPlayer first = viewer(1);
        final ServerPlayer outside = viewer(2);
        this.candidates.remove(outside);
        tick();
        this.tracker.updatePlayer(outside);
        assertTrue(this.tracker.seenBy.contains(outside.connection));
        tick();
        assertEquals(Set.of(first.connection), this.tracker.seenBy);
    }

    @Test
    void pairingCallbackInvalidatingAnEarlierViewerIsRecheckedInTheSameTick() {
        final ServerPlayer first = viewer(1);
        tick();
        final ServerPlayer second = viewer(2);
        doAnswer(call -> {
            when(first.getBukkitEntity().canSee(any(org.bukkit.entity.Entity.class))).thenReturn(false);
            return null;
        }).when(this.synchronizer).addPairing(second);
        tick();
        assertEquals(Set.of(second.connection), this.tracker.seenBy);
        verify(this.synchronizer).removePairing(first);
    }

    @Test
    void candidateChangesDuringValidationCannotLeaveAnEarlierViewerTracked() {
        final ServerPlayer first = viewer(1);
        final ServerPlayer second = viewer(2);
        tick();
        when(this.entity.broadcastToPlayer(second)).thenAnswer(call -> {
            if (this.candidates.remove(first)) this.candidateVersion.incrementAndGet();
            return true;
        });
        tick();
        assertEquals(Set.of(second.connection), this.tracker.seenBy);
    }

    @Test
    void limitedTrackingStillSelectsByDistanceAndPrunesHiddenViewersImmediately() {
        final ServerPlayer near = viewer(1);
        final ServerPlayer far = viewer(2);
        when(far.getX()).thenReturn(20.0);
        ShreddedPaperConfiguration.get().optimizations.maximumTrackersPerEntity = 1;
        tick();
        assertEquals(Set.of(near.connection), this.tracker.seenBy);
        when(near.getBukkitEntity().canSee(any(org.bukkit.entity.Entity.class))).thenReturn(false);
        tick();
        assertTrue(this.tracker.seenBy.isEmpty());
        this.gameTime.set(20);
        tick();
        assertEquals(Set.of(far.connection), this.tracker.seenBy);
    }

    @Test
    void randomizedMembershipAndEligibilityMatchAnIndependentFullScan() {
        final ServerPlayer[] viewers = new ServerPlayer[16];
        final boolean[] visible = new boolean[viewers.length];
        final double[] x = new double[viewers.length];
        for (int i = 0; i < viewers.length; ++i) {
            final int index = i;
            visible[i] = true;
            viewers[i] = viewer(i + 1);
            when(viewers[i].getX()).thenAnswer(call -> x[index]);
            when(viewers[i].getBukkitEntity().canSee(any(org.bukkit.entity.Entity.class))).thenAnswer(call -> visible[index]);
        }
        final Random random = new Random(0xA57A71);
        for (int step = 0; step < 300; ++step) {
            final int index = random.nextInt(viewers.length);
            switch (random.nextInt(3)) {
                case 0 -> visible[index] = !visible[index];
                case 1 -> x[index] = random.nextDouble() * 100.0;
                default -> {
                    if (!this.candidates.remove(viewers[index])) this.candidates.add(viewers[index]);
                    this.candidateVersion.incrementAndGet();
                }
            }
            tick();
            final Set<ServerPlayerConnection> expected = new HashSet<>();
            for (int i = 0; i < viewers.length; ++i) {
                if (this.candidates.contains(viewers[i]) && visible[i] && x[i] <= 64.0) expected.add(viewers[i].connection);
            }
            assertEquals(expected, this.tracker.seenBy, "step " + step);
            assertFalse(this.tracker.seenByLock.isLocked(), "tracker lock leaked");
        }
    }

    private ServerPlayer viewer(final int id) {
        final ServerPlayer player = mock(ServerPlayer.class);
        player.connection = mock(ServerGamePacketListenerImpl.class);
        when(player.connection.getPlayer()).thenReturn(player);
        when(player.getId()).thenReturn(id);
        final CraftPlayer bukkit = mock(CraftPlayer.class);
        when(bukkit.canSee(any(org.bukkit.entity.Entity.class))).thenReturn(true);
        when(player.getBukkitEntity()).thenReturn(bukkit);
        this.candidates.add(player);
        this.candidateVersion.incrementAndGet();
        return player;
    }

    private void tick() {
        this.tracker.moonrise$tick(this.chunk);
        this.gameTime.incrementAndGet();
    }

    private static void setField(final Object target, final String name, final Object value) throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
