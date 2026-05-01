package io.multipaper.shreddedpaper.threading;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import net.minecraft.server.level.ServerPlayer;

public class ShreddedPaperPlayerTicker {

    public static void tickPlayer(ServerPlayer serverPlayer) {
        if (!canTickPlayer(serverPlayer)) {
            return;
        }
        final io.multipaper.shreddedpaper.region.LevelChunkRegion tickingRegion = ShreddedPaperChunkTicker.currentlyTickingRegion();
        if (tickingRegion != null && (serverPlayer.currentRegion != tickingRegion || serverPlayer.level() != tickingRegion.getLevel())) {
            serverPlayer.level().chunkSource.tickingRegions.reconcilePlayerIfNeeded(serverPlayer);
        }
        if (tickingRegion != null && (serverPlayer.currentRegion != tickingRegion || serverPlayer.level() != tickingRegion.getLevel())) {
            return;
        }

        tickPlayerChunkLoader(serverPlayer);
        serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);

        serverPlayer.connection.connection.tick();

        tickPlayerChunkLoader(serverPlayer);
        serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
        serverPlayer.connection.keepConnectionAlive();
        serverPlayer.connection.resumeFlushing();
    }

    public static boolean canTickPlayer(ServerPlayer serverPlayer) {
        return serverPlayer.connection.player == serverPlayer
                && !serverPlayer.connection.processedDisconnect
                && !serverPlayer.isRemoved()
                && serverPlayer.valid;
    }

    private static void tickPlayerChunkLoader(ServerPlayer serverPlayer) {
        RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader = serverPlayer.moonrise$getChunkLoader();
        if (loader != null && !loader.isForWorld(serverPlayer.level())) {
            loader.scheduleStaleWorldChangeCleanup();
            serverPlayer.moonrise$setChunkLoader(null);
            serverPlayer.connection.chunkSender.clearPendingChunks();
            loader = null;
        }
        if (loader == null && !serverPlayer.isRemoved() && serverPlayer.valid) {
            PlatformHooks.get().addPlayerToDistanceMaps(serverPlayer.level(), serverPlayer);
            loader = serverPlayer.moonrise$getChunkLoader();
        }
        if (loader != null) {
            loader.update(); // can't invoke plugin logic
            loader.updateQueues(System.nanoTime());
        }
    }

}
