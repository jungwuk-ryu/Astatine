package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperRegionLocker;
import io.multipaper.shreddedpaper.threading.region.events.RegionLockWaitEvent;
import java.nio.file.Path;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

@AllFeatures
class RegionLockWaitTest {
    @TempDir Path directory;

    @Test
    void retriesRetainOriginalDeadlineAndFinishExactlyOnceOnSuccessOrRetirement() throws Exception {
        final Path output = directory.resolve("waits.jfr");
        try (var recording = new Recording()) {
            recording.enable(RegionLockWaitEvent.class);
            recording.start();
            final var wait = new RegionLockWait();
            final var locker = new ShreddedPaperRegionLocker();
            final var position = new RegionPos(1, 2);
            final long[] cells = {position.toLong()};
            wait.failed("world", position, 100, 120, locker, cells);
            wait.failed("world", position, 200, 220, locker, cells);
            wait.finish(300, "acquired");
            wait.finish(400, "retired"); // No duplicate completion.
            wait.failed("world", position, 500, 510, locker, cells);
            wait.finish(550, "retired");
            recording.stop();
            recording.dump(output);
        }
        final var events = RecordingFile.readAllEvents(output).stream()
            .filter(event -> event.getEventType().getName().equals(RegionLockWaitEvent.class.getName())).toList();
        assertEquals(2, events.size());
        final var acquired = events.get(0);
        assertEquals(100, acquired.getLong("originalScheduledNanos"));
        assertEquals(2, acquired.getInt("failedAttempts"));
        assertEquals(180, acquired.getLong("waitNanos"));
        assertEquals(200, acquired.getLong("overdueNanos"));
        assertEquals("acquired", acquired.getString("outcome"));
        assertEquals(1, events.get(1).getInt("failedAttempts"));
        assertEquals(40, events.get(1).getLong("waitNanos"));
        assertEquals("retired", events.get(1).getString("outcome"));
    }
}
