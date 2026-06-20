package org.bxteam.divinemc.region.type;

import ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.lz4.LZ4FastDecompressor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.openhft.hashing.LongHashFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bxteam.divinemc.region.IRegionFile;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

public class LinearRegionFile implements IRegionFile {
    private static final long SUPERBLOCK = 0xc3ff13183cca9d9aL;
    private static final byte VERSION = 3;
    private static final int HEADER_SIZE = 27;
    private static final int FOOTER_SIZE = 8;
    private static final Logger LOGGER = LogManager.getLogger(LinearRegionFile.class.getSimpleName());
    private static final Object saveLock = new Object();

    public static final int MAX_CHUNK_SIZE = 256 * 1024 * 1024;
    private static final int MAX_REGION_FILE_SIZE = 1024 * 1024 * 1024;
    private static final int MAX_BUCKET_SIZE = 512 * 1024 * 1024;
    private static final int MAX_BUCKET_DECOMPRESSED_SIZE = 512 * 1024 * 1024;
    private static final int MAX_LINEAR_V1_DECOMPRESSED_SIZE = 512 * 1024 * 1024;
    private static final int MAX_FEATURE_NAME_SIZE = 128;
    public static int SAVE_THREAD_MAX_COUNT = 6;
    public static int SAVE_DELAY_MS = 100;
    public static boolean USE_VIRTUAL_THREAD = true;
    private static int activeSaveThreads = 0;

    public final ReentrantLock fileLock = new ReentrantLock(true);
    public Path regionFile;
    public boolean regionFileOpen = false;

    private final byte[][] buffer = new byte[1024][];
    private final int[] bufferUncompressedSize = new int[1024];
    private final long[] chunkTimestamps = new long[1024];
    private final Object markedToSaveLock = new Object();
    private final LZ4Compressor compressor;
    private final LZ4FastDecompressor decompressor;
    private final int compressionLevel;
    private final Thread bindThread;
    private final java.util.concurrent.atomic.AtomicInteger recalculateCount = new java.util.concurrent.atomic.AtomicInteger();

    private byte[][] bucketBuffers;
    private boolean markedToSave = false;
    private int saveGeneration = 0;
    private boolean close = false;
    private int gridSize = 8;
    private int bucketSize = 4;

    public LinearRegionFile(RegionStorageInfo storageKey, Path directory, Path path, boolean dsync, int compressionLevel) throws IOException {
        this(storageKey, directory, path, RegionFileVersion.getCompressionFormat(), dsync, compressionLevel);
    }

    public LinearRegionFile(RegionStorageInfo storageKey, Path path, Path directory, RegionFileVersion compressionFormat, boolean dsync, int compressionLevel) throws IOException {
        Runnable flushCheck = () -> {
            while (!close) {
                synchronized (saveLock) {
                    if (markedToSave && activeSaveThreads < SAVE_THREAD_MAX_COUNT) {
                        activeSaveThreads++;
                        Runnable flushOperation = () -> {
                            try {
                                flush();
                            } catch (IOException ex) {
                                LOGGER.error("Region file {} flush failed", this.regionFile.toAbsolutePath(), ex);
                            } finally {
                                synchronized (saveLock) {
                                    activeSaveThreads--;
                                }
                            }
                        };

                        Thread saveThread = USE_VIRTUAL_THREAD ?
                            Thread.ofVirtual().name("Linear IO - " + LinearRegionFile.this.hashCode()).unstarted(flushOperation) :
                            Thread.ofPlatform().name("Linear IO - " + LinearRegionFile.this.hashCode()).unstarted(flushOperation);
                        if (!saveThread.isVirtual()) {
                            saveThread.setPriority(Thread.NORM_PRIORITY - 3);
                        }
                        saveThread.start();
                    }
                }
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(SAVE_DELAY_MS));
            }
        };
        this.bindThread = USE_VIRTUAL_THREAD ? Thread.ofVirtual().unstarted(flushCheck) : Thread.ofPlatform().unstarted(flushCheck);
        this.bindThread.setName("Linear IO Schedule - " + this.hashCode());
        this.regionFile = path;
        this.compressionLevel = compressionLevel;

        this.compressor = LZ4Factory.fastestInstance().fastCompressor();
        this.decompressor = LZ4Factory.fastestInstance().fastDecompressor();
    }

    public Path getRegionFile() {
        return this.regionFile;
    }

    public ReentrantLock getFileLock() {
        return this.fileLock;
    }

    public Path getPath() {
        return this.regionFile;
    }

    public int getRecalculateCount() {
        return this.recalculateCount.get();
    }

    public boolean recalculateHeader() {
        return false;
    }

    private int chunkToBucketIdx(int chunkX, int chunkZ) {
        int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;
        return bx * gridSize + bz;
    }

    private void openBucket(int chunkX, int chunkZ) throws IOException {
        chunkX = Math.floorMod(chunkX, 32);
        chunkZ = Math.floorMod(chunkZ, 32);
        int idx = chunkToBucketIdx(chunkX, chunkZ);

        if (bucketBuffers == null) return;
        if (bucketBuffers[idx] != null) {
            try {
                ByteArrayInputStream bucketByteStream = new ByteArrayInputStream(bucketBuffers[idx]);
                ZstdInputStream zstdStream = new ZstdInputStream(bucketByteStream);
                ByteBuffer bucketBuffer = ByteBuffer.wrap(readAllBytesBounded(zstdStream, MAX_BUCKET_DECOMPRESSED_SIZE, "bucket " + idx + " in " + this.regionFile));

                int bx = chunkX / bucketSize, bz = chunkZ / bucketSize;

                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * (32 / gridSize) + cx) + (bz * (32 / gridSize) + cz) * 32;

                        requireRemaining(bucketBuffer, Integer.BYTES + Long.BYTES, "bucket entry header");
                        int chunkSize = bucketBuffer.getInt();
                        long timestamp = bucketBuffer.getLong();
                        this.chunkTimestamps[chunkIndex] = timestamp;

                        if (chunkSize > 0) {
                            if (chunkSize < Long.BYTES) {
                                throw new IOException("Invalid chunk size " + chunkSize + " in " + this.regionFile);
                            }
                            final int chunkDataSize = chunkSize - Long.BYTES;
                            validateChunkSize(chunkDataSize, "bucket chunk");
                            requireRemaining(bucketBuffer, chunkDataSize, "bucket chunk data");
                            byte[] chunkData = new byte[chunkDataSize];
                            bucketBuffer.get(chunkData);

                            int maxCompressedLength = this.compressor.maxCompressedLength(chunkData.length);
                            byte[] compressed = new byte[maxCompressedLength];
                            int compressedLength = this.compressor.compress(chunkData, 0, chunkData.length, compressed, 0, maxCompressedLength);
                            byte[] finalCompressed = new byte[compressedLength];
                            System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                            this.buffer[chunkIndex] = finalCompressed;
                            this.bufferUncompressedSize[chunkIndex] = chunkData.length;
                        }
                    }
                }
            } catch (IOException ex) {
                LOGGER.error("Region file corrupted: {} bucket: {}", regionFile, idx, ex);
                bucketBuffers[idx] = null;
                throw ex;
            }
            bucketBuffers[idx] = null;
        }
    }

    private synchronized void openRegionFile() throws IOException {
        if (regionFileOpen) return;

        File regionFile = new File(this.regionFile.toString());

        if(!regionFile.canRead()) {
            regionFileOpen = true;
            this.bindThread.start();
            return;
        }

        final long fileSize = Files.size(this.regionFile);
        if (fileSize < HEADER_SIZE + FOOTER_SIZE || fileSize > MAX_REGION_FILE_SIZE) {
            throw new IOException("Invalid region file size " + fileSize + " for " + this.regionFile);
        }
        byte[] fileContent = Files.readAllBytes(this.regionFile);
        ByteBuffer buffer = ByteBuffer.wrap(fileContent);

        requireRemaining(buffer, Long.BYTES + Byte.BYTES, "linear region header");
        long superBlock = buffer.getLong();
        if (superBlock != SUPERBLOCK) {
            throw new IOException("Invalid superblock: " + superBlock + " file " + this.regionFile);
        }

        byte version = buffer.get();
        if (version == 1 || version == 2) {
            parseLinearV1(buffer);
        } else if (version == 3) {
            parseLinearV2(buffer);
        } else {
            throw new IOException("Invalid version: " + version + " file " + this.regionFile);
        }

        regionFileOpen = true;
        this.bindThread.start();
    }

    private void parseLinearV1(ByteBuffer buffer) throws IOException {
        final int HEADER_SIZE = 32;
        final int FOOTER_SIZE = 8;

        // Skip newestTimestamp (Long) + Compression level (Byte) + Chunk count (Short): Unused.
        requireRemaining(buffer, Long.BYTES + Byte.BYTES + Short.BYTES, "linear v1 fixed header");
        buffer.position(buffer.position() + 11);

        requireRemaining(buffer, Integer.BYTES, "linear v1 data count");
        int dataCount = buffer.getInt();
        if (dataCount <= 0 || dataCount > MAX_REGION_FILE_SIZE) {
            throw new IOException("Invalid v1 data length " + dataCount + " in " + this.regionFile);
        }
        long fileLength = this.regionFile.toFile().length();
        final long expectedLength = HEADER_SIZE + (long) dataCount + FOOTER_SIZE;
        if (fileLength != expectedLength) {
            throw new IOException("Invalid file length: " + this.regionFile + " " + fileLength + " " + expectedLength);
        }

        requireRemaining(buffer, Long.BYTES + dataCount + FOOTER_SIZE, "linear v1 payload");
        buffer.position(buffer.position() + 8); // Skip data hash (Long): Unused.

        byte[] rawCompressed = new byte[dataCount];
        buffer.get(rawCompressed);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(rawCompressed);
        ZstdInputStream zstdInputStream = new ZstdInputStream(byteArrayInputStream);
        ByteBuffer decompressedBuffer = ByteBuffer.wrap(readAllBytesBounded(zstdInputStream, MAX_LINEAR_V1_DECOMPRESSED_SIZE, "linear v1 payload in " + this.regionFile));

        int[] starts = new int[1024];
        requireRemaining(decompressedBuffer, 1024 * (Integer.BYTES + Integer.BYTES), "linear v1 chunk table");
        for (int i = 0; i < 1024; i++) {
            starts[i] = decompressedBuffer.getInt();
            decompressedBuffer.getInt(); // Skip timestamps (Int): Unused.
        }

        for (int i = 0; i < 1024; i++) {
            if (starts[i] > 0) {
                int size = starts[i];
                validateChunkSize(size, "linear v1 chunk");
                requireRemaining(decompressedBuffer, size, "linear v1 chunk data");
                byte[] chunkData = new byte[size];
                decompressedBuffer.get(chunkData);

                int maxCompressedLength = this.compressor.maxCompressedLength(size);
                byte[] compressed = new byte[maxCompressedLength];
                int compressedLength = this.compressor.compress(chunkData, 0, size, compressed, 0, maxCompressedLength);
                byte[] finalCompressed = new byte[compressedLength];
                System.arraycopy(compressed, 0, finalCompressed, 0, compressedLength);

                this.buffer[i] = finalCompressed;
                this.bufferUncompressedSize[i] = size;
                this.chunkTimestamps[i] = getTimestamp(); // Use current timestamp as we don't have the original
            }
        }
    }

    private void parseLinearV2(ByteBuffer buffer) throws IOException {
        requireRemaining(buffer, Long.BYTES + Byte.BYTES, "linear v2 fixed header");
        buffer.getLong(); // Skip newestTimestamp (Long)
        gridSize = buffer.get();
        if (gridSize != 1 && gridSize != 2 && gridSize != 4 && gridSize != 8 && gridSize != 16 && gridSize != 32)
            throw new IOException("Invalid grid size: " + gridSize + " file " + this.regionFile);
        bucketSize = 32 / gridSize;

        requireRemaining(buffer, Integer.BYTES + Integer.BYTES + 128, "linear v2 region header");
        buffer.getInt(); // Skip region_x (Int)
        buffer.getInt(); // Skip region_z (Int)

        boolean[] chunkExistenceBitmap = deserializeExistenceBitmap(buffer);

        while (true) {
            requireRemaining(buffer, Byte.BYTES, "linear v2 feature length");
            int featureNameLength = Byte.toUnsignedInt(buffer.get());
            if (featureNameLength == 0) break;
            if (featureNameLength > MAX_FEATURE_NAME_SIZE) {
                throw new IOException("Invalid feature name length " + featureNameLength + " in " + this.regionFile);
            }
            requireRemaining(buffer, featureNameLength + Integer.BYTES, "linear v2 feature entry");
            byte[] featureNameBytes = new byte[featureNameLength];
            buffer.get(featureNameBytes);
            String featureName = new String(featureNameBytes, StandardCharsets.UTF_8);
            int featureValue = buffer.getInt();
            // System.out.println("NBT Feature: " + featureName + " = " + featureValue);
        }

        int[] bucketSizes = new int[gridSize * gridSize];
        byte[] bucketCompressionLevels = new byte[gridSize * gridSize];
        long[] bucketHashes = new long[gridSize * gridSize];
        for (int i = 0; i < gridSize * gridSize; i++) {
            requireRemaining(buffer, Integer.BYTES + Byte.BYTES + Long.BYTES, "linear v2 bucket header");
            bucketSizes[i] = buffer.getInt();
            if (bucketSizes[i] < 0 || bucketSizes[i] > MAX_BUCKET_SIZE) {
                throw new IOException("Invalid bucket size " + bucketSizes[i] + " in " + this.regionFile);
            }
            bucketCompressionLevels[i] = buffer.get();
            bucketHashes[i] = buffer.getLong();
        }

        bucketBuffers = new byte[gridSize * gridSize][];
        for (int i = 0; i < gridSize * gridSize; i++) {
            if (bucketSizes[i] > 0) {
                requireRemaining(buffer, bucketSizes[i], "linear v2 bucket payload");
                bucketBuffers[i] = new byte[bucketSizes[i]];
                buffer.get(bucketBuffers[i]);
                long rawHash = LongHashFunction.xx().hashBytes(bucketBuffers[i]);
                if (rawHash != bucketHashes[i]) throw new IOException("Region file hash incorrect " + this.regionFile);
            }
        }

        requireRemaining(buffer, Long.BYTES, "linear v2 footer");
        long footerSuperBlock = buffer.getLong();
        if (footerSuperBlock != SUPERBLOCK)
            throw new IOException("Footer superblock invalid " + this.regionFile);
    }

    @Override
    public MoonriseRegionFileIO.RegionDataController.WriteData moonrise$startWrite(CompoundTag data, ChunkPos pos) throws IOException {
        final ChunkBuffer buffer = new ChunkBuffer(pos, false);
        final DataOutputStream out = new DataOutputStream(new BufferedOutputStream(buffer));

        return new ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData(
            data, ca.spottedleaf.moonrise.patches.chunk_system.io.MoonriseRegionFileIO.RegionDataController.WriteData.WriteResult.WRITE,
            out, regionFile -> buffer.moonrise$write(regionFile)
        );
    }

    private synchronized void markToSave() {
        synchronized(markedToSaveLock) {
            markedToSave = true;
            saveGeneration++;
        }
    }

    private synchronized int getSaveGenerationIfMarked() {
        synchronized(markedToSaveLock) {
            return markedToSave ? saveGeneration : -1;
        }
    }

    private synchronized void markSaveComplete(int generation) {
        synchronized(markedToSaveLock) {
            if (markedToSave && saveGeneration == generation) {
                markedToSave = false;
            }
        }
    }

    public synchronized boolean doesChunkExist(ChunkPos pos) {
        try {
            openRegionFile();
            openBucket(pos.x, pos.z);
            return this.bufferUncompressedSize[getChunkIndex(pos.x, pos.z)] > 0;
        } catch (IOException ex) {
            LOGGER.error("Failed to check chunk existence in {} chunk {}", this.regionFile, pos, ex);
            return false;
        }
    }

    public synchronized boolean hasChunk(ChunkPos pos) {
        try {
            openRegionFile();
            openBucket(pos.x, pos.z);
            return this.bufferUncompressedSize[getChunkIndex(pos.x, pos.z)] > 0;
        } catch (IOException ex) {
            LOGGER.error("Failed to check chunk presence in {} chunk {}", this.regionFile, pos, ex);
            return false;
        }
    }

    public synchronized void write(ChunkPos pos, ByteBuffer buffer) throws IOException {
        openRegionFile();
        openBucket(pos.x, pos.z);
        try {
            ByteBuffer source = buffer.slice();
            int uncompressedSize = source.remaining();
            if (uncompressedSize > MAX_CHUNK_SIZE) {
                LOGGER.error("Chunk {} exceeds maximum size {} (max {}) {}; preserving existing stored data", pos, uncompressedSize, MAX_CHUNK_SIZE, this.regionFile);
                return;
            }

            byte[] b = new byte[uncompressedSize];
            source.get(b);

            int maxCompressedLength = this.compressor.maxCompressedLength(b.length);
            byte[] compressed = new byte[maxCompressedLength];
            int compressedLength = this.compressor.compress(b, 0, b.length, compressed, 0, maxCompressedLength);
            b = new byte[compressedLength];
            System.arraycopy(compressed, 0, b, 0, compressedLength);

            int index = getChunkIndex(pos.x, pos.z);
            this.buffer[index] = b;
            this.chunkTimestamps[index] = getTimestamp();
            this.bufferUncompressedSize[getChunkIndex(pos.x, pos.z)] = uncompressedSize;
        } catch (RuntimeException e) {
            LOGGER.error("Chunk write failure {} {}", e, this.regionFile);
        }
        markToSave();
    }

    public DataOutputStream getChunkDataOutputStream(ChunkPos pos) throws IOException {
        openRegionFile();
        openBucket(pos.x, pos.z);
        return new DataOutputStream(new BufferedOutputStream(new LinearRegionFile.ChunkBuffer(pos)));
    }

    @Nullable
    public synchronized DataInputStream getChunkDataInputStream(ChunkPos pos) throws IOException {
        openRegionFile();
        openBucket(pos.x, pos.z);

        final int chunkIndex = getChunkIndex(pos.x, pos.z);
        if(this.bufferUncompressedSize[chunkIndex] != 0) {
            final int uncompressedSize = this.bufferUncompressedSize[chunkIndex];
            if (this.buffer[chunkIndex] == null || uncompressedSize <= 0 || uncompressedSize > MAX_CHUNK_SIZE) {
                LOGGER.error("Invalid chunk buffer metadata for {} chunk {}", this.regionFile, pos);
                return null;
            }
            byte[] content = new byte[uncompressedSize];
            try {
                this.decompressor.decompress(this.buffer[chunkIndex], 0, content, 0, uncompressedSize);
            } catch (RuntimeException ex) {
                throw new IOException("Chunk decompression failed for " + this.regionFile + " chunk " + pos, ex);
            }
            return new DataInputStream(new ByteArrayInputStream(content));
        }
        return null;
    }

    public synchronized void clear(ChunkPos pos) throws IOException {
        openRegionFile();
        openBucket(pos.x, pos.z);
        int i = getChunkIndex(pos.x, pos.z);
        this.buffer[i] = null;
        this.bufferUncompressedSize[i] = 0;
        this.chunkTimestamps[i] = 0;
        markToSave();
    }

    public synchronized void close() throws IOException {
        openRegionFile();
        close = true;
        try {
            flush();
        } catch(IOException e) {
            throw new IOException("Region flush IOException " + e + " " + this.regionFile);
        }
    }

    public synchronized void flush() throws IOException {
        int saveGeneration = getSaveGenerationIfMarked();
        if (saveGeneration < 0) return;

        openRegionFile();

        long timestamp = getTimestamp();

        long writeStart = System.nanoTime();
        File tempFile = new File(regionFile.toString() + ".tmp");
        FileOutputStream fileStream = new FileOutputStream(tempFile);
        DataOutputStream dataStream = new DataOutputStream(fileStream);

        dataStream.writeLong(SUPERBLOCK);
        dataStream.writeByte(VERSION);
        dataStream.writeLong(timestamp);
        dataStream.writeByte(gridSize);

        String fileName = regionFile.getFileName().toString();
        String[] parts = fileName.split("\\.");
        int regionX = 0;
        int regionZ = 0;
        try {
            if (parts.length >= 4) {
                regionX = Integer.parseInt(parts[1]);
                regionZ = Integer.parseInt(parts[2]);
            } else {
                LOGGER.warn("Unexpected file name format: {}", fileName);
            }
        } catch (NumberFormatException e) {
            LOGGER.error("Failed to parse region coordinates from file name: {}", fileName, e);
        }

        dataStream.writeInt(regionX);
        dataStream.writeInt(regionZ);

        boolean[] chunkExistenceBitmap = new boolean[1024];
        for (int i = 0; i < 1024; i++) {
            chunkExistenceBitmap[i] = (this.bufferUncompressedSize[i] > 0);
        }
        writeSerializedExistenceBitmap(dataStream, chunkExistenceBitmap);

        writeNBTFeatures(dataStream);

        int bucketMisses = 0;
        byte[][] buckets = new byte[gridSize * gridSize][];
        for (int bx = 0; bx < gridSize; bx++) {
            for (int bz = 0; bz < gridSize; bz++) {
                if (bucketBuffers != null && bucketBuffers[bx * gridSize + bz] != null) {
                    buckets[bx * gridSize + bz] = bucketBuffers[bx * gridSize + bz];
                    continue;
                }
                bucketMisses++;

                ByteArrayOutputStream bucketStream = new ByteArrayOutputStream();
                ZstdOutputStream zstdStream = new ZstdOutputStream(bucketStream, this.compressionLevel);
                DataOutputStream bucketDataStream = new DataOutputStream(zstdStream);

                boolean hasData = false;
                for (int cx = 0; cx < 32 / gridSize; cx++) {
                    for (int cz = 0; cz < 32 / gridSize; cz++) {
                        int chunkIndex = (bx * 32 / gridSize + cx) + (bz * 32 / gridSize + cz) * 32;
                        if (this.bufferUncompressedSize[chunkIndex] > 0) {
                            hasData = true;
                            byte[] chunkData = new byte[this.bufferUncompressedSize[chunkIndex]];
                            this.decompressor.decompress(this.buffer[chunkIndex], 0, chunkData, 0, this.bufferUncompressedSize[chunkIndex]);
                            bucketDataStream.writeInt(chunkData.length + 8);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                            bucketDataStream.write(chunkData);
                        } else {
                            bucketDataStream.writeInt(0);
                            bucketDataStream.writeLong(this.chunkTimestamps[chunkIndex]);
                        }
                    }
                }
                bucketDataStream.close();

                if (hasData) {
                    buckets[bx * gridSize + bz] = bucketStream.toByteArray();
                }
            }
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            dataStream.writeInt(buckets[i] != null ? buckets[i].length : 0);
            dataStream.writeByte(this.compressionLevel);
            long rawHash = 0;
            if (buckets[i] != null) {
                rawHash = LongHashFunction.xx().hashBytes(buckets[i]);
            }
            dataStream.writeLong(rawHash);
        }

        for (int i = 0; i < gridSize * gridSize; i++) {
            if (buckets[i] != null) {
                dataStream.write(buckets[i]);
            }
        }

        dataStream.writeLong(SUPERBLOCK);

        dataStream.flush();
        fileStream.getFD().sync();
        fileStream.getChannel().force(true); // Ensure atomicity on Btrfs
        dataStream.close();

        fileStream.close();
        Files.move(tempFile.toPath(), this.regionFile, StandardCopyOption.REPLACE_EXISTING);
        markSaveComplete(saveGeneration);
    }

    private void writeNBTFeatures(DataOutputStream dataStream) throws IOException {
        // writeNBTFeature(dataStream, "example", 1);
        dataStream.writeByte(0); // End of NBT features
    }

    private void writeNBTFeature(DataOutputStream dataStream, String featureName, int featureValue) throws IOException {
        byte[] featureNameBytes = featureName.getBytes();
        dataStream.writeByte(featureNameBytes.length);
        dataStream.write(featureNameBytes);
        dataStream.writeInt(featureValue);
    }

    private boolean[] deserializeExistenceBitmap(ByteBuffer buffer) {
        boolean[] result = new boolean[1024];
        for (int i = 0; i < 128; i++) {
            byte b = buffer.get();
            for (int j = 0; j < 8; j++) {
                result[i * 8 + j] = ((b >> (7 - j)) & 1) == 1;
            }
        }
        return result;
    }

    private void writeSerializedExistenceBitmap(DataOutputStream out, boolean[] bitmap) throws IOException {
        for (int i = 0; i < 128; i++) {
            byte b = 0;
            for (int j = 0; j < 8; j++) {
                if (bitmap[i * 8 + j]) {
                    b |= (1 << (7 - j));
                }
            }
            out.writeByte(b);
        }
    }

    private static void requireRemaining(ByteBuffer buffer, int bytes, String context) throws IOException {
        if (bytes < 0 || buffer.remaining() < bytes) {
            throw new IOException("Truncated " + context + ": need " + bytes + " bytes, have " + buffer.remaining());
        }
    }

    private static void validateChunkSize(int size, String context) throws IOException {
        if (size <= 0 || size > MAX_CHUNK_SIZE) {
            throw new IOException("Invalid " + context + " size " + size + " (max " + MAX_CHUNK_SIZE + ")");
        }
    }

    private static byte[] readAllBytesBounded(InputStream in, int maxBytes, String context) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(8192, maxBytes));
        byte[] tempBuffer = new byte[8192];
        int length;
        while ((length = in.read(tempBuffer)) >= 0) {
            if (out.size() > maxBytes - length) {
                throw new IOException("Decompressed " + context + " exceeds " + maxBytes + " bytes");
            }
            out.write(tempBuffer, 0, length);
        }
        return out.toByteArray();
    }

    private byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] tempBuffer = new byte[4096];

        int length;
        while ((length = in.read(tempBuffer)) >= 0) {
            out.write(tempBuffer, 0, length);
        }

        return out.toByteArray();
    }

    private static int getChunkIndex(int x, int z) {
        return (x & 31) + ((z & 31) << 5);
    }

    private static int getTimestamp() {
        return (int) (System.currentTimeMillis() / 1000L);
    }

    public void setOversized(int x, int z, boolean something) { }

    public CompoundTag getOversizedData(int x, int z) throws IOException {
        return null;
    }

    public boolean isOversized(int x, int z) {
        return false;
    }

    private class ChunkBuffer extends ByteArrayOutputStream {
        private final ChunkPos pos;

        public ChunkBuffer(ChunkPos chunkcoordintpair) {
            this(chunkcoordintpair, true);
        }

        public ChunkBuffer(ChunkPos chunkcoordintpair, boolean writeOnClose) {
            super();
            this.pos = chunkcoordintpair;
            this.writeOnClose = writeOnClose;
        }

        private final boolean writeOnClose;

        public void moonrise$write(org.bxteam.divinemc.region.IRegionFile regionFile) throws IOException {
            regionFile.write(this.pos, ByteBuffer.wrap(this.buf, 0, this.count));
        }

        public void close() throws IOException {
            if (this.writeOnClose) {
                this.moonrise$write(LinearRegionFile.this);
            }
        }
    }
}
