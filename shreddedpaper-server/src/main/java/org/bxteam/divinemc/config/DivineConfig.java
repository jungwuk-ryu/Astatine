package org.bxteam.divinemc.config;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import org.bxteam.divinemc.chunk.ChunkSystemAlgorithm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.bxteam.divinemc.async.pathfinding.PathfindTaskRejectPolicy;
import org.bxteam.divinemc.region.EnumRegionFileExtension;
import org.bxteam.divinemc.region.type.LinearRegionFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class DivineConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DivineConfig.class);

    private DivineConfig() {
    }

    public static void syncFromShreddedPaper(final ShreddedPaperConfiguration configuration) {
        MiscCategory.sync(configuration);
        PerformanceCategory.sync(configuration);
        VirtualThreadsCategory.sync(configuration);
        AsyncCategory.sync(configuration);
    }

    public static final class PerformanceCategory {
        public static long chunkDataCacheSoftLimit = 8192L;
        public static long chunkDataCacheLimit = 32678L;
        public static int maxViewDistance = 16;
        public static int playerNearChunkDetectionRange = 128;
        public static ChunkSystemAlgorithm chunkWorkerAlgorithm = ChunkSystemAlgorithm.C2ME_NEW;
        public static boolean useEuclideanDistanceSquared = true;
        public static boolean endBiomeCacheEnabled = true;
        public static int endBiomeCacheCapacity = 2048;
        public static boolean enableDensityFunctionCompiler = true;
        public static boolean enableStructureLayoutOptimizer = true;
        public static boolean deduplicateShuffledTemplatePoolElementList = true;
        public static boolean disableMethodProfiler = true;
        public static boolean skipUselessSecondaryPoiSensor = true;
        public static boolean clumpOrbs = true;
        public static boolean enableSuffocationOptimization = true;
        public static boolean useCompactBitStorage = true;
        public static boolean commandBlockParseResultsCaching = true;
        public static boolean sheepOptimization = true;
        public static boolean optimizedDragonRespawn = true;
        public static boolean reduceChuckLoadAndLookup = true;
        public static boolean createSnapshotOnRetrievingBlockState = true;
        public static boolean sleepingBlockEntity = true;
        public static boolean equipmentTracking = true;
        public static boolean hopperThrottleWhenFull = true;
        public static int hopperThrottleSkipTicks = 8;
        public static int projectileChunkLoadsPerTick = 10;
        public static int projectileChunkLoadsPerProjectile = 10;
        public static boolean projectileChunkLoadResetMovement = false;
        public static boolean projectileChunkLoadRemoveFromWorld = false;

        public static boolean dabEnabled = false;
        public static int dabStartDistance = 12;
        public static int dabStartDistanceSquared = dabStartDistance * dabStartDistance;
        public static int dabMaximumActivationFrequency = 20;
        public static int dabActivationDistanceMod = 8;
        public static boolean dabDontEnableIfInWater = false;
        public static List<String> dabBlackedEntities = new ArrayList<>(Arrays.asList(
            "villager",
            "axolotl",
            "hoglin",
            "zombified_piglin",
            "goat"
        ));

        private PerformanceCategory() {
        }

        public static void sync(final ShreddedPaperConfiguration configuration) {
            if (configuration == null) {
                applyDefaults();
                return;
            }

            ShreddedPaperConfiguration.Performance performance = configuration.performance;
            if (performance == null) {
                performance = configuration.new Performance();
                configuration.performance = performance;
            }

            syncChunkSettings(performance);

            ShreddedPaperConfiguration.Performance.Optimizations optimizations = performance.optimizations;
            if (optimizations == null) {
                optimizations = performance.new Optimizations();
                performance.optimizations = optimizations;
            }

            ShreddedPaperConfiguration.Performance.Optimizations.HopperThrottleWhenFull hopperThrottle = optimizations.hopperThrottleWhenFull;
            if (hopperThrottle == null) {
                hopperThrottle = optimizations.new HopperThrottleWhenFull();
                optimizations.hopperThrottleWhenFull = hopperThrottle;
            }
            ShreddedPaperConfiguration.Performance.Optimizations.ReduceProjectileChunkLoading reduceProjectileChunkLoading = optimizations.reduceProjectileChunkLoading;
            if (reduceProjectileChunkLoading == null) {
                reduceProjectileChunkLoading = optimizations.new ReduceProjectileChunkLoading();
                optimizations.reduceProjectileChunkLoading = reduceProjectileChunkLoading;
            }

            disableMethodProfiler = optimizations.disableMethodProfiler;
            skipUselessSecondaryPoiSensor = optimizations.skipUselessSecondaryPoiSensor;
            clumpOrbs = optimizations.clumpOrbs;
            enableSuffocationOptimization = optimizations.enableSuffocationOptimization;
            useCompactBitStorage = optimizations.useCompactBitStorage;
            commandBlockParseResultsCaching = optimizations.commandBlockParseResultsCaching;
            sheepOptimization = optimizations.sheepOptimization;
            optimizedDragonRespawn = optimizations.optimizedDragonRespawn;
            reduceChuckLoadAndLookup = optimizations.reduceChunkLoadAndLookup;
            createSnapshotOnRetrievingBlockState = optimizations.createSnapshotOnRetrievingBlockState;
            sleepingBlockEntity = optimizations.sleepingBlockEntity;
            equipmentTracking = optimizations.equipmentTracking;
            hopperThrottleWhenFull = hopperThrottle.enabled;
            hopperThrottleSkipTicks = Math.max(0, hopperThrottle.skipTicks);
            projectileChunkLoadsPerTick = reduceProjectileChunkLoading.perTick;
            projectileChunkLoadsPerProjectile = reduceProjectileChunkLoading.perProjectileMax;
            projectileChunkLoadResetMovement = reduceProjectileChunkLoading.resetMovementAfterReachLimit;
            projectileChunkLoadRemoveFromWorld = reduceProjectileChunkLoading.removeFromWorldAfterReachLimit;

            hopperThrottle.skipTicks = hopperThrottleSkipTicks;

            ShreddedPaperConfiguration.Performance.Dab dab = performance.dab;
            if (dab == null) {
                dab = performance.new Dab();
                performance.dab = dab;
            }

            dabEnabled = dab.enabled;
            dabStartDistance = Math.max(0, dab.startDistance);
            dabStartDistanceSquared = dabStartDistance * dabStartDistance;
            dabMaximumActivationFrequency = Math.max(1, dab.maximumActivationFrequency);
            dabActivationDistanceMod = Math.max(1, dab.activationDistanceMod);
            dabDontEnableIfInWater = dab.dontEnableIfInWater;
            dabBlackedEntities = dab.blackedEntities == null
                ? new ArrayList<>()
                : new ArrayList<>(dab.blackedEntities);

            dab.startDistance = dabStartDistance;
            dab.maximumActivationFrequency = dabMaximumActivationFrequency;
            dab.activationDistanceMod = dabActivationDistanceMod;
            dab.blackedEntities = dabBlackedEntities;

        }

        private static void syncChunkSettings(ShreddedPaperConfiguration.Performance performance) {
            ShreddedPaperConfiguration.Performance.Chunks chunks = performance.chunks;
            if (chunks == null) {
                chunks = performance.new Chunks();
                performance.chunks = chunks;
            }
            ShreddedPaperConfiguration.Performance.Chunks.Experimental experimental = chunks.experimental;
            if (experimental == null) {
                experimental = chunks.new Experimental();
                chunks.experimental = experimental;
            }

            chunkDataCacheSoftLimit = Math.max(1L, chunks.chunkDataCacheSoftLimit);
            chunkDataCacheLimit = Math.max(chunkDataCacheSoftLimit, chunks.chunkDataCacheLimit);
            maxViewDistance = Math.max(2, chunks.maxViewDistance);
            playerNearChunkDetectionRange = Math.max(0, chunks.playerNearChunkDetectionRange);
            chunkWorkerAlgorithm = parseChunkWorkerAlgorithm(chunks.chunkWorkerAlgorithm);
            useEuclideanDistanceSquared = chunks.useEuclideanDistanceSquared;
            endBiomeCacheEnabled = chunks.endBiomeCacheEnabled;
            endBiomeCacheCapacity = Math.max(1, chunks.endBiomeCacheCapacity);
            enableDensityFunctionCompiler = experimental.enableDensityFunctionCompiler;
            enableStructureLayoutOptimizer = experimental.enableStructureLayoutOptimizer;
            deduplicateShuffledTemplatePoolElementList = experimental.deduplicateShuffledTemplatePoolElementList;

            chunks.chunkDataCacheSoftLimit = chunkDataCacheSoftLimit;
            chunks.chunkDataCacheLimit = chunkDataCacheLimit;
            chunks.maxViewDistance = maxViewDistance;
            chunks.playerNearChunkDetectionRange = playerNearChunkDetectionRange;
            chunks.chunkWorkerAlgorithm = chunkWorkerAlgorithm.name();
            chunks.endBiomeCacheCapacity = endBiomeCacheCapacity;
        }

        private static ChunkSystemAlgorithm parseChunkWorkerAlgorithm(String rawAlgorithm) {
            if (rawAlgorithm != null) {
                try {
                    return ChunkSystemAlgorithm.valueOf(rawAlgorithm.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                    LOGGER.warn("Unknown chunk worker algorithm '{}', using C2ME_NEW", rawAlgorithm);
                }
            }
            return ChunkSystemAlgorithm.C2ME_NEW;
        }

        private static void applyDefaults() {
            chunkDataCacheSoftLimit = 8192L;
            chunkDataCacheLimit = 32678L;
            maxViewDistance = 16;
            playerNearChunkDetectionRange = 128;
            chunkWorkerAlgorithm = ChunkSystemAlgorithm.C2ME_NEW;
            useEuclideanDistanceSquared = true;
            endBiomeCacheEnabled = true;
            endBiomeCacheCapacity = 2048;
            enableDensityFunctionCompiler = true;
            enableStructureLayoutOptimizer = true;
            deduplicateShuffledTemplatePoolElementList = true;
            disableMethodProfiler = true;
            skipUselessSecondaryPoiSensor = true;
            clumpOrbs = true;
            enableSuffocationOptimization = true;
            useCompactBitStorage = true;
            commandBlockParseResultsCaching = true;
            sheepOptimization = true;
            optimizedDragonRespawn = true;
            reduceChuckLoadAndLookup = true;
            createSnapshotOnRetrievingBlockState = true;
            sleepingBlockEntity = true;
            equipmentTracking = true;
            hopperThrottleWhenFull = true;
            hopperThrottleSkipTicks = 8;
            projectileChunkLoadsPerTick = 10;
            projectileChunkLoadsPerProjectile = 10;
            projectileChunkLoadResetMovement = false;
            projectileChunkLoadRemoveFromWorld = false;
            dabEnabled = false;
            dabStartDistance = 12;
            dabStartDistanceSquared = dabStartDistance * dabStartDistance;
            dabMaximumActivationFrequency = 20;
            dabActivationDistanceMod = 8;
            dabDontEnableIfInWater = false;
            dabBlackedEntities = new ArrayList<>(Arrays.asList(
                "villager",
                "axolotl",
                "hoglin",
                "zombified_piglin",
                "goat"
            ));
        }

        public static void configureDabEntityTypes() {
            for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
                entityType.dabEnabled = true;
            }

            final String defaultPrefix = Identifier.DEFAULT_NAMESPACE + Identifier.NAMESPACE_SEPARATOR;
            for (final String name : dabBlackedEntities) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                final String lowerName = name.toLowerCase(Locale.ROOT);
                final String typeId = lowerName.startsWith(defaultPrefix) ? lowerName : defaultPrefix + lowerName;

                EntityType.byString(typeId).ifPresentOrElse(
                    entityType -> entityType.dabEnabled = false,
                    () -> LOGGER.warn("Unknown entity {}, in performance.dab.blacked-entities", name)
                );
            }
        }
    }

    public static final class VirtualThreadsCategory {
        public static boolean virtualThreadsEnabled = true;
        public static boolean virtualBukkitScheduler = true;
        public static boolean virtualChatScheduler = true;
        public static boolean virtualTabCompleteScheduler = true;
        public static boolean virtualAsyncExecutor = true;
        public static boolean virtualCommandBuilderScheduler = true;
        public static boolean virtualServerTextFilterPool = true;

        private VirtualThreadsCategory() {
        }

        public static void sync(final ShreddedPaperConfiguration configuration) {
            if (configuration == null) {
                applyDefaults();
                return;
            }

            ShreddedPaperConfiguration.VirtualThreads virtualThreads = configuration.virtualThreads;
            if (virtualThreads == null) {
                virtualThreads = configuration.new VirtualThreads();
                configuration.virtualThreads = virtualThreads;
            }

            virtualThreadsEnabled = virtualThreads.enabled;
            virtualBukkitScheduler = virtualThreads.bukkitScheduler;
            virtualChatScheduler = virtualThreads.chatScheduler;
            virtualTabCompleteScheduler = virtualThreads.tabCompleteScheduler;
            virtualAsyncExecutor = virtualThreads.asyncExecutor;
            virtualCommandBuilderScheduler = virtualThreads.commandBuilderScheduler;
            virtualServerTextFilterPool = virtualThreads.serverTextFilterPool;
        }
        private static void applyDefaults() {
            virtualThreadsEnabled = true;
            virtualBukkitScheduler = true;
            virtualChatScheduler = true;
            virtualTabCompleteScheduler = true;
            virtualAsyncExecutor = true;
            virtualCommandBuilderScheduler = true;
            virtualServerTextFilterPool = true;
        }

        public static boolean useBukkitScheduler() {
            return virtualThreadsEnabled && virtualBukkitScheduler;
        }

        public static boolean useChatScheduler() {
            return virtualThreadsEnabled && virtualChatScheduler;
        }

        public static boolean useTabCompleteScheduler() {
            return virtualThreadsEnabled && virtualTabCompleteScheduler;
        }

        public static boolean useAsyncExecutor() {
            return virtualThreadsEnabled && virtualAsyncExecutor;
        }

        public static boolean useCommandBuilderScheduler() {
            return virtualThreadsEnabled && virtualCommandBuilderScheduler;
        }

        public static boolean useServerTextFilterPool() {
            return virtualThreadsEnabled && virtualServerTextFilterPool;
        }
    }

    public static final class AsyncCategory {
        public static boolean asyncPathfinding = true;
        public static int asyncPathfindingMaxThreads = 1;
        public static int asyncPathfindingKeepalive = 60;
        public static int asyncPathfindingQueueSize = 0;
        public static PathfindTaskRejectPolicy asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.CALLER_RUNS;
        public static boolean multithreadedEnabled = true;
        public static boolean multithreadedCompatModeEnabled = false;
        public static int asyncEntityTrackerMaxThreads = 1;
        public static int asyncEntityTrackerKeepalive = 60;
        public static int asyncEntityTrackerQueueSize = 256;

        private AsyncCategory() {
        }

        public static void sync(final ShreddedPaperConfiguration configuration) {
            if (configuration == null) {
                applyDefaults();
                return;
            }

            ShreddedPaperConfiguration.AsyncOperations async = configuration.asyncOperations;
            if (async == null) {
                async = configuration.new AsyncOperations();
                configuration.asyncOperations = async;
            }

            ShreddedPaperConfiguration.AsyncOperations.Pathfinding pathfinding = async.pathfinding;
            if (pathfinding == null) {
                pathfinding = async.new Pathfinding();
                async.pathfinding = pathfinding;
            }

            ShreddedPaperConfiguration.AsyncOperations.MultithreadedTracker multithreadedTracker = async.multithreadedTracker;
            if (multithreadedTracker == null) {
                multithreadedTracker = async.new MultithreadedTracker();
                async.multithreadedTracker = multithreadedTracker;
            }

            final int availableProcessors = Runtime.getRuntime().availableProcessors();

            multithreadedEnabled = multithreadedTracker.enable;
            multithreadedCompatModeEnabled = multithreadedTracker.compatMode;
            asyncEntityTrackerMaxThreads = multithreadedTracker.maxThreads;
            asyncEntityTrackerKeepalive = Math.max(1, multithreadedTracker.keepalive);

            if (asyncEntityTrackerMaxThreads < 0) {
                asyncEntityTrackerMaxThreads = Math.max(availableProcessors + asyncEntityTrackerMaxThreads, 1);
            } else if (asyncEntityTrackerMaxThreads == 0) {
                asyncEntityTrackerMaxThreads = Math.max(availableProcessors / 4, 1);
            }

            if (!multithreadedEnabled) {
                asyncEntityTrackerMaxThreads = 0;
            }

            asyncEntityTrackerQueueSize = multithreadedTracker.queueSize <= 0
                ? Math.max(asyncEntityTrackerMaxThreads, 1) * 256
                : multithreadedTracker.queueSize;

            multithreadedTracker.enable = multithreadedEnabled;
            multithreadedTracker.compatMode = multithreadedCompatModeEnabled;
            multithreadedTracker.maxThreads = asyncEntityTrackerMaxThreads;
            multithreadedTracker.keepalive = asyncEntityTrackerKeepalive;
            multithreadedTracker.queueSize = asyncEntityTrackerQueueSize;

            asyncPathfinding = pathfinding.enable;
            asyncPathfindingMaxThreads = pathfinding.maxThreads;
            asyncPathfindingKeepalive = Math.max(1, pathfinding.keepalive);

            if (asyncPathfindingMaxThreads < 0) {
                asyncPathfindingMaxThreads = Math.max(availableProcessors + asyncPathfindingMaxThreads, 1);
            } else if (asyncPathfindingMaxThreads == 0) {
                asyncPathfindingMaxThreads = Math.max(availableProcessors / 4, 1);
            }

            if (!asyncPathfinding) {
                asyncPathfindingMaxThreads = 0;
            } else {
                LOGGER.info("Using {} threads for Async Pathfinding", asyncPathfindingMaxThreads);
            }

            asyncPathfindingQueueSize = pathfinding.queueSize <= 0
                ? Math.max(asyncPathfindingMaxThreads, 1) * 256
                : pathfinding.queueSize;

            final String configuredPolicy = pathfinding.rejectPolicy == null ? "" : pathfinding.rejectPolicy.trim().toUpperCase(java.util.Locale.ROOT);
            try {
                asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.valueOf(configuredPolicy);
            } catch (IllegalArgumentException ex) {
                asyncPathfindingRejectPolicy = availableProcessors >= 12 && asyncPathfindingQueueSize < 512
                    ? PathfindTaskRejectPolicy.FLUSH_ALL
                    : PathfindTaskRejectPolicy.CALLER_RUNS;
                LOGGER.warn("Invalid async pathfinding reject policy '{}', using {}", pathfinding.rejectPolicy, asyncPathfindingRejectPolicy);
            }

            pathfinding.enable = asyncPathfinding;
            pathfinding.maxThreads = asyncPathfindingMaxThreads;
            pathfinding.keepalive = asyncPathfindingKeepalive;
            pathfinding.queueSize = asyncPathfindingQueueSize;
            pathfinding.rejectPolicy = asyncPathfindingRejectPolicy.name();
        }
        private static void applyDefaults() {
            asyncPathfinding = true;
            asyncPathfindingMaxThreads = 1;
            asyncPathfindingKeepalive = 60;
            asyncPathfindingQueueSize = 256;
            asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.CALLER_RUNS;
            multithreadedEnabled = true;
            multithreadedCompatModeEnabled = false;
            asyncEntityTrackerMaxThreads = 1;
            asyncEntityTrackerKeepalive = 60;
            asyncEntityTrackerQueueSize = 256;
        }
    }

    public static final class MiscCategory {
        public static EnumRegionFileExtension regionFileType = EnumRegionFileExtension.MCA;
        public static int linearCompressionLevel = 1;
        public static int linearIoThreadCount = 6;
        public static int linearIoFlushDelayMs = 100;
        public static boolean linearUseVirtualThreads = true;

        private MiscCategory() {
        }

        public static void sync(final ShreddedPaperConfiguration configuration) {
            if (configuration == null) {
                applyDefaults();
                return;
            }

            ShreddedPaperConfiguration.RegionFormat regionFormat = configuration.regionFormat;
            if (regionFormat == null) {
                regionFormat = configuration.new RegionFormat();
                configuration.regionFormat = regionFormat;
            }

            final EnumRegionFileExtension configuredType = EnumRegionFileExtension.fromString(regionFormat.type);
            regionFileType = configuredType != null ? configuredType : EnumRegionFileExtension.MCA;
            if (configuredType == null) {
                LOGGER.warn("Invalid region format type '{}', defaulting to {}", regionFormat.type, EnumRegionFileExtension.MCA.name());
            }
            regionFormat.type = regionFileType.name();

            linearCompressionLevel = clamp(regionFormat.compressionLevel, 1, 22, 1, "linear compression level");
            linearIoThreadCount = Math.max(1, regionFormat.linearIoThreadCount);
            if (linearIoThreadCount != regionFormat.linearIoThreadCount) {
                LOGGER.warn("Invalid linear I/O thread count '{}', defaulting to 1", regionFormat.linearIoThreadCount);
            }

            linearIoFlushDelayMs = Math.max(0, regionFormat.linearIoFlushDelayMs);
            if (linearIoFlushDelayMs != regionFormat.linearIoFlushDelayMs) {
                LOGGER.warn("Invalid linear I/O flush delay '{}', defaulting to 0", regionFormat.linearIoFlushDelayMs);
            }

            linearUseVirtualThreads = regionFormat.linearUseVirtualThreads;

            regionFormat.compressionLevel = linearCompressionLevel;
            regionFormat.linearIoThreadCount = linearIoThreadCount;
            regionFormat.linearIoFlushDelayMs = linearIoFlushDelayMs;
            regionFormat.linearUseVirtualThreads = linearUseVirtualThreads;

            LinearRegionFile.SAVE_DELAY_MS = linearIoFlushDelayMs;
            LinearRegionFile.SAVE_THREAD_MAX_COUNT = linearIoThreadCount;
            LinearRegionFile.USE_VIRTUAL_THREAD = linearUseVirtualThreads;
        }
        private static void applyDefaults() {
            regionFileType = EnumRegionFileExtension.MCA;
            linearCompressionLevel = 1;
            linearIoThreadCount = 6;
            linearIoFlushDelayMs = 100;
            linearUseVirtualThreads = true;
            LinearRegionFile.SAVE_DELAY_MS = linearIoFlushDelayMs;
            LinearRegionFile.SAVE_THREAD_MAX_COUNT = linearIoThreadCount;
            LinearRegionFile.USE_VIRTUAL_THREAD = linearUseVirtualThreads;
        }

        private static int clamp(final int value, final int min, final int max, final int fallback, final String description) {
            if (value < min || value > max) {
                LOGGER.warn("Invalid {} '{}', defaulting to {}", description, value, fallback);
                return fallback;
            }
            return value;
        }
    }
}
