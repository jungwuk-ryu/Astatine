package io.multipaper.shreddedpaper.audit;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import io.multipaper.shreddedpaper.util.PlayerDataSaveQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Normal
class PlayerDataStorageRegressionTest {
    @BeforeAll
    static void configure() throws Exception {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("player-save-test", ".yml").toFile());
    }

    @Test
    @Timeout(15)
    void newestSnapshotAndItsBackupArePersistedInOrder(@TempDir Path dir) throws Exception {
        var access = mock(LevelStorageSource.LevelStorageAccess.class);
        when(access.getLevelPath(LevelResource.PLAYER_DATA_DIR)).thenReturn(dir);
        var storage = new PlayerDataStorage(access, null);
        var player = mock(Player.class);
        String uuid = UUID.randomUUID().toString();
        when(player.getStringUUID()).thenReturn(uuid);
        when(player.getPlainTextName()).thenReturn("Audit");
        when(player.registryAccess()).thenReturn(RegistryAccess.EMPTY);
        when(player.problemPath()).thenReturn(new ProblemReporter.RootFieldPathElement("audit"));
        AtomicInteger sequence = new AtomicInteger(1);
        doAnswer(invocation -> {
            ((ValueOutput) invocation.getArgument(0)).putInt("audit_sequence", sequence.get());
            return null;
        }).when(player).saveWithoutId(any(ValueOutput.class));
        var field = PlayerDataStorage.class.getDeclaredField("SAVE_QUEUE");
        field.setAccessible(true);
        PlayerDataSaveQueue queue = (PlayerDataSaveQueue) field.get(null);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        queue.submit(uuid, () -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("gate timed out");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }, true);
        try (var tick = mockStatic(TickThread.class); var executor = Executors.newSingleThreadExecutor()) {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            tick.when(TickThread::isShutdownThread).thenReturn(false);
            ShreddedPaperConfiguration.get().optimizations.writePlayerSavesAsync = true;
            storage.save(player);
            sequence.set(2);
            ShreddedPaperConfiguration.get().optimizations.writePlayerSavesAsync = false;
            var shutdown = executor.submit(() -> storage.save(player));
            release.countDown();
            shutdown.get(5, TimeUnit.SECONDS);
            queue.await(uuid);
            assertEquals(2, NbtIo.readCompressed(dir.resolve(uuid + ".dat"), NbtAccounter.unlimitedHeap()).getIntOr("audit_sequence", -1));
            assertEquals(1, NbtIo.readCompressed(dir.resolve(uuid + ".dat_old"), NbtAccounter.unlimitedHeap()).getIntOr("audit_sequence", -1));
        } finally {
            release.countDown();
            ShreddedPaperConfiguration.get().optimizations.writePlayerSavesAsync = true;
        }
    }
}
