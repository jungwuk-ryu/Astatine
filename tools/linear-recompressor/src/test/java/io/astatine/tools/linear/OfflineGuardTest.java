package io.astatine.tools.linear;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertThrows;

class OfflineGuardTest {
    @TempDir Path directory;

    @Test
    void refusesHeldMinecraftSessionLock() throws Exception {
        Path region = LinearFixture.write(directory.resolve("locked.linear"), 3);
        Path sessionLock = directory.resolve("session.lock");
        Path checkpoint = directory.resolve("checkpoint.tsv");
        try (FileChannel channel = FileChannel.open(sessionLock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            String[] args = {"apply", "--level", "9", "--checkpoint", checkpoint.toString(), "--lock-file", sessionLock.toString(), region.toString()};
            assertThrows(RuntimeException.class, () -> LinearRecompressorMain.run(args));
        }
    }

    @Test
    void refusesLauncherAliveState() throws Exception {
        Path region = LinearFixture.write(directory.resolve("alive.linear"), 3);
        Path lock = directory.resolve("session.lock");
        Path checkpoint = directory.resolve("checkpoint.tsv");
        Path state = directory.resolve("launcher-state.json");
        Files.writeString(state, "{\"server\":{\"alive\":true,\"pid\":12345}}");
        String[] args = {"apply", "--level", "9", "--checkpoint", checkpoint.toString(), "--lock-file", lock.toString(), "--launcher-state", state.toString(), region.toString()};

        assertThrows(IOException.class, () -> LinearRecompressorMain.run(args));
    }
}
