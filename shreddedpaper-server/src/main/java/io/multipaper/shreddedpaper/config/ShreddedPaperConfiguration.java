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
            This is the main configuration file for Astatine.
            There's quite alot to configure. Read the docs for more information.

            Docs: https://github.com/jungwuk-ryu/Astatine/blob/main/ASTATINE_YAML.md\s
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
        @Comment("Warning reserve threshold for non-dropping critical system work per region.")
        public int criticalRegionMailboxCapacity = 1024;
        @Comment("Maximum queued player-action tasks per region. Values below 0 use regionMailboxCapacity.")
        public int playerActionRegionMailboxCapacity = 2048;
        @Comment("Maximum queued cross-owner world mutation handoffs per region. Values below 0 use regionMailboxCapacity.")
        public int ownerHandoffRegionMailboxCapacity = 2048;
        @Comment("Maximum queued deferred chunk load/generation retry tasks per region before async chunk request backpressure rejects plugin/external requests.")
        public int chunkIoLoadRegionMailboxCapacity = 1024;
        @Comment("Maximum in-flight ticketed async chunk load/generation requests per normal region owner before requests are deferred.")
        public int chunkIoLoadMaxInflightNormalPerRegion = 256;
        @Comment("Maximum in-flight ticketed async chunk load/generation requests per degraded region owner before requests are deferred.")
        public int chunkIoLoadMaxInflightDegradedPerRegion = 32;
        @Comment("Region-local delay in ticks before retrying an async chunk load/generation request deferred by per-region QoS.")
        public long chunkIoLoadDeferredRetryDelayTicks = 2L;
        @Comment("Lower admitted async chunk load/generation requests to LOW priority while the target region is degraded.")
        public boolean chunkIoLoadDowngradeDegradedPriority = true;
        @Comment("Warning reserve for deferred internal chunk worker retries per region. Engine work remains non-dropping.")
        public int chunkIoExecutorDeferredRetryReserve = 1024;
        @Comment("Maximum in-flight internal chunk worker tasks per normal region owner before worker enqueue is deferred.")
        public int chunkIoExecutorMaxInflightNormalPerRegion = 8;
        @Comment("Maximum in-flight internal chunk worker tasks per degraded region owner before worker enqueue is deferred.")
        public int chunkIoExecutorMaxInflightDegradedPerRegion = 2;
        @Comment("Maximum permitless internal chunk worker overflow tasks per region after the deferred retry reserve is full.")
        public int chunkIoExecutorMaxOverflowPerRegion = 64;
        @Comment("Hard cap for accepted but not-yet-started internal chunk worker tasks per region. Producers above this cap wait instead of growing memory without bound.")
        public int chunkIoExecutorMaxBacklogPerRegion = 8192;
        @Comment("Warning reserve for internal chunk worker tasks waiting in the non-blocking backpressure retry coordinator per region. Engine work remains non-dropping.")
        public int chunkIoExecutorBackpressureRetryReserve = 4096;
        @Comment("Lower admitted internal chunk worker tasks to LOW priority while the target region is degraded.")
        public boolean chunkIoExecutorDowngradeDegradedPriority = true;
        @Comment("Maximum queued background chunk save tasks per region before autosave/save-all backpressure begins.")
        public int chunkIoSaveRegionMailboxCapacity = 512;
        @Comment("Maximum background autosave tasks admitted for one region owner per autosave producer pass.")
        public int chunkIoSaveMaxAutoSavesPerRegion = 2;
        @Comment("Maximum autosave queue entries scanned per pass, as a multiplier of max-auto-save-chunks-per-tick.")
        public int chunkIoSaveAutoSaveScanMultiplier = 4;
        @Comment("Maximum queued plugin tasks per region before RegionScheduler fail-fast rejection.")
        public int pluginRegionMailboxCapacity = 1024;
        @Comment("Maximum queued tracker/broadcast tasks per region. Repeated holder updates should be coalesced before reaching this queue.")
        public int trackerBroadcastRegionMailboxCapacity = 1024;
        @Comment("Maximum queued explosion/physics tasks per region.")
        public int explosionPhysicsRegionMailboxCapacity = 2048;
        @Comment("Target per-region cooperative auxiliary-work budget in milliseconds. Core game tick phases are not interrupted by this budget.")
        public long regionTickBudgetMs = 45;
        @Comment("Maximum deferred TNT explosions kept as frozen live entities per world. Overflow TNT is discarded without exploding to prevent hostile-load entity/save debt.")
        public int deferredTntBacklogPerWorld = 1024;
        @Comment("EWMA MSPT threshold that moves a region into the degraded scheduler lane.")
        public long degradedRegionMsptThreshold = 75;
        @Comment("EWMA MSPT threshold that marks a region as quarantined.")
        public long quarantinedRegionMsptThreshold = 5000;

    }

    @Setting("lag-compensation")
    public LagCompensation lagCompensation = new LagCompensation();

    public class LagCompensation extends ConfigurationPart {

        @Comment("Improves gameplay timing while a world or independent region is running below 20 TPS.")
        public boolean enabled = true;

        @Setting("block-entity-acceleration")
        public boolean blockEntityAcceleration = true;

        @Setting("block-breaking-acceleration")
        public boolean blockBreakingAcceleration = true;

        @Setting("eating-acceleration")
        public boolean eatingAcceleration = true;

        @Setting("potion-effect-acceleration")
        public boolean potionEffectAcceleration = true;

        @Setting("fluid-acceleration")
        public boolean fluidAcceleration = true;

        @Setting("pickup-acceleration")
        public boolean pickupAcceleration = true;

        @Setting("portal-acceleration")
        public boolean portalAcceleration = true;

        @Setting("time-acceleration")
        public boolean timeAcceleration = true;

        @Setting("random-tick-speed-acceleration")
        public boolean randomTickSpeedAcceleration = true;

        @Setting("ignore-moved-too-quickly-when-lagging")
        @Comment("Suppresses moved-too-quickly setbacks while the player's own independent region is measurably behind.")
        public boolean ignoreMovedTooQuicklyWhenLagging = true;

        @Setting("ignore-moved-wrongly-when-lagging")
        @Comment("Suppresses moved-wrongly setbacks while the player's own independent region is measurably behind.")
        public boolean ignoreMovedWronglyWhenLagging = true;

        @Setting("always-allow-weird-movement")
        @Comment("Disables moved-too-quickly and moved-wrongly enforcement entirely. This is intentionally off by default.")
        public boolean alwaysAllowWeirdMovement = false;

        @Setting("max-compensated-missed-ticks")
        @Comment("Upper bound for extra low-TPS compensation ticks applied in one server tick.")
        public int maxCompensatedMissedTicks = 20;

        @Setting("region-lag-mspt-threshold")
        @Comment("Region EWMA MSPT threshold used by movement lag compensation.")
        public double regionLagMsptThreshold = 55.0D;

        @Setting("region-lag-schedule-lag-threshold-ms")
        @Comment("Region scheduler lag threshold used by movement lag compensation.")
        public double regionLagScheduleLagThresholdMs = 100.0D;
    }

    public Optimizations optimizations = new Optimizations();

    public class Optimizations extends ConfigurationPart {

        public int entityActivationCheckFrequency = 20;
        public boolean disableVanishApi = false;
        public boolean disableLocatorBar = true;
        public boolean useLazyExecuteWhenNotFlushing = true;
        public boolean processTrackQueueInParallel = true;
        public boolean flushQueueInParallel = true;
        @Setting("prefer-io-uring-transport")
        @Comment("Prefer Netty io_uring over epoll for TCP listeners when native transport is enabled and io_uring is available. Linux-only and off by default.")
        public boolean preferIoUringTransport = false;
        public int maximumTrackersPerEntity = 500;
        public long trackerFullUpdateFrequency = 20;
        public long purgeStaleTicketsFrequency = 20;
        public boolean writePlayerSavesAsync = true;
        @Setting("scheduled-tick-presence-guard")
        @Comment("Skips per-cell loaded chunk scans during independent region scheduled ticks when neither block nor fluid tick data exists for the region cell.")
        public boolean scheduledTickPresenceGuard = true;
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

            @Setting("worldgen-computation-cache")
            public WorldgenComputationCache worldgenComputationCache = new WorldgenComputationCache();

            @Setting("experimental")
            public Experimental experimental = new Experimental();

            public class WorldgenComputationCache extends ConfigurationPart {
                @Comment("Emit sampled JFR events for region-limited parallel chunk generation tasks. This is instrumentation-only and is disabled by default.")
                @Setting("instrumentation-enabled")
                public boolean instrumentationEnabled = false;

                @Comment("Emit one chunk generation task timing event per N submitted generation tasks when instrumentation is enabled.")
                @Setting("instrumentation-sample-rate")
                public int instrumentationSampleRate = 256;
            }

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
