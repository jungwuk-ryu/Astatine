package io.multipaper.shreddedpaper.threading;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import io.multipaper.shreddedpaper.region.RegionPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

/**
 * Read lock:
 *  - Only one thread can hold a read lock at a time.
 *  - Many servers can hold a read lock at the same time.
 *  - Good for tasks such as sending data to clients and loading chunks.
 * Write lock:
 *  - An extension of the read lock.
 *  - Only one server can hold a write lock at a time.
 *  - Important for tasks that modify the region, such as modifying blocks or entities.
 */
public class ShreddedPaperRegionLocker {

    public static final int REGION_LOCK_RADIUS = 1;

    private final ConcurrentHashMap<RegionPos, LockedRegion> lockedRegions = new ConcurrentHashMap<>();
    private final StampedLock globalLock = new StampedLock();

    private final ThreadLocal<Set<RegionPos>> localLocks = ThreadLocal.withInitial(ObjectOpenHashSet::new);
    private final ThreadLocal<Set<RegionPos>> readOnlyLocks = ThreadLocal.withInitial(ObjectOpenHashSet::new);
    private final ThreadLocal<Set<RegionPos>> writeLocks = ThreadLocal.withInitial(ObjectOpenHashSet::new);
    private final ThreadLocal<Set<ReadOnlyRegionLock>> activeReadLocks = ThreadLocal.withInitial(ObjectOpenHashSet::new);
    private final ThreadLocal<Set<RegionPos>> unmodifiableLocalLocks = ThreadLocal.withInitial(() -> Collections.unmodifiableSet(localLocks.get()));
    private final ThreadLocal<CurrentThreadWritePromotion> currentThreadWritePromotion = ThreadLocal.withInitial(CurrentThreadWritePromotion::new);

    /**
     * Checks if the current thread holds a read lock for the given region
     */
    public boolean hasLock(RegionPos regionPos) {
        return localLocks.get().contains(regionPos);
    }

    /**
     * Checks if the current thread holds a write lock for the given region.
     * This check is usually unnecessary, but ensures that there will be no
     * syncing conflicts with other servers.
     */
    public boolean hasWriteLock(RegionPos regionPos) {
        return writeLocks.get().contains(regionPos) || this.currentThreadWritePromotion.get().isActiveFor(regionPos);
    }

    /**
     * Temporarily treats every region already locked by this thread as writable.
     * This is only for vanilla boundary-mutation phases, such as chunk post-processing,
     * where a chunk in the owner region may legitimately update a neighboring chunk
     * that is held as an isolation lock.
     */
    public ScopedWriteAccess promoteCurrentThreadLocksToWrite() {
        final CurrentThreadWritePromotion promotion = this.currentThreadWritePromotion.get();
        promotion.open();
        return promotion;
    }

    public ScopedWriteAccess promoteLocalLocksToWrite(final Collection<RegionPos> regionPositions) {
        if (regionPositions.isEmpty()) {
            return () -> {};
        }

        final Thread owner = Thread.currentThread();
        final Set<RegionPos> local = this.localLocks.get();
        final Set<RegionPos> writes = this.writeLocks.get();
        final Set<RegionPos> readOnly = this.readOnlyLocks.get();
        final List<RegionPos> promoted = new ArrayList<>(regionPositions.size());

        for (final RegionPos regionPos : regionPositions) {
            if (!local.contains(regionPos)) {
                throw new IllegalStateException("Cannot promote unheld region lock to write access: " + regionPos);
            }
            if (!writes.contains(regionPos)) {
                writes.add(regionPos);
                readOnly.remove(regionPos);
                promoted.add(regionPos);
            }
        }

        if (promoted.isEmpty()) {
            return () -> {};
        }

        return () -> {
            if (owner != Thread.currentThread()) {
                throw new IllegalStateException("Cannot close write promotion from a different thread [expected=%s,got=%s]".formatted(owner, Thread.currentThread()));
            }
            for (final RegionPos regionPos : promoted) {
                writes.remove(regionPos);
                if (local.contains(regionPos)) {
                    readOnly.add(regionPos);
                }
            }
        };
    }

    /**
     * Returns an unmodifiable view of the locked regions for the current thread.
     */
    public Set<RegionPos> getLockedRegions() {
        // Use an unmodifiable view to ensure the underlying set doesn't get accidentally modified
        return unmodifiableLocalLocks.get();
    }

    /**
     * Returns an unmodifiable view of all locked regions across all threads.
     */
    public Set<Map.Entry<RegionPos, LockedRegion>> getAllLockedRegions() {
        return Collections.unmodifiableSet(lockedRegions.entrySet());
    }

    public int releaseCurrentThreadLocks() {
        final List<ReadOnlyRegionLock> activeLocks = new ArrayList<>(this.activeReadLocks.get());
        int released = 0;
        for (final ReadOnlyRegionLock lock : activeLocks) {
            released += lock.readLocks.size();
            lock.unlock();
        }

        // The active lock registry should cover all normal paths. Keep this as a
        // last-resort cleanup so a worker never returns to the scheduler queue
        // while still owning region entries.
        final Thread current = Thread.currentThread();
        final List<LockedRegion> leakedRegions = this.lockedRegions.entrySet().stream()
                .filter(entry -> entry.getValue().owner() == current)
                .map(Map.Entry::getValue)
                .toList();
        for (final LockedRegion lockedRegion : leakedRegions) {
            if (this.lockedRegions.remove(lockedRegion.regionPos(), lockedRegion)) {
                released++;
                lockedRegion.complete();
            }
        }

        this.localLocks.get().clear();
        this.readOnlyLocks.get().clear();
        this.writeLocks.get().clear();
        this.activeReadLocks.get().clear();
        this.currentThreadWritePromotion.get().forceClose();
        return released;
    }

    public RegionLock lockRegion(RegionPos regionPos) {
        int tries = 0;
        RegionLock lock;
        while ((lock = this.tryTakeLockNow(regionPos)) == null) {
            LockSupport.parkNanos(Math.min(100_000, 1_000 * (++tries)));
        }
        return lock;
    }

    /**
     * Returns whether any regions are currently locked.
     */
    public boolean areAnyLocked() {
        return !lockedRegions.isEmpty();
    }

    /**
     * Lock the region and run the runnable. If the region is already locked, wait until it is unlocked.
     * Can be called recursively and will respect existing locks created by the same thread.
     */
    public <T> T lockRegion(RegionPos regionPos, Supplier<T> runnableWithReturnValue) {
        RegionLock lock = this.lockRegion(regionPos);
        try {
            return runnableWithReturnValue.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Lock the region and run the runnable. If the region is already locked, wait until it is unlocked.
     * Can be called recursively and will respect existing locks created by the same thread.
     */
    public void lockRegion(RegionPos regionPos, Runnable runnable) {
        RegionLock lock = this.lockRegion(regionPos);
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to acquire the region lock immediately, if successful run the runnable.
     * If unsuccessful, return false and the runnable will not be run.
     * If the region is already locked, it will return unsuccessfully immediately instead of waiting to try to acquire the lock.
     * Can be called recursively and will respect existing locks created by the same thread.
     * This method will sync the lock with other servers.
     * @return true if the lock was acquired and the runnable was run, false if the runnable was not run
     */
    public boolean tryLockNow(RegionPos centerPos, Runnable ifSuccess) {
        RegionLock lock = this.tryTakeLockNow(centerPos);
        if (lock == null) {
            return false;
        }

        try {
            ifSuccess.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean tryLockNow(Collection<RegionPos> regionPositions, Runnable ifSuccess) {
        RegionLock lock = this.internalTryTakeExactLockNow(regionPositions);
        if (lock == null) {
            return false;
        }

        try {
            ifSuccess.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean tryLockNow(Collection<RegionPos> writeRegionPositions, Collection<RegionPos> isolationRegionPositions, Runnable ifSuccess) {
        RegionLock lock = this.internalTryTakeExactLockNow(writeRegionPositions, isolationRegionPositions);
        if (lock == null) {
            return false;
        }

        try {
            ifSuccess.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to acquire the region lock immediately, if successful run the runnable.
     * If unsuccessful, return false and the runnable will not be run.
     * If the region is already locked, it will return unsuccessfully immediately instead of waiting to try to acquire the lock.
     * Can be called recursively and will respect existing locks created by the same thread.
     * The specified region must not be modified within this lock. This method will not sync the lock with other servers.
     * return true if the lock was acquired and the runnable was run, false if the runnable was not run
     */
    public boolean tryReadOnlyLockNow(RegionPos centerPos, Runnable ifSuccess) {
        final RegionLock lock = this.tryTakeReadOnlyLockNow(centerPos);
        if (lock == null) {
            return false;
        }

        try {
            ifSuccess.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean tryReadOnlyLockNow(Collection<RegionPos> regionPositions, Runnable ifSuccess) {
        final RegionLock lock = this.internalTryTakeExactReadOnlyLockNow(regionPositions);
        if (lock == null) {
            return false;
        }

        try {
            ifSuccess.run();
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    public RegionLock tryTakeLockNow(RegionPos centerPos) {
        return internalTryTakeLockNow(centerPos, REGION_LOCK_RADIUS);
    }

    @Nullable
    public WriteRegionLock internalTryTakeLockNow(RegionPos centerPos, int lockRadius) {
        final ReadOnlyRegionLock lock = internalTryTakeReadOnlyLockNow(centerPos, lockRadius);
        return lock == null ? null : new WriteRegionLock(lock);
    }

    @Nullable
    public WriteRegionLock internalTryTakeExactLockNow(Collection<RegionPos> regionPositions) {
        final ReadOnlyRegionLock lock = internalTryTakeExactReadOnlyLockNow(regionPositions);
        return lock == null ? null : new WriteRegionLock(lock);
    }

    @Nullable
    public WriteRegionLock internalTryTakeExactLockNow(long[] sortedRegionKeys) {
        final ReadOnlyRegionLock lock = internalTryTakeExactReadOnlyLockNow(sortedRegionKeys);
        return lock == null ? null : new WriteRegionLock(lock);
    }

    @Nullable
    public WriteRegionLock internalTryTakeExactLockNow(Collection<RegionPos> writeRegionPositions, Collection<RegionPos> isolationRegionPositions) {
        if (writeRegionPositions.isEmpty() || isolationRegionPositions.isEmpty()) {
            return null;
        }
        final ReadOnlyRegionLock lock = internalTryTakeExactReadOnlyLockNow(isolationRegionPositions);
        return lock == null ? null : new WriteRegionLock(lock, sortedUniqueRegions(writeRegionPositions));
    }

    @Nullable
    public WriteRegionLock internalTryTakeExactLockNow(long[] sortedWriteRegionKeys, long[] sortedIsolationRegionKeys) {
        if (sortedWriteRegionKeys.length == 0 || sortedIsolationRegionKeys.length == 0) {
            return null;
        }
        requireSortedUniqueRegionKeys(sortedWriteRegionKeys);
        final ReadOnlyRegionLock lock = internalTryTakeExactReadOnlyLockNow(sortedIsolationRegionKeys);
        return lock == null ? null : new WriteRegionLock(lock, sortedWriteRegionKeys);
    }

    @Nullable
    public RegionLock tryTakeReadOnlyLockNow(RegionPos centerPos) {
        return internalTryTakeReadOnlyLockNow(centerPos, REGION_LOCK_RADIUS);
    }

    @Nullable
    public ReadOnlyRegionLock internalTryTakeReadOnlyLockNow(RegionPos centerPos, int lockRadius) {
        final ReadOnlyRegionLock lock = new ReadOnlyRegionLock((lockRadius * 2 + 1) * (lockRadius * 2 + 1));

        for (int x = -lockRadius; x <= lockRadius; x++) {
            for (int z = -lockRadius; z <= lockRadius; z++) {
                RegionPos regionPos = x == 0 && z == 0 ? centerPos : new RegionPos(centerPos.x + x, centerPos.z + z);
                if (!lock.tryLockRegion(regionPos)) {
                    // Failed to take lock, abort
                    lock.unlock();
                    return null;
                }
            }
        }

        return lock;
    }

    @Nullable
    public ReadOnlyRegionLock internalTryTakeExactReadOnlyLockNow(Collection<RegionPos> regionPositions) {
        final List<RegionPos> sortedRegions = sortedUniqueRegions(regionPositions);
        final ReadOnlyRegionLock lock = new ReadOnlyRegionLock(sortedRegions.size());

        for (final RegionPos regionPos : sortedRegions) {
            if (!lock.tryLockRegion(regionPos)) {
                lock.unlock();
                return null;
            }
        }

        return lock;
    }

    @Nullable
    public ReadOnlyRegionLock internalTryTakeExactReadOnlyLockNow(long[] sortedRegionKeys) {
        requireSortedUniqueRegionKeys(sortedRegionKeys);
        final ReadOnlyRegionLock lock = new ReadOnlyRegionLock(sortedRegionKeys.length);

        for (final long regionKey : sortedRegionKeys) {
            if (!lock.tryLockRegion(new RegionPos(regionKey))) {
                lock.unlock();
                return null;
            }
        }

        return lock;
    }

    @Nullable
    public CompletableFuture<Void> onUnlock(RegionPos centerPos, Supplier<CompletableFuture<Void>> nextTask) {
        for (int x = -REGION_LOCK_RADIUS; x <= REGION_LOCK_RADIUS; x++) {
            for (int z = -REGION_LOCK_RADIUS; z <= REGION_LOCK_RADIUS; z++) {
                RegionPos regionPos = x == 0 && z == 0 ? centerPos : new RegionPos(centerPos.x + x, centerPos.z + z);
                LockedRegion lockedRegion = this.lockedRegions.get(regionPos);
                if (lockedRegion != null) {
                    return lockedRegion.onUnlock(nextTask);
                }
            }
        }

        return nextTask.get();
    }

    @Nullable
    public CompletableFuture<Void> onUnlock(Collection<RegionPos> regionPositions, Supplier<CompletableFuture<Void>> nextTask) {
        for (final RegionPos regionPos : sortedUniqueRegions(regionPositions)) {
            LockedRegion lockedRegion = this.lockedRegions.get(regionPos);
            if (lockedRegion != null) {
                return lockedRegion.onUnlock(nextTask);
            }
        }

        return nextTask.get();
    }

    private static List<RegionPos> sortedUniqueRegions(Collection<RegionPos> regionPositions) {
        if (regionPositions.isEmpty()) {
            throw new IllegalArgumentException("regionPositions must not be empty");
        }

        final List<RegionPos> sortedRegions = new ArrayList<>(regionPositions);
        sortedRegions.sort(Comparator.comparingLong(RegionPos::toLong));
        for (int i = sortedRegions.size() - 1; i > 0; i--) {
            if (sortedRegions.get(i).longKey == sortedRegions.get(i - 1).longKey) {
                sortedRegions.remove(i);
            }
        }
        return sortedRegions;
    }

    private static void requireSortedUniqueRegionKeys(final long[] sortedRegionKeys) {
        if (sortedRegionKeys.length == 0) {
            throw new IllegalArgumentException("regionPositions must not be empty");
        }
        long previous = sortedRegionKeys[0];
        for (int i = 1; i < sortedRegionKeys.length; i++) {
            final long current = sortedRegionKeys[i];
            if (current <= previous) {
                throw new IllegalArgumentException("regionPositions must be sorted and unique");
            }
            previous = current;
        }
    }

    /**
     * globalLock.writeLock() will claim all regions
     */
    public StampedLock globalLock() {
        return this.globalLock;
    }

    public interface RegionLock {
        Collection<RegionPos> lockedRegions();
        Thread owner();
        void unlock();
    }

    public interface ScopedWriteAccess extends AutoCloseable {
        @Override
        void close();
    }

    private final class CurrentThreadWritePromotion implements ScopedWriteAccess {
        private Thread owner;
        private int depth;

        private void open() {
            final Thread current = Thread.currentThread();
            if (this.depth == 0) {
                this.owner = current;
            } else if (this.owner != current) {
                throw new IllegalStateException("Cannot share write promotion across threads [expected=%s,got=%s]".formatted(this.owner, current));
            }
            this.depth++;
        }

        private boolean isActiveFor(final RegionPos regionPos) {
            return this.depth > 0 && ShreddedPaperRegionLocker.this.localLocks.get().contains(regionPos);
        }

        @Override
        public void close() {
            final Thread current = Thread.currentThread();
            if (this.depth <= 0) {
                throw new IllegalStateException("Cannot close inactive write promotion");
            }
            if (this.owner != current) {
                throw new IllegalStateException("Cannot close write promotion from a different thread [expected=%s,got=%s]".formatted(this.owner, current));
            }
            if (--this.depth == 0) {
                this.owner = null;
            }
        }

        private void forceClose() {
            this.depth = 0;
            this.owner = null;
        }
    }

    public class ReadOnlyRegionLock implements RegionLock {
        private final List<LockedRegion> readLocks;
        private final Thread thread;
        private long globalLockStamp = 0;
        private boolean registered;
        private boolean unlocked;

        private ReadOnlyRegionLock(int expectedRegionCount) {
            this.thread = Thread.currentThread();
            this.readLocks = new ArrayList<>(expectedRegionCount);
        }

        protected boolean tryLockRegion(RegionPos regionPos) {
            if (this.thread != Thread.currentThread()) {
                throw new IllegalStateException("Cannot lock a region from a different thread [expected=%s,got=%s]".formatted(thread, Thread.currentThread()));
            }

            if (globalLockStamp == 0 && (globalLockStamp = ShreddedPaperRegionLocker.this.globalLock.tryReadLock()) == 0) {
                return false;
            }
            this.registerActiveLock();

            LockedRegion lockedRegion = ShreddedPaperRegionLocker.this.lockedRegions.compute(regionPos, (k, prevValue) -> {
                if (prevValue == null) {
                    // This region is unlocked, let's lock it
                    ShreddedPaperRegionLocker.this.localLocks.get().add(regionPos);
                    ShreddedPaperRegionLocker.this.readOnlyLocks.get().add(regionPos);
                    LockedRegion newLockedRegion = new LockedRegion(regionPos, this.thread);
                    this.readLocks.add(newLockedRegion);
                    return newLockedRegion;
                } else {
                    // This region is already locked, it could be already locked by us or someone else
                    return prevValue;
                }
            });

            return lockedRegion.owner() == this.owner();
        }

        public Collection<RegionPos> lockedRegions() {
            return this.readLocks.stream().map(LockedRegion::regionPos).toList();
        }

        public Thread owner() {
            return this.thread;
        }

        public void unlock() {
            if (this.thread != Thread.currentThread()) {
                throw new IllegalStateException("Cannot unlock a lock from a different thread [expected=%s,got=%s]".formatted(thread, Thread.currentThread()));
            }
            if (this.unlocked) {
                return;
            }
            this.unlocked = true;

            for (LockedRegion lockedRegion : this.readLocks) {
                ShreddedPaperRegionLocker.this.lockedRegions.remove(lockedRegion.regionPos(), lockedRegion);
                ShreddedPaperRegionLocker.this.localLocks.get().remove(lockedRegion.regionPos());
                ShreddedPaperRegionLocker.this.readOnlyLocks.get().remove(lockedRegion.regionPos());
            }

            for (LockedRegion lockedRegion : this.readLocks) {
                lockedRegion.complete();
            }

            if (this.globalLockStamp != 0) {
                ShreddedPaperRegionLocker.this.globalLock.unlockRead(this.globalLockStamp);
                this.globalLockStamp = 0;
            }
            if (this.registered) {
                ShreddedPaperRegionLocker.this.activeReadLocks.get().remove(this);
                this.registered = false;
            }
        }

        private void registerActiveLock() {
            if (!this.registered) {
                ShreddedPaperRegionLocker.this.activeReadLocks.get().add(this);
                this.registered = true;
            }
        }

    }

    public class WriteRegionLock implements RegionLock {
        private final List<RegionPos> writeLocks;
        private final ReadOnlyRegionLock superLock;

        private WriteRegionLock(ReadOnlyRegionLock superLock) {
            this(superLock, superLock.lockedRegions());
        }

        private WriteRegionLock(ReadOnlyRegionLock superLock, Collection<RegionPos> writeRegions) {
            this.superLock = superLock;

            final Set<RegionPos> local = ShreddedPaperRegionLocker.this.localLocks.get();
            final Set<RegionPos> writes = ShreddedPaperRegionLocker.this.writeLocks.get();
            this.writeLocks = new ArrayList<>(writeRegions.size());
            for (final RegionPos writeRegion : writeRegions) {
                if (local.contains(writeRegion)) {
                    if (writes.contains(writeRegion)) {
                        continue;
                    }
                    this.writeLocks.add(writeRegion);
                }
            }
            writes.addAll(this.writeLocks);
            final Set<RegionPos> readOnly = ShreddedPaperRegionLocker.this.readOnlyLocks.get();
            for (final RegionPos writeRegion : this.writeLocks) {
                readOnly.remove(writeRegion);
            }
        }

        private WriteRegionLock(ReadOnlyRegionLock superLock, long[] sortedWriteRegionKeys) {
            this.superLock = superLock;

            final Set<RegionPos> local = ShreddedPaperRegionLocker.this.localLocks.get();
            final Set<RegionPos> writes = ShreddedPaperRegionLocker.this.writeLocks.get();
            this.writeLocks = new ArrayList<>(sortedWriteRegionKeys.length);
            for (final long writeRegionKey : sortedWriteRegionKeys) {
                final RegionPos writeRegion = new RegionPos(writeRegionKey);
                if (local.contains(writeRegion)) {
                    if (writes.contains(writeRegion)) {
                        continue;
                    }
                    this.writeLocks.add(writeRegion);
                }
            }
            writes.addAll(this.writeLocks);
            final Set<RegionPos> readOnly = ShreddedPaperRegionLocker.this.readOnlyLocks.get();
            for (final RegionPos writeRegion : this.writeLocks) {
                readOnly.remove(writeRegion);
            }
        }

        @Override
        public Collection<RegionPos> lockedRegions() {
            return Collections.unmodifiableCollection(this.writeLocks);
        }

        @Override
        public Thread owner() {
            return this.superLock.owner();
        }

        @Override
        public void unlock() {
            final Set<RegionPos> writes = ShreddedPaperRegionLocker.this.writeLocks.get();
            final Set<RegionPos> readOnly = ShreddedPaperRegionLocker.this.readOnlyLocks.get();
            for (final RegionPos writeRegion : this.writeLocks) {
                writes.remove(writeRegion);
                readOnly.add(writeRegion);
            }
            this.superLock.unlock();
        }
    }

    public static class LockedRegion {
        private final RegionPos regionPos;
        private final Thread owner;
        private final CompletableFuture<Void> headUnlockFuture = new CompletableFuture<>();
        private CompletableFuture<Void> tailUnlockFuture = headUnlockFuture;

        private LockedRegion(RegionPos regionPos, Thread owner) {
            this.regionPos = regionPos;
            this.owner = owner;
        }

        public RegionPos regionPos() {
            return regionPos;
        }

        public Thread owner() {
            return owner;
        }

        public void complete() {
            headUnlockFuture.complete(null);
        }

        public synchronized CompletableFuture<Void> onUnlock(Supplier<CompletableFuture<Void>> nextTask) {
            return tailUnlockFuture = tailUnlockFuture.handle((ignored, throwable) -> null).thenCompose(v -> nextTask.get());
        }
    }

}
