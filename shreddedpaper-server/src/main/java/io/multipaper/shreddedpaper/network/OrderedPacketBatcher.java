package io.multipaper.shreddedpaper.network;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A connection's producers append directly to Netty's MPSC array queue. The event
 * loop drains at most 64 entries per task, then yields to other connections.
 * Packet writes, callbacks and barriers keep queue order without a producer lock.
 * No packet, encoder or plugin callback executes under a synchronization monitor.
 */
public final class OrderedPacketBatcher<T> {
    static final int MAX_PACKETS = 64;
    private final Queue<Object> pending = PlatformDependent.newMpscQueue();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Consumer<T> writer;
    private final Runnable flusher;
    private final Consumer<Throwable> failureHandler;
    private final Runnable drainTask = this::run;
    private volatile EventExecutor executor;
    private volatile long queuedNanos;

    public OrderedPacketBatcher(final Consumer<T> writer, final Runnable flusher, final Consumer<Throwable> failureHandler) {
        this.writer = writer;
        this.flusher = flusher;
        this.failureHandler = failureHandler;
    }

    public void send(final EventExecutor executor, final T packet, final boolean flush) {
        if (executor.inEventLoop()) {
            this.writer.accept(packet);
            if (flush) this.flusher.run();
            return;
        }
        // Flush intent is atomic with its packet. A concurrent close must not be
        // inserted between a write and a separately submitted flush marker.
        this.pending.offer(flush ? new FlushedPacket(packet) : packet);
        NetworkBatchMetrics.enqueuedPacket();
        this.schedule(executor);
    }

    /** Ordinary sends, callbacks, configuration and close are ordered queue entries. */
    public void execute(final EventExecutor executor, final Runnable action) {
        if (executor.inEventLoop()) {
            action.run();
            return;
        }
        this.pending.offer(new Action(action));
        this.schedule(executor);
    }

    private void schedule(final EventExecutor executor) {
        if (!this.scheduled.get() && this.scheduled.compareAndSet(false, true)) {
            this.executor = executor;
            this.queuedNanos = NetworkBatchMetrics.enabled() ? System.nanoTime() : 0L;
            try {
                // One wakeup per idle-to-active transition, including urgent work
                // appended behind deferred writes. No per-packet wakeup tasks.
                executor.execute(this.drainTask);
                NetworkBatchMetrics.scheduledBatch();
            } catch (RuntimeException failure) {
                // Rejection occurs at the event loop's shutdown boundary. Release
                // references instead of leaving an unscheduled connection backlog.
                this.pending.clear();
                this.scheduled.set(false);
                throw failure;
            }
        }
    }

    private void run() {
        final long started = NetworkBatchMetrics.enabled() ? System.nanoTime() : 0L;
        final long enqueued = this.queuedNanos;
        final int packets = this.drain(MAX_PACKETS);
        NetworkBatchMetrics.drainedBatch(packets, enqueued, started);
        if (!this.pending.isEmpty()) {
            this.queuedNanos = NetworkBatchMetrics.enabled() ? System.nanoTime() : 0L;
            try {
                this.executor.execute(this.drainTask);
                NetworkBatchMetrics.scheduledBatch();
            } catch (RuntimeException failure) {
                // Already accepted work must still complete when a shutting-down
                // event loop rejects the continuation. We are its consumer thread.
                while (!this.pending.isEmpty()) this.drain(MAX_PACKETS);
                this.scheduled.set(false);
                this.reportFailure(failure);
            }
            return;
        }
        this.scheduled.set(false);
        if (!this.pending.isEmpty()) {
            try {
                this.schedule(this.executor);
            } catch (RuntimeException failure) {
                this.reportFailure(failure);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int drain(final int limit) {
        boolean flush = false;
        int packets = 0;
        try {
            for (int i = 0; i < limit; i++) {
                final Object entry = this.pending.poll();
                if (entry == null) break;
                try {
                    if (entry instanceof Action action) {
                        if (flush) {
                            flush = false;
                            this.flushSafely();
                        }
                        action.runnable.run();
                        // Large writes and control callbacks remain scheduling
                        // boundaries, not a run of 64 large encodes in one task.
                        break;
                    } else {
                        packets++;
                        if (entry instanceof FlushedPacket flushed) {
                            flush = true;
                            this.writer.accept((T) flushed.packet);
                        } else {
                            this.writer.accept((T) entry);
                        }
                    }
                } catch (Throwable failure) {
                    this.reportFailure(failure);
                }
            }
        } finally {
            if (flush) this.flushSafely();
        }
        return packets;
    }

    private void flushSafely() {
        try { this.flusher.run(); }
        catch (Throwable failure) { this.reportFailure(failure); }
    }

    private void reportFailure(final Throwable failure) {
        try {
            this.failureHandler.accept(failure);
        } catch (Throwable handlerFailure) {
            org.slf4j.LoggerFactory.getLogger(OrderedPacketBatcher.class).error("Packet batch exception handler failed", handlerFailure);
        }
    }

    private record Action(Runnable runnable) {
    }

    private record FlushedPacket(Object packet) {
    }
}
