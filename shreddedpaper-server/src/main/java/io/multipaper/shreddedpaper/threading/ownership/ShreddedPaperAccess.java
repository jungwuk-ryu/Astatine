package io.multipaper.shreddedpaper.threading.ownership;

import io.multipaper.shreddedpaper.ShreddedPaper;
import io.multipaper.shreddedpaper.config.ShreddedPaperConfiguration;
import io.multipaper.shreddedpaper.region.RegionPos;
import io.multipaper.shreddedpaper.threading.ShreddedPaperTickThread;
import io.multipaper.shreddedpaper.threading.region.RegionTaskClass;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class ShreddedPaperAccess {

    private static final int MAX_OWNER_HANDOFF_REQUEUES = 8;
    private static final int MAX_LOADED_READ_FALLBACK_SAMPLES = 8;
    private static final int LOADED_READ_FALLBACK_SAMPLE_MASK = MAX_LOADED_READ_FALLBACK_SAMPLES - 1;
    private static final int LOADED_READ_FALLBACK_SAMPLE_INTERVAL = 256;
    private static final LongAdder LOADED_READ_FALLBACKS = new LongAdder();
    private static final LongAdder OWNER_HANDOFFS = new LongAdder();
    private static final LongAdder OWNER_HANDOFF_REQUEUES = new LongAdder();
    private static final LongAdder OWNER_HANDOFF_REJECTIONS = new LongAdder();
    private static final LongAdder PREFETCH_FAILURES = new LongAdder();
    private static final AtomicLong LOADED_READ_FALLBACK_SAMPLE_ORDINAL = new AtomicLong();
    private static final AtomicLong LOADED_READ_FALLBACK_SAMPLE_SEQUENCE = new AtomicLong();
    private static final AtomicReferenceArray<LoadedReadFallbackSample> LOADED_READ_FALLBACK_SAMPLES =
            new AtomicReferenceArray<>(MAX_LOADED_READ_FALLBACK_SAMPLES);

    private ShreddedPaperAccess() {
    }

    public static boolean isOwned(final ServerLevel level, final BlockPos pos) {
        return isOwned(level, ChunkPos.of(pos));
    }

    public static boolean isOwned(final ServerLevel level, final ChunkPos pos) {
        if (!independentRegionTicking()) {
            return true;
        }
        return ShreddedPaper.isSync(level, pos);
    }

    public static BlockState getBlockStateIfSafe(final Level level, final BlockPos pos, final OwnershipIntent intent) {
        return getBlockStateIfSafe(level, pos, Blocks.AIR.defaultBlockState(), intent);
    }

    public static BlockState getBlockStateIfSafe(
            final Level level,
            final BlockPos pos,
            final BlockState fallback,
            final OwnershipIntent intent
    ) {
        requireLoadedReadIntent(intent);
        if (!canReadLoadedData(level, pos)) {
            recordLoadedReadFallback("block-unowned", level, pos);
            return fallback;
        }

        final BlockState state = level.getBlockStateIfLoaded(pos);
        if (state == null) {
            recordLoadedReadFallback("block-unloaded", level, pos);
            return fallback;
        }
        return state;
    }

    public static FluidState getFluidStateIfSafe(final Level level, final BlockPos pos, final OwnershipIntent intent) {
        return getFluidStateIfSafe(level, pos, Fluids.EMPTY.defaultFluidState(), intent);
    }

    public static FluidState getFluidStateIfSafe(
            final Level level,
            final BlockPos pos,
            final FluidState fallback,
            final OwnershipIntent intent
    ) {
        requireLoadedReadIntent(intent);
        if (!canReadLoadedData(level, pos)) {
            recordLoadedReadFallback("fluid-unowned", level, pos);
            return fallback;
        }

        final FluidState state = level.getFluidIfLoaded(pos);
        if (state == null) {
            recordLoadedReadFallback("fluid-unloaded", level, pos);
            return fallback;
        }
        return state;
    }

    public static BlockEntity getBlockEntityIfOwnedOrLoaded(final Level level, final BlockPos pos) {
        if (!level.isInValidBounds(pos) || !canReadLoadedData(level, pos)) {
            recordLoadedReadFallback("block-entity-unowned-or-invalid", level, pos);
            return null;
        }

        final LevelChunk chunk = level.getChunkIfLoaded(pos);
        if (chunk == null) {
            recordLoadedReadFallback("block-entity-unloaded", level, pos);
            return null;
        }
        return chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    }

    public static OwnerTaskResult setBlockEntityIfOwnedOrDefer(
            final ServerLevel level,
            final BlockEntity blockEntity,
            final RegionTaskClass taskClass
    ) {
        final BlockPos pos = blockEntity.getBlockPos();
        if (!level.isInValidBounds(pos)) {
            return OwnerTaskResult.CHUNK_UNAVAILABLE;
        }

        if (!isOwned(level, pos)) {
            return runOrDeferToOwner(level, pos, taskClass, () -> writeLoadedBlockEntity(level, blockEntity));
        }

        return writeLoadedBlockEntity(level, blockEntity);
    }

    public static OwnerTaskResult runOrDeferToOwner(
            final ServerLevel level,
            final BlockPos pos,
            final RegionTaskClass taskClass,
            final Runnable task
    ) {
        return runOrDeferToOwner(level, ChunkPos.of(pos), taskClass, task);
    }

    public static OwnerTaskResult runOrDeferToOwner(
            final ServerLevel level,
            final ChunkPos pos,
            final RegionTaskClass taskClass,
            final Runnable task
    ) {
        return runOrDeferToOwner(level, pos, ownerHandoffTaskClass(taskClass), task, 0, 0L);
    }

    private static OwnerTaskResult runOrDeferToOwner(
            final ServerLevel level,
            final ChunkPos pos,
            final RegionTaskClass taskClass,
            final Runnable task,
            final int requeues,
            final long delayTicks
    ) {
        if (isOwned(level, pos)) {
            task.run();
            return OwnerTaskResult.RAN_INLINE;
        }

        OWNER_HANDOFFS.increment();
        final RegionPos regionPos = RegionPos.forChunk(pos);
        final boolean queued = level.getChunkSource().tickingRegions.scheduleTask(regionPos, () -> {
            if (isOwned(level, pos)) {
                task.run();
                return;
            }

            OWNER_HANDOFF_REQUEUES.increment();
            if (requeues >= MAX_OWNER_HANDOFF_REQUEUES) {
                OWNER_HANDOFF_REJECTIONS.increment();
                throw new IllegalStateException(
                        "Cross-owner handoff exceeded requeue limit; world=" + level.getWorld().getName()
                                + ", chunk=[" + pos.x + "," + pos.z + "]"
                                + ", taskClass=" + taskClass
                );
            }
            runOrDeferToOwner(level, pos, taskClass, task, requeues + 1, 1L);
        }, delayTicks, taskClass);

        if (!queued) {
            OWNER_HANDOFF_REJECTIONS.increment();
            return OwnerTaskResult.REJECTED;
        }
        return OwnerTaskResult.QUEUED_TO_OWNER;
    }

    private static RegionTaskClass ownerHandoffTaskClass(final RegionTaskClass taskClass) {
        return taskClass == RegionTaskClass.CRITICAL_SYSTEM ? RegionTaskClass.OWNER_HANDOFF : taskClass;
    }

    public static OwnerTaskResult runWithWriteLockIfHeldOrDeferToOwner(
            final ServerLevel level,
            final BlockPos pos,
            final RegionTaskClass taskClass,
            final Runnable task
    ) {
        return runWithWriteLockIfHeldOrDeferToOwner(level, ChunkPos.of(pos), taskClass, task);
    }

    public static OwnerTaskResult runWithWriteLockIfHeldOrDeferToOwner(
            final ServerLevel level,
            final ChunkPos pos,
            final RegionTaskClass taskClass,
            final Runnable task
    ) {
        if (isOwned(level, pos)) {
            task.run();
            return OwnerTaskResult.RAN_INLINE;
        }

        final RegionPos regionPos = RegionPos.forChunk(pos);
        if (independentRegionTicking()
                && ShreddedPaperTickThread.isShreddedPaperTickThread()
                && level.chunkScheduler.getRegionLocker().hasLock(regionPos)) {
            try (var ignored = level.chunkScheduler.getRegionLocker().promoteLocalLocksToWrite(Collections.singletonList(regionPos))) {
                task.run();
            }
            return OwnerTaskResult.RAN_INLINE;
        }

        return runOrDeferToOwner(level, pos, taskClass, task);
    }

    public static CompletableFuture<OwnerTaskResult> prefetchFullChunkThenOwner(
            final ServerLevel level,
            final ChunkPos pos,
            final boolean gen,
            final RegionTaskClass taskClass,
            final Runnable task
    ) {
        final CompletableFuture<ChunkResult<ChunkAccess>> future =
                level.getChunkSource().getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, gen);

        return future.handle((result, throwable) -> {
            if (throwable != null || result == null || !result.isSuccess()) {
                PREFETCH_FAILURES.increment();
                return OwnerTaskResult.CHUNK_UNAVAILABLE;
            }
            return runOrDeferToOwner(level, pos, taskClass, task);
        });
    }

    public static void assertNoRegionWorkerSyncLoad(final ServerLevel level, final ChunkPos pos, final String reason) {
        final boolean loaded = level.getChunkSource().getChunkAtIfLoadedImmediately(pos.x, pos.z) != null;
        if (independentRegionTicking() && ShreddedPaperTickThread.isShreddedPaperTickThread() && (!loaded || !isOwned(level, pos))) {
            throw new IllegalStateException(
                    "Synchronous chunk load is not allowed from an independent region worker"
                            + " for a missing or unowned chunk; world=" + level.getWorld().getName()
                            + ", chunk=[" + pos.x + "," + pos.z + "]"
                            + ", reason=" + reason
            );
        }
    }

    public static long loadedReadFallbacks() {
        return LOADED_READ_FALLBACKS.sum();
    }

    public static List<String> loadedReadFallbackSamples() {
        final long nextSequence = LOADED_READ_FALLBACK_SAMPLE_SEQUENCE.get();
        final long startSequence = Math.max(0L, nextSequence - MAX_LOADED_READ_FALLBACK_SAMPLES);
        final List<String> samples = new ArrayList<>((int) Math.min(MAX_LOADED_READ_FALLBACK_SAMPLES, nextSequence));
        for (long sequence = startSequence; sequence < nextSequence; sequence++) {
            final LoadedReadFallbackSample sample = LOADED_READ_FALLBACK_SAMPLES.get(sampleIndex(sequence));
            if (sample != null && sample.sequence == sequence) {
                samples.add(sample.value);
            }
        }
        return List.copyOf(samples);
    }

    public static long ownerHandoffs() {
        return OWNER_HANDOFFS.sum();
    }

    public static long ownerHandoffRequeues() {
        return OWNER_HANDOFF_REQUEUES.sum();
    }

    public static long ownerHandoffRejections() {
        return OWNER_HANDOFF_REJECTIONS.sum();
    }

    public static long prefetchFailures() {
        return PREFETCH_FAILURES.sum();
    }

    private static boolean canReadLoadedData(final Level level, final BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !independentRegionTicking()) {
            return true;
        }
        return isOwned(serverLevel, pos);
    }

    private static void requireLoadedReadIntent(final OwnershipIntent intent) {
        if (!intent.allowsLoadedOnlyRead()) {
            throw new IllegalArgumentException("Intent " + intent + " is not a loaded-only read intent");
        }
    }

    private static void recordLoadedReadFallback(final String reason, final Level level, final BlockPos pos) {
        if (ShreddedPaperTickThread.isShreddedPaperTickThread()) {
            LOADED_READ_FALLBACKS.increment();
            final long fallbackCount = LOADED_READ_FALLBACK_SAMPLE_ORDINAL.incrementAndGet();
            if (shouldRecordLoadedReadFallbackSample(fallbackCount)) {
                recordLoadedReadFallbackSample(fallbackCount, reason, level, pos);
            }
        }
    }

    static boolean shouldRecordLoadedReadFallbackSample(final long fallbackCount) {
        return fallbackCount <= MAX_LOADED_READ_FALLBACK_SAMPLES
                || (fallbackCount % LOADED_READ_FALLBACK_SAMPLE_INTERVAL) == 0L;
    }

    private static void recordLoadedReadFallbackSample(final long fallbackCount, final String reason, final Level level, final BlockPos pos) {
        final String world = level instanceof ServerLevel serverLevel ? serverLevel.getWorld().getName() : level.dimension().toString();
        final ChunkPos chunkPos = ChunkPos.of(pos);
        final String stack = firstRelevantCaller();
        final String sample = reason
                + " count=" + fallbackCount
                + " world=" + world
                + " pos=[" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + "]"
                + " chunk=[" + chunkPos.x + "," + chunkPos.z + "]"
                + " thread=" + Thread.currentThread().getName()
                + " caller=" + stack;
        final long sequence = LOADED_READ_FALLBACK_SAMPLE_SEQUENCE.getAndIncrement();
        publishLoadedReadFallbackSample(new LoadedReadFallbackSample(sequence, sample));
    }

    private static void publishLoadedReadFallbackSample(final LoadedReadFallbackSample sample) {
        final int index = sampleIndex(sample.sequence);
        LoadedReadFallbackSample current;
        do {
            current = LOADED_READ_FALLBACK_SAMPLES.get(index);
            if (current != null && current.sequence > sample.sequence) {
                return;
            }
        } while (!LOADED_READ_FALLBACK_SAMPLES.compareAndSet(index, current, sample));
    }

    private static int sampleIndex(final long sequence) {
        return (int) sequence & LOADED_READ_FALLBACK_SAMPLE_MASK;
    }

    private record LoadedReadFallbackSample(long sequence, String value) {
    }

    private static String firstRelevantCaller() {
        final List<String> frames = new ArrayList<>(4);
        for (final StackTraceElement element : Thread.currentThread().getStackTrace()) {
            final String className = element.getClassName();
            if (className.equals(Thread.class.getName()) || className.equals(ShreddedPaperAccess.class.getName())) {
                continue;
            }
            if (className.startsWith("java.") || className.startsWith("jdk.")) {
                continue;
            }
            frames.add(className + "#" + element.getMethodName() + ":" + element.getLineNumber());
            if (frames.size() >= 4) {
                break;
            }
        }
        return frames.isEmpty() ? "(unknown)" : String.join(" <- ", frames);
    }

    private static OwnerTaskResult writeLoadedBlockEntity(final ServerLevel level, final BlockEntity blockEntity) {
        final BlockPos pos = blockEntity.getBlockPos();
        if (!isOwned(level, pos)) {
            OWNER_HANDOFF_REJECTIONS.increment();
            recordLoadedReadFallback("block-entity-write-unowned", level, pos);
            return OwnerTaskResult.REJECTED;
        }

        final LevelChunk chunk = level.getChunkIfLoaded(pos);
        if (chunk == null) {
            recordLoadedReadFallback("block-entity-write-unloaded", level, pos);
            return OwnerTaskResult.CHUNK_UNAVAILABLE;
        }
        chunk.setBlockEntity(blockEntity);
        return OwnerTaskResult.RAN_INLINE;
    }

    private static boolean independentRegionTicking() {
        return ShreddedPaperConfiguration.get().multithreading.independentRegionTicking;
    }
}
