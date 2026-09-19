package alternate.current.wire;
import static alternate.current.wire.ActualNodes.*;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Queue;
import java.util.SplittableRandom;

/**
 * Dependency-free exploratory microbenchmark, NOT JMH and NOT Minecraft MSPT.
 * Uses actual Minecraft Node and WireNode classes, not the attachment's fixtures.
 * Each measured cycle starts/ends with an empty queue. Node allocation and input
 * generation occur before measurement. Every workload is run unchanged on both
 * implementations in separate JVMs by run.py. Registry bootstrap is not timed.
 */
public final class QueueBenchmark {
    static final String[] WORKLOADS = {"singleton_64", "same_64", "ascending_16",
        "descending_16", "mixed_256", "sparse_256", "reprioritize_128",
        "duplicates_128", "interleaved_256", "fanout_24", "clear_256"};
    private static volatile long blackhole;
    private static volatile Object retainedQueue;
    private QueueBenchmark() {}

    static final class Work {
        final String name;
        final Node[] nodes = new Node[256];
        final int[] priorities = new int[256];
        int phase;
        Work(String name) {
            this.name = name;
            SplittableRandom random = new SplittableRandom(0x4541525448L);
            for (int i = 0; i < nodes.length; i++) {
                nodes[i] = create(i);
                priorities[i] = random.nextInt(16);
            }
        }
        long run(Queue<Node> q) {
            long sum = 0;
            int p = phase++ & 15;
            switch (name) {
                case "singleton_64" -> {
                    for (int i = 0; i < 64; i++) {
                        Node node = nodes[i]; requested(node, (i + p) & 15);
                        q.offer(node); sum += q.poll().pos.getX();
                    }
                }
                case "same_64" -> {
                    for (int i = 0; i < 64; i++) { requested(nodes[i], p); q.offer(nodes[i]); }
                    for (int i = 0; i < 64; i++) sum = sum * 31 + q.poll().pos.getX();
                }
                case "ascending_16", "descending_16" -> {
                    for (int i = 0; i < 16; i++) {
                        requested(nodes[i], name.equals("ascending_16") ? i : 15 - i);
                        q.offer(nodes[i]);
                    }
                    for (int i = 0; i < 16; i++) sum = sum * 31 + q.poll().pos.getX();
                }
                case "mixed_256", "sparse_256" -> {
                    for (int i = 0; i < 256; i++) {
                        requested(nodes[i], name.equals("mixed_256")
                            ? (priorities[i] + p) & 15 : (i % 3 == 0 ? 15 : i % 3 == 1 ? 0 : 7));
                        q.offer(nodes[i]);
                    }
                    for (int i = 0; i < 256; i++) sum = sum * 31 + q.poll().pos.getX();
                }
                case "reprioritize_128" -> {
                    for (int i = 0; i < 128; i++) {
                        requested(nodes[i], (priorities[i] + p) & 15); q.offer(nodes[i]);
                    }
                    for (int i = 0; i < 128; i += 3) {
                        requested(nodes[i], (nodes[i].priority() + 7) & 15);
                        if (q.offer(nodes[i])) sum++;
                    }
                    for (int i = 0; i < 128; i += 4) if (q.offer(nodes[i])) sum++;
                    for (int i = 0; i < 128; i++) sum = sum * 31 + q.poll().pos.getX();
                }
                case "duplicates_128" -> {
                    for (int i = 0; i < 128; i++) {
                        requested(nodes[i], (priorities[i] + p) & 15); q.offer(nodes[i]);
                    }
                    for (int repeat = 0; repeat < 2; repeat++) {
                        for (int i = 0; i < 128; i++) if (q.offer(nodes[i])) sum++;
                    }
                    for (int i = 0; i < 128; i++) sum = sum * 31 + q.poll().pos.getX();
                }
                case "interleaved_256" -> {
                    for (int i = 0; i < 512; i++) {
                        Node node = nodes[(i * 17) & 255];
                        requested(node, (priorities[i & 255] + p) & 15);
                        if (q.offer(node)) sum++;
                        if ((i & 1) == 0) sum = sum * 31 + q.poll().pos.getX();
                    }
                    Node node;
                    while ((node = q.poll()) != null) sum = sum * 31 + node.pos.getX();
                }
                case "fanout_24" -> {
                    // Synthetic queue trace, NOT a simulation of Minecraft's wire engine.
                    requested(nodes[0], 15); q.offer(nodes[0]);
                    Node node;
                    while ((node = q.poll()) != null) {
                        sum = sum * 31 + node.pos.getX();
                        if (node.pos.getX() < 16) {
                            int i = node.pos.getX();
                            if (i < 15) {
                                requested(nodes[i + 1], 14 - i); q.offer(nodes[i + 1]);
                            }
                            for (int j = 0; j < 24; j++) {
                                Node neighbor = nodes[16 + ((i * 12 + j) & 127)];
                                requested(neighbor, node.priority);
                                q.offer(neighbor);
                            }
                        }
                    }
                }
                case "clear_256" -> {
                    for (int i = 0; i < 256; i++) {
                        requested(nodes[i], (priorities[i] + p) & 15);
                        if (q.offer(nodes[i])) sum++;
                    }
                    q.clear();
                }
                default -> throw new IllegalArgumentException("Unknown workload: " + name);
            }
            return sum;
        }
    }

    private record Sample(long cycles, long nanos, long bytes, long checksum) {}
    private static Sample sample(Queue<Node> queue, Work work, long durationNanos, ThreadMXBean bean) {
        long cycles = 0, checksum = 0;
        long thread = Thread.currentThread().threadId();
        long before = bean == null ? 0 : bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime(), end;
        do {
            for (int i = 0; i < 64; i++) checksum += work.run(queue);
            cycles += 64;
            end = System.nanoTime();
        } while (end - start < durationNanos);
        long after = bean == null ? 0 : bean.getThreadAllocatedBytes(thread);
        blackhole ^= checksum;
        return new Sample(cycles, end - start, bean == null ? -1 : after - before, checksum);
    }

    static void verify() {
        for (String name : WORKLOADS) {
            Work a = new Work(name), b = new Work(name);
            Queue<Node> baseline = new PriorityQueue(), candidate = new BucketedPriorityQueue();
            for (int step = 0; step < 256; step++) {
                if (a.run(baseline) != b.run(candidate)) throw new AssertionError("Trace checksum: " + name);
                if (!baseline.isEmpty() || !candidate.isEmpty()) throw new AssertionError("Undrained trace: " + name);
                for (Node node : b.nodes) {
                    if (node.prev_node != null || node.next_node != null) throw new AssertionError("Leaked link: " + name);
                }
            }
            System.out.println("PASS: 256 matched synthetic trace cycles: " + name);
        }
    }

    public static void main(String[] args) {
        bootstrap();
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)));
        if (args.length == 1) { verify(); return; }
        if (args[0].equals("one")) {
            runKernel(java.util.Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        String[] work = WORKLOADS.clone();
        if (Boolean.parseBoolean(args[4])) java.util.Collections.reverse(java.util.Arrays.asList(work));
        for (String name : work) runKernel(new String[] {args[0], name, args[1], args[2], args[3]});
    }
    private static void runKernel(String[] args) {
        if (args.length == 1 && args[0].equals("verify")) { verify(); return; }
        if (args.length != 5) throw new IllegalArgumentException("variant workload warmupMillis samples sampleMillis");
        Queue<Node> queue = switch (args[0]) {
            case "ac" -> new PriorityQueue();
            case "ec" -> new BucketedPriorityQueue();
            default -> throw new IllegalArgumentException("variant must be ac or ec");
        };
        retainedQueue = queue;
        Work work = new Work(args[1]);
        int warmup = Integer.parseInt(args[2]), samples = Integer.parseInt(args[3]), millis = Integer.parseInt(args[4]);
        if (warmup < 0 || samples <= 0 || millis <= 0) throw new IllegalArgumentException("Invalid duration/count");
        ThreadMXBean bean = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean x && x.isThreadAllocatedMemorySupported() ? x : null;
        if (bean != null) bean.setThreadAllocatedMemoryEnabled(true);
        sample(queue, work, warmup * 1_000_000L, bean);
        for (int i = 0; i < samples; i++) {
            Sample s = sample(queue, work, millis * 1_000_000L, bean);
            System.out.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%.6f,%.6f,%d%n", args[0], args[1], i,
                s.cycles, s.nanos, (double) s.nanos / s.cycles,
                s.bytes < 0 ? -1.0 : (double) s.bytes / s.cycles, s.checksum);
        }
    }
}
