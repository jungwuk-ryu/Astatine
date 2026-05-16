package io.multipaper.shreddedpaper.threading.region;

import io.multipaper.shreddedpaper.region.RegionPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
    void criticalSystemClassRejectsAfterCapacityWithoutLosingAccounting() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.CRITICAL_SYSTEM);

        for (int i = 0; i < capacity; i++) {
            assertTrue(mailbox.offer(RegionTaskClass.CRITICAL_SYSTEM, () -> {}, 0L), "critical offer " + i);
        }

        assertFalse(mailbox.offer(RegionTaskClass.CRITICAL_SYSTEM, () -> {}, 0L));
        assertEquals(capacity, mailbox.queued(RegionTaskClass.CRITICAL_SYSTEM));
        assertEquals(1L, mailbox.rejected());
    }

    @Test
    void transferredClassUsesEmergencyMailboxAfterCapacity() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.OWNER_HANDOFF);
        final List<String> ran = new ArrayList<>();

        for (int i = 0; i < capacity; i++) {
            assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)), "transferred offer " + i);
        }

        assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> ran.add("emergency"), 0L, new RegionPos(1, 1)));
        assertEquals(capacity, mailbox.queued(RegionTaskClass.OWNER_HANDOFF));
        assertEquals(1, mailbox.pressureDiagnostics().emergency().queued());
        assertEquals(0L, mailbox.rejected());

        mailbox.runDue(null);

        assertEquals(List.of("emergency"), ran);
    }

    @Test
    void emergencyMailboxPreservesDelayTicks() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.OWNER_HANDOFF);
        final List<String> ran = new ArrayList<>();

        for (int i = 0; i < capacity; i++) {
            assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)), "transferred offer " + i);
        }

        assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> ran.add("delayed"), 3L, new RegionPos(1, 1)));

        mailbox.runDue(null);
        mailbox.runDue(null);
        assertTrue(ran.isEmpty());

        mailbox.runDue(null);
        assertEquals(List.of("delayed"), ran);
    }

    @Test
    void emergencyMailboxRejectsAtBoundedCapacity() {
        final RegionMailbox mailbox = mailbox();
        final int capacity = mailbox.capacity(RegionTaskClass.OWNER_HANDOFF);
        final int emergencyCapacity = mailbox.emergencyCapacity();

        for (int i = 0; i < capacity; i++) {
            assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)), "transferred offer " + i);
        }
        for (int i = 0; i < emergencyCapacity; i++) {
            assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)), "emergency offer " + i);
        }

        assertFalse(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)));
        assertEquals(emergencyCapacity, mailbox.pressureDiagnostics().emergency().queued());
        assertEquals(1L, mailbox.rejected());
    }

    @Test
    void staleOwnerWithoutBackingLevelRejectsWithoutThrowing() {
        final AtomicLong epoch = new AtomicLong(1L);
        final RegionMailbox mailbox = new RegionMailbox("world", REGION_POS, 1L, epoch::get, ignored -> false);

        assertTrue(mailbox.offerTransferred(RegionTaskClass.OWNER_HANDOFF, () -> {}, 0L, new RegionPos(1, 1)));
        epoch.incrementAndGet();

        assertEquals(1, mailbox.runDue(null));
        assertEquals(0, mailbox.depth());
        assertEquals(1L, mailbox.rejected());
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
        assertEquals(0, pressure.emergency().queued());

        mailbox.runDue(null);

        assertEquals(0, mailbox.pressureDiagnostics().transferred().queued());
        assertEquals(1, mailbox.pressureDiagnostics().transferred().peakDepth());
    }

    private static RegionMailbox mailbox() {
        return new RegionMailbox("world", REGION_POS, 1L, () -> 1L, ignored -> true);
    }
}
