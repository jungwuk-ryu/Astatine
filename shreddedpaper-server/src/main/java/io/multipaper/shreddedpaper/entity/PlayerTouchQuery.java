package io.multipaper.shreddedpaper.entity;

import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;

/** The shared predicate also selects the contact index in ChunkEntitySlices. */
public final class PlayerTouchQuery {

    public static final Predicate<Entity> PREDICATE = entity ->
            isCandidate(entity) && EntitySelector.NO_SPECTATORS.test(entity);

    private PlayerTouchQuery() {
    }

    public static boolean isCandidate(final Entity entity) {
        // Vanilla ServerPlayer inherits Entity's empty playerTouch method. Keep subclasses:
        // plugins may override playerTouch, even when the entity type is still PLAYER.
        return entity.getClass() != ServerPlayer.class;
    }
}
