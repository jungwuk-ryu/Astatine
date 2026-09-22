package io.multipaper.shreddedpaper.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import org.bukkit.support.environment.AllFeatures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@AllFeatures
class TrackingFlushTest {
    @Test
    void realConnectionPreservesListenerKeepaliveAndCloseBoundaries() {
        final var loop = new OrderedPacketBatcherTest.Loop();
        final Channel channel = mock(Channel.class);
        when(channel.eventLoop()).thenReturn(loop.executor);
        when(channel.isOpen()).thenReturn(true);
        when(channel.voidPromise()).thenAnswer(ignored -> new DefaultChannelPromise(channel, loop.executor));
        final List<Object> events = new ArrayList<>();
        org.mockito.stubbing.Answer<ChannelFuture> write = invocation -> {
            events.add(invocation.getArgument(0));
            final ChannelPromise promise = invocation.getArguments().length == 2
                ? invocation.getArgument(1) : new DefaultChannelPromise(channel, loop.executor);
            promise.setSuccess();
            return promise;
        };
        when(channel.write(any(), any(ChannelPromise.class))).thenAnswer(write);
        when(channel.writeAndFlush(any(), any(ChannelPromise.class))).thenAnswer(write);
        when(channel.writeAndFlush(any())).thenAnswer(write);
        when(channel.flush()).thenAnswer(ignored -> { events.add("flush"); return channel; });
        when(channel.close()).thenAnswer(ignored -> {
            events.add("close"); return new DefaultChannelPromise(channel, loop.executor).setSuccess();
        });
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = channel;
        final Packet<?> first = mock(ClientboundRotateHeadPacket.class);
        final Packet<?> listenerPacket = mock(ClientboundRotateHeadPacket.class);
        final Packet<?> last = mock(ClientboundRotateHeadPacket.class);
        final Packet<?> keepalive = new ClientboundKeepAlivePacket(123);
        connection.send(first);
        connection.send(listenerPacket, ignored -> events.add("listener"), true);
        connection.send(keepalive);
        connection.send(last);
        connection.flushChannel();
        connection.disconnect(Component.literal("test"));
        loop.drain();
        assertEquals(List.of(first, "flush", listenerPacket, "listener", keepalive, last, "flush", "flush", "close"), events);
        verify(first).onPacketDispatch(null);
        verify(last).onPacketDispatch(null);
    }

    @Test
    void nestedAndExceptionalScopesFlushEveryTouchedConnection() {
        final Connection outer = mock(Connection.class), inner = mock(Connection.class);
        assertFalse(RegionPacketFlushScope.defer(outer));
        assertThrows(IllegalStateException.class, () -> {
            try (var first = RegionPacketFlushScope.open()) {
                assertTrue(RegionPacketFlushScope.defer(outer));
                try (var second = RegionPacketFlushScope.open()) {
                    assertTrue(RegionPacketFlushScope.defer(inner));
                }
                verify(inner).flushChannel();
                assertTrue(RegionPacketFlushScope.defer(outer));
                throw new IllegalStateException("tracker exception");
            }
        });
        verify(outer, atLeastOnce()).flushChannel();
        assertFalse(RegionPacketFlushScope.defer(outer));
    }

    @Test
    void rejectedFlushCannotStrandOtherConnections() {
        final Connection closing = mock(Connection.class), healthy = mock(Connection.class);
        final var scope = RegionPacketFlushScope.open();
        RegionPacketFlushScope.defer(closing);
        RegionPacketFlushScope.defer(healthy);
        RegionPacketFlushScope.defer(closing); // May have been flushed by the interval while mocking.
        doThrow(new RejectedExecutionException()).when(closing).flushChannel();
        assertThrows(RejectedExecutionException.class, scope::close);
        verify(healthy).flushChannel();
        assertFalse(RegionPacketFlushScope.defer(healthy));
    }

    @Test
    void closingAConnectionBeforeItsTrackerScopeDoesNotRetainFlushActions() throws Exception {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        connection.preparing = false;
        try (var scope = RegionPacketFlushScope.open()) {
            RegionPacketFlushScope.defer(connection);
        }
        final var field = Connection.class.getDeclaredField("pendingActions");
        field.setAccessible(true);
        assertTrue(((java.util.Queue<?>) field.get(connection)).isEmpty());
    }

    @Test
    void scopesRejectOutOfOrderCloseWithoutLosingTheOwnerScope() {
        final var outer = RegionPacketFlushScope.open();
        final var inner = RegionPacketFlushScope.open();
        assertThrows(IllegalStateException.class, outer::close);
        inner.close();
        outer.close();
        assertThrows(IllegalStateException.class, outer::close);
    }
}
