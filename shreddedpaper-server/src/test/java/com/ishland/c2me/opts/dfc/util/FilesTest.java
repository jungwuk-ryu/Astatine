package com.ishland.c2me.opts.dfc.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FilesTest {

    @TempDir
    Path tempDir;

    @Test
    void deleteRecursivelyDeletesSymlinkItselfWithoutFollowingTarget() throws IOException {
        final Path root = this.tempDir.resolve("root");
        final Path child = root.resolve("child");
        final Path outside = this.tempDir.resolve("outside");
        final Path outsideFile = outside.resolve("kept.txt");

        java.nio.file.Files.createDirectories(child);
        java.nio.file.Files.createDirectories(outside);
        java.nio.file.Files.writeString(child.resolve("deleted.txt"), "inside");
        java.nio.file.Files.writeString(outsideFile, "outside");

        try {
            java.nio.file.Files.createSymbolicLink(child.resolve("outside-link"), outside);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "symbolic links are not available in this test environment");
        }

        Files.deleteRecursively(root.toFile());

        assertFalse(java.nio.file.Files.exists(root));
        assertTrue(java.nio.file.Files.exists(outsideFile));
    }

    @Test
    void deleteRecursivelyIgnoresRootSymlinkDirectory() throws IOException {
        final Path realRoot = this.tempDir.resolve("real-root");
        final Path symlinkRoot = this.tempDir.resolve("root-link");
        final Path realFile = realRoot.resolve("kept.txt");

        java.nio.file.Files.createDirectories(realRoot);
        java.nio.file.Files.writeString(realFile, "kept");
        try {
            java.nio.file.Files.createSymbolicLink(symlinkRoot, realRoot);
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            assumeTrue(false, "symbolic links are not available in this test environment");
        }

        Files.deleteRecursively(symlinkRoot.toFile());

        assertTrue(java.nio.file.Files.exists(symlinkRoot));
        assertTrue(java.nio.file.Files.exists(realFile));
    }

    @Test
    void deleteRecursivelyIgnoresPlainFiles() throws IOException {
        final File file = this.tempDir.resolve("plain.txt").toFile();
        assertTrue(file.createNewFile());

        Files.deleteRecursively(file);

        assertTrue(file.exists());
    }
}
