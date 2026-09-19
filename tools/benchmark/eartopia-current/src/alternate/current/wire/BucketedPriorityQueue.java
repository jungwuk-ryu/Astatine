/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Eartopia Current contributors
 * Compatible replacement for Alternate Current's intrusive update queue.
 */
package alternate.current.wire;

import java.util.AbstractQueue;
import java.util.Iterator;
import net.minecraft.world.level.redstone.Redstone;

/**
 * Sixteen independent, intrusive FIFO buckets, selected by a non-empty bit mask.
 *
 * <p>Contract: priorities are in Redstone.SIGNAL_MIN..SIGNAL_MAX; nodes belong
 * to at most one update queue, and all access is on that handler's owner thread.
 * Re-offering the same priority is a no-op. Reprioritizing appends to the target
 * bucket, matching Alternate Current, rather than retaining original age.
 * Node.priority stores the QUEUED priority; Node.priority() may already differ.
 *
 * <p>No additional Node fields, allocation per offer, world access, scheduling,
 * signal calculations, or changes to the order of equal-priority updates.
 */
public final class BucketedPriorityQueue extends AbstractQueue<Node> {
    private static final int OFFSET = -Redstone.SIGNAL_MIN;
    private static final int LEVELS = Redstone.SIGNAL_MAX + OFFSET + 1;

    private final Node[] heads = new Node[LEVELS];
    private final Node[] tails = new Node[LEVELS];
    private int nonEmpty;
    private int highest = -1;
    private int size;

    BucketedPriorityQueue() {
        if (LEVELS <= 0 || LEVELS > Integer.SIZE) {
            throw new IllegalStateException("Priority domain does not fit a 32-bit mask");
        }
    }

    @Override
    public boolean offer(Node node) {
        if (node == null) {
            throw new NullPointerException("node");
        }
        final int priority = node.priority();
        final int index = priority + OFFSET;
        if (index < 0 || index >= LEVELS) {
            throw new IllegalArgumentException("Redstone priority out of range: " + priority);
        }
        if (contains(node)) {
            if (node.priority == priority) {
                return false;
            }
            unlink(node);
        }

        final Node tail = tails[index];
        node.priority = priority;
        node.prev_node = tail;
        node.next_node = null;
        if (tail == null) {
            heads[index] = node;
            nonEmpty |= 1 << index;
            if (index > highest) {
                highest = index;
            }
        } else {
            tail.next_node = node;
        }
        tails[index] = node;
        size++;
        return true;
    }

    @Override
    public Node poll() {
        final int index = highest;
        if (index < 0) {
            return null;
        }
        final Node node = heads[index];
        final Node next = node.next_node;
        heads[index] = next;
        if (next == null) {
            tails[index] = null;
            nonEmpty &= ~(1 << index);
            highest = 31 - Integer.numberOfLeadingZeros(nonEmpty);
        } else {
            next.prev_node = null;
            node.next_node = null;
        }
        // node was a bucket head: prev_node was already null.
        size--;
        return node;
    }

    @Override
    public Node peek() {
        return highest < 0 ? null : heads[highest];
    }

    @Override
    public int size() {
        return size;
    }

    /** Identity membership, not Node.equals (which compares world positions). */
    public boolean contains(Node node) {
        return node != null && (node.prev_node != null || heads[node.priority + OFFSET] == node);
    }

    private void unlink(Node node) {
        final int index = node.priority + OFFSET;
        final Node prev = node.prev_node;
        final Node next = node.next_node;
        if (prev == null) {
            heads[index] = next;
        } else {
            prev.next_node = next;
        }
        if (next == null) {
            tails[index] = prev;
        } else {
            next.prev_node = prev;
        }
        if (heads[index] == null) {
            nonEmpty &= ~(1 << index);
            if (highest == index) {
                highest = 31 - Integer.numberOfLeadingZeros(nonEmpty);
            }
        }
        node.prev_node = null;
        node.next_node = null;
        size--;
    }

    @Override
    public void clear() {
        int occupied = nonEmpty;
        while (occupied != 0) {
            final int index = Integer.numberOfTrailingZeros(occupied);
            occupied &= occupied - 1;
            Node node = heads[index];
            while (node != null) {
                final Node next = node.next_node;
                node.prev_node = null;
                node.next_node = null;
                node = next;
            }
            heads[index] = null;
            tails[index] = null;
        }
        nonEmpty = 0;
        highest = -1;
        size = 0;
    }

    @Override
    public Iterator<Node> iterator() {
        throw new UnsupportedOperationException("Update queues do not support iteration");
    }
}
