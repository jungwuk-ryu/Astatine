package io.multipaper.shreddedpaper.entity;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.ChunkEntitySlices;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerTouchQueryTest {

    private static final AABB BOX = new AABB(7, 64, 7, 9, 66, 9);
    private int nextId;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void omittedPlayerContactReallyIsANoOp() throws ReflectiveOperationException, IOException {
        assertEquals(Entity.class, ServerPlayer.class.getMethod("playerTouch", Player.class).getDeclaringClass());
        final ClassNode node = new ClassNode();
        try (var input = Entity.class.getResourceAsStream("Entity.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        final var method = node.methods.stream().filter(candidate -> candidate.name.equals("playerTouch")
                && candidate.desc.equals("(Lnet/minecraft/world/entity/player/Player;)V")).findFirst().orElseThrow();
        final List<Integer> instructions = new ArrayList<>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) {
                instructions.add(instruction.getOpcode());
            }
        }
        assertEquals(List.of(Opcodes.RETURN), instructions, "Revisit the index if vanilla adds player contact behavior");
    }

    @Test
    void crowdQueryDoesNotVisitPlayerBoundingBoxes() {
        final ChunkEntitySlices slices = slices();
        final List<ServerPlayer> players = new ArrayList<>();
        for (int i = 0; i < 32; ++i) {
            final ServerPlayer player = entity(ServerPlayer.class, EntityType.PLAYER);
            assertEquals(ServerPlayer.class, player.getClass());
            slices.addEntity(player, 4);
            players.add(player);
        }
        assertEquals(players, query(slices, null, EntitySelector.NO_SPECTATORS));
        players.forEach(player -> clearInvocations(player));

        assertTrue(query(slices, null, PlayerTouchQuery.PREDICATE).isEmpty());
        players.forEach(player -> verify(player, never()).getBoundingBox());
    }

    @Test
    void preservesPickupsContactDamageAndCustomPlayersInOrder() {
        final ChunkEntitySlices slices = slices();
        final List<Entity> contacts = List.of(
                entity(ItemEntity.class, EntityType.ITEM),
                entity(ExperienceOrb.class, EntityType.EXPERIENCE_ORB),
                entity(Arrow.class, EntityType.ARROW),
                entity(ThrownTrident.class, EntityType.TRIDENT),
                entity(Slime.class, EntityType.SLIME),
                entity(Pufferfish.class, EntityType.PUFFERFISH),
                entity(TouchingPlayer.class, EntityType.PLAYER)
        );
        for (final Entity contact : contacts) {
            slices.addEntity(entity(ServerPlayer.class, EntityType.PLAYER), 4);
            slices.addEntity(contact, 4);
        }
        assertEquals(contacts, query(slices, null, PlayerTouchQuery.PREDICATE));
        assertMatchesFullScan(slices, null);
    }

    @Test
    void respectsSpectatorsExclusionAndLiveBoundingBoxes() {
        final ChunkEntitySlices slices = slices();
        final TouchingPlayer custom = entity(TouchingPlayer.class, EntityType.PLAYER);
        final ItemEntity item = entity(ItemEntity.class, EntityType.ITEM);
        slices.addEntity(custom, 4);
        slices.addEntity(item, 4);
        assertEquals(List.of(custom), query(slices, item, PlayerTouchQuery.PREDICATE));

        when(custom.isSpectator()).thenReturn(true);
        assertEquals(List.of(item), query(slices, null, PlayerTouchQuery.PREDICATE));
        when(item.getBoundingBox()).thenReturn(BOX.move(8, 0, 0));
        assertTrue(query(slices, null, PlayerTouchQuery.PREDICATE).isEmpty());
        when(custom.isSpectator()).thenReturn(false);
        when(item.getBoundingBox()).thenReturn(BOX);
        assertMatchesFullScan(slices, null);
    }

    @Test
    void maintainsIndexAcrossRemovalSectionMovesAndChunkMerges() {
        final ChunkEntitySlices source = slices();
        final ChunkEntitySlices destination = slices();
        final ItemEntity item = entity(ItemEntity.class, EntityType.ITEM);
        final ExperienceOrb orb = entity(ExperienceOrb.class, EntityType.EXPERIENCE_ORB);
        source.addEntity(item, 4);
        source.addEntity(orb, 4);
        assertFalse(source.addEntity(item, 4));
        assertMatchesFullScan(source, null);

        assertTrue(source.removeEntity(item, 4));
        assertFalse(source.removeEntity(item, 4));
        when(item.getBoundingBox()).thenReturn(BOX.move(0, 32, 0));
        source.addEntity(item, 6);
        when(item.moonrise$getSectionY()).thenReturn(6);
        assertEquals(List.of(orb), query(source, null, PlayerTouchQuery.PREDICATE));
        source.mergeInto(destination);
        assertMatchesFullScan(destination, null);
        source.removeEntity(orb, 4);
        source.removeEntity(item, 6);
        assertTrue(query(source, null, PlayerTouchQuery.PREDICATE).isEmpty());

        when(item.getBoundingBox()).thenReturn(BOX);
        source.addEntity(item, 4);
        assertEquals(List.of(item), query(source, null, PlayerTouchQuery.PREDICATE));
        assertMatchesFullScan(source, null);
    }

    @Test
    void randomizedMembershipChangesMatchTheOriginalFilteredQuery() {
        final Random random = new Random(0xA57A71E5);
        final ChunkEntitySlices slices = slices();
        final List<Entity> entities = new ArrayList<>();
        for (int i = 0; i < 40; ++i) {
            entities.add(i % 3 == 0 ? entity(ItemEntity.class, EntityType.ITEM) : entity(ServerPlayer.class, EntityType.PLAYER));
        }
        for (int step = 0; step < 300; ++step) {
            final Entity entity = entities.get(random.nextInt(entities.size()));
            if (random.nextBoolean()) {
                slices.addEntity(entity, 4);
            } else {
                slices.removeEntity(entity, 4);
            }
            assertMatchesFullScan(slices, entities.get(random.nextInt(entities.size())));
        }
    }

    private static ChunkEntitySlices slices() {
        return new ChunkEntitySlices(null, 0, 0, FullChunkStatus.ENTITY_TICKING, null, -4, 19);
    }

    private <T extends Entity> T entity(final Class<T> type, final EntityType<?> entityType) {
        final T entity = mock(type);
        when(entity.getId()).thenReturn(++this.nextId);
        doReturn(entityType).when(entity).getType();
        when(entity.getBoundingBox()).thenReturn(BOX);
        when(entity.moonrise$getSectionY()).thenReturn(4);
        return entity;
    }

    private static List<Entity> query(final ChunkEntitySlices slices, final Entity except, final Predicate<Entity> predicate) {
        final List<Entity> result = new ArrayList<>();
        slices.getEntities(except, BOX, result, predicate);
        return result;
    }

    private static void assertMatchesFullScan(final ChunkEntitySlices slices, final Entity except) {
        // A distinct predicate forces the original all-entity scan through the same geometry/order.
        final List<Entity> expected = query(slices, except, entity -> PlayerTouchQuery.PREDICATE.test(entity));
        assertEquals(expected, query(slices, except, PlayerTouchQuery.PREDICATE));
    }

    private static class TouchingPlayer extends ServerPlayer {
        TouchingPlayer(final MinecraftServer server, final ServerLevel level, final GameProfile profile, final ClientInformation information) {
            super(server, level, profile, information);
        }

        @Override
        public void playerTouch(final Player player) {
            throw new AssertionError("custom player contact must remain queryable");
        }
    }
}
