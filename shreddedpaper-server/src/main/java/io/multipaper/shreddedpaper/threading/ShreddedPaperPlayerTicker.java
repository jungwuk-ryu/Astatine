package io.multipaper.shreddedpaper.threading;

import ca.spottedleaf.moonrise.common.PlatformHooks;
import ca.spottedleaf.moonrise.patches.chunk_system.player.RegionizedPlayerChunkLoader;
import net.minecraft.server.level.ServerPlayer;

public class ShreddedPaperPlayerTicker {

    public static void tickPlayer(ServerPlayer serverPlayer) {
        tickPlayerChunkLoader(serverPlayer);
        serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);

        serverPlayer.connection.connection.tick();

        tickPlayerChunkLoader(serverPlayer);
        serverPlayer.connection.chunkSender.sendNextChunks(serverPlayer);
        serverPlayer.connection.keepConnectionAlive();
        serverPlayer.connection.resumeFlushing();
    }

    private static void tickPlayerChunkLoader(ServerPlayer serverPlayer) {
        RegionizedPlayerChunkLoader.PlayerChunkLoaderData loader = serverPlayer.moonrise$getChunkLoader();
        if (loader != null && !loader.isForWorld(serverPlayer.level())) {
            loader.scheduleStaleWorldChangeCleanup();
            serverPlayer.moonrise$setChunkLoader(null);
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
