package io.multipaper.shreddedpaper.entity;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.world.entity.EntityType;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@AllFeatures
class CraftPlayerVisibilityTest {
    private final UUID viewerId = new UUID(1, 2);
    private CraftPlayer viewer;
    private Map<UUID, Object> visibility;
    private boolean previousDisableVanish;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws ReflectiveOperationException {
        this.previousDisableVanish = ShreddedPaperConfiguration.get().optimizations.disableVanishApi;
        ShreddedPaperConfiguration.get().optimizations.disableVanishApi = false;
        final ServerPlayer handle = mock(ServerPlayer.class);
        doReturn(EntityType.PLAYER).when(handle).getType();
        when(handle.getUUID()).thenReturn(this.viewerId);
        final CraftServer server = mock(CraftServer.class);
        when(server.getHandle()).thenReturn(mock(DedicatedPlayerList.class));
        this.viewer = new CraftPlayer(server, handle);
        final Field field = CraftPlayer.class.getDeclaredField("invertedVisibilityEntities");
        field.setAccessible(true);
        this.visibility = (Map<UUID, Object>) field.get(this.viewer);
    }

    @AfterEach
    void restore() {
        ShreddedPaperConfiguration.get().optimizations.disableVanishApi = this.previousDisableVanish;
    }

    @Test
    void ordinaryVisibleTargetNeedsNoUuidLookup() {
        final Player target = mock(Player.class);
        when(target.isVisibleByDefault()).thenReturn(true);
        assertTrue(this.viewer.canSee(target));
        verify(target, never()).getUniqueId();
    }

    @Test
    void unrelatedOverridesDoNotRepeatTheVisibleTargetsUuidLookup() {
        final Player target = mock(Player.class);
        when(target.isVisibleByDefault()).thenReturn(true);
        when(target.getUniqueId()).thenReturn(new UUID(3, 4));
        this.visibility.put(new UUID(5, 6), Set.of());
        assertTrue(this.viewer.canSee(target));
        verify(target, times(1)).getUniqueId();
    }

    @Test
    void hideShowAndDefaultChangesAreEffectiveImmediately() {
        final UUID id = new UUID(3, 4);
        final Player target = mock(Player.class);
        when(target.getUniqueId()).thenReturn(id);
        when(target.isVisibleByDefault()).thenReturn(true);
        assertTrue(this.viewer.canSee(target));
        this.visibility.put(id, Set.of());
        assertFalse(this.viewer.canSee(target));
        this.visibility.remove(id);
        assertTrue(this.viewer.canSee(target));

        when(target.isVisibleByDefault()).thenReturn(false);
        assertFalse(this.viewer.canSee(target));
        this.visibility.put(id, Set.of());
        assertTrue(this.viewer.canSee(target));
        this.visibility.remove(id);
        assertFalse(this.viewer.canSee(target));
    }

    @Test
    void matchesOriginalVisibilityTruthTableIncludingSelfAndUnrelatedOverrides() {
        for (boolean self : new boolean[]{false, true}) {
            final UUID id = self ? this.viewerId : new UUID(5, 6);
            final Player target = mock(Player.class);
            when(target.getUniqueId()).thenReturn(id);
            for (boolean visible : new boolean[]{false, true}) {
                when(target.isVisibleByDefault()).thenReturn(visible);
                for (boolean inverted : new boolean[]{false, true}) {
                    for (boolean unrelated : new boolean[]{false, true}) {
                        this.visibility.clear();
                        if (inverted) this.visibility.put(id, Set.of());
                        if (unrelated) this.visibility.put(new UUID(7, 8), Set.of());
                        final boolean expected = self || (visible ^ inverted);
                        assertEquals(expected, this.viewer.canSee(target));
                        assertEquals(expected, this.viewer.canSee((org.bukkit.entity.Entity) target));
                    }
                }
            }
        }
        assertTrue(this.viewer.canSee(this.viewer));
        ShreddedPaperConfiguration.get().optimizations.disableVanishApi = true;
        final Player hidden = mock(Player.class);
        assertTrue(this.viewer.canSee(hidden));
    }
}
