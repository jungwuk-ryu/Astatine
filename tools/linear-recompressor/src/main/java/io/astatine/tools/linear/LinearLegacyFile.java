package io.astatine.tools.linear;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class LinearLegacyFile {
    private static final int LEGACY_HEADER_SIZE = 32;
    private static final int FOOTER_SIZE = 8;
    private static final int GRID_SIZE = 8;
    private static final int BUCKET_SIZE = 4;
    private static final int BUCKET_COUNT = 64;
    private static final int MAX_LEGACY_DECOMPRESSED_SIZE = 512 << 20;
    private static final int BUFFER_SIZE = 64 << 10;
    private static final XXHashFactory XX_HASH = XXHashFactory.safeInstance();

    private LinearLegacyFile() {}

    static int formatVersion(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(9);
            readFully(channel, header, 0);
            header.flip();
            if (header.getLong() != LinearV3File.SUPERBLOCK) throw new IOException("Invalid Linear superblock: " + path);
            int version = Byte.toUnsignedInt(header.get());
            if (version < 1 || version > 3) throw new IOException("Unsupported Linear version " + version + " in " + path);
            return version;
        }
    }

    static LinearV3File.Verification verify(Path source) throws IOException {
        return readLegacy(source).verification();
    }

    static LinearV3File.RewriteResult rewrite(Path source, int level, long minFreeBytes, boolean replace, LinearV3File.StopCheck stopCheck) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        BasicFileAttributes original = Files.readAttributes(absolute, BasicFileAttributes.class);
        long required = Math.addExact(minFreeBytes, Math.addExact(original.size(), (long) MAX_LEGACY_DECOMPRESSED_SIZE + (64L << 20)));
        long usable = Files.getFileStore(absolute).getUsableSpace();
        if (usable < required) throw new IOException("Insufficient free space: usable=" + usable + " required=" + required + " for " + absolute);

        LegacyData legacy = readLegacy(absolute);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".recompress-l" + level + ".tmp");
        boolean reused = false;
        if (Files.exists(temporary)) {
            try {
                LinearV3File.Verification existing = LinearV3File.verify(temporary, level);
                requireLogicalMatch(legacy.verification(), existing, absolute, temporary);
                reused = true;
            } catch (IOException invalid) {
                System.err.println("WARN rebuilding invalid legacy-conversion temporary file " + temporary + ": " + invalid.getMessage());
                Files.deleteIfExists(temporary);
            }
        }
        if (!reused) {
            writeV3Temporary(absolute, temporary, legacy, level);
            LinearV3File.Verification written = LinearV3File.verify(temporary, level);
            requireLogicalMatch(legacy.verification(), written, absolute, temporary);
        }

        long outputSize = Files.size(temporary);
        if (!replace) {
            Files.delete(temporary);
            return new LinearV3File.RewriteResult(outputSize, reused);
        }
        copyAttributes(absolute, temporary);
        stopCheck.check();
        requireUnchanged(absolute, original);
        try {
            Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic replacement is not supported for " + absolute + "; source was not changed", exception);
        }
        forceDirectory(absolute.getParent());
        return new LinearV3File.RewriteResult(outputSize, reused);
    }

    private static LegacyData readLegacy(Path source) throws IOException {
        byte[] file = Files.readAllBytes(source);
        if (file.length < LEGACY_HEADER_SIZE + FOOTER_SIZE || file.length > LinearV3File.MAX_FILE_SIZE) {
            throw new IOException("Invalid legacy Linear file size " + file.length + " in " + source);
        }
        ByteBuffer header = ByteBuffer.wrap(file).order(ByteOrder.BIG_ENDIAN);
        if (header.getLong() != LinearV3File.SUPERBLOCK) throw new IOException("Invalid Linear superblock: " + source);
        int version = Byte.toUnsignedInt(header.get());
        if (version != 1 && version != 2) throw new IOException("Expected legacy Linear v1/v2, found v" + version + " in " + source);
        header.getLong();
        int sourceLevel = Byte.toUnsignedInt(header.get());
        int declaredChunks = Short.toUnsignedInt(header.getShort());
        int compressedSize = header.getInt();
        long storedHash = header.getLong();
        if (compressedSize <= 0 || compressedSize > LinearV3File.MAX_FILE_SIZE || LEGACY_HEADER_SIZE + (long) compressedSize + FOOTER_SIZE != file.length) {
            throw new IOException("Invalid legacy payload size " + compressedSize + " in " + source);
        }
        long footer = ByteBuffer.wrap(file, LEGACY_HEADER_SIZE + compressedSize, FOOTER_SIZE).getLong();
        if (footer != LinearV3File.SUPERBLOCK) throw new IOException("Invalid legacy Linear footer: " + source);
        byte[] compressed = Arrays.copyOfRange(file, LEGACY_HEADER_SIZE, LEGACY_HEADER_SIZE + compressedSize);
        long actualHash = XX_HASH.hash64().hash(compressed, 0, compressed.length, 0);
        if (storedHash != 0 && storedHash != actualHash) throw new IOException("Legacy compressed xxHash mismatch in " + source);

        int[] sizes = new int[1024];
        byte[][] chunks = new byte[1024][];
        long total = 1024L * 8;
        int chunkCount = 0;
        try (ZstdInputStream zstd = new ZstdInputStream(new ByteArrayInputStream(compressed)); DataInputStream input = new DataInputStream(zstd)) {
            for (int i = 0; i < 1024; i++) {
                int size = input.readInt();
                input.readInt();
                if (size < 0 || size > LinearV3File.MAX_CHUNK_DATA_SIZE) throw new IOException("Invalid legacy chunk size " + size + " at " + i + " in " + source);
                sizes[i] = size;
                total += size;
                if (total > MAX_LEGACY_DECOMPRESSED_SIZE) throw new IOException("Legacy payload exceeds 512 MiB in " + source);
                if (size > 0) chunkCount++;
            }
            for (int i = 0; i < 1024; i++) {
                if (sizes[i] == 0) continue;
                chunks[i] = input.readNBytes(sizes[i]);
                if (chunks[i].length != sizes[i]) throw new IOException("Truncated legacy chunk " + i + " in " + source);
            }
            if (input.read() != -1) throw new IOException("Trailing legacy decompressed data in " + source);
        }
        if (declaredChunks != 0 && declaredChunks != chunkCount) {
            throw new IOException("Legacy chunk count mismatch: header=" + declaredChunks + " actual=" + chunkCount + " in " + source);
        }
        long timestamp = Instant.now().getEpochSecond();
        return new LegacyData(version, sourceLevel, chunks, timestamp, buildVerification(chunks, timestamp, sourceLevel));
    }

    private static LinearV3File.Verification buildVerification(byte[][] chunks, long timestamp, int sourceLevel) throws IOException {
        List<byte[]> digests = new ArrayList<>(BUCKET_COUNT);
        int nonEmpty = 0;
        int chunkCount = 0;
        for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
            MessageDigest digest = sha256();
            int bx = bucket / GRID_SIZE;
            int bz = bucket % GRID_SIZE;
            boolean hasData = false;
            ByteBuffer entry = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
            for (int cx = 0; cx < BUCKET_SIZE; cx++) {
                for (int cz = 0; cz < BUCKET_SIZE; cz++) {
                    int index = (bx * BUCKET_SIZE + cx) + (bz * BUCKET_SIZE + cz) * 32;
                    byte[] chunk = chunks[index];
                    entry.clear();
                    entry.putInt(chunk == null ? 0 : chunk.length + Long.BYTES).putLong(chunk == null ? 0 : timestamp).flip();
                    digest.update(entry);
                    if (chunk != null) {
                        digest.update(chunk);
                        chunkCount++;
                        hasData = true;
                    }
                }
            }
            if (hasData) nonEmpty++;
            digests.add(digest.digest());
        }
        return new LinearV3File.Verification(chunkCount, nonEmpty, Set.of(sourceLevel), List.copyOf(digests));
    }

    private static void writeV3Temporary(Path source, Path temporary, LegacyData legacy, int level) throws IOException {
        Files.deleteIfExists(temporary);
        String[] name = source.getFileName().toString().split("\\.");
        int regionX = name.length >= 4 ? parseCoordinate(name[1]) : 0;
        int regionZ = name.length >= 4 ? parseCoordinate(name[2]) : 0;
        List<BucketMeta> metadata = new ArrayList<>(BUCKET_COUNT);
        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer fixed = ByteBuffer.allocate(27).order(ByteOrder.BIG_ENDIAN);
            fixed.putLong(LinearV3File.SUPERBLOCK).put((byte) 3).putLong(legacy.timestamp()).put((byte) GRID_SIZE).putInt(regionX).putInt(regionZ).flip();
            writeFully(output, fixed);
            byte[] bitmap = new byte[128];
            for (int i = 0; i < legacy.chunks().length; i++) if (legacy.chunks()[i] != null) bitmap[i / 8] |= (byte) (1 << (7 - (i % 8)));
            writeFully(output, ByteBuffer.wrap(bitmap));
            writeFully(output, ByteBuffer.wrap(new byte[]{0}));
            long metadataOffset = output.position();
            writeFully(output, ByteBuffer.allocate(BUCKET_COUNT * 13));

            for (int bucket = 0; bucket < BUCKET_COUNT; bucket++) {
                int bx = bucket / GRID_SIZE;
                int bz = bucket % GRID_SIZE;
                boolean hasData = false;
                for (int cx = 0; cx < BUCKET_SIZE; cx++) for (int cz = 0; cz < BUCKET_SIZE; cz++) {
                    int index = (bx * BUCKET_SIZE + cx) + (bz * BUCKET_SIZE + cz) * 32;
                    hasData |= legacy.chunks()[index] != null;
                }
                if (!hasData) {
                    metadata.add(new BucketMeta(0, 0));
                    continue;
                }
                long start = output.position();
                HashingOutputStream hashing = new HashingOutputStream(new NonClosingOutputStream(Channels.newOutputStream(output)));
                try (ZstdOutputStream zstd = new ZstdOutputStream(hashing, level); DataOutputStream data = new DataOutputStream(zstd)) {
                    for (int cx = 0; cx < BUCKET_SIZE; cx++) {
                        for (int cz = 0; cz < BUCKET_SIZE; cz++) {
                            int index = (bx * BUCKET_SIZE + cx) + (bz * BUCKET_SIZE + cz) * 32;
                            byte[] chunk = legacy.chunks()[index];
                            data.writeInt(chunk == null ? 0 : chunk.length + Long.BYTES);
                            data.writeLong(chunk == null ? 0 : legacy.timestamp());
                            if (chunk != null) data.write(chunk);
                        }
                    }
                }
                int size = Math.toIntExact(output.position() - start);
                if (size <= 0 || size > LinearV3File.MAX_BUCKET_SIZE) throw new IOException("Converted bucket has invalid size " + size + " in " + source);
                metadata.add(new BucketMeta(size, hashing.value()));
            }
            writeFully(output, ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(LinearV3File.SUPERBLOCK).flip());
            long end = output.position();
            output.position(metadataOffset);
            ByteBuffer table = ByteBuffer.allocate(BUCKET_COUNT * 13).order(ByteOrder.BIG_ENDIAN);
            for (BucketMeta bucket : metadata) table.putInt(bucket.size()).put((byte) level).putLong(bucket.hash());
            table.flip();
            writeFully(output, table);
            output.position(end);
            output.force(true);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
    }

    private static int parseCoordinate(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return 0; }
    }

    private static void requireLogicalMatch(LinearV3File.Verification source, LinearV3File.Verification output, Path sourcePath, Path outputPath) throws IOException {
        if (!source.logicallyEquals(output)) throw new IOException("Converted output differs from legacy source: " + sourcePath + " -> " + outputPath);
    }

    private static void requireUnchanged(Path path, BasicFileAttributes before) throws IOException {
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
        if (before.size() != after.size() || before.lastModifiedTime().toMillis() != after.lastModifiedTime().toMillis()
            || !String.valueOf(before.fileKey()).equals(String.valueOf(after.fileKey()))) {
            throw new IOException("Source changed during legacy conversion; refusing replacement: " + path);
        }
    }

    private static void copyAttributes(Path source, Path target) throws IOException {
        BasicFileAttributes basic = Files.readAttributes(source, BasicFileAttributes.class);
        PosixFileAttributeView sourceView = Files.getFileAttributeView(source, PosixFileAttributeView.class);
        PosixFileAttributeView targetView = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (sourceView != null && targetView != null) {
            PosixFileAttributes posix = sourceView.readAttributes();
            targetView.setPermissions(posix.permissions());
            targetView.setGroup(posix.group());
            targetView.setOwner(posix.owner());
        }
        Files.setLastModifiedTime(target, FileTime.fromMillis(basic.lastModifiedTime().toMillis()));
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) { channel.force(true); }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) { channel.force(true); }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) channel.write(buffer);
    }

    private static void readFully(FileChannel channel, ByteBuffer buffer, long position) throws IOException {
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, position);
            if (read < 0) throw new IOException("Unexpected EOF");
            position += read;
        }
    }

    private record LegacyData(int version, int sourceLevel, byte[][] chunks, long timestamp, LinearV3File.Verification verification) {}
    private record BucketMeta(int size, long hash) {}

    private static final class HashingOutputStream extends FilterOutputStream {
        private final StreamingXXHash64 hash = XX_HASH.newStreamingHash64(0);
        HashingOutputStream(OutputStream output) { super(output); }
        long value() { return hash.getValue(); }
        @Override public void write(int value) throws IOException { out.write(value); hash.update(new byte[]{(byte) value}, 0, 1); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException { out.write(bytes, offset, length); hash.update(bytes, offset, length); }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) { super(output); }
        @Override public void close() throws IOException { flush(); }
    }
}
