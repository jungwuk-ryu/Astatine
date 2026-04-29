package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionMailboxTest {

    private static final RegionPos REGION_POS = new RegionPos(0, 0);

    @Test
    void ordinalArraysPreserveDrainOrderAndPerClassFifo() {
        final RegionMailbox mailbox = mailbox();
        final List<String> ran = new ArrayList<>();

        assertTrue(mailbox.offer(RegionTaskClass.PLUGIN, () -> ran.add("plugin-1"), 0L));
        assertTrue(mailbox.offer(RegionTaskClass.PLAYER_ACTION, () -> ran.add("player"), 0L));
        assertTrue(mailbox.offer(RegionTaskClass.PLUGIN, () -> ran.add("plugin-2"), 0L));
        assertTrue(mailbox.offer(RegionTaskClass.CRITICAL_SYSTEM, () -> ran.add("critical"), 0L));

        assertEquals(4, mailbox.runDue(null));
        assertEquals(List.of("critical", "player", "plugin-1", "plugin-2"), ran);
        assertEquals(0, mailbox.depth());
    }

    @Test
    void boundedPluginClassRejectsAfterCapacityWithoutLosingAccounting() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.PLUGIN);

        for (int i = 0; i < capacity; i++) {
            assertTrue(mailbox.offer(RegionTaskClass.PLUGIN, () -> {}, 0L), "offer " + i);
        }

        assertFalse(mailbox.offer(RegionTaskClass.PLUGIN, () -> {}, 0L));
        assertEquals(capacity, mailbox.queued(RegionTaskClass.PLUGIN));
        assertEquals(1L, mailbox.rejected());
    }

    @Test
    void criticalSystemClassRemainsNonDroppingPastReserve() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.CRITICAL_SYSTEM);

        for (int i = 0; i < capacity + 2; i++) {
            assertTrue(mailbox.offer(RegionTaskClass.CRITICAL_SYSTEM, () -> {}, 0L), "critical offer " + i);
        }

        assertEquals(capacity + 2, mailbox.queued(RegionTaskClass.CRITICAL_SYSTEM));
        assertEquals(0L, mailbox.rejected());
    }

    @Test
    void pressureDiagnosticsTrackCriticalAndTransferredQueues() {
        final RegionMailbox mailbox = mailbox();

        assertTrue(mailbox.offer(RegionTaskClass.CRITICAL_SYSTEM, () -> {}, 0L));
        assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)));

        final RegionMailbox.PressureDiagnostics pressure = mailbox.pressureDiagnostics();
        assertEquals(1, pressure.criticalSystem().queued());
        assertEquals(1, pressure.criticalSystem().peakDepth());
        assertTrue(pressure.criticalSystem().oldestAgeNanos() >= 0L);
        assertTrue(pressure.criticalSystem().peakProducerContext().contains("taskClass=CRITICAL_SYSTEM"));
        assertEquals(1, pressure.transferred().queued());
        assertEquals(1, pressure.transferred().peakDepth());
        assertTrue(pressure.transferred().oldestAgeNanos() >= 0L);
        assertTrue(pressure.transferred().peakProducerContext().contains("taskClass=OWNER_HANDOFF"));

        mailbox.runDue(null);

        assertEquals(0, mailbox.pressureDiagnostics().transferred().queued());
        assertEquals(1, mailbox.pressureDiagnostics().transferred().peakDepth());
    }

    private static RegionMailbox mailbox() {
        return new RegionMailbox("world", REGION_POS, 1L, () -> 1L, ignored -> true);
    }
}
