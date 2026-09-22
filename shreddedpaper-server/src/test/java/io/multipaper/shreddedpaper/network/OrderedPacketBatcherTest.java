package io.multipaper.shreddedpaper.network;

import io.netty.channel.EventLoop;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderedPacketBatcherTest {
    static final class Loop {
        final EventLoop executor = mock(EventLoop.class);
        final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        boolean reject;
        boolean running;

        Loop() {
            when(executor.inEventLoop()).thenAnswer(ignored -> running);
            doAnswer(invocation -> {
                if (reject) throw new RejectedExecutionException();
                queue.addLast(invocation.getArgument(0));
                return null;
            }).when(executor).execute(any(Runnable.class));
        }

        void drain() {
            running = true;
            try { while (!queue.isEmpty()) queue.removeFirst().run(); }
            finally { running = false; }
        }
    }

    @Test
    void boundedBatchesPreserveEveryPacketAndFlushOncePerBatch() {
        final Loop loop = new Loop();
        final List<Integer> packets = new ArrayList<>();
        final List<Integer> flushes = new ArrayList<>();
        final var batcher = new OrderedPacketBatcher<Integer>(packets::add, () -> flushes.add(packets.size()), failure -> fail(failure));
        for (int i = 0; i < 150; i++) batcher.send(loop.executor, i, true, false);
        assertEquals(3, loop.queue.size());
        loop.drain();
        assertEquals(java.util.stream.IntStream.range(0, 150).boxed().toList(), packets);
        assertEquals(List.of(64, 128, 150), flushes);
        batcher.send(loop.executor, 150, false, false);
        batcher.execute(loop.executor, () -> flushes.add(packets.size()));
        loop.drain();
        assertEquals(List.of(64, 128, 150, 151), flushes);
    }

    @Test
    void ordinarySendsProtocolChangesAndExplicitFlushesSealEarlierBatches() {
        final Loop loop = new Loop();
        final List<String> events = new ArrayList<>();
        final var batcher = new OrderedPacketBatcher<String>(events::add, () -> events.add("flush"), failure -> fail(failure));
        batcher.send(loop.executor, "old-protocol", false, false);
        batcher.execute(loop.executor, () -> events.add("configure"));
        batcher.send(loop.executor, "new-protocol", false, false);
        batcher.execute(loop.executor, () -> events.add("callback"));
        batcher.send(loop.executor, "last-packet", true, false);
        batcher.execute(loop.executor, () -> events.add("close"));
        loop.drain();
        assertEquals(List.of("old-protocol", "configure", "new-protocol", "callback", "last-packet", "flush", "close"), events);
    }

    @Test
    void writerAndPluginCallbacksRunWithoutProducerMonitorAndReentrantSendsStayImmediate() throws Exception {
        final Loop loop = new Loop();
        final List<Integer> seen = new ArrayList<>();
        final AtomicBoolean lockHeld = new AtomicBoolean();
        final var reference = new java.util.concurrent.atomic.AtomicReference<OrderedPacketBatcher<Integer>>();
        final var batcher = new OrderedPacketBatcher<Integer>(packet -> {
            lockHeld.set(lockHeld.get() || Thread.holdsLock(reference.get()));
            seen.add(packet);
            if (packet == 1) reference.get().send(loop.executor, 3, false, false);
        }, () -> {}, failure -> fail(failure));
        reference.set(batcher);
        batcher.send(loop.executor, 1, false, false);
        batcher.send(loop.executor, 2, false, false);
        loop.drain();
        assertFalse(lockHeld.get());
        assertEquals(List.of(1, 3, 2), seen);
    }

    @Test
    void aFailedWriteDoesNotDiscardLaterPacketsOrTheFlush() {
        final Loop loop = new Loop();
        final List<Integer> seen = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        final var batcher = new OrderedPacketBatcher<Integer>(packet -> {
            if (packet == 1) throw new IllegalStateException("encoder failure");
            seen.add(packet);
        }, () -> seen.add(-1), failures::add);
        batcher.send(loop.executor, 1, true, false);
        batcher.send(loop.executor, 2, false, false);
        loop.drain();
        assertEquals(List.of(2, -1), seen);
        assertEquals(1, failures.size());
    }

    @Test
    void rejectedSubmissionIsNotReplayedFromTheRecycler() {
        final Loop loop = new Loop();
        final List<Integer> seen = new ArrayList<>();
        final var batcher = new OrderedPacketBatcher<Integer>(seen::add, () -> {}, failure -> fail(failure));
        loop.reject = true;
        assertThrows(RejectedExecutionException.class, () -> batcher.send(loop.executor, 1, true, false));
        loop.reject = false;
        batcher.send(loop.executor, 2, true, false);
        loop.drain();
        assertEquals(List.of(2), seen);
    }

    @Test
    void concurrentRegionProducersKeepEachStreamsOrderAndDoNotLosePackets() throws Exception {
        final Loop loop = new Loop();
        final List<Integer> seen = new ArrayList<>();
        final var batcher = new OrderedPacketBatcher<Integer>(seen::add, () -> {}, failure -> fail(failure));
        final CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            final List<java.util.concurrent.Future<?>> producers = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                final int producer = p;
                producers.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1000; i++) batcher.send(loop.executor, producer * 1000 + i, i == 999, false);
                    return null;
                }));
            }
            start.countDown();
            for (var producer : producers) producer.get(10, TimeUnit.SECONDS);
        }
        loop.drain();
        assertEquals(4000, seen.size());
        for (int p = 0; p < 4; p++) {
            final int startValue = p * 1000;
            assertEquals(java.util.stream.IntStream.range(startValue, startValue + 1000).boxed().toList(),
                seen.stream().filter(value -> value / 1000 == startValue / 1000).toList());
        }
    }

    @Test
    void realEventLoopCanDrainAndRecycleWhileMultipleProducersAppend() throws Exception {
        final var loop = new io.netty.channel.DefaultEventLoop();
        final List<Integer> seen = new ArrayList<>();
        final var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        final var batcher = new OrderedPacketBatcher<Integer>(seen::add, () -> {}, failure::set);
        try (var pool = Executors.newFixedThreadPool(4)) {
            final List<java.util.concurrent.Future<?>> producers = new ArrayList<>();
            for (int p = 0; p < 4; p++) {
                final int producer = p;
                producers.add(pool.submit(() -> {
                    for (int i = 0; i < 1000; i++) batcher.send(loop, producer * 1000 + i, i % 64 == 63, true);
                }));
            }
            for (var producer : producers) producer.get(10, TimeUnit.SECONDS);
            final var finished = new java.util.concurrent.CompletableFuture<Void>();
            batcher.execute(loop, () -> finished.complete(null));
            finished.get(10, TimeUnit.SECONDS);
            assertNull(failure.get());
            assertEquals(4000, seen.size());
            for (int p = 0; p < 4; p++) {
                final int startValue = p * 1000;
                assertEquals(java.util.stream.IntStream.range(startValue, startValue + 1000).boxed().toList(),
                    seen.stream().filter(value -> value / 1000 == startValue / 1000).toList());
            }
        } finally {
            loop.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
}
