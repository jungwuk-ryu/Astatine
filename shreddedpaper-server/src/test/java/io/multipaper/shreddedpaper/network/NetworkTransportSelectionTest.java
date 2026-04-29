package io.multipaper.shreddedpaper.network;

import io.multipaper.shreddedpaper.network.NetworkTransportSelection.Transport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkTransportSelectionTest {

    @Test
    void disablesAllNativeTransportsWhenNativeTransportIsOff() {
        assertEquals(
                Transport.NIO,
                NetworkTransportSelection.selectRemote(false, true, true, true, true, false)
        );
    }

    @Test
    void keepsKqueuePriorityOnPlatformsWhereItIsAvailable() {
        assertEquals(
                Transport.KQUEUE,
                NetworkTransportSelection.selectRemote(true, true, true, true, true, false)
        );
    }

    @Test
    void prefersIoUringOverEpollOnlyWhenExplicitlyEnabled() {
        assertEquals(
                Transport.IO_URING,
                NetworkTransportSelection.selectRemote(true, true, true, false, true, false)
        );
        assertEquals(
                Transport.EPOLL,
                NetworkTransportSelection.selectRemote(true, false, true, false, true, false)
        );
    }

    @Test
    void keepsUnixDomainSocketsOnEpollDomainTransport() {
        assertEquals(
                Transport.EPOLL_UNIX_DOMAIN,
                NetworkTransportSelection.selectRemote(true, true, true, false, true, true)
        );
    }

    @Test
    void fallsBackToNioWhenPreferredIoUringIsUnavailable() {
        assertEquals(
                Transport.NIO,
                NetworkTransportSelection.selectRemote(true, true, false, false, false, false)
        );
    }

    @Test
    void avoidsAvailabilityProbesWhenNativeTransportIsOff() {
        final Probe ioUring = new Probe(false);
        final Probe kqueue = new Probe(true);
        final Probe epoll = new Probe(true);

        assertEquals(
                Transport.NIO,
                NetworkTransportSelection.selectRemote(false, true, ioUring, kqueue, epoll, false)
        );
        assertFalse(ioUring.wasChecked());
        assertFalse(kqueue.wasChecked());
        assertFalse(epoll.wasChecked());
    }

    @Test
    void avoidsIoUringProbeWhenPreferenceOrSocketTypeCannotSelectIt() {
        final Probe ioUring = new Probe(true);

        assertEquals(
                Transport.EPOLL,
                NetworkTransportSelection.selectRemote(true, false, ioUring, new Probe(false), new Probe(true), false)
        );
        assertFalse(ioUring.wasChecked());

        assertEquals(
                Transport.EPOLL_UNIX_DOMAIN,
                NetworkTransportSelection.selectRemote(true, true, ioUring, new Probe(false), new Probe(true), true)
        );
        assertFalse(ioUring.wasChecked());
    }

    @Test
    void stopsAfterKqueueWhenKqueueIsAvailable() {
        final Probe ioUring = new Probe(true);
        final Probe kqueue = new Probe(true);
        final Probe epoll = new Probe(true);

        assertEquals(
                Transport.KQUEUE,
                NetworkTransportSelection.selectRemote(true, true, ioUring, kqueue, epoll, false)
        );
        assertTrue(kqueue.wasChecked());
        assertFalse(ioUring.wasChecked());
        assertFalse(epoll.wasChecked());
    }

    private static final class Probe implements java.util.function.BooleanSupplier {
        private final boolean value;
        private boolean checked;

        private Probe(final boolean value) {
            this.value = value;
        }

        @Override
        public boolean getAsBoolean() {
            this.checked = true;
            return this.value;
        }

        private boolean wasChecked() {
            return this.checked;
        }
    }
}
