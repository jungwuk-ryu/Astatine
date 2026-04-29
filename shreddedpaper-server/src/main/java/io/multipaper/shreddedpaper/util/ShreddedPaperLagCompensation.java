package io.multipaper.shreddedpaper.util;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.LevelChunkRegion;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperChunkTicker;
import io.multipaper.shreddedpaper.threading.region.RegionOverloadController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import org.bxteam.divinemc.config.DivineConfig;
import org.jetbrains.annotations.Nullable;

public final class ShreddedPaperLagCompensation {

    private static final double FULL_TICK_MS = 50.0D;

    private ShreddedPaperLagCompensation() {
    }

    public static int compensatedMoveTicks(final ServerPlayer player, final int baseTicks) {
        return Math.max(baseTicks, baseTicks + applicableMissedTicks(player));
    }

    public static boolean shouldBypassMovedTooQuickly(final ServerPlayer player) {
        return DivineConfig.FixesCategory.alwaysAllowWeirdMovement
                || (DivineConfig.FixesCategory.ignoreMovedTooQuicklyWhenLagging && isLaggingForMovement(player));
    }

    public static boolean shouldBypassMovedWrongly(final ServerPlayer player) {
        return DivineConfig.FixesCategory.alwaysAllowWeirdMovement
                || (DivineConfig.FixesCategory.ignoreMovedWronglyWhenLagging && isLaggingForMovement(player));
    }

    public static boolean isLaggingForMovement(final ServerPlayer player) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled) {
            return false;
        }

        final LevelChunkRegion region = player.currentRegion;
        if (region != null && isRegionLagging(region.getOverloadController())) {
            return true;
        }

        final MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.lagging) {
            return true;
        }

        return player.level() instanceof ServerLevel level && applicableMissedTicks(level, player.blockPosition()) > 0;
    }

    public static int applicableMissedTicks(final Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return 0;
        }
        return applicableMissedTicks(level, entity.blockPosition());
    }

    public static int applicableMissedTicks(final ServerLevel level, final @Nullable BlockPos pos) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled) {
            return 0;
        }

        final LevelChunkRegion tickingRegion = ShreddedPaperChunkTicker.currentlyTickingRegion();
        if (tickingRegion != null && tickingRegion.getLevel() == level) {
            final int regionMissedTicks = applicableMissedTicks(tickingRegion.getOverloadController());
            if (regionMissedTicks > 0) {
                return regionMissedTicks;
            }
        }

        if (pos != null) {
            final RegionPos regionPos = RegionPos.forBlockPos(pos);
            final LevelChunkRegion region = level.chunkSource.tickingRegions.get(regionPos);
            if (region != null) {
                final int regionMissedTicks = applicableMissedTicks(region.getOverloadController());
                if (regionMissedTicks > 0) {
                    return regionMissedTicks;
                }
            }
        }

        return clampMissedTicks(level.tpsCalculator.applicableMissedTicks());
    }

    public static int tickIncrement(final ServerLevel level, final @Nullable BlockPos pos, final boolean featureEnabled) {
        return 1 + applicableMissedTicks(level, pos, featureEnabled);
    }

    public static int applicableMissedTicks(final ServerLevel level, final @Nullable BlockPos pos, final boolean featureEnabled) {
        return DivineConfig.MiscCategory.lagCompensationEnabled && featureEnabled ? applicableMissedTicks(level, pos) : 0;
    }

    public static int applicableMissedTicks(final Entity entity, final boolean featureEnabled) {
        return DivineConfig.MiscCategory.lagCompensationEnabled && featureEnabled ? applicableMissedTicks(entity) : 0;
    }

    public static int adjustedUseDuration(final int original, final LivingEntity entity) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled || !DivineConfig.MiscCategory.eatingAcceleration || original <= 0) {
            return original;
        }
        final int missedTicks = applicableMissedTicks(entity);
        return missedTicks <= 0 ? original : Math.max(1, (int)Math.ceil(original * 20.0D / (20.0D + missedTicks)));
    }

    public static float adjustedBlockDestroyProgress(final float original, final Player player, final BlockGetter level) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled || !DivineConfig.MiscCategory.blockBreakingAcceleration || original <= 0.0F) {
            return original;
        }
        final int missedTicks;
        if (level instanceof ServerLevel serverLevel) {
            missedTicks = applicableMissedTicks(serverLevel, player.blockPosition());
        } else {
            missedTicks = applicableMissedTicks(player);
        }
        return missedTicks <= 0 ? original : (float)(original * ((20.0D + missedTicks) / 20.0D));
    }

    public static int extraPotionEffectTicks(final LivingEntity entity) {
        return applicableMissedTicks(entity, DivineConfig.MiscCategory.potionEffectAcceleration);
    }

    public static void decrementPickupDelay(final ItemEntity item) {
        if (item.pickupDelay <= 0 || item.pickupDelay == 32767) {
            return;
        }
        if (!(item.level() instanceof ServerLevel level)) {
            item.pickupDelay--;
            return;
        }
        final int decrement = tickIncrement(level, item.blockPosition(), DivineConfig.MiscCategory.pickupAcceleration);
        item.pickupDelay = Math.max(0, item.pickupDelay - decrement);
    }

    public static int adjustedFluidDelay(final int original, final ServerLevel level) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled || !DivineConfig.MiscCategory.fluidAcceleration || original <= 1) {
            return original;
        }
        final int missedTicks = applicableMissedTicks(level, null);
        return missedTicks <= 0 ? original : Math.max(1, (int)Math.ceil(original * 20.0D / (20.0D + missedTicks)));
    }

    public static int adjustedRandomTickSpeed(final ServerLevel level, final BlockPos pos, final int randomTickSpeed) {
        if (!DivineConfig.MiscCategory.lagCompensationEnabled || !DivineConfig.MiscCategory.randomTickSpeedAcceleration || randomTickSpeed <= 0) {
            return randomTickSpeed;
        }
        final long adjusted = (long)randomTickSpeed * tickIncrement(level, pos, true);
        return adjusted > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int)adjusted;
    }

    public static long adjustedDayTimeIncrement(final ServerLevel level) {
        return tickIncrement(level, null, DivineConfig.MiscCategory.timeAcceleration);
    }

    public static int applicableMissedTicks(final RegionOverloadController controller) {
        final double effectiveMspt = Math.max(controller.ewmaMspt(), FULL_TICK_MS + controller.ewmaScheduleLagMs());
        if (effectiveMspt <= FULL_TICK_MS) {
            return 0;
        }
        return clampMissedTicks((int)Math.floor((effectiveMspt / FULL_TICK_MS) - 1.0D));
    }

    private static boolean isRegionLagging(final RegionOverloadController controller) {
        return controller.ewmaMspt() >= DivineConfig.MiscCategory.regionLagMsptThreshold
                || controller.ewmaScheduleLagMs() >= DivineConfig.MiscCategory.regionLagScheduleLagThresholdMs
                || isBudgetSaturated(controller)
                || applicableMissedTicks(controller) > 0;
    }

    private static boolean isBudgetSaturated(final RegionOverloadController controller) {
        final double budgetMs = Math.max(1.0D, ShreddedPaperConfiguration.get().multithreading.regionTickBudgetMs);
        return controller.lastDeferredWork() > 0L && controller.ewmaMspt() >= budgetMs * 0.95D;
    }

    private static int clampMissedTicks(final int missedTicks) {
        return Math.max(0, Math.min(missedTicks, DivineConfig.MiscCategory.maxCompensatedMissedTicks));
    }

    public static void tickServerFallbackCalculator() {
        final MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            server.tpsCalculator.doTick();
        }
    }
}
