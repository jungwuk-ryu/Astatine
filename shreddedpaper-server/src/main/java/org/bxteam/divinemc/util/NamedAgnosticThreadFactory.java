package org.bxteam.divinemc.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class NamedAgnosticThreadFactory<T extends Thread> implements ThreadFactory {

    @FunctionalInterface
    public interface ThreadCreator<T extends Thread> {
        T create(ThreadGroup group, Runnable runnable, String name);
    }

    private final String namePrefix;
    private final ThreadCreator<T> threadCreator;
    private final int priority;
    private final ThreadGroup threadGroup;
    private final AtomicInteger counter = new AtomicInteger();

    public NamedAgnosticThreadFactory(final String namePrefix, final ThreadCreator<T> threadCreator, final int priority) {
        this.namePrefix = namePrefix;
        this.threadCreator = threadCreator;
        this.priority = priority;
        this.threadGroup = Thread.currentThread().getThreadGroup();
    }

    @Override
    public Thread newThread(final Runnable runnable) {
        final T thread = this.threadCreator.create(this.threadGroup, runnable, this.namePrefix + " #" + this.counter.incrementAndGet());
        thread.setPriority(this.priority);
        return thread;
    }
}
