package io.multipaper.shreddedpaper.privacy;

import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;

public final class AnonymousMode {
    private static final InetAddress REDACTED_ADDRESS = redactedAddress();
    private static final InetSocketAddress REDACTED_SOCKET_ADDRESS = new InetSocketAddress(REDACTED_ADDRESS, 0);
    public static final String REDACTED_IP = "0.0.0.0";

    private AnonymousMode() {
    }

    public static boolean enabled() {
        return ShreddedPaperConfiguration.get().privacy.anonymousMode.enabled;
    }

    public static boolean redactLoginEventAddresses() {
        ShreddedPaperConfiguration.Privacy.AnonymousMode config = ShreddedPaperConfiguration.get().privacy.anonymousMode;
        return config.enabled && config.redactLoginEventAddresses;
    }

    public static InetAddress redactedInetAddress() {
        return REDACTED_ADDRESS;
    }

    public static InetSocketAddress redactedSocketAddress() {
        return REDACTED_SOCKET_ADDRESS;
    }

    public static InetAddress redactLoginEventAddress(InetAddress address) {
        return redactLoginEventAddresses() ? REDACTED_ADDRESS : address;
    }

    public static InetSocketAddress redactPublicSocketAddress(InetSocketAddress address) {
        return enabled() ? REDACTED_SOCKET_ADDRESS : address;
    }

    public static SocketAddress redactPublicSocketAddress(SocketAddress address) {
        return enabled() && address instanceof InetSocketAddress ? REDACTED_SOCKET_ADDRESS : address;
    }

    private static InetAddress redactedAddress() {
        try {
            return InetAddress.getByAddress(new byte[] {0, 0, 0, 0});
        } catch (UnknownHostException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
