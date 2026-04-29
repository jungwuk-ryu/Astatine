package org.bxteam.divinemc.util.tps;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public final class TPSUtil {

    public static final int MAX_TPS = 20;

    private TPSUtil() {
    }

    public static float tt20(final float ticks, final boolean limitZero, final @Nullable ServerLevel level) {
        final float adjusted = (float)rawTT20(ticks, level);
        return limitZero ? Math.max(adjusted, 1.0F) : adjusted;
    }

    public static int tt20(final int ticks, final boolean limitZero, final @Nullable ServerLevel level) {
        final int adjusted = (int)Math.ceil(rawTT20(ticks, level));
        return limitZero ? Math.max(adjusted, 1) : adjusted;
    }

    public static double tt20(final double ticks, final boolean limitZero, final @Nullable ServerLevel level) {
        final double adjusted = rawTT20(ticks, level);
        return limitZero ? Math.max(adjusted, 1.0D) : adjusted;
    }

    public static double rawTT20(final double ticks, final @Nullable ServerLevel level) {
        if (ticks == 0.0D) {
            return 0.0D;
        }
        final double tps = level == null
                ? MinecraftServer.getServer().tpsCalculator.getMostAccurateTPS()
                : level.tpsCalculator.getMostAccurateTPS();
        return ticks * tps / MAX_TPS;
    }
}
