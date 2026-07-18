package io.astatine.tools.linear;

import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import net.jpountz.xxhash.StreamingXXHash64;
import net.jpountz.xxhash.XXHashFactory;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileStore;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class LinearV3File {
    static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;
    static final int VERSION = 3;
    static final long MAX_FILE_SIZE = 1L << 30;
    static final int MAX_BUCKET_SIZE = 512 << 20;
    static final long MAX_BUCKET_DECOMPRESSED_SIZE = 512L << 20;
    static final int MAX_CHUNK_DATA_SIZE = 256 << 20;
    private static final int MAX_HEADER_PREFIX_SIZE = 1 << 20;
    private static final int BUFFER_SIZE = 64 << 10;
    private static final long EXTRA_SPACE_MARGIN = 64L << 20;
    private static final XXHashFactory XX_HASH = XXHashFactory.safeInstance();

    private LinearV3File() {}

    interface StopCheck {
        void check() throws IOException;
    }

    record Verification(int chunkCount, int nonEmptyBuckets, Set<Integer> levels, List<byte[]> bucketDigests) {
        boolean logicallyEquals(Verification other) {
            if (chunkCount != other.chunkCount || bucketDigests.size() != other.bucketDigests.size()) return false;
            for (int i = 0; i < bucketDigests.size(); i++) {
                if (!Arrays.equals(bucketDigests.get(i), other.bucketDigests.get(i))) return false;
            }
            return true;
        }
    }

    record SourceIdentity(long size, long mtime, String fileKey) {
        static SourceIdentity from(BasicFileAttributes attributes) {
            return new SourceIdentity(attributes.size(), attributes.lastModifiedTime().toMillis(), String.valueOf(attributes.fileKey()));
        }

        static SourceIdentity read(Path path) throws IOException {
            return from(Files.readAttributes(path, BasicFileAttributes.class));
        }
    }

    record RewriteResult(long outputBytes, boolean reusedTemporary, boolean replaced, SourceIdentity sourceIdentity) {
        boolean isSmaller() {
            return outputBytes < sourceIdentity.size();
        }
    }

    static Verification verify(Path path, Integer requiredLevel) throws IOException {
        Parsed parsed = parse(path);
        List<byte[]> digests = new ArrayList<>(parsed.bucketCount());
        Set<Integer> levels = new LinkedHashSet<>();
        int chunks = 0;
        int nonEmptyBuckets = 0;
        for (int i = 0; i < parsed.bucketCount(); i++) {
            BucketMeta meta = parsed.buckets().get(i);
            levels.add(meta.level());
            if (requiredLevel != null && meta.level() != requiredLevel) {
                throw new IOException("Bucket " + i + " compression metadata is level " + meta.level() + ", expected " + requiredLevel + " in " + path);
            }
            if (meta.size() == 0) {
                validateEmptyBitmap(parsed, i);
                digests.add(emptyBucketDigest(parsed, i));
                continue;
            }
            nonEmptyBuckets++;
            BucketResult result = readBucket(parsed, i, null, true);
            chunks += result.chunkCount();
            digests.add(result.digest());
        }
        return new Verification(chunks, nonEmptyBuckets, Set.copyOf(levels), List.copyOf(digests));
    }

    static RewriteResult rewrite(Path source, int level, long minFreeBytes, boolean replace, StopCheck stopCheck) throws IOException {
        Path absolute = source.toAbsolutePath().normalize();
        Parsed parsed = parse(absolute);
        BasicFileAttributes original = Files.readAttributes(absolute, BasicFileAttributes.class);
        SourceIdentity sourceIdentity = SourceIdentity.from(original);
        ensureFreeSpace(absolute, original.size(), minFreeBytes);
        Path temporary = absolute.resolveSibling(absolute.getFileName() + ".recompress-l" + level + ".tmp");

        boolean reused = false;
        Verification sourceVerification;
        Verification temporaryVerification;
        if (Files.exists(temporary)) {
            try {
                sourceVerification = verify(absolute, null);
                temporaryVerification = verify(temporary, level);
                if (!sourceVerification.logicallyEquals(temporaryVerification)) {
                    throw new IOException("temporary logical content differs from source");
                }
                reused = true;
            } catch (IOException invalid) {
                System.err.println("WARN rebuilding invalid temporary file " + temporary + ": " + invalid.getMessage());
                Files.deleteIfExists(temporary);
                WriteResult writeResult = writeTemporary(parsed, temporary, level);
                sourceVerification = writeResult.sourceVerification();
                temporaryVerification = verify(temporary, level);
                requireLogicalMatch(sourceVerification, temporaryVerification, absolute, temporary);
            }
        } else {
            WriteResult writeResult = writeTemporary(parsed, temporary, level);
            sourceVerification = writeResult.sourceVerification();
            temporaryVerification = verify(temporary, level);
            requireLogicalMatch(sourceVerification, temporaryVerification, absolute, temporary);
        }

        long outputSize = Files.size(temporary);
        if (!replace) {
            Files.delete(temporary);
            return new RewriteResult(outputSize, reused, false, sourceIdentity);
        }

        if (outputSize >= original.size()) {
            stopCheck.check();
            requireUnchanged(absolute, original);
            Files.delete(temporary);
            forceDirectory(absolute.getParent());
            return new RewriteResult(outputSize, reused, false, sourceIdentity);
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
        return new RewriteResult(outputSize, reused, true, sourceIdentity);
    }

    private static WriteResult writeTemporary(Parsed source, Path temporary, int level) throws IOException {
        Files.deleteIfExists(temporary);
        List<byte[]> sourceDigests = new ArrayList<>(source.bucketCount());
        Set<Integer> levels = new LinkedHashSet<>();
        int chunks = 0;
        int nonEmptyBuckets = 0;
        List<BucketMeta> outputMetadata = new ArrayList<>(source.bucketCount());
        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            writeFully(output, ByteBuffer.wrap(source.headerPrefix()));
            long metadataOffset = output.position();
            writeFully(output, ByteBuffer.allocate(Math.multiplyExact(source.bucketCount(), 13)));

            for (int i = 0; i < source.bucketCount(); i++) {
                BucketMeta inputMeta = source.buckets().get(i);
                levels.add(inputMeta.level());
                if (inputMeta.size() == 0) {
                    sourceDigests.add(emptyBucketDigest(source, i));
                    outputMetadata.add(new BucketMeta(0, level, 0, 0));
                    continue;
                }
                nonEmptyBuckets++;
                long frameStart = output.position();
                HashingOutputStream hashing = new HashingOutputStream(new NonClosingOutputStream(Channels.newOutputStream(output)));
                BucketResult result;
                try (ZstdOutputStream zstd = new ZstdOutputStream(hashing, level)) {
                    result = readBucket(source, i, zstd, false);
                }
                long compressedSize = output.position() - frameStart;
                if (compressedSize <= 0 || compressedSize > MAX_BUCKET_SIZE) {
                    throw new IOException("Recompressed bucket " + i + " has invalid size " + compressedSize + " in " + source.path());
                }
                chunks += result.chunkCount();
                sourceDigests.add(result.digest());
                outputMetadata.add(new BucketMeta(Math.toIntExact(compressedSize), level, hashing.value(), frameStart));
            }
            writeFully(output, longBuffer(SUPERBLOCK));
            long finalPosition = output.position();
            if (finalPosition > MAX_FILE_SIZE) throw new IOException("Output exceeds Linear file limit: " + finalPosition + " for " + source.path());
            output.position(26);
            writeFully(output, ByteBuffer.wrap(serializeExistence(source.actualExistence())));
            output.position(metadataOffset);
            ByteBuffer metadata = ByteBuffer.allocate(Math.multiplyExact(source.bucketCount(), 13)).order(ByteOrder.BIG_ENDIAN);
            for (BucketMeta bucket : outputMetadata) metadata.putInt(bucket.size()).put((byte) bucket.level()).putLong(bucket.hash());
            metadata.flip();
            writeFully(output, metadata);
            output.position(finalPosition);
            output.force(true);
        } catch (IOException | RuntimeException exception) {
            // Keep a completed file for restart recovery, but remove partial output.
            try {
                verify(temporary, level);
            } catch (Exception ignored) {
                Files.deleteIfExists(temporary);
            }
            throw exception;
        }
        Verification verification = new Verification(chunks, nonEmptyBuckets, Set.copyOf(levels), List.copyOf(sourceDigests));
        return new WriteResult(verification);
    }

    private static BucketResult readBucket(Parsed parsed, int bucketIndex, OutputStream recompressed, boolean validateBitmap) throws IOException {
        BucketMeta meta = parsed.buckets().get(bucketIndex);
        LimitedInputStream limited = new LimitedInputStream(new PositionalInputStream(parsed.path(), meta.offset(), meta.size()), meta.size());
        HashingInputStream hashing = new HashingInputStream(limited);
        MessageDigest digest = sha256();
        OutputStream logicalTarget = recompressed == null ? OutputStream.nullOutputStream() : recompressed;
        DataOutputStream logical = new DataOutputStream(new DigestOutputStream(logicalTarget, digest));
        int chunks = 0;
        long decompressed = 0;
        byte[] transfer = new byte[BUFFER_SIZE];
        try (ZstdInputStream zstd = new ZstdInputStream(hashing); DataInputStream input = new DataInputStream(zstd)) {
            int grid = parsed.gridSize();
            int bucketSize = 32 / grid;
            int bx = bucketIndex / grid;
            int bz = bucketIndex % grid;
            for (int cx = 0; cx < bucketSize; cx++) {
                for (int cz = 0; cz < bucketSize; cz++) {
                    int chunkSize = input.readInt();
                    long timestamp = input.readLong();
                    logical.writeInt(chunkSize);
                    logical.writeLong(timestamp);
                    decompressed += 12;
                    int chunkIndex = (bx * bucketSize + cx) + (bz * bucketSize + cz) * 32;
                    boolean exists = chunkSize > 0;
                    parsed.actualExistence()[chunkIndex] = exists;
                    if (validateBitmap && parsed.existence()[chunkIndex] != exists) {
                        throw new IOException("Existence bitmap mismatch for chunk index " + chunkIndex + " in " + parsed.path());
                    }
                    if (chunkSize < 0 || (chunkSize > 0 && chunkSize < Long.BYTES)) {
                        throw new IOException("Invalid chunk size " + chunkSize + " in bucket " + bucketIndex + " of " + parsed.path());
                    }
                    int dataLength = chunkSize == 0 ? 0 : chunkSize - Long.BYTES;
                    if (dataLength > MAX_CHUNK_DATA_SIZE) throw new IOException("Chunk exceeds 256 MiB limit in " + parsed.path());
                    int remaining = dataLength;
                    while (remaining > 0) {
                        int amount = input.read(transfer, 0, Math.min(remaining, transfer.length));
                        if (amount < 0) throw new IOException("Truncated chunk data in bucket " + bucketIndex + " of " + parsed.path());
                        logical.write(transfer, 0, amount);
                        remaining -= amount;
                        decompressed += amount;
                        if (decompressed > MAX_BUCKET_DECOMPRESSED_SIZE) throw new IOException("Bucket exceeds 512 MiB decompressed limit in " + parsed.path());
                    }
                    if (exists) chunks++;
                }
            }
            if (input.read() != -1) throw new IOException("Trailing decompressed data in bucket " + bucketIndex + " of " + parsed.path());
        }
        logical.flush();
        if (limited.remaining() != 0) throw new IOException("Compressed frame ended before bucket boundary " + bucketIndex + " in " + parsed.path());
        if (hashing.value() != meta.hash()) throw new IOException("Compressed xxHash mismatch in bucket " + bucketIndex + " of " + parsed.path());
        return new BucketResult(digest.digest(), chunks);
    }

    private static Parsed parse(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        long fileSize = Files.size(absolute);
        if (fileSize < 36 || fileSize > MAX_FILE_SIZE) throw new IOException("Invalid Linear file size " + fileSize + ": " + absolute);
        try (InputStream raw = Files.newInputStream(absolute); RecordingInputStream recording = new RecordingInputStream(raw); DataInputStream input = new DataInputStream(recording)) {
            long magic = input.readLong();
            if (magic != SUPERBLOCK) throw new IOException("Invalid Linear superblock: " + absolute);
            int version = input.readUnsignedByte();
            if (version != VERSION) throw new IOException("Only Linear v3 is supported; found v" + version + " in " + absolute);
            input.readLong();
            int grid = input.readUnsignedByte();
            if (grid != 1 && grid != 2 && grid != 4 && grid != 8 && grid != 16 && grid != 32) throw new IOException("Invalid grid size " + grid + " in " + absolute);
            input.readInt();
            input.readInt();
            byte[] bitmap = input.readNBytes(128);
            if (bitmap.length != 128) throw new IOException("Truncated existence bitmap in " + absolute);
            while (true) {
                int length = input.readUnsignedByte();
                if (length == 0) break;
                if (length > 128) throw new IOException("Feature name exceeds 128 bytes in " + absolute);
                byte[] feature = input.readNBytes(length);
                if (feature.length != length) throw new IOException("Truncated feature in " + absolute);
                input.readInt();
                if (recording.recordedSize() > MAX_HEADER_PREFIX_SIZE) throw new IOException("Feature header exceeds 1 MiB in " + absolute);
            }
            recording.stopRecording();
            int count = Math.multiplyExact(grid, grid);
            List<BucketMeta> buckets = new ArrayList<>(count);
            long payloadOffset = recording.count() + (long) count * 13;
            long nextOffset = payloadOffset;
            for (int i = 0; i < count; i++) {
                int size = input.readInt();
                int level = input.readUnsignedByte();
                long hash = input.readLong();
                if (size < 0 || size > MAX_BUCKET_SIZE) throw new IOException("Invalid bucket size " + size + " at " + i + " in " + absolute);
                buckets.add(new BucketMeta(size, level, hash, nextOffset));
                nextOffset = Math.addExact(nextOffset, size);
            }
            if (nextOffset + Long.BYTES != fileSize) throw new IOException("Linear file length does not match bucket table: " + absolute);
            try (FileChannel channel = FileChannel.open(absolute, StandardOpenOption.READ)) {
                ByteBuffer footer = ByteBuffer.allocate(Long.BYTES);
                readFully(channel, footer, nextOffset);
                footer.flip();
                if (footer.getLong() != SUPERBLOCK) throw new IOException("Invalid Linear footer: " + absolute);
            }
            return new Parsed(absolute, grid, bitmapToBooleans(bitmap), new boolean[1024], recording.recorded(), List.copyOf(buckets));
        }
    }

    private static void validateEmptyBitmap(Parsed parsed, int bucketIndex) throws IOException {
        int grid = parsed.gridSize();
        int bucketSize = 32 / grid;
        int bx = bucketIndex / grid;
        int bz = bucketIndex % grid;
        for (int cx = 0; cx < bucketSize; cx++) {
            for (int cz = 0; cz < bucketSize; cz++) {
                int index = (bx * bucketSize + cx) + (bz * bucketSize + cz) * 32;
                if (parsed.existence()[index]) throw new IOException("Empty bucket " + bucketIndex + " contains existing chunk bit " + index + " in " + parsed.path());
            }
        }
    }

    private static byte[] emptyBucketDigest(Parsed parsed, int bucketIndex) throws IOException {
        MessageDigest digest = sha256();
        int entries = 1024 / parsed.bucketCount();
        ByteBuffer emptyEntry = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN);
        for (int i = 0; i < entries; i++) {
            emptyEntry.clear();
            emptyEntry.putInt(0).putLong(0).flip();
            digest.update(emptyEntry);
        }
        return digest.digest();
    }

    private static boolean[] bitmapToBooleans(byte[] bitmap) {
        boolean[] result = new boolean[1024];
        for (int i = 0; i < bitmap.length; i++) {
            for (int bit = 0; bit < 8; bit++) result[i * 8 + bit] = ((bitmap[i] >> (7 - bit)) & 1) == 1;
        }
        return result;
    }

    private static byte[] serializeExistence(boolean[] existence) {
        byte[] result = new byte[128];
        for (int i = 0; i < existence.length; i++) {
            if (existence[i]) result[i / 8] |= (byte) (1 << (7 - (i % 8)));
        }
        return result;
    }

    private static void ensureFreeSpace(Path source, long sourceSize, long reserve) throws IOException {
        FileStore store = Files.getFileStore(source);
        long required = Math.addExact(reserve, Math.addExact(sourceSize, EXTRA_SPACE_MARGIN));
        long usable = store.getUsableSpace();
        if (usable < required) throw new IOException("Insufficient free space: usable=" + usable + " required=" + required + " for " + source);
    }

    private static void requireLogicalMatch(Verification source, Verification output, Path sourcePath, Path outputPath) throws IOException {
        if (!source.logicallyEquals(output)) throw new IOException("Reopened output differs from source: " + sourcePath + " -> " + outputPath);
    }

    private static void requireUnchanged(Path path, BasicFileAttributes before) throws IOException {
        BasicFileAttributes after = Files.readAttributes(path, BasicFileAttributes.class);
        if (before.size() != after.size() || before.lastModifiedTime().toMillis() != after.lastModifiedTime().toMillis()
            || !String.valueOf(before.fileKey()).equals(String.valueOf(after.fileKey()))) {
            throw new IOException("Source changed during recompression; refusing replacement: " + path);
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
        try (FileChannel channel = FileChannel.open(target, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static ByteBuffer longBuffer(long value) {
        return ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).flip();
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

    private record Parsed(Path path, int gridSize, boolean[] existence, boolean[] actualExistence, byte[] headerPrefix, List<BucketMeta> buckets) {
        int bucketCount() { return buckets.size(); }
    }

    private record BucketMeta(int size, int level, long hash, long offset) {}
    private record BucketResult(byte[] digest, int chunkCount) {}
    private record WriteResult(Verification sourceVerification) {}

    private static final class RecordingInputStream extends FilterInputStream {
        private final ByteArrayOutputStream recorded = new ByteArrayOutputStream();
        private long count;
        private boolean recording = true;

        RecordingInputStream(InputStream input) { super(input); }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                count++;
                if (recording) recorded.write(value);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int amount = super.read(bytes, offset, length);
            if (amount > 0) {
                count += amount;
                if (recording) recorded.write(bytes, offset, amount);
            }
            return amount;
        }

        void stopRecording() { recording = false; }
        long count() { return count; }
        int recordedSize() { return recorded.size(); }
        byte[] recorded() { return recorded.toByteArray(); }
    }

    private static final class PositionalInputStream extends InputStream {
        private final FileChannel channel;
        private long position;
        private long remaining;

        PositionalInputStream(Path path, long position, long length) throws IOException {
            this.channel = FileChannel.open(path, StandardOpenOption.READ);
            this.position = position;
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : Byte.toUnsignedInt(one[0]);
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) return -1;
            int wanted = (int) Math.min(length, remaining);
            ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, wanted);
            int read = channel.read(buffer, position);
            if (read < 0) return -1;
            position += read;
            remaining -= read;
            return read;
        }

        @Override
        public void close() throws IOException { channel.close(); }
    }

    private static final class LimitedInputStream extends FilterInputStream {
        private long remaining;
        LimitedInputStream(InputStream input, long remaining) { super(input); this.remaining = remaining; }
        long remaining() { return remaining; }

        @Override
        public int read() throws IOException {
            if (remaining == 0) return -1;
            int value = super.read();
            if (value >= 0) remaining--;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (remaining == 0) return -1;
            int read = super.read(bytes, offset, (int) Math.min(length, remaining));
            if (read > 0) remaining -= read;
            return read;
        }
    }

    private static final class HashingInputStream extends FilterInputStream {
        private final StreamingXXHash64 hash = XX_HASH.newStreamingHash64(0);
        HashingInputStream(InputStream input) { super(input); }
        long value() { return hash.getValue(); }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) hash.update(new byte[]{(byte) value}, 0, 1);
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) hash.update(bytes, offset, read);
            return read;
        }
    }

    private static final class HashingOutputStream extends FilterOutputStream {
        private final StreamingXXHash64 hash = XX_HASH.newStreamingHash64(0);
        HashingOutputStream(OutputStream output) { super(output); }
        long value() { return hash.getValue(); }

        @Override
        public void write(int value) throws IOException {
            out.write(value);
            hash.update(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            out.write(bytes, offset, length);
            hash.update(bytes, offset, length);
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        NonClosingOutputStream(OutputStream output) { super(output); }
        @Override public void close() throws IOException { flush(); }
    }
}
