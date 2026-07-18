package io.astatine.tools.linear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinearLegacyFileTest {
    @TempDir Path directory;

    @Test
    void convertsLegacyV1ToVerifiedLevel9V3() throws Exception {
        Path file = LinearFixture.writeLegacy(directory.resolve("r.4.-7.linear"), 1, 3);
        assertEquals(3, LinearLegacyFile.verify(file).chunkCount());

        LinearV3File.RewriteResult result = LinearLegacyFile.rewrite(file, 9, 0, true, () -> {});

        assertEquals(3, LinearLegacyFile.formatVersion(file));
        assertEquals(3, LinearV3File.verify(file, 9).chunkCount());
        assertFalse(result.reusedTemporary());
    }

    @Test
    void convertsLegacyV2WithTheDeployedLegacyLayout() throws Exception {
        Path file = LinearFixture.writeLegacy(directory.resolve("v2.linear"), 2, 1);

        LinearLegacyFile.rewrite(file, 9, 0, true, () -> {});

        assertEquals(3, LinearLegacyFile.formatVersion(file));
        LinearV3File.verify(file, 9);
    }

    @Test
    void refusesLegacyHashMismatchWithoutChangingSource() throws Exception {
        Path file = LinearFixture.writeLegacy(directory.resolve("bad-hash.linear"), 1, 3);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{99}), 31);
        }
        byte[] corrupt = Files.readAllBytes(file);

        assertThrows(IOException.class, () -> LinearLegacyFile.rewrite(file, 9, 0, true, () -> {}));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
    }
}
