package io.multipaper.shreddedpaper.threading;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import io.multipaper.shreddedpaper.region.RegionPos;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

public class ShreddedPaperRegionScheduler {

    private static final Logger LOGGER = LogUtils.getClassLogger();

    private final ShreddedPaperRegionLocker locker = new ShreddedPaperRegionLocker();

    public static CompletableFuture<Void> schedule(ServerLevel level, RegionPos regionPos, Runnable runnable) {
        return level.chunkScheduler.schedule(regionPos, runnable);
    }

    /**
     * Schedule a task to run on the given region's thread.
     */
    public CompletableFuture<Void> schedule(RegionPos regionPos, Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        submit(future, () -> run(regionPos, runnable, future));

        return future;
    }

    /**
     * Avoid using this often. Locking massive parts of the world can take time and will freeze these regions during the process.
     */
    public CompletableFuture<Void> scheduleOnMany(Runnable runnable, RegionPos... posArray) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        submit(future, () -> runOnMany(sortPredictably(posArray), runnable, future));

        return future;
    }

    public static CompletableFuture<Void> scheduleAcrossLevels(ServerLevel level1, RegionPos regionPos1, ServerLevel level2, RegionPos regionPos2, Runnable runnable) {
        if (level1 == level2) {
            // We don't sort the regionPos in this method because we assume they're a different level, use the method that does sort them instead
            return level1.chunkScheduler.scheduleOnMany(runnable, regionPos1, regionPos2);
        }

        ServerLevel finalLevel1;
        RegionPos finalRegionPos1;
        ServerLevel finalLevel2;
        RegionPos finalRegionPos2;

        // Sort predictably to avoid deadlocks
        if (compare(level1, level2) > 0) {
            finalLevel1 = level2;
            finalRegionPos1 = regionPos2;
            finalLevel2 = level1;
            finalRegionPos2 = regionPos1;
        } else {
            finalLevel1 = level1;
            finalRegionPos1 = regionPos1;
            finalLevel2 = level2;
            finalRegionPos2 = regionPos2;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        submit(future, () -> runAcrossLevels(finalLevel1, finalRegionPos1, finalLevel2, finalRegionPos2, runnable, future));

        return future;
    }

    public static CompletableFuture<Void> scheduleAcrossLevels(ServerLevel level1, RegionPos[] regionPosArray1, ServerLevel level2, RegionPos[] regionPosArray2, Runnable runnable) {
        if (level1 == level2) {
            RegionPos[] combinedRegions = new RegionPos[regionPosArray1.length + regionPosArray2.length];
            System.arraycopy(regionPosArray1, 0, combinedRegions, 0, regionPosArray1.length);
            System.arraycopy(regionPosArray2, 0, combinedRegions, regionPosArray1.length, regionPosArray2.length);
            return level1.chunkScheduler.scheduleOnMany(runnable, combinedRegions);
        }

        ServerLevel finalLevel1;
        RegionPos[] finalRegionPosArray1;
        ServerLevel finalLevel2;
        RegionPos[] finalRegionPosArray2;

        // Sort predictably to avoid deadlocks
        if (compare(level1, level2) > 0) {
            finalLevel1 = level2;
            finalRegionPosArray1 = regionPosArray2;
            finalLevel2 = level1;
            finalRegionPosArray2 = regionPosArray1;
        } else {
            finalLevel1 = level1;
            finalRegionPosArray1 = regionPosArray1;
            finalLevel2 = level2;
            finalRegionPosArray2 = regionPosArray2;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();

        submit(future, () -> runAcrossLevels(finalLevel1, finalRegionPosArray1, finalLevel2, finalRegionPosArray2, runnable, future));

        return future;
    }

    private static CompletableFuture<Void> submit(CompletableFuture<Void> result, Runnable task) {
        CompletableFuture<Void> submitted = new CompletableFuture<>();
        try {
            ShreddedPaperTickThread.getExecutor().execute(() -> {
                try {
                    task.run();
                    submitted.complete(null);
                } catch (Throwable throwable) {
                    result.completeExceptionally(throwable);
                    submitted.completeExceptionally(throwable);
                    LOGGER.error("Region scheduler task failed", throwable);
                }
            });
        } catch (RejectedExecutionException rejected) {
            result.completeExceptionally(rejected);
            submitted.completeExceptionally(rejected);
        }
        return submitted;
    }

    /**
     * Sort the region positions in a predictable order to avoid deadlocks.
     */
    private RegionPos[] sortPredictably(RegionPos[] posArray) {
        // This should only be used on very small arrays, usually just 2 elements, so this simple O(n^2) sort is fine
        for (int i = 0; i < posArray.length; i++) {
            for (int j = i + 1; j < posArray.length; j++) {
                if (compare(posArray[i], posArray[j]) > 0) {
                    RegionPos temp = posArray[i];
                    posArray[i] = posArray[j];
                    posArray[j] = temp;
                }
            }
        }
        return posArray;
    }

    private static int compare(RegionPos a, RegionPos b) {
        int x = Integer.compare(a.x, b.x);
        if (x != 0) {
            return x;
        }
        return Integer.compare(a.z, b.z);
    }

    private static int compare(ServerLevel a, ServerLevel b) {
        return a.uuid.compareTo(b.uuid);
    }

    private void run(RegionPos regionPos, Runnable runnable, CompletableFuture<Void> future) {
        ShreddedPaperRegionLocker.RegionLock lock = null;
        try {
            lock = locker.tryTakeLockNow(regionPos);
            if (lock == null) {
                // Wait for unlock, then retry
                locker.onUnlock(regionPos, () -> submit(future, () -> run(regionPos, runnable, future)));
                return;
            }

            try {
                runnable.run();
            } finally {
                lock.unlock();
            }

            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private void runOnMany(RegionPos[] regionPosArray, Runnable runnable, CompletableFuture<Void> future) {
        ShreddedPaperRegionLocker.RegionLock lock = null;
        try {
            try {
                final List<RegionPos> writeRegions = sortedUniqueRegions(regionPosArray);
                final List<RegionPos> isolationRegions = isolationRegionsFor(writeRegions);
                lock = locker.internalTryTakeExactLockNow(writeRegions, isolationRegions);
                if (lock == null) {
                    locker.onUnlock(isolationRegions, () -> submit(future, () -> runOnMany(regionPosArray, runnable, future)));
                    return;
                }

                try (var ignored = locker.promoteCurrentThreadLocksToWrite()) {
                    runnable.run();
                }
            } finally {
                if (lock != null) lock.unlock();
            }

            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static void runAcrossLevels(ServerLevel level1, RegionPos regionPos1, ServerLevel level2, RegionPos regionPos2, Runnable runnable, CompletableFuture<Void> future) {
        ShreddedPaperRegionLocker.RegionLock lock1 = null;
        ShreddedPaperRegionLocker.RegionLock lock2 = null;
        try {
            try {
                lock1 = level1.chunkScheduler.locker.tryTakeLockNow(regionPos1);
                lock2 = lock1 == null ? null : level2.chunkScheduler.locker.tryTakeLockNow(regionPos2);

                if (lock2 == null) {
                    Supplier<CompletableFuture<Void>> unlockCallback = () -> submit(future, () -> runAcrossLevels(level1, regionPos1, level2, regionPos2, runnable, future));
                    if (lock1 == null) level1.chunkScheduler.locker.onUnlock(regionPos1, unlockCallback);
                    else level2.chunkScheduler.locker.onUnlock(regionPos2, unlockCallback);
                    return;
                }

                // Both locks acquired, run the task
                runnable.run();
            } finally {
                if (lock2 != null) lock2.unlock();
                if (lock1 != null) lock1.unlock();
            }

            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static void runAcrossLevels(ServerLevel level1, RegionPos[] regionPosArray1, ServerLevel level2, RegionPos[] regionPosArray2, Runnable runnable, CompletableFuture<Void> future) {
        ShreddedPaperRegionLocker.RegionLock lock1 = null;
        ShreddedPaperRegionLocker.RegionLock lock2 = null;
        try {
            try {
                final List<RegionPos> writeRegions1 = sortedUniqueRegions(regionPosArray1);
                final List<RegionPos> isolationRegions1 = isolationRegionsFor(writeRegions1);
                final List<RegionPos> writeRegions2 = sortedUniqueRegions(regionPosArray2);
                final List<RegionPos> isolationRegions2 = isolationRegionsFor(writeRegions2);

                lock1 = level1.chunkScheduler.locker.internalTryTakeExactLockNow(writeRegions1, isolationRegions1);
                lock2 = lock1 == null ? null : level2.chunkScheduler.locker.internalTryTakeExactLockNow(writeRegions2, isolationRegions2);

                if (lock2 == null) {
                    Supplier<CompletableFuture<Void>> unlockCallback = () -> submit(future, () -> runAcrossLevels(level1, regionPosArray1, level2, regionPosArray2, runnable, future));
                    if (lock1 == null) level1.chunkScheduler.locker.onUnlock(isolationRegions1, unlockCallback);
                    else level2.chunkScheduler.locker.onUnlock(isolationRegions2, unlockCallback);
                    return;
                }

                try (
                        var ignored1 = level1.chunkScheduler.locker.promoteCurrentThreadLocksToWrite();
                        var ignored2 = level2.chunkScheduler.locker.promoteCurrentThreadLocksToWrite()
                ) {
                    runnable.run();
                }
            } finally {
                if (lock2 != null) lock2.unlock();
                if (lock1 != null) lock1.unlock();
            }

            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static List<RegionPos> sortedUniqueRegions(RegionPos[] posArray) {
        final List<RegionPos> regions = new ArrayList<>(posArray.length);
        for (final RegionPos regionPos : posArray) {
            regions.add(regionPos);
        }
        regions.sort(ShreddedPaperRegionScheduler::compare);
        for (int i = regions.size() - 1; i > 0; i--) {
            if (regions.get(i).longKey == regions.get(i - 1).longKey) {
                regions.remove(i);
            }
        }
        return regions;
    }

    private static List<RegionPos> isolationRegionsFor(List<RegionPos> writeRegions) {
        final List<RegionPos> regions = new ArrayList<>(writeRegions.size() * 9);
        for (final RegionPos writeRegion : writeRegions) {
            for (int x = -ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; x <= ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; x++) {
                for (int z = -ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; z <= ShreddedPaperRegionLocker.REGION_LOCK_RADIUS; z++) {
                    regions.add(new RegionPos(writeRegion.x + x, writeRegion.z + z));
                }
            }
        }
        regions.sort(ShreddedPaperRegionScheduler::compare);
        for (int i = regions.size() - 1; i > 0; i--) {
            if (regions.get(i).longKey == regions.get(i - 1).longKey) {
                regions.remove(i);
            }
        }
        return regions;
    }

    public ShreddedPaperRegionLocker getRegionLocker() {
        return locker;
    }

}
