package io.multipaper.shreddedpaper.audit;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raids;
import org.bxteam.divinemc.config.DivineConfig;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Normal
class RaidConcurrencyRegressionTest {
    @BeforeAll
    static void configure() throws IOException {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("astatine-bug-audit", ".yml").toFile());
    }

    @AfterEach
    void restoreConfig() {
        DivineConfig.syncFromShreddedPaper(new ShreddedPaperConfiguration());
    }

    @Test
    void concurrentRaidIdsRemainUnique() throws Exception {
        Method next = Raids.class.getDeclaredMethod("getUniqueId");
        next.setAccessible(true);
        final int workers = 12;
        final int perWorker = 30_000;
        long duplicates = 0;
        for (int attempt = 0; attempt < 1 && duplicates == 0; attempt++) {
            Raids raids = new Raids();
            int[][] ids = new int[workers][perWorker];
            CyclicBarrier start = new CyclicBarrier(workers);
            try (var executor = Executors.newFixedThreadPool(workers)) {
                var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int worker = 0; worker < workers; worker++) {
                    final int slot = worker;
                    futures.add(executor.submit(() -> {
                        start.await(10, TimeUnit.SECONDS);
                        for (int i = 0; i < perWorker; i++) ids[slot][i] = (int) next.invoke(raids);
                        return null;
                    }));
                }
                for (var future : futures) future.get(20, TimeUnit.SECONDS);
            }
            long unique = Arrays.stream(ids).flatMapToInt(Arrays::stream).distinct().count();
            duplicates = (long) workers * perWorker - unique;
        }
        assertEquals(0, duplicates);
    }

    @Test
    void raidCooldownMutationDoesNotCrashTheGlobalTick() throws Exception {
        Raids raids = new Raids();
        ServerLevel level = mock(ServerLevel.class);
        var config = mock(org.purpurmc.purpur.PurpurWorldConfig.class);
        config.raidCooldownSeconds = 30;
        setField(level, "purpurConfig", config);
        for (int i = 0; i < 10_000; i++) raids.playerCooldowns.put(new java.util.UUID(0, i), 30);
        var active = new java.util.concurrent.atomic.AtomicBoolean(true);
        java.util.ConcurrentModificationException observed = null;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                var key = new java.util.UUID(1, 0);
                while (active.get()) {
                    raids.playerCooldowns.put(key, 30);
                    raids.playerCooldowns.remove(key);
                }
            });
            try {
                for (int i = 0; i < 2000 && observed == null; i++) {
                    try { raids.tick(level); }
                    catch (java.util.ConcurrentModificationException failure) { observed = failure; }
                }
            } finally {
                active.set(false);
                writer.get(5, TimeUnit.SECONDS);
            }
        }
        assertNull(observed);
    }

    static void setField(Object instance, String name, Object value) throws Exception {
        Field field = instance.getClass().getField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
