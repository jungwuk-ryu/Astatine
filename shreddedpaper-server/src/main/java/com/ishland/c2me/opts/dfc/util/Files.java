package com.ishland.c2me.opts.dfc.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class Files {
    public static void deleteRecursively(File dir) throws IOException {
        if (dir == null) {
            return;
        }

        try {
            final Path root = dir.toPath().toAbsolutePath().normalize();
            if (!java.nio.file.Files.exists(root, LinkOption.NOFOLLOW_LINKS)
                    || !java.nio.file.Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }

            java.nio.file.Files.walkFileTree(root, new SimpleFileVisitor<>() {
                private void validateContained(final Path path) throws IOException {
                    if (!path.toAbsolutePath().normalize().startsWith(root)) {
                        throw new IOException("Refusing to delete path outside recursive delete root: " + path);
                    }
                }

                @Override
                public FileVisitResult preVisitDirectory(final Path directory, final BasicFileAttributes attrs) throws IOException {
                    this.validateContained(directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    this.validateContained(file);
                    java.nio.file.Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(final Path directory, final IOException exc) throws IOException {
                    if (exc != null) {
                        throw exc;
                    }
                    this.validateContained(directory);
                    java.nio.file.Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (SecurityException ex) {
            throw new IOException("Security error during recursive delete operation", ex);
        }
    }
}
