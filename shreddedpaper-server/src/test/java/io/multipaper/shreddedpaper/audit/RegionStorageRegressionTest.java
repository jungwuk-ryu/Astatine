package io.multipaper.shreddedpaper.audit;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfigurationLoader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.bxteam.divinemc.config.DivineConfig;
import org.bxteam.divinemc.region.type.BufferedRegionFile;
import org.bxteam.divinemc.region.type.LinearRegionFile;
import org.bukkit.support.environment.Normal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Normal
class RegionStorageRegressionTest {
    @BeforeAll
    static void configure() throws IOException {
        ShreddedPaperConfigurationLoader.init(Files.createTempFile("astatine-bug-audit", ".yml").toFile());
    }

    @AfterEach
    void restoreConfig() {
        DivineConfig.syncFromShreddedPaper(new ShreddedPaperConfiguration());
    }

    @Test
    void bufferedCloseFailureStillClosesTheChannel(@TempDir Path dir) throws Exception {
        BufferedRegionFile file = new BufferedRegionFile(dir.resolve("r.0.0.b_linear"), 1);
        Field field = BufferedRegionFile.class.getDeclaredField("channel");
        field.setAccessible(true);
        FileChannel real = (FileChannel) field.get(file);
        FileChannel failing = mock(FileChannel.class, delegatesTo(real));
        doThrow(new IOException("injected force failure")).when(failing).force(anyBoolean());
        field.set(file, failing);
        try {
            assertThrows(IOException.class, file::close);
            assertFalse(real.isOpen(), "failed close must still release the channel");
        } finally {
            real.close();
        }
    }

    @Test
    void bufferedFailedOpenClosesItsFileDescriptor(@TempDir Path dir) throws Exception {
        Path descriptors = Path.of("/proc/self/fd");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(descriptors));
        Path path = dir.resolve("r.0.0.b_linear");
        Files.write(path, new byte[18 * 1024]); // A complete header with an invalid magic number.
        for (int i = 0; i < 20; i++) {
            assertThrows(IOException.class, () -> new BufferedRegionFile(path, 1));
        }
        try (var open = Files.list(descriptors)) {
            assertEquals(0, open.filter(fd -> {
                try { return Files.readSymbolicLink(fd).equals(path.toAbsolutePath()); }
                catch (IOException disappeared) { return false; }
            }).count(), "failed constructors must not leave channels open");
        }
    }

    @Test
    void bufferedLegacyClearedHeaderIsNormalized(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        try (BufferedRegionFile file = new BufferedRegionFile(path, 1)) {
            file.write(pos, ByteBuffer.wrap(new byte[] {1, 2, 3}));
        }
        // Old clear() changed only the hasData byte, retaining a nonzero offset and length.
        byte[] bytes = Files.readAllBytes(path);
        bytes[Long.BYTES + 2 + Integer.BYTES + Long.BYTES + 2 * Long.BYTES] = 0;
        Files.write(path, bytes);
        try (BufferedRegionFile reopened = new BufferedRegionFile(path, 1)) {
            assertFalse(reopened.hasChunk(pos));
            assertNull(reopened.getChunkDataInputStream(pos));
        }
    }

    @Test
    void bufferedClearRemainsReadableBeforeAndAfterCleanClose(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        try (BufferedRegionFile writer = new BufferedRegionFile(path, 1)) {
            writer.write(pos, ByteBuffer.wrap(new byte[] {1, 2, 3}));
            writer.flush();
            writer.clear(pos);
            writer.flush();
            assertFalse(writer.hasChunk(pos));
            Path snapshot = dir.resolve("r.1.0.b_linear");
            Files.copy(path, snapshot);
            try (BufferedRegionFile reopened = new BufferedRegionFile(snapshot, 1)) {
                assertFalse(reopened.hasChunk(pos));
            }
        }
        try (BufferedRegionFile afterCleanClose = new BufferedRegionFile(path, 1)) {
            assertFalse(afterCleanClose.hasChunk(pos));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bufferedWriteFailurePreservesThePreviouslyDurableSector(boolean partialWrite, @TempDir Path dir) throws Exception {
        Path path = dir.resolve("r.0.0.b_linear");
        ChunkPos pos = new ChunkPos(0, 0);
        BufferedRegionFile writer = new BufferedRegionFile(path, 1);
        Field channelField = BufferedRegionFile.class.getDeclaredField("channel");
        channelField.setAccessible(true);
        FileChannel real = (FileChannel) channelField.get(writer);
        try {
            writer.write(pos, ByteBuffer.wrap(new byte[] {11, 12, 13}));
            writer.flush();
            FileChannel failing = mock(FileChannel.class, delegatesTo(real));
            var first = new java.util.concurrent.atomic.AtomicBoolean(true);
            doAnswer(invocation -> {
                if (partialWrite && first.getAndSet(false)) {
                    ByteBuffer data = invocation.getArgument(0);
                    int limit = data.limit();
                    try {
                        data.limit(data.position() + 2);
                        int written = real.write(data, invocation.getArgument(1));
                        assertEquals(2, written);
                        return written;
                    } finally {
                        data.limit(limit);
                    }
                }
                throw new IOException("injected write failure");
            }).when(failing).write(any(ByteBuffer.class), anyLong());
            channelField.set(writer, failing);
            assertThrows(IOException.class, () -> writer.write(pos, ByteBuffer.wrap(new byte[] {21, 22, 23, 24})));
            channelField.set(writer, real);
            try (var in = writer.getChunkDataInputStream(pos)) {
                assertArrayEquals(new byte[] {11, 12, 13}, in.readAllBytes());
            }
            writer.flush();
        } finally {
            channelField.set(writer, real);
            writer.close();
        }
        try (BufferedRegionFile reopened = new BufferedRegionFile(path, 1);
             var in = reopened.getChunkDataInputStream(pos)) {
            assertArrayEquals(new byte[] {11, 12, 13}, in.readAllBytes());
        }
    }

    @Test
    void linearFlushPreservesTheExistenceBitOfAnUnopenedBucket(@TempDir Path dir) throws Exception {
        Path path = dir.resolve("r.0.0.linear");
        ChunkPos opened = new ChunkPos(0, 0);
        ChunkPos unopened = new ChunkPos(8, 0);
        byte[] expected = {41, 42, 43};
        RegionStorageInfo info = mock(RegionStorageInfo.class);
        try (LinearRegionFile file = new LinearRegionFile(info, path, dir, false, 1)) {
            file.write(opened, ByteBuffer.wrap(new byte[] {1}));
            file.write(unopened, ByteBuffer.wrap(expected));
        }
        assertTrue(existenceBit(path, unopened));
        try (LinearRegionFile file = new LinearRegionFile(info, path, dir, false, 1)) {
            file.write(opened, ByteBuffer.wrap(new byte[] {2}));
            file.flush();
        }
        assertTrue(existenceBit(path, unopened));
        try (LinearRegionFile file = new LinearRegionFile(info, path, dir, false, 1);
             var in = file.getChunkDataInputStream(unopened)) {
            assertNotNull(in);
            assertArrayEquals(expected, in.readAllBytes());
        }
    }

    private static boolean existenceBit(Path file, ChunkPos pos) throws IOException {
        int index = (pos.x & 31) + ((pos.z & 31) << 5);
        return (Files.readAllBytes(file)[26 + index / 8] & (1 << (7 - index % 8))) != 0;
    }
}
