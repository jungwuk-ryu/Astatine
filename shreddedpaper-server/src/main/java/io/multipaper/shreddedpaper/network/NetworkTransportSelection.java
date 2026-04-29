package io.multipaper.shreddedpaper.network;

import java.util.function.BooleanSupplier;

public final class NetworkTransportSelection {

    private NetworkTransportSelection() {
    }

    public enum Transport {
        NIO,
        KQUEUE,
        EPOLL,
        EPOLL_UNIX_DOMAIN,
        IO_URING
    }

    public static Transport selectRemote(
            final boolean tryNativeTransport,
            final boolean preferIoUringTransport,
            final boolean ioUringAvailable,
            final boolean kqueueAvailable,
            final boolean epollAvailable,
            final boolean unixDomainSocket
    ) {
        return selectRemote(
                tryNativeTransport,
                preferIoUringTransport,
                () -> ioUringAvailable,
                () -> kqueueAvailable,
                () -> epollAvailable,
                unixDomainSocket
        );
    }

    public static Transport selectRemote(
            final boolean tryNativeTransport,
            final boolean preferIoUringTransport,
            final BooleanSupplier ioUringAvailable,
            final BooleanSupplier kqueueAvailable,
            final BooleanSupplier epollAvailable,
            final boolean unixDomainSocket
    ) {
        if (!tryNativeTransport) {
            return Transport.NIO;
        }

        if (kqueueAvailable.getAsBoolean()) {
            return Transport.KQUEUE;
        }

        if (!unixDomainSocket && preferIoUringTransport && ioUringAvailable.getAsBoolean()) {
            return Transport.IO_URING;
        }

        if (epollAvailable.getAsBoolean()) {
            return unixDomainSocket ? Transport.EPOLL_UNIX_DOMAIN : Transport.EPOLL;
        }

        return Transport.NIO;
    }
}
