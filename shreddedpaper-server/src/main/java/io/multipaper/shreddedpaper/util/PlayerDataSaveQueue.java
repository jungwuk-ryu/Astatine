package io.multipaper.shreddedpaper.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Orders writes for each player, including synchronous saves during shutdown. */
public final class PlayerDataSaveQueue {
    private final Executor executor;
    private final Map<String, CompletableFuture<Void>> pending = new HashMap<>();

    public PlayerDataSaveQueue(final Executor executor) {
        this.executor = executor;
    }

    public CompletableFuture<Void> submit(final String player, final Runnable write, final boolean async) {
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        final CompletableFuture<Void> previous;
        synchronized (this.pending) {
            previous = this.pending.put(player, completion);
        }
        // Publish the completion before scheduling: even a direct executor must see its predecessor.
        final CompletableFuture<Void> ready = previous == null
                ? CompletableFuture.completedFuture(null)
                : previous.handle((ignored, failure) -> null);
        try {
            ready.thenRunAsync(write, async ? this.executor : Runnable::run)
                    .whenComplete((ignored, failure) -> this.finish(player, completion, failure));
        } catch (RuntimeException | Error failure) {
            this.finish(player, completion, failure);
            throw failure;
        }
        if (!async) {
            completion.join();
        }
        return completion;
    }

    private void finish(final String player, final CompletableFuture<Void> completion, final Throwable failure) {
        synchronized (this.pending) {
            this.pending.remove(player, completion);
        }
        if (failure == null) {
            completion.complete(null);
        } else {
            completion.completeExceptionally(failure);
        }
    }

    /** A reconnect must not read the disk snapshot from before its last queued save. */
    public void await(final String player) {
        final CompletableFuture<Void> completion;
        synchronized (this.pending) {
            completion = this.pending.get(player);
        }
        if (completion != null) {
            // Failed writes already log their error; allow the existing disk recovery path to run.
            completion.handle((ignored, failure) -> null).join();
        }
    }
}
