package io.multipaper.shreddedpaper.config;

import io.papermc.paper.configuration.ConfigurationPart;
import org.spongepowered.configurate.objectmapping.meta.Comment;

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


}
