package io.astatine.tools.linear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearV3FileTest {
    @TempDir Path directory;

    @Test
    void rewritesLevelAndPreservesLogicalContent() throws Exception {
        Path file = LinearFixture.write(directory.resolve("r.4.-7.linear"), 3);
        LinearV3File.Verification before = LinearV3File.verify(file, 3);

        LinearV3File.RewriteResult result = LinearV3File.rewrite(file, 9, 0, true, () -> {});

        LinearV3File.Verification after = LinearV3File.verify(file, 9);
        assertTrue(before.logicallyEquals(after));
        assertEquals(3, after.chunkCount());
        assertFalse(result.reusedTemporary());
        assertFalse(Files.exists(directory.resolve("r.4.-7.linear.recompress-l9.tmp")));
    }

    @Test
    void rejectsCorruptCompressedDataWithoutChangingSource() throws Exception {
        Path file = LinearFixture.write(directory.resolve("corrupt.linear"), 3);
        byte[] original = Files.readAllBytes(file);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            long position = Files.size(file) - 12;
            channel.write(ByteBuffer.wrap(new byte[]{(byte) 0xA5}), position);
        }
        byte[] corrupt = Files.readAllBytes(file);

        assertThrows(IOException.class, () -> LinearV3File.rewrite(file, 9, 0, true, () -> {}));
        assertArrayEquals(corrupt, Files.readAllBytes(file));
        assertFalse(Arrays.equals(original, corrupt));
    }

    @Test
    void reusesOnlyVerifiedCompleteTemporaryFile() throws Exception {
        Path source = LinearFixture.write(directory.resolve("source.linear"), 3);
        Path prepared = LinearFixture.write(directory.resolve("prepared.linear"), 3);
        LinearV3File.rewrite(prepared, 9, 0, true, () -> {});
        Path temporary = directory.resolve("source.linear.recompress-l9.tmp");
        Files.copy(prepared, temporary);

        LinearV3File.RewriteResult result = LinearV3File.rewrite(source, 9, 0, true, () -> {});

        assertTrue(result.reusedTemporary());
        LinearV3File.verify(source, 9);
    }

    @Test
    void rebuildsInvalidLeftoverTemporaryFile() throws Exception {
        Path source = LinearFixture.write(directory.resolve("invalid-temp.linear"), 3);
        Path temporary = directory.resolve("invalid-temp.linear.recompress-l9.tmp");
        Files.writeString(temporary, "partial-crash-output");

        LinearV3File.RewriteResult result = LinearV3File.rewrite(source, 9, 0, true, () -> {});

        assertFalse(result.reusedTemporary());
        LinearV3File.verify(source, 9);
        assertFalse(Files.exists(temporary));
    }

    @Test
    void completedTemporarySurvivesPreMoveStopFailureAndResumes() throws Exception {
        Path source = LinearFixture.write(directory.resolve("stop.linear"), 3);
        byte[] original = Files.readAllBytes(source);

        assertThrows(IOException.class, () -> LinearV3File.rewrite(source, 9, 0, true, () -> {
            throw new IOException("simulated server restart");
        }));

        assertArrayEquals(original, Files.readAllBytes(source));
        assertTrue(Files.exists(directory.resolve("stop.linear.recompress-l9.tmp")));
        LinearV3File.RewriteResult resumed = LinearV3File.rewrite(source, 9, 0, true, () -> {});
        assertTrue(resumed.reusedTemporary());
        LinearV3File.verify(source, 9);
    }

    @Test
    void rejectsTruncatedFile() throws Exception {
        Path source = LinearFixture.write(directory.resolve("truncated.linear"), 3);
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.WRITE)) {
            channel.truncate(Files.size(source) - 20);
        }
        assertThrows(IOException.class, () -> LinearV3File.verify(source, null));
    }

    @Test
    void estimateLeavesSourceAndNoTemporary() throws Exception {
        Path file = LinearFixture.write(directory.resolve("estimate.linear"), 3);
        byte[] original = Files.readAllBytes(file);

        LinearV3File.RewriteResult result = LinearV3File.rewrite(file, 9, 0, false, () -> {});

        assertTrue(result.outputBytes() > 0);
        assertArrayEquals(original, Files.readAllBytes(file));
        assertFalse(Files.exists(directory.resolve("estimate.linear.recompress-l9.tmp")));
    }

    @Test
    void refusesWhenFreeSpaceReserveCannotBeMet() throws Exception {
        Path file = LinearFixture.write(directory.resolve("space.linear"), 3);
        byte[] original = Files.readAllBytes(file);

        assertThrows(IOException.class, () -> LinearV3File.rewrite(file, 9, 1L << 60, true, () -> {}));
        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void rejectsOlderVersion() throws Exception {
        Path file = LinearFixture.write(directory.resolve("old.linear"), 3);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{1}), Long.BYTES);
        }
        IOException failure = assertThrows(IOException.class, () -> LinearV3File.verify(file, null));
        assertTrue(failure.getMessage().contains("v1"));
    }

    @Test
    void checkpointSkipsAnUnchangedAppliedFile() throws Exception {
        Path file = LinearFixture.write(directory.resolve("checkpoint.linear"), 3);
        Path checkpoint = directory.resolve("checkpoint.tsv");
        String[] arguments = {"apply", "--level", "9", "--min-free-gib", "0", "--assume-offline", "--checkpoint", checkpoint.toString(), file.toString()};
        assertEquals(0, LinearRecompressorMain.run(arguments));
        FileTime mtime = Files.getLastModifiedTime(file);
        long checkpointSize = Files.size(checkpoint);

        assertEquals(0, LinearRecompressorMain.run(arguments));

        assertEquals(mtime, Files.getLastModifiedTime(file));
        assertEquals(checkpointSize, Files.size(checkpoint));
    }

    @Test
    void liveAgeGateDefersRecentFileUntilLaterPass() throws Exception {
        Path file = LinearFixture.write(directory.resolve("recent.linear"), 3);
        Path checkpoint = directory.resolve("recent-checkpoint.tsv");
        String[] deferred = {"apply", "--level", "9", "--min-free-gib", "0", "--min-age-seconds", "600", "--assume-offline", "--checkpoint", checkpoint.toString(), file.toString()};

        assertEquals(0, LinearRecompressorMain.run(deferred));
        LinearV3File.verify(file, 3);
        assertEquals(0, Files.size(checkpoint));

        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 700_000));
        assertEquals(0, LinearRecompressorMain.run(deferred));
        LinearV3File.verify(file, 9);
        assertTrue(Files.size(checkpoint) > 0);
    }
}
