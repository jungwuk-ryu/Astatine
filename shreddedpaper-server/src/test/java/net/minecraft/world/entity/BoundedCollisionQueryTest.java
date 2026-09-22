package net.minecraft.world.entity;

import io.papermc.paper.configuration.WorldConfiguration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class BoundedCollisionQueryTest {
    private static final AABB BOX = new AABB(0, 0, 0, 1, 2, 1);
    private LivingEntity entity;
    private ServerLevel level;
    private GameRules rules;
    private WorldConfiguration config;

    @BeforeEach
    void setup() {
        entity = mock(LivingEntity.class);
        level = mock(ServerLevel.class);
        rules = mock(GameRules.class);
        config = mock(WorldConfiguration.class);
        config.collisions = mock(WorldConfiguration.Collisions.class);
        config.collisions.maxEntityCollisions = 2;
        when(level.paperConfig()).thenReturn(config);
        when(level.getGameRules()).thenReturn(rules);
        when(entity.level()).thenReturn(level);
        when(entity.isPushable()).thenReturn(true);
        when(entity.getBoundingBox()).thenReturn(BOX);
        doCallRealMethod().when(entity).pushEntities();
    }

    @Test
    void noCrammingUsesOnlyTheConsumablePrefixAndKeepsOrder() {
        when(rules.get(GameRules.MAX_ENTITY_CRAMMING)).thenReturn(0);
        final Entity first = mock(Entity.class), second = mock(Entity.class);
        when(level.getPushableEntities(entity, BOX, 2)).thenReturn(List.of(first, second));
        final List<Entity> pushed = new ArrayList<>();
        doAnswer(call -> { pushed.add(call.getArgument(0)); return null; }).when(entity).doPush(any());
        entity.pushEntities();
        assertEquals(List.of(first, second), pushed);
        assertEquals(1, first.numCollisions);
        assertEquals(1, second.numCollisions);
        assertEquals(2, entity.numCollisions);
        verify(level, never()).getPushableEntities(any(), any());
    }

    @Test
    void crammingStillRequestsTheEntirePopulation() {
        when(rules.get(GameRules.MAX_ENTITY_CRAMMING)).thenReturn(24);
        when(level.getPushableEntities(entity, BOX)).thenReturn(List.of(mock(Entity.class)));
        entity.pushEntities();
        verify(level).getPushableEntities(entity, BOX);
        verify(level, never()).getPushableEntities(any(), any(), anyInt());
    }

    @Test
    void emptyQueriesDoNotDecayCollisionDebtButNonEmptyQueriesDo() {
        when(rules.get(GameRules.MAX_ENTITY_CRAMMING)).thenReturn(0);
        entity.numCollisions = 7;
        when(level.getPushableEntities(entity, BOX, 2)).thenReturn(List.of(), List.of(mock(Entity.class)));
        entity.pushEntities();
        assertEquals(7, entity.numCollisions);
        entity.pushEntities();
        assertEquals(5, entity.numCollisions);
        verify(entity, never()).doPush(any());
    }

    @Test
    void disabledCollisionAndCrammingAvoidTheQueryEntirely() {
        when(rules.get(GameRules.MAX_ENTITY_CRAMMING)).thenReturn(0);
        config.collisions.maxEntityCollisions = 0;
        entity.pushEntities();
        verify(level, never()).getPushableEntities(any(), any());
        verify(level, never()).getPushableEntities(any(), any(), anyInt());
    }
}
