package org.bxteam.divinemc.config;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import org.bxteam.divinemc.async.pathfinding.PathfindTaskRejectPolicy;
import org.bxteam.divinemc.region.EnumRegionFileExtension;
import org.bxteam.divinemc.region.type.LinearRegionFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DivineConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(DivineConfig.class);

    private DivineConfig() {
    }

    public static void syncFromShreddedPaper(final ShreddedPaperConfiguration configuration) {
        MiscCategory.sync(configuration);
        AsyncCategory.sync(configuration);
    }

    public static final class AsyncCategory {
        public static boolean multithreadedEnabled = true;
        public static boolean multithreadedCompatModeEnabled = false;
        public static int asyncEntityTrackerMaxThreads = 1;
        public static int asyncEntityTrackerKeepalive = 60;
        public static int asyncEntityTrackerQueueSize = 384;

        public static boolean asyncPathfinding = true;
        public static int asyncPathfindingMaxThreads = 1;
        public static int asyncPathfindingKeepalive = 60;
        public static int asyncPathfindingQueueSize = 0;
        public static PathfindTaskRejectPolicy asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.CALLER_RUNS;

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

            ShreddedPaperConfiguration.AsyncOperations.MultithreadedTracker multithreadedTracker = async.multithreadedTracker;
            if (multithreadedTracker == null) {
                multithreadedTracker = async.new MultithreadedTracker();
                async.multithreadedTracker = multithreadedTracker;
            }

            ShreddedPaperConfiguration.AsyncOperations.Pathfinding pathfinding = async.pathfinding;
            if (pathfinding == null) {
                pathfinding = async.new Pathfinding();
                async.pathfinding = pathfinding;
            }

            final int availableProcessors = Runtime.getRuntime().availableProcessors();

            multithreadedEnabled = multithreadedTracker.enable;
            multithreadedCompatModeEnabled = multithreadedTracker.compatMode;
            asyncEntityTrackerMaxThreads = normalizeAsyncThreads(multithreadedTracker.maxThreads, availableProcessors);
            asyncEntityTrackerKeepalive = Math.max(1, multithreadedTracker.keepalive);

            if (!multithreadedEnabled) {
                asyncEntityTrackerMaxThreads = 0;
            } else {
                LOGGER.info("Using {} threads for Async Entity Tracker", asyncEntityTrackerMaxThreads);
            }

            asyncEntityTrackerQueueSize = multithreadedTracker.queueSize <= 0
                ? Math.max(asyncEntityTrackerMaxThreads, 1) * 384
                : multithreadedTracker.queueSize;

            asyncPathfinding = pathfinding.enable;
            asyncPathfindingMaxThreads = normalizeAsyncThreads(pathfinding.maxThreads, availableProcessors);
            asyncPathfindingKeepalive = Math.max(1, pathfinding.keepalive);

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

            multithreadedTracker.enable = multithreadedEnabled;
            multithreadedTracker.compatMode = multithreadedCompatModeEnabled;
            multithreadedTracker.maxThreads = asyncEntityTrackerMaxThreads;
            multithreadedTracker.keepalive = asyncEntityTrackerKeepalive;
            multithreadedTracker.queueSize = asyncEntityTrackerQueueSize;
        }

        private static void applyDefaults() {
            multithreadedEnabled = true;
            multithreadedCompatModeEnabled = false;
            asyncEntityTrackerMaxThreads = 1;
            asyncEntityTrackerKeepalive = 60;
            asyncEntityTrackerQueueSize = 384;
            asyncPathfinding = true;
            asyncPathfindingMaxThreads = 1;
            asyncPathfindingKeepalive = 60;
            asyncPathfindingQueueSize = 256;
            asyncPathfindingRejectPolicy = PathfindTaskRejectPolicy.CALLER_RUNS;
        }

        private static int normalizeAsyncThreads(final int configuredThreads, final int availableProcessors) {
            if (configuredThreads < 0) {
                return Math.max(availableProcessors + configuredThreads, 1);
            }
            if (configuredThreads == 0) {
                return Math.max(availableProcessors / 4, 1);
            }
            return Math.max(configuredThreads, 1);
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
