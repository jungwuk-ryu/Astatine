package io.multipaper.shreddedpaper.network;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import net.minecraft.network.Connection;

/** Flush grouping belongs to the executing tracker phase, not a global tick flag. */
public final class RegionPacketFlushScope implements AutoCloseable {
    private static final long FLUSH_INTERVAL_NANOS = 1_000_000L;
    private static final ThreadLocal<State> LOCAL = ThreadLocal.withInitial(State::new);
    private final IdentityHashMap<Connection, Boolean> connections = new IdentityHashMap<>();
    private RegionPacketFlushScope parent;
    private State state;
    private long nextFlushNanos;

    private RegionPacketFlushScope() {
    }

    public static RegionPacketFlushScope open() {
        final State state = LOCAL.get();
        RegionPacketFlushScope scope = state.free.pollFirst();
        if (scope == null) scope = new RegionPacketFlushScope();
        scope.state = state;
        scope.parent = state.current;
        scope.nextFlushNanos = System.nanoTime() + FLUSH_INTERVAL_NANOS;
        state.current = scope;
        return scope;
    }

    public static boolean defer(final Connection connection) {
        final RegionPacketFlushScope scope = LOCAL.get().current;
        if (scope == null) return false;
        final long now = System.nanoTime();
        if (now >= scope.nextFlushNanos) {
            scope.flush();
            scope.nextFlushNanos = now + FLUSH_INTERVAL_NANOS;
        }
        scope.connections.put(connection, Boolean.TRUE);
        return true;
    }

    private void flush() {
        RuntimeException firstFailure = null;
        try {
            for (final Connection connection : this.connections.keySet()) {
                try {
                    connection.flushChannel();
                } catch (RuntimeException failure) {
                    // A closing/rejected channel must not strand other connections.
                    if (firstFailure == null) firstFailure = failure;
                    else firstFailure.addSuppressed(failure);
                }
            }
        } finally {
            this.connections.clear();
        }
        if (firstFailure != null) throw firstFailure;
    }

    @Override
    public void close() {
        if (this.state == null || this.state != LOCAL.get() || this.state.current != this) {
            throw new IllegalStateException("Tracker flush scopes must close in owner-thread stack order");
        }
        final State state = this.state;
        state.current = this.parent;
        this.parent = null;
        this.state = null;
        try {
            this.flush();
        } finally {
            if (state.free.size() < 4) state.free.addFirst(this);
        }
    }

    private static final class State {
        RegionPacketFlushScope current;
        final ArrayDeque<RegionPacketFlushScope> free = new ArrayDeque<>();
    }
}
