package io.multipaper.shreddedpaper.threading;

import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DimensionDataCacheTest {
    @TempDir Path directory;

    private static final class Data extends SavedData {
    }

    @Test
    void publishedValueCanBeReadWhileAnotherThreadHoldsTheComputeLock() throws Exception {
        final var storage = new DimensionDataStorage(directory, null, null);
        final var type = new SavedDataType<>("cached", Data::new, Codec.INT.xmap(ignored -> new Data(), ignored -> 0), null);
        final Data value = new Data();
        storage.cache.put(type, Optional.of(value));
        final var entered = new CountDownLatch(1);
        final var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            final var holder = executor.submit(() -> storage.cache.compute(type, (key, current) -> {
                entered.countDown();
                try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return current;
            }));
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertSame(value, executor.submit(() -> storage.computeIfAbsent(type)).get(5, TimeUnit.SECONDS));
            } finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void concurrentMissesStillConstructOneSharedValueIncludingNegativeCache() throws Exception {
        final var storage = new DimensionDataStorage(directory, null, null);
        final AtomicInteger constructions = new AtomicInteger();
        final var type = new SavedDataType<>("missing", () -> { constructions.incrementAndGet(); return new Data(); },
            Codec.INT.xmap(ignored -> new Data(), ignored -> 0), null);
        storage.cache.put(type, Optional.empty());
        final CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            final var futures = new ArrayList<java.util.concurrent.Future<Data>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> { start.await(); return storage.computeIfAbsent(type); }));
            start.countDown();
            final Data first = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) assertSame(first, future.get(5, TimeUnit.SECONDS));
            assertEquals(1, constructions.get());
            final Data replacement = new Data();
            storage.set(type, replacement);
            assertSame(replacement, storage.computeIfAbsent(type));
        }
    }
}
