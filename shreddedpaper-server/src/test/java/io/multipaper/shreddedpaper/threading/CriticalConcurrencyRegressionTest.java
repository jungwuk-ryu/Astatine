package io.multipaper.shreddedpaper.threading;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.PotentialCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CriticalConcurrencyRegressionTest {

    @Test
    @Timeout(15)
    void potentialChargesRemainConsistentAcrossRegionWorkers() throws Exception {
        final int threadCount = 16;
        final int tasksPerThread = 2_000;
        final PotentialCalculator calculator = new PotentialCalculator();
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);

        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int thread = 0; thread < threadCount; thread++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    for (int task = 0; task < tasksPerThread; task++) {
                        calculator.addCharge(new BlockPos(1, 0, 0), 1.0D);
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (final Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(threadCount * tasksPerThread, calculator.getPotentialEnergyChange(BlockPos.ZERO, 1.0D));
    }

    @Test
    void crossOwnerDisconnectDefersBeforeCreatingAWaitable() throws IOException {
        final String patch = Files.readString(repositoryFile(
                "shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/network/ServerCommonPacketListenerImpl.java.patch"
        ));
        final int guardStart = patch.indexOf("region workers must never wait for another owner");
        final int waitableStart = patch.indexOf("Waitable waitable", guardStart);

        assertTrue(guardStart >= 0, "missing region-worker disconnect guard");
        assertTrue(waitableStart > guardStart, "guard must run before the blocking Waitable path");
        final String guard = patch.substring(guardStart, waitableStart);
        assertTrue(guard.contains("ShreddedPaperTickThread.isShreddedPaperTickThread()"));
        assertTrue(guard.contains("serverGamePacketListener.disconnectAsync(disconnectionDetails)"));
        assertTrue(guard.contains("return;"));
    }

    private static Path repositoryFile(final String relativePath) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            final Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository file: " + relativePath);
    }
}
