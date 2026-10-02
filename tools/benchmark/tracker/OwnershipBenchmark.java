package io.multipaper.benchmark;

import com.sun.management.ThreadMXBean;
import io.multipaper.shreddedpaper.ShreddedPaper;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.bukkit.plugin.java.JavaPlugin;

/** Measures the installed ownership query on a real region worker, with observable results. */
final class OwnershipBenchmark {
    private static volatile long sink;

    static void run(JavaPlugin plugin, ServerLevel level, int count) throws Exception {
        final BoundingBox[] boxes = new BoundingBox[count];
        for (int i = 0; i < count; ++i) {
            final int x = 2 + i % 8;
            final int z = 2 + (i / 8) % 8;
            boxes[i] = new BoundingBox(x - 1, -61, z - 1, x + 1, -58, z + 1);
            if (!ShreddedPaper.isSync(level, boxes[i])) throw new IllegalStateException("Benchmark is outside the current owner");
        }
        final ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported() || !bean.isCurrentThreadCpuTimeSupported()) throw new IllegalStateException("CPU and allocation accounting required");
        bean.setThreadAllocatedMemoryEnabled(true);
        bean.setThreadCpuTimeEnabled(true);
        final int rounds = 1_000_000 / count;
        for (int i = 0; i < 20; ++i) sample(level, boxes, rounds, bean);
        final List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < 9; ++i) samples.add(sample(level, boxes, rounds, bean));
        final String result = String.format(Locale.ROOT,
                "{\"boxes\":%d,\"roundsPerSample\":%d,\"cpuNsPerQuery\":%.3f,\"wallNsPerQuery\":%.3f,\"bytesPerQuery\":%.3f,\"samples\":%s}",
                count, rounds, median(samples.stream().mapToDouble(Sample::cpuNs).toArray()),
                median(samples.stream().mapToDouble(Sample::wallNs).toArray()),
                median(samples.stream().mapToDouble(Sample::bytes).toArray()), samples);
        Files.createDirectories(plugin.getDataFolder().toPath());
        Files.writeString(plugin.getDataFolder().toPath().resolve("ownership-" + count + ".json"), result + "\n");
        plugin.getLogger().info("OWNERSHIP_BENCH_DONE " + result);
    }

    private static Sample sample(ServerLevel level, BoundingBox[] boxes, int rounds, ThreadMXBean bean) {
        final long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().threadId());
        final long cpu = bean.getCurrentThreadCpuTime();
        final long wall = System.nanoTime();
        long confirmed = 0;
        for (int round = 0; round < rounds; ++round) {
            long owned = 0;
            for (BoundingBox box : boxes) if (ShreddedPaper.isSync(level, box)) ++owned;
            sink = owned;
            confirmed += owned;
        }
        final long elapsed = System.nanoTime() - wall;
        final long cpuElapsed = bean.getCurrentThreadCpuTime() - cpu;
        final long bytes = bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) - allocated;
        final double queries = (double) rounds * boxes.length;
        if (confirmed != (long) rounds * boxes.length) throw new IllegalStateException("Ownership result changed");
        return new Sample(cpuElapsed / queries, elapsed / queries, bytes / queries);
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        return values[values.length / 2];
    }

    private record Sample(double cpuNs, double wallNs, double bytes) {
        @Override
        public String toString() {
            return String.format(Locale.ROOT, "{\"cpuNs\":%.3f,\"wallNs\":%.3f,\"bytes\":%.3f}", this.cpuNs, this.wallNs, this.bytes);
        }
    }
}
