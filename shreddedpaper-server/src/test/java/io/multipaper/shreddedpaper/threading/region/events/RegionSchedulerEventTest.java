package io.multipaper.shreddedpaper.threading.region.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSchedulerEventTest {

    @Test
    void schedulerSamplesAreThinnedAndDisabledZeroDoesNotSample() {
        assertFalse(RegionSchedulerEvent.shouldCommitSample(0L));
        assertTrue(RegionSchedulerEvent.shouldCommitSample(1L));
        assertFalse(RegionSchedulerEvent.shouldCommitSample(2L));
        assertFalse(RegionSchedulerEvent.shouldCommitSample(255L));
        assertTrue(RegionSchedulerEvent.shouldCommitSample(256L));
    }
}
