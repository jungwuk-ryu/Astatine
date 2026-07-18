package io.astatine.tools.linear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearRecompressorMainTest {
    @TempDir Path directory;

    @Test
    void sixWorkersProduceDurableResumableProgress() throws Exception {
        Path regions = Files.createDirectory(directory.resolve("regions"));
        List<Path> files = new ArrayList<>();
        List<byte[]> originals = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            Path file = LinearFixture.write(regions.resolve("r." + index + ".0.linear"), 3);
            files.add(file);
            originals.add(Files.readAllBytes(file));
        }
        Path checkpoint = directory.resolve("parallel.checkpoint.tsv");
        String[] arguments = {
            "apply", "--level", "9", "--threads", "6", "--label", "parallel-test",
            "--progress-every", "5", "--min-free-gib", "0", "--assume-offline",
            "--checkpoint", checkpoint.toString(), regions.toString()
        };

        String output = captureStandardOut(() -> assertEquals(0, LinearRecompressorMain.run(arguments)));

        assertTrue(output.contains("PLAN label=parallel-test mode=APPLY workers=6 total=18"));
        assertTrue(output.contains("percent=100.00%"));
        assertTrue(output.contains("eta=00:00:00"));
        assertTrue(output.contains("SUMMARY label=parallel-test mode=APPLY workers=6 total=18 processed=18"));
        assertEquals(18, Files.readAllLines(checkpoint).size());
        for (int index = 0; index < files.size(); index++) assertArrayEquals(originals.get(index), Files.readAllBytes(files.get(index)));

        long checkpointSize = Files.size(checkpoint);
        String resumed = captureStandardOut(() -> assertEquals(0, LinearRecompressorMain.run(arguments)));
        assertEquals(checkpointSize, Files.size(checkpoint));
        assertTrue(resumed.contains("checkpointSkipped=18"));
    }

    @Test
    void readsExistingSixFieldCheckpointWithoutRewriting() throws Exception {
        Path file = LinearFixture.writeBeneficial(directory.resolve("old-checkpoint.linear"), 1);
        LinearV3File.RewriteResult rewrite = LinearV3File.rewrite(file, 9, 0, true, () -> {});
        assertTrue(rewrite.replaced());
        byte[] applied = Files.readAllBytes(file);
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
        String key = file.toAbsolutePath().normalize().toString();
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
        Path checkpoint = directory.resolve("old.checkpoint.tsv");
        Files.writeString(checkpoint,
            "OK\t9\t" + attributes.size() + "\t" + attributes.lastModifiedTime().toMillis() + "\t"
                + attributes.fileKey() + "\t" + encoded + "\n");
        long checkpointSize = Files.size(checkpoint);

        String[] arguments = {
            "apply", "--level", "9", "--threads", "6", "--min-free-gib", "0",
            "--assume-offline", "--checkpoint", checkpoint.toString(), file.toString()
        };
        assertEquals(0, LinearRecompressorMain.run(arguments));

        assertEquals(checkpointSize, Files.size(checkpoint));
        assertArrayEquals(applied, Files.readAllBytes(file));
        LinearV3File.verify(file, 9);
    }

    private static String captureStandardOut(ThrowingRunnable action) throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            action.run();
        } finally {
            System.setOut(original);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
