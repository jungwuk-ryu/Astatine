/* SPDX-License-Identifier: MIT */
package alternate.current.wire;
import static alternate.current.wire.ActualNodes.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.SplittableRandom;

/** Attachment's differential model, adapted to actual Minecraft Node and WireNode. */
public final class QueueTest {
    private static long checks;
    private static final Field HEADS = field("heads");
    private static final Field TAILS = field("tails");
    private static final Field MASK = field("nonEmpty");
    private static final Field HIGHEST = field("highest");

    private QueueTest() {}
    private static Field field(String name) {
        try {
            Field f = BucketedPriorityQueue.class.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    static void require(boolean ok, String msg) {
        checks++;
        if (!ok) throw new AssertionError(msg);
    }
    private static int id(Node n) { return n == null ? -1 : n.pos.getX(); }

    /** Deliberately simple, non-intrusive model; independent of both queue algorithms. */
    private static final class Model {
        final ArrayList<LinkedHashSet<Integer>> buckets = new ArrayList<>(16);
        final int[] priority;
        int size;
        Model(int n) {
            priority = new int[n];
            Arrays.fill(priority, -1);
            for (int p = 0; p < 16; p++) buckets.add(new LinkedHashSet<>());
        }
        boolean offer(int id, int p) {
            int old = priority[id];
            if (old == p) return false;
            if (old >= 0) buckets.get(old).remove(id);
            else size++;
            buckets.get(p).add(id);
            priority[id] = p;
            return true;
        }
        int peek() {
            for (int p = 15; p >= 0; p--) {
                if (!buckets.get(p).isEmpty()) return buckets.get(p).getFirst();
            }
            return -1;
        }
        int poll() {
            int id = peek();
            if (id >= 0) {
                buckets.get(priority[id]).remove(id);
                priority[id] = -1;
                size--;
            }
            return id;
        }
        void clear() {
            buckets.forEach(LinkedHashSet::clear);
            Arrays.fill(priority, -1);
            size = 0;
        }
    }

    private static final class Pair {
        final PriorityQueue baseline = new PriorityQueue();
        final BucketedPriorityQueue candidate = new BucketedPriorityQueue();
        final Node[] a;
        final Node[] b;
        final Model model;
        Pair(int n) {
            a = new Node[n]; b = new Node[n]; model = new Model(n);
            for (int i = 0; i < n; i++) { a[i] = create(i); b[i] = create(i); }
        }
        void desired(int i, int p) { requested(a[i], p); requested(b[i], p); }
        void offer(int i, int p) {
            desired(i, p);
            boolean expected = model.offer(i, p);
            require(baseline.offer(a[i]) == expected, "baseline offer mismatch");
            require(candidate.offer(b[i]) == expected, "candidate offer mismatch");
            shallow();
        }
        void poll() {
            int expected = model.poll();
            Node x = baseline.poll(), y = candidate.poll();
            require(id(x) == expected, "baseline poll mismatch");
            require(id(y) == expected, "candidate poll mismatch");
            if (x != null) {
                require(x.prev_node == null && x.next_node == null, "baseline retained links");
                require(y.prev_node == null && y.next_node == null, "candidate retained links");
            }
            shallow();
        }
        void clear() {
            model.clear(); baseline.clear(); candidate.clear();
            for (int i = 0; i < a.length; i++) {
                require(a[i].prev_node == null && a[i].next_node == null, "baseline clear links");
                require(b[i].prev_node == null && b[i].next_node == null, "candidate clear links");
            }
            shallow();
        }
        void drain() { while (model.size != 0) poll(); poll(); }
        void shallow() {
            require(baseline.size() == model.size, "baseline size");
            require(candidate.size() == model.size, "candidate size");
            int expected = model.peek();
            require(id(baseline.peek()) == expected, "baseline peek");
            require(id(candidate.peek()) == expected, "candidate peek");
        }
        void deep() {
            shallow();
            for (int i = 0; i < a.length; i++) {
                boolean in = model.priority[i] >= 0;
                require(baseline.contains(a[i]) == in, "baseline membership");
                require(candidate.contains(b[i]) == in, "candidate membership");
                if (in) {
                    require(a[i].priority == model.priority[i], "baseline queued priority");
                    require(b[i].priority == model.priority[i], "candidate queued priority");
                }
            }
            try {
                Node[] heads = (Node[]) HEADS.get(candidate);
                Node[] tails = (Node[]) TAILS.get(candidate);
                boolean[] seen = new boolean[b.length];
                int expectedMask = 0, count = 0;
                for (int p = 0; p < 16; p++) {
                    Node prev = null;
                    var order = model.buckets.get(p).iterator();
                    for (Node n = heads[p]; n != null; n = n.next_node) {
                        require(!seen[n.pos.getX()], "duplicate or cyclic node");
                        seen[n.pos.getX()] = true;
                        require(n.prev_node == prev, "broken previous link");
                        require(n.priority == p, "wrong bucket");
                        require(order.hasNext() && order.next() == n.pos.getX(), "FIFO mismatch");
                        prev = n; count++;
                    }
                    require(!order.hasNext(), "missing node");
                    require(prev == tails[p], "wrong tail");
                    if (heads[p] != null) expectedMask |= 1 << p;
                }
                require(count == model.size, "deep size");
                require(MASK.getInt(candidate) == expectedMask, "stale mask");
                require(HIGHEST.getInt(candidate) == 31 - Integer.numberOfLeadingZeros(expectedMask), "stale highest");
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
    }

    private static void examples() {
        Pair t = new Pair(8);
        t.poll(); t.clear();
        t.offer(0, 15); t.offer(1, 15); t.offer(2, 4); t.offer(3, 15);
        t.offer(1, 15); // unchanged priority must NOT move behind node 3
        t.deep(); t.poll(); t.poll(); t.poll(); t.poll();
        t.offer(0, 5); t.offer(1, 7); t.offer(2, 7); t.offer(0, 7); // append after 2
        t.deep(); t.drain();
        t.offer(0, 15); t.offer(1, 0); t.desired(0, 0); // do NOT requeue implicitly
        t.deep(); t.drain();
        t.offer(0, 15); t.offer(1, 15); t.offer(2, 0); t.offer(0, 0);
        t.deep(); t.clear(); t.offer(2, 15); t.offer(0, 0); t.deep(); t.drain();
        for (Queue<Node> q : new ArrayList<Queue<Node>>(java.util.List.of(t.baseline, t.candidate))) {
            try { q.offer(null); throw new AssertionError("null accepted"); }
            catch (NullPointerException expected) { checks++; }
            try { q.iterator(); throw new AssertionError("iterator supported"); }
            catch (UnsupportedOperationException expected) { checks++; }
        }
        // Additional fail-fast guarantee; upstream's invalid-priority behavior is NOT the contract.
        t.offer(5, 8);
        for (int invalid : new int[] {-1, 16, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            requested(t.b[5], invalid);
            try { t.candidate.offer(t.b[5]); throw new AssertionError("invalid accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            requested(t.b[5], 8); t.deep();
        }
        t.clear();
    }

    private static int exhaustivePriorities() {
        Pair t = new Pair(3);
        int[][] perms = {{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};
        int cases = 0;
        for (int encoded = 0; encoded < 4096; encoded++) {
            for (int[] perm : perms) {
                for (int i : perm) t.offer(i, (encoded >>> (4 * i)) & 15);
                t.deep(); t.drain(); cases++;
            }
        }
        return cases;
    }
    private static int exhaustiveTraces() {
        Pair t = new Pair(2);
        int[] powers = {0, 1, 14, 15};
        final int cases = 100_000; // all length-five sequences from ten operations
        for (int encoded = 0; encoded < cases; encoded++) {
            t.clear();
            int code = encoded;
            for (int i = 0; i < 5; i++, code /= 10) {
                int action = code % 10;
                if (action < 8) t.offer(action / 4, powers[action % 4]);
                else if (action == 8) t.poll();
                else t.clear();
            }
            t.deep(); t.drain();
        }
        return cases;
    }
    private static long random(int seeds, int steps) {
        Pair t = new Pair(128);
        for (int seed = 0; seed < seeds; seed++) {
            SplittableRandom r = new SplittableRandom(0x454152544850L + seed);
            t.clear();
            try {
                for (int s = 0; s < steps; s++) {
                    int action = r.nextInt(100), id = r.nextInt(t.a.length), p = r.nextInt(16);
                    if (action < 55) t.offer(id, p);
                    else if (action < 77) t.poll();
                    else if (action < 92) t.desired(id, p);
                    else if (action < 95) t.clear();
                    else t.shallow();
                    if ((s & 255) == 0) t.deep();
                }
                t.deep(); t.drain();
            } catch (AssertionError e) {
                throw new AssertionError("random seed=" + seed + ": " + e.getMessage(), e);
            }
        }
        return (long) seeds * steps;
    }
    public static void main(String[] args) {
        bootstrap();
        long start = System.nanoTime();
        examples();
        System.out.println("PASS: FIFO, reprioritization, priority snapshots, cleanup, boundaries");
        System.out.println("PASS: exhaustive priority/permutation cases=" + exhaustivePriorities());
        System.out.println("PASS: exhaustive five-operation traces=" + exhaustiveTraces());
        System.out.println("PASS: seeded randomized operations=" + random(100, 50_000));
        System.out.println("PASS: assertion checks=" + checks);
        System.out.printf(java.util.Locale.ROOT, "Elapsed seconds=%.3f%n", (System.nanoTime() - start) / 1e9);
        System.out.println("SCOPE: real Minecraft Node/WireNode queue contract; no world/GameTests.");
    }
}
