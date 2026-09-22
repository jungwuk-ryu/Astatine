package io.multipaper.shreddedpaper.network;

import io.netty.util.concurrent.AbstractEventExecutor;
import io.netty.util.concurrent.EventExecutor;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Coalesces small writes into bounded event-loop tasks. Barriers seal the current
 * task, so later writes cannot move ahead of a protocol change or explicit flush.
 * No encoder, callback or plugin code is invoked while holding the producer lock.
 */
public final class OrderedPacketBatcher<T> {
    static final int MAX_PACKETS = 64;
    private static final int MAX_RECYCLED_BATCHES = 4;
    private static final Runnable WAKEUP = () -> {};
    private final Consumer<T> writer;
    private final Runnable flusher;
    private final Consumer<Throwable> failureHandler;
    private Batch pending;
    private Batch recycled;
    private int recycledCount;

    public OrderedPacketBatcher(final Consumer<T> writer, final Runnable flusher, final Consumer<Throwable> failureHandler) {
        this.writer = writer;
        this.flusher = flusher;
        this.failureHandler = failureHandler;
    }

    public void send(final EventExecutor executor, final T packet, final boolean flush, final boolean lazy) {
        if (executor.inEventLoop()) {
            this.seal();
            this.writer.accept(packet);
            if (flush) this.flusher.run();
            return;
        }
        synchronized (this) {
            Batch batch = this.pending;
            if (batch != null && batch.executor == executor && batch.size < MAX_PACKETS) {
                if (flush && !batch.wakeupScheduled) {
                    executor.execute(WAKEUP);
                    batch.wakeupScheduled = true;
                }
                batch.add(packet, flush);
                return;
            }
            batch = this.takeBatch();
            batch.executor = executor;
            batch.firstQueuedNanos = NetworkBatchMetrics.enabled() ? System.nanoTime() : 0L;
            batch.add(packet, flush);
            this.pending = batch;
            try {
                if (!flush && lazy && executor instanceof AbstractEventExecutor abstractExecutor) {
                    abstractExecutor.lazyExecute(batch);
                } else {
                    batch.wakeupScheduled = true;
                    executor.execute(batch);
                }
                NetworkBatchMetrics.scheduledBatch();
            } catch (RuntimeException failure) {
                this.pending = null;
                batch.clear();
                this.recycle(batch);
                throw failure;
            }
        }
    }

    /** Register non-batch work at the same ordering boundary as packet submission. */
    public void execute(final EventExecutor executor, final Runnable action) {
        this.execute(executor, action, false);
    }

    public void execute(final EventExecutor executor, final Runnable action, final boolean lazy) {
        if (executor.inEventLoop()) {
            this.seal();
            action.run();
        } else {
            synchronized (this) {
                this.pending = null;
                if (lazy && executor instanceof AbstractEventExecutor abstractExecutor) {
                    abstractExecutor.lazyExecute(action);
                } else {
                    executor.execute(action);
                }
            }
        }
    }

    private synchronized void seal() {
        this.pending = null;
    }

    private Batch takeBatch() {
        if (this.recycled == null) {
            return new Batch();
        }
        final Batch result = this.recycled;
        this.recycled = result.next;
        this.recycledCount--;
        result.next = null;
        return result;
    }

    private void recycle(final Batch batch) {
        batch.executor = null;
        if (this.recycledCount < MAX_RECYCLED_BATCHES) {
            batch.next = this.recycled;
            this.recycled = batch;
            this.recycledCount++;
        }
    }

    private final class Batch implements Runnable {
        private Object[] packets = new Object[8];
        private int size;
        private boolean flush;
        private boolean wakeupScheduled;
        private EventExecutor executor;
        private long firstQueuedNanos;
        private Batch next;

        void add(final T packet, final boolean flush) {
            if (this.size == this.packets.length) {
                this.packets = Arrays.copyOf(this.packets, Math.min(MAX_PACKETS, this.size * 2));
            }
            this.packets[this.size++] = packet;
            this.flush |= flush;
        }

        void clear() {
            Arrays.fill(this.packets, 0, this.size, null);
            this.size = 0;
            this.flush = false;
            this.wakeupScheduled = false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void run() {
            final int count;
            final boolean flushAtEnd;
            synchronized (OrderedPacketBatcher.this) {
                if (OrderedPacketBatcher.this.pending == this) {
                    OrderedPacketBatcher.this.pending = null;
                }
                count = this.size;
                flushAtEnd = this.flush;
            }
            NetworkBatchMetrics.drainedBatch(count, this.firstQueuedNanos);
            try {
                for (int i = 0; i < count; i++) {
                    final T packet = (T) this.packets[i];
                    this.packets[i] = null;
                    try {
                        OrderedPacketBatcher.this.writer.accept(packet);
                    } catch (Throwable failure) {
                        // Netty normally isolates failures between separate tasks. Preserve
                        // that behavior inside a batch so later packets are not silently lost.
                        try {
                            OrderedPacketBatcher.this.failureHandler.accept(failure);
                        } catch (Throwable handlerFailure) {
                            org.slf4j.LoggerFactory.getLogger(OrderedPacketBatcher.class).error("Packet batch exception handler failed", handlerFailure);
                        }
                    }
                }
            } finally {
                try {
                    if (flushAtEnd) OrderedPacketBatcher.this.flusher.run();
                } finally {
                    this.clear();
                    synchronized (OrderedPacketBatcher.this) {
                        OrderedPacketBatcher.this.recycle(this);
                    }
                }
            }
        }
    }
}
