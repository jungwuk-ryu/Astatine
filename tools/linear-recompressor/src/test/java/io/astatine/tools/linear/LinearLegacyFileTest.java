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
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearLegacyFileTest {
    @TempDir Path directory;

    @Test
    void keepsLegacyV1WhenV3CandidateIsNotSmaller() throws Exception {
        Path file = LinearFixture.writeLegacy(directory.resolve("r.4.-7.linear"), 1, 3);
        assertEquals(3, LinearLegacyFile.verify(file).chunkCount());
        byte[] original = Files.readAllBytes(file);

        LinearV3File.RewriteResult result = LinearLegacyFile.rewrite(file, 9, 0, true, () -> {});

        assertEquals(1, LinearLegacyFile.formatVersion(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(result.replaced());
        assertTrue(result.outputBytes() >= original.length);
        assertFalse(result.reusedTemporary());
    }

    @Test
    void keepsLegacyV2WhenV3CandidateIsNotSmaller() throws Exception {
        Path file = LinearFixture.writeLegacy(directory.resolve("v2.linear"), 2, 1);
        byte[] original = Files.readAllBytes(file);

        LinearV3File.RewriteResult result = LinearLegacyFile.rewrite(file, 9, 0, true, () -> {});

        assertEquals(2, LinearLegacyFile.formatVersion(file));
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(result.replaced());
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
