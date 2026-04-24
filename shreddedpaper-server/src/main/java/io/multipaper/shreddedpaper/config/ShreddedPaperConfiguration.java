package io.multipaper.shreddedpaper.config;

import io.papermc.paper.configuration.ConfigurationPart;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings({ "InnerClassMayBeStatic" })
public class ShreddedPaperConfiguration extends ConfigurationPart {

    public static final String HEADER = """
            This is the main configuration file for ShreddedPaper.
            There's quite alot to configure. Read the docs for more information.

            Docs: https://github.com/MultiPaper/ShreddedPaper/blob/main/SHREDDEDPAPER_YAML.md\s
            """;

    private static ShreddedPaperConfiguration instance;

    public static ShreddedPaperConfiguration get() {
        if (instance == null) {
            instance = new ShreddedPaperConfiguration();
            org.bxteam.divinemc.config.DivineConfig.syncFromShreddedPaper(instance);
        }
        return instance;
    }

    static void set(ShreddedPaperConfiguration instance) {
        ShreddedPaperConfiguration.instance = instance;
    }

    public Multithreading multithreading = new Multithreading();

    public class Multithreading extends ConfigurationPart {

        public int threadCount = -1;
        public int regionSize = 8;
        public boolean runUnsupportedPluginsInSync = true;
        public boolean allowUnsupportedPluginsToModifyChunksViaGlobalScheduler = true;
        @Comment("Runs chunk regions on independent deadline-based tick loops instead of waiting for every region in the world tick.")
        public boolean independentRegionTicking = true;
        @Comment("Maximum workers reserved for degraded regions. Values below 0 use max(1, tick threads / 8).")
        public int degradedRegionThreads = -1;
        @Comment("Maximum queued tasks per region task class before backpressure/rejection begins.")
        public int regionMailboxCapacity = 4096;
        @Comment("Reserved queued tasks for critical system work per region.")
        public int criticalRegionMailboxCapacity = 1024;
        @Comment("Target per-region cooperative work budget in milliseconds.")
        public long regionTickBudgetMs = 45;
        @Comment("EWMA MSPT threshold that moves a region into the degraded scheduler lane.")
        public long degradedRegionMsptThreshold = 75;
        @Comment("EWMA MSPT threshold that marks a region as quarantined.")
        public long quarantinedRegionMsptThreshold = 5000;

    }

    public Optimizations optimizations = new Optimizations();

    public class Optimizations extends ConfigurationPart {

        public int entityActivationCheckFrequency = 20;
        public boolean disableVanishApi = false;
        public boolean disableLocatorBar = true;
        public boolean useLazyExecuteWhenNotFlushing = true;
        public boolean processTrackQueueInParallel = true;
        public boolean flushQueueInParallel = true;
        public int maximumTrackersPerEntity = 500;
        public long trackerFullUpdateFrequency = 20;
        public long purgeStaleTicketsFrequency = 20;
        public boolean writePlayerSavesAsync = true;
        public ChunkPacketCaching chunkPacketCaching = new ChunkPacketCaching();

        public class ChunkPacketCaching extends ConfigurationPart {

            public boolean enabled = true;
            public boolean useSoftReferences = true;
            public long expireAfter = 1200;

        }

    }

    @Setting("performance")
    public Performance performance = new Performance();

    public class Performance extends ConfigurationPart {

        @Setting("optimizations")
        public Optimizations optimizations = new Optimizations();

        @Setting("chunks")
        public Chunks chunks = new Chunks();

        public class Optimizations extends ConfigurationPart {

            @Setting("disable-method-profiler")
            @Comment("Disables the method profiler to save some performance. Mainly used for debugging purposes.")
            public boolean disableMethodProfiler = true;

            @Setting("skip-useless-secondary-poi-sensor")
            public boolean skipUselessSecondaryPoiSensor = true;

            @Setting("clump-orbs")
            @Comment("Clumps experience orbs together to reduce entity count.")
            public boolean clumpOrbs = true;

            @Setting("enable-suffocation-optimization")
            @Comment("Optimizes the suffocation check by selectively skipping the check in a way that still appears vanilla.")
            public boolean enableSuffocationOptimization = true;

            @Setting("use-compact-bit-storage")
            @Comment("Fixes memory waste caused by sending empty chunks as if they contain blocks.")
            public boolean useCompactBitStorage = true;

            @Setting("command-block-parse-results-caching")
            @Comment("Caches the parse results of command blocks.")
            public boolean commandBlockParseResultsCaching = true;

            @Setting("sheep-optimization")
            @Comment("Uses a prebaked list of all possible sheep color combinations.")
            public boolean sheepOptimization = true;

            @Setting("optimized-dragon-respawn")
            @Comment("Improves performance and reduces lag during the dragon resurrection event.")
            public boolean optimizedDragonRespawn = true;

            @Setting("reduce-chunk-load-and-lookup")
            @Comment("Reduces chunk accesses required during operations such as Enderman teleportation.")
            public boolean reduceChunkLoadAndLookup = true;

            @Setting("create-snapshot-on-retrieving-block-state")
            @Comment("Whether to create a snapshot when plugins retrieve BlockState data.")
            public boolean createSnapshotOnRetrievingBlockState = true;

            @Setting("sleeping-block-entity")
            @Comment("Allows inactive block entities to enter a sleeping state.")
            public boolean sleepingBlockEntity = true;

            @Setting("equipment-tracking")
            @Comment("Skips repeated checks whether the equipment of an entity changed.")
            public boolean equipmentTracking = true;

            @Setting("hopper-throttle-when-full")
            public HopperThrottleWhenFull hopperThrottleWhenFull = new HopperThrottleWhenFull();

            public class HopperThrottleWhenFull extends ConfigurationPart {

                @Comment("When enabled, hoppers will throttle if target container is full.")
                public boolean enabled = true;

                @Setting("skip-ticks")
                @Comment("The amount of ticks to skip when the hopper is throttled.")
                public int skipTicks = 8;
            }

            @Setting("reduce-projectile-chunk-loading")
            public ReduceProjectileChunkLoading reduceProjectileChunkLoading = new ReduceProjectileChunkLoading();

            public class ReduceProjectileChunkLoading extends ConfigurationPart {

                @Setting("per-tick")
                @Comment("Maximum unloaded chunks all projectiles may enter per world per tick. Values below 0 disable this limit.")
                public int perTick = 10;

                @Setting("per-projectile-max")
                @Comment("Maximum unloaded chunks a projectile may enter during its lifetime. Values below 0 disable this limit.")
                public int perProjectileMax = 10;

                @Setting("reset-movement-after-reach-limit")
                @Comment("Reset horizontal projectile movement when the per-projectile limit is reached.")
                public boolean resetMovementAfterReachLimit = false;

                @Setting("remove-from-world-after-reach-limit")
                @Comment("Remove projectiles from the world when the per-projectile limit is reached.")
                public boolean removeFromWorldAfterReachLimit = false;
            }
        }

        public class Chunks extends ConfigurationPart {
            @Comment("Soft cap for cached serialized chunk data entries.")
            @Setting("chunk-data-cache-soft-limit")
            public long chunkDataCacheSoftLimit = 8192L;

            @Comment("Hard cap for cached serialized chunk data entries.")
            @Setting("chunk-data-cache-limit")
            public long chunkDataCacheLimit = 32678L;

            @Comment("Maximum server view distance advertised by chunk systems.")
            @Setting("max-view-distance")
            public int maxViewDistance = 16;

            @Comment("Distance in blocks used by player-near-chunk checks.")
            @Setting("player-near-chunk-detection-range")
            public int playerNearChunkDetectionRange = 128;

            @Comment("Chunk worker allocation algorithm: MOONRISE, C2ME, or C2ME_NEW.")
            @Setting("chunk-worker-algorithm")
            public String chunkWorkerAlgorithm = "C2ME_NEW";

            @Comment("Use euclidean distance squared for chunk task ordering.")
            @Setting("use-euclidean-distance-squared")
            public boolean useEuclideanDistanceSquared = true;

            @Comment("Enable The End biome cache for faster End world generation.")
            @Setting("end-biome-cache-enabled")
            public boolean endBiomeCacheEnabled = true;

            @Comment("The End biome cache capacity.")
            @Setting("end-biome-cache-capacity")
            public int endBiomeCacheCapacity = 2048;

            @Setting("experimental")
            public Experimental experimental = new Experimental();

            public class Experimental extends ConfigurationPart {
                @Comment("Use the C2ME density function compiler to accelerate world generation.")
                @Setting("enable-density-function-compiler")
                public boolean enableDensityFunctionCompiler = true;

                @Comment("Optimize jigsaw structure layout generation.")
                @Setting("enable-structure-layout-optimizer")
                public boolean enableStructureLayoutOptimizer = true;

                @Comment("Deduplicate shuffled template pool element lists for faster structure layout generation.")
                @Setting("deduplicate-shuffled-template-pool-element-list")
                public boolean deduplicateShuffledTemplatePoolElementList = true;
            }
        }

        @Setting("dab")
        public Dab dab = new Dab();

        public class Dab extends ConfigurationPart {

            @Comment("Enables DivineMC's Dynamic Activation of Brain optimization.")
            public boolean enabled = false;

            @Setting("start-distance")
            @Comment("How far away an entity has to be before DAB starts reducing brain tick frequency.")
            public int startDistance = 12;

            @Setting("maximum-activation-frequency")
            @Comment("How often, in ticks, the furthest entities get their pathfinders and behaviors ticked.")
            public int maximumActivationFrequency = 20;

            @Setting("activation-distance-mod")
            @Comment("Distance-to-frequency divisor exponent. Frequency is roughly distanceToPlayer^2 / 2^value.")
            public int activationDistanceMod = 8;

            @Setting("dont-enable-if-in-water")
            @Comment("When enabled, non-aquatic entities in water are not affected by DAB.")
            public boolean dontEnableIfInWater = false;

            @Setting("blacked-entities")
            @Comment("Entity ids that should not be affected by DAB.")
            public List<String> blackedEntities = new ArrayList<>(Arrays.asList(
                "villager",
                "axolotl",
                "hoglin",
                "zombified_piglin",
                "goat"
            ));
        }
    }

    public RegionFormat regionFormat = new RegionFormat();

    public class RegionFormat extends ConfigurationPart {

        @Comment("Equivalent to DivineMC's region-format.type. Valid values: MCA, LINEAR, B_LINEAR.")
        public String type = "MCA";

        @Comment("Equivalent to DivineMC's region-format.compression-level. Valid range: 1..22.")
        public int compressionLevel = 1;

        @Comment("Equivalent to DivineMC's region-format.linear-io-thread-count.")
        public int linearIoThreadCount = 6;

        @Comment("Equivalent to DivineMC's region-format.linear-io-flush-delay-ms.")
        public int linearIoFlushDelayMs = 100;

        @Comment("Equivalent to DivineMC's region-format.linear-use-virtual-threads.")
        public boolean linearUseVirtualThreads = true;
    }

    @Setting("virtual-threads")
    public VirtualThreads virtualThreads = new VirtualThreads();

    public class VirtualThreads extends ConfigurationPart {

        @Comment("Enables use of virtual threads that were added in Java 21.")
        public boolean enabled = true;

        @Comment("Uses virtual threads for the Bukkit async scheduler.")
        public boolean bukkitScheduler = true;

        @Comment("Uses virtual threads for the chat scheduler.")
        public boolean chatScheduler = true;

        @Comment("Uses virtual threads for the tab-complete scheduler.")
        public boolean tabCompleteScheduler = true;

        @Comment("Uses virtual threads for the MCUtil async executor.")
        public boolean asyncExecutor = true;

        @Comment("Uses virtual threads for the async command builder thread pool.")
        public boolean commandBuilderScheduler = true;

        @Comment("Uses virtual threads for the server text filter pool.")
        public boolean serverTextFilterPool = true;
    }

    @Setting("async")
    public AsyncOperations asyncOperations = new AsyncOperations();

    public class AsyncOperations extends ConfigurationPart {

        public Pathfinding pathfinding = new Pathfinding();
        public MultithreadedTracker multithreadedTracker = new MultithreadedTracker();

        public class Pathfinding extends ConfigurationPart {

            @Comment("Equivalent to DivineMC's async.pathfinding.enable.")
            public boolean enable = true;

            @Comment("Equivalent to DivineMC's async.pathfinding.max-threads. Use 0 for availableProcessors / 4, or a negative value to reserve that many processors.")
            public int maxThreads = 1;

            @Comment("Equivalent to DivineMC's async.pathfinding.keepalive, in seconds.")
            public int keepalive = 60;

            @Comment("Equivalent to DivineMC's async.pathfinding.queue-size. Values <= 0 use max-threads * 256.")
            public int queueSize = 0;

            @Comment("Equivalent to DivineMC's async.pathfinding.reject-policy. Valid values: CALLER_RUNS, FLUSH_ALL.")
            public String rejectPolicy = "CALLER_RUNS";
        }

        public class MultithreadedTracker extends ConfigurationPart {

            @Comment("Equivalent to DivineMC's async.multithreaded-tracker.enable.")
            public boolean enable = true;

            @Comment("Equivalent to DivineMC's async.multithreaded-tracker.compat-mode. Enable for better compatibility with NPC plugins that use real player entities.")
            public boolean compatMode = false;

            @Comment("Equivalent to DivineMC's async.multithreaded-tracker.max-threads. Use 0 for availableProcessors / 4, or a negative value to reserve that many processors.")
            public int maxThreads = 1;

            @Comment("Equivalent to DivineMC's async.multithreaded-tracker.keepalive, in seconds.")
            public int keepalive = 60;

            @Comment("Equivalent to DivineMC's async.multithreaded-tracker.queue-size. Values <= 0 use max-threads * 256.")
            public int queueSize = 0;
        }
    }


}
