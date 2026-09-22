import com.sun.management.ThreadMXBean;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Uses real classes from the measured runtime jar; no replacement lock implementation. */
public final class LockProbe {
    private static final ThreadMXBean BEAN = (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static volatile long sink;

    private static long[] square(int originX, int originZ, int width, int border) {
        long[] cells = new long[(width + 2 * border) * (width + 2 * border)];
        int i = 0;
        for (int x = originX - border; x < originX + width + border; x++) {
            for (int z = originZ - border; z < originZ + width + border; z++) {
                cells[i++] = RegionPos.asLong(x, z);
            }
        }
        Arrays.sort(cells);
        return cells;
    }

    private static void geometry() throws Exception {
        ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();
        var first = locker.internalTryTakeExactLockNow(square(0, 0, 1, 0), square(0, 0, 1, 1));
        if (first == null) throw new AssertionError("Initial lock failed");
        try {
            for (int distance = 1; distance <= 3; distance++) {
                final int offset = distance;
                AtomicReference<Boolean> acquired = new AtomicReference<>();
                AtomicReference<Throwable> error = new AtomicReference<>();
                Thread other = new Thread(() -> {
                    try {
                        var lock = locker.internalTryTakeExactLockNow(square(offset, 0, 1, 0), square(offset, 0, 1, 1));
                        acquired.set(lock != null);
                        if (lock != null) lock.unlock();
                    } catch (Throwable failure) { error.set(failure); }
                });
                other.start();
                other.join();
                if (error.get() != null) throw new AssertionError(error.get());
                if (acquired.get() != (distance >= 3)) throw new AssertionError("Unexpected halo behavior");
                System.out.printf("PROBE {\"case\":\"halo_geometry\",\"cell_distance\":%d,\"second_acquired\":%s}%n", distance, acquired.get());
            }
        } finally { first.unlock(); }
        if (locker.areAnyLocked()) throw new AssertionError("Leaked locks");
    }

    private static void measure(String label, int count, Runnable operation) {
        final int iterations = 5000;
        for (int i = 0; i < 20000; i++) operation.run();
        double[] cpu = new double[9], allocated = new double[9];
        long thread = Thread.currentThread().threadId();
        for (int sample = 0; sample < cpu.length; sample++) {
            long beforeBytes = BEAN.getThreadAllocatedBytes(thread);
            long beforeCpu = BEAN.getCurrentThreadCpuTime();
            for (int i = 0; i < iterations; i++) operation.run();
            cpu[sample] = (BEAN.getCurrentThreadCpuTime() - beforeCpu) / (double) iterations;
            allocated[sample] = (BEAN.getThreadAllocatedBytes(thread) - beforeBytes) / (double) iterations;
        }
        double[] sortedCpu = cpu.clone(), sortedAllocated = allocated.clone();
        Arrays.sort(sortedCpu);
        Arrays.sort(sortedAllocated);
        System.out.printf(java.util.Locale.ROOT,
            "PROBE {\"case\":\"%s\",\"cells\":%d,\"median_cpu_ns\":%.3f,\"median_bytes\":%.3f,\"cpu_samples\":%s}%n",
            label, count, sortedCpu[4], sortedAllocated[4], Arrays.toString(cpu));
    }

    private static void lockCosts() {
        for (int width : new int[]{1, 2, 4, 8}) {
            ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();
            long[] write = square(0, 0, width, 0), isolation = square(0, 0, width, 1);
            measure("acquire_release", write.length, () -> {
                var lock = locker.internalTryTakeExactLockNow(write, isolation);
                if (lock == null) throw new AssertionError("Uncontended acquisition failed");
                lock.unlock();
                sink++;
            });
            if (locker.areAnyLocked()) throw new AssertionError("Leaked locks");
        }
    }

    private static void cleanupCosts() throws Exception {
        for (int held : new int[]{0, 64, 512}) {
            ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();
            CountDownLatch ready = new CountDownLatch(1), release = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();
            Thread owner = new Thread(() -> {
                ShreddedPaperRegionLocker.RegionLock lock = null;
                try {
                    if (held > 0) {
                        long[] cells = new long[held];
                        for (int i = 0; i < held; i++) cells[i] = RegionPos.asLong(1000 + i, 1000);
                        lock = locker.internalTryTakeExactLockNow(cells);
                        if (lock == null) throw new AssertionError("Setup acquisition failed");
                    }
                    ready.countDown();
                    release.await();
                } catch (Throwable failure) { error.set(failure); ready.countDown(); }
                finally { if (lock != null) lock.unlock(); }
            });
            owner.start();
            ready.await();
            try {
                if (error.get() != null) throw new AssertionError(error.get());
                measure("empty_current_thread_cleanup", held, () -> {
                    int released = locker.releaseCurrentThreadLocks();
                    if (released != 0) throw new AssertionError("Released someone else's lock");
                    sink += released;
                });
            } finally { release.countDown(); owner.join(); }
            if (error.get() != null) throw new AssertionError(error.get());
            if (locker.areAnyLocked()) throw new AssertionError("Leaked locks");
        }
    }

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        geometry();
        lockCosts();
        cleanupCosts();
        var collector = new net.minecraft.world.entity.InsideBlockEffectApplier.StepBasedCollector();
        // With no effects, the actual implementation never dereferences the entity argument.
        measure("empty_effect_collector", 0, () -> collector.applyAndClear(null));
        System.out.println("LOCK_PROBE_PASS sink=" + sink);
    }
}
