package io.astatine.tools.linear;

import com.github.luben.zstd.ZstdOutputStream;
import net.openhft.hashing.LongHashFunction;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class LinearFixture {
    private LinearFixture() {}

    static Path write(Path path, int level) throws IOException {
        return write(path, level, false);
    }

    static Path writeBeneficial(Path path, int level) throws IOException {
        return write(path, level, true);
    }

    private static Path write(Path path, int level, boolean largePayload) throws IOException {
        int grid = 2;
        int bucketSize = 32 / grid;
        boolean[] exists = new boolean[1024];
        if (largePayload) {
            for (int index = 0; index < exists.length; index += 17) exists[index] = true;
        } else {
            exists[0] = true;
            exists[17] = true;
            exists[700] = true;
        }

        List<byte[]> buckets = new ArrayList<>();
        for (int bx = 0; bx < grid; bx++) {
            for (int bz = 0; bz < grid; bz++) {
                ByteArrayOutputStream raw = new ByteArrayOutputStream();
                boolean hasData = false;
                try (DataOutputStream data = new DataOutputStream(raw)) {
                    for (int cx = 0; cx < bucketSize; cx++) {
                        for (int cz = 0; cz < bucketSize; cz++) {
                            int index = (bx * bucketSize + cx) + (bz * bucketSize + cz) * 32;
                            if (exists[index]) {
                                hasData = true;
                                String payload = largePayload
                                    ? ("fixture-pattern-" + (index % 7) + "-").repeat(4_096) + "tail-" + index
                                    : "fixture-chunk-" + index + "-" + "x".repeat(index % 53);
                                byte[] value = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                data.writeInt(value.length + Long.BYTES);
                                data.writeLong(1_700_000_000L + index);
                                data.write(value);
                            } else {
                                data.writeInt(0);
                                data.writeLong(0);
                            }
                        }
                    }
                }
                if (!hasData) {
                    buckets.add(null);
                } else {
                    ByteArrayOutputStream compressed = new ByteArrayOutputStream();
                    try (ZstdOutputStream zstd = new ZstdOutputStream(compressed, level)) {
                        zstd.write(raw.toByteArray());
                    }
                    buckets.add(compressed.toByteArray());
                }
            }
        }

        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeLong(LinearV3File.SUPERBLOCK);
            output.writeByte(LinearV3File.VERSION);
            output.writeLong(1_700_000_700L);
            output.writeByte(grid);
            output.writeInt(4);
            output.writeInt(-7);
            for (int group = 0; group < 128; group++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) if (exists[group * 8 + bit]) value |= 1 << (7 - bit);
                output.writeByte(value);
            }
            byte[] feature = "fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            output.writeByte(feature.length);
            output.write(feature);
            output.writeInt(42);
            output.writeByte(0);
            for (byte[] bucket : buckets) {
                output.writeInt(bucket == null ? 0 : bucket.length);
                output.writeByte(level);
                output.writeLong(bucket == null ? 0 : LongHashFunction.xx().hashBytes(bucket));
            }
            for (byte[] bucket : buckets) if (bucket != null) output.write(bucket);
            output.writeLong(LinearV3File.SUPERBLOCK);
        }
        return path;
    }

    static Path writeLegacy(Path path, int version, int level) throws IOException {
        byte[][] chunks = new byte[1024][];
        chunks[0] = "legacy-zero".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        chunks[17] = "legacy-seventeen".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        chunks[700] = ("legacy-seven-hundred-" + "z".repeat(80)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        try (DataOutputStream data = new DataOutputStream(raw)) {
            for (byte[] chunk : chunks) {
                data.writeInt(chunk == null ? 0 : chunk.length);
                data.writeInt(1_700_000_000);
            }
            for (byte[] chunk : chunks) if (chunk != null) data.write(chunk);
        }
        ByteArrayOutputStream compressedBytes = new ByteArrayOutputStream();
        try (ZstdOutputStream zstd = new ZstdOutputStream(compressedBytes, level)) {
            zstd.write(raw.toByteArray());
        }
        byte[] compressed = compressedBytes.toByteArray();
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(path))) {
            output.writeLong(LinearV3File.SUPERBLOCK);
            output.writeByte(version);
            output.writeLong(1_700_000_700L);
            output.writeByte(level);
            output.writeShort(3);
            output.writeInt(compressed.length);
            output.writeLong(LongHashFunction.xx().hashBytes(compressed));
            output.write(compressed);
            output.writeLong(LinearV3File.SUPERBLOCK);
        }
        return path;
    }
}
