package io.multipaper.shreddedpaper.util;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.threading.region.RegionLoadClass;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.UUID;

public final class DegradedRegionBossBar {

    private static final int HIDDEN = 0;
    private static final int SHOWN = 1;
    private static final BossEvent EVENT = createEvent(
            UUID.fromString("0eeb46ca-92dc-43ab-a7d1-9eac0d0dd042"),
            "Degraded Region",
            BossEvent.BossBarColor.YELLOW
    );

    private DegradedRegionBossBar() {
    }

    public static void updatePlayers(final LevelChunkRegion region, final RegionLoadClass loadClass) {
        final int targetState = isEnabled() && loadClass == RegionLoadClass.DEGRADED ? SHOWN : HIDDEN;
        if (targetState == HIDDEN && !region.getOverloadController().degradedRegionBossBarVisible()) {
            return;
        }

        boolean visible = false;
        for (final ServerPlayer player : region.getPlayers()) {
            if (player.currentRegion != region || player.level() != region.getLevel()) {
                continue;
            }
            visible |= update(player, targetState);
        }
        region.getOverloadController().degradedRegionBossBarVisible(visible);
    }

    public static void hide(final ServerPlayer player) {
        update(player, HIDDEN);
    }

    public static void updateCurrentRegion(final ServerPlayer player) {
        final LevelChunkRegion region = player.currentRegion;
        if (region == null || player.level() != region.getLevel()) {
            hide(player);
            return;
        }

        final int targetState = isEnabled() && region.getOverloadController().loadClass() == RegionLoadClass.DEGRADED ? SHOWN : HIDDEN;
        if (update(player, targetState)) {
            region.getOverloadController().degradedRegionBossBarVisible(true);
        }
    }

    private static boolean update(final ServerPlayer player, final int targetState) {
        final int currentState = player.shreddedpaper$degradedRegionBossBarState;
        if (currentState == targetState) {
            return targetState == SHOWN;
        }

        if (currentState != HIDDEN) {
            sendRemove(player);
            player.shreddedpaper$degradedRegionBossBarState = HIDDEN;
        }

        if (targetState != HIDDEN && canSend(player)) {
            player.connection.send(ClientboundBossEventPacket.createAddPacket(EVENT));
            player.shreddedpaper$degradedRegionBossBarState = targetState;
        }
        return player.shreddedpaper$degradedRegionBossBarState == SHOWN;
    }

    private static void sendRemove(final ServerPlayer player) {
        if (canSend(player)) {
            player.connection.send(ClientboundBossEventPacket.createRemovePacket(EVENT.getId()));
        }
    }

    private static boolean canSend(final ServerPlayer player) {
        return player.connection != null && !player.connection.processedDisconnect;
    }

    private static boolean isEnabled() {
        final ShreddedPaperConfiguration.Multithreading.DegradedRegionBossBar config =
                ShreddedPaperConfiguration.get().multithreading.degradedRegionBossBar;
        return config != null && config.enabled;
    }

    private static BossEvent createEvent(final UUID id, final String title, final BossEvent.BossBarColor color) {
        return new BossEvent(id, Component.literal(title), color, BossEvent.BossBarOverlay.PROGRESS) {
        };
    }
}
