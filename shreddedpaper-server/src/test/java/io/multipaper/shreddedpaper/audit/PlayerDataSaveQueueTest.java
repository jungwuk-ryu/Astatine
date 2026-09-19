package io.multipaper.shreddedpaper.audit;

import io.multipaper.shreddedpaper.util.PlayerDataSaveQueue;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PlayerDataSaveQueueTest {
    @Test
    void writesForOnePlayerCannotRunOutOfOrder() {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        PlayerDataSaveQueue queue = new PlayerDataSaveQueue(executor::add);
        List<Integer> written = new ArrayList<>();
        var first = queue.submit("player", () -> written.add(1), true);
        var second = queue.submit("player", () -> written.add(2), true);
        var third = queue.submit("player", () -> written.add(3), true);
        assertEquals(1, executor.size(), "only the first write may be dispatched");
        executor.removeLast().run();
        assertTrue(first.isDone());
        assertFalse(second.isDone());
        executor.removeLast().run();
        executor.removeLast().run();
        third.join();
        assertEquals(List.of(1, 2, 3), written);
    }

    @Test
    void shutdownSaveAndReconnectWaitForThePriorWrite() throws Exception {
        try (var executor = Executors.newFixedThreadPool(3)) {
            PlayerDataSaveQueue queue = new PlayerDataSaveQueue(executor);
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            List<Integer> written = java.util.Collections.synchronizedList(new ArrayList<>());
            queue.submit("player", () -> {
                started.countDown();
                await(release);
                written.add(1);
            }, true);
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var shutdown = executor.submit(() -> queue.submit("player", () -> written.add(2), false));
                var reconnect = executor.submit(() -> queue.await("player"));
                assertFalse(shutdown.isDone());
                assertFalse(reconnect.isDone());
                release.countDown();
                shutdown.get(5, TimeUnit.SECONDS);
                reconnect.get(5, TimeUnit.SECONDS);
                assertEquals(List.of(1, 2), written);
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void failureDoesNotPreventANewerSaveOrAnotherPlayer() {
        ArrayDeque<Runnable> executor = new ArrayDeque<>();
        PlayerDataSaveQueue queue = new PlayerDataSaveQueue(executor::add);
        var failed = queue.submit("first", () -> { throw new IllegalStateException("injected"); }, true);
        var recovered = queue.submit("first", () -> {}, true);
        var other = queue.submit("other", () -> {}, true);
        assertEquals(2, executor.size());
        executor.removeLast().run();
        assertTrue(other.isDone());
        assertFalse(recovered.isDone());
        executor.removeFirst().run();
        assertTrue(failed.isCompletedExceptionally());
        executor.removeFirst().run();
        recovered.join();
        queue.await("first");
    }

    @Test
    void executorRejectionDoesNotLeaveAStuckPendingSave() {
        PlayerDataSaveQueue queue = new PlayerDataSaveQueue(ignored -> { throw new RejectedExecutionException("injected"); });
        try {
            CompletableFuture<Void> rejected = queue.submit("player", () -> fail("must not execute"), true);
            assertTrue(rejected.isCompletedExceptionally());
        } catch (RejectedExecutionException expected) {
            // Executors may reject the dispatch synchronously.
        }
        queue.submit("player", () -> {}, false).join();
        queue.await("player");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
