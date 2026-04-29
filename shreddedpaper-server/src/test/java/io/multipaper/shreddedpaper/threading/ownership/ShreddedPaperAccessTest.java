package io.multipaper.shreddedpaper.threading.ownership;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShreddedPaperAccessTest {

    @Test
    void loadedReadFallbackSamplingKeepsFirstSamplesAndThenThinsStorms() {
        for (long count = 1L; count <= 8L; count++) {
            assertTrue(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(count));
        }

        assertFalse(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(9L));
        assertFalse(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(255L));
        assertTrue(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(256L));
        assertFalse(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(257L));
        assertTrue(ShreddedPaperAccess.shouldRecordLoadedReadFallbackSample(512L));
    }
}
