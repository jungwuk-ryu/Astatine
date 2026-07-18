package io.astatine.tools.linear;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinearRecompressorMain {
    private LinearRecompressorMain() {}

    public static void main(String[] args) {
        try {
            int code = run(args);
            if (code != 0) System.exit(code);
        } catch (Exception exception) {
            System.err.println("FATAL: " + exception.getMessage());
            if (Boolean.getBoolean("linear.recompressor.debug")) exception.printStackTrace(System.err);
            System.exit(2);
        }
    }

    static int run(String[] args) throws Exception {
        Options options = Options.parse(args);
        if (options.help) {
            usage();
            return 0;
        }

        long startedNanos = System.nanoTime();
        try (OfflineGuard guard = OfflineGuard.acquire(options);
             Checkpoint checkpoint = Checkpoint.open(options)) {
            Counters counters = new Counters();
            long scanStartedNanos = System.nanoTime();
            List<Path> files = discover(options, counters);
            double scanSeconds = elapsedSeconds(scanStartedNanos);
            System.out.printf(Locale.ROOT,
                "PLAN label=%s mode=%s workers=%d total=%d checkpointEntries=%d scanSeconds=%.3f%n",
                options.label, options.mode, options.threads, files.size(), checkpoint.size(), scanSeconds);

            ProgressReporter progress = new ProgressReporter(options, counters, files.size(), startedNanos);
            processFiles(files, options, guard, checkpoint, counters, progress);
            progress.finish();

            CounterSnapshot snapshot = counters.snapshot();
            System.out.printf(Locale.ROOT,
                "SUMMARY label=%s mode=%s workers=%d total=%d processed=%d completed=%d applied=%d keptNotSmaller=%d " +
                    "checkpointSkipped=%d checkpointApplied=%d checkpointKept=%d deferred=%d failed=%d " +
                    "input=%d candidateOutput=%d saved=%d elapsed=%s%n",
                options.label, options.mode, options.threads, files.size(), snapshot.processed(), snapshot.completed(),
                snapshot.applied(), snapshot.keptNotSmaller(), snapshot.checkpointSkipped(), snapshot.checkpointApplied(),
                snapshot.checkpointKept(), snapshot.deferred(), snapshot.failed(), snapshot.inputBytes(),
                snapshot.candidateOutputBytes(), snapshot.savedBytes(), formatDuration(elapsedSeconds(startedNanos)));
            return snapshot.failed() == 0 ? 0 : 1;
        }
    }

    private static List<Path> discover(Options options, Counters counters) throws IOException {
        Set<Path> files = new LinkedHashSet<>();
        for (Path root : options.roots) {
            if (files.size() >= options.maxFiles) break;
            Path absolute = root.toAbsolutePath().normalize();
            if (Files.isRegularFile(absolute)) {
                if (isLinear(absolute)) files.add(absolute);
                continue;
            }
            try {
                Files.walkFileTree(absolute, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                        if (files.size() >= options.maxFiles) return FileVisitResult.TERMINATE;
                        if (isLinear(file)) files.add(file.toAbsolutePath().normalize());
                        return files.size() >= options.maxFiles ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                        counters.recordScanFailure();
                        System.err.printf("ERROR label=%s phase=scan path=%s message=%s%n", options.label, file, exception.getMessage());
                        if (!options.continueOnError) throw exception;
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException exception) {
                if (!options.continueOnError) throw exception;
                counters.recordScanFailure();
                System.err.printf("ERROR label=%s phase=scan path=%s message=%s%n", options.label, absolute, exception.getMessage());
            }
        }
        return List.copyOf(files);
    }

    private static boolean isLinear(Path path) {
        return path.getFileName().toString().endsWith(".linear");
    }

    private static void processFiles(List<Path> files, Options options, OfflineGuard guard, Checkpoint checkpoint,
                                     Counters counters, ProgressReporter progress) throws Exception {
        if (files.isEmpty()) return;
        if (options.threads == 1 || files.size() == 1) {
            for (Path file : files) process(file, options, guard, checkpoint, counters, progress);
            return;
        }

        int workerCount = Math.min(options.threads, files.size());
        AtomicInteger nextIndex = new AtomicInteger();
        AtomicReference<Throwable> fatal = new AtomicReference<>();
        ThreadFactory factory = Thread.ofPlatform().name("linear-recompressor-worker-", 1).factory();
        ExecutorService executor = Executors.newFixedThreadPool(workerCount, factory);
        List<Future<?>> workers = new ArrayList<>(workerCount);
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                workers.add(executor.submit(() -> {
                    while (fatal.get() == null && !Thread.currentThread().isInterrupted()) {
                        int index = nextIndex.getAndIncrement();
                        if (index >= files.size()) return;
                        try {
                            process(files.get(index), options, guard, checkpoint, counters, progress);
                        } catch (Throwable failure) {
                            fatal.compareAndSet(null, failure);
                            return;
                        }
                    }
                }));
            }
            for (Future<?> worker : workers) {
                try {
                    worker.get();
                } catch (ExecutionException exception) {
                    fatal.compareAndSet(null, exception.getCause());
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            fatal.compareAndSet(null, new IOException("Interrupted while waiting for recompression workers", interrupted));
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    fatal.compareAndSet(null, new IOException("Recompression workers did not terminate within 30 seconds"));
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                fatal.compareAndSet(null, new IOException("Interrupted while stopping recompression workers", interrupted));
            }
        }

        Throwable failure = fatal.get();
        if (failure == null) return;
        if (failure instanceof IOException io) throw io;
        if (failure instanceof RuntimeException runtime) throw runtime;
        if (failure instanceof Error error) throw error;
        throw new IOException("Recompression worker failed", failure);
    }

    private static void process(Path file, Options options, OfflineGuard guard, Checkpoint checkpoint,
                                Counters counters, ProgressReporter progress) throws IOException {
        try {
            Checkpoint.Entry matched = options.mode == Mode.APPLY ? checkpoint.match(file, options.level) : null;
            if (matched != null) {
                counters.recordCheckpoint(matched.outcome());
                if (options.verbose) {
                    System.out.printf("CHECKPOINT label=%s outcome=%s path=%s%n", options.label, matched.outcome(), file);
                }
                progress.maybePrint(file);
                return;
            }

            BasicFileAttributes initial = Files.readAttributes(file, BasicFileAttributes.class);
            if (options.minAgeSeconds > 0) {
                long ageMillis = System.currentTimeMillis() - initial.lastModifiedTime().toMillis();
                if (ageMillis < options.minAgeSeconds * 1_000L) {
                    counters.recordDeferred();
                    if (options.verbose) {
                        System.out.printf("DEFER label=%s path=%s ageSeconds=%d requiredSeconds=%d%n",
                            options.label, file, Math.max(0, ageMillis / 1_000L), options.minAgeSeconds);
                    }
                    progress.maybePrint(file);
                    return;
                }
            }

            guard.checkStopped();
            int formatVersion = LinearLegacyFile.formatVersion(file);
            if (options.mode == Mode.VERIFY) {
                LinearV3File.Verification verification = formatVersion <= 2
                    ? LinearLegacyFile.verify(file)
                    : LinearV3File.verify(file, null);
                counters.recordVerified(initial.size());
                if (options.verbose) {
                    System.out.printf("VERIFIED label=%s path=%s version=%d chunks=%d buckets=%d levels=%s%n",
                        options.label, file, formatVersion, verification.chunkCount(), verification.nonEmptyBuckets(), verification.levels());
                }
                progress.maybePrint(file);
                return;
            }

            LinearV3File.RewriteResult result = formatVersion <= 2
                ? LinearLegacyFile.rewrite(file, options.level, options.minFreeBytes, options.mode == Mode.APPLY, guard::checkStopped)
                : LinearV3File.rewrite(file, options.level, options.minFreeBytes, options.mode == Mode.APPLY, guard::checkStopped);
            long input = result.sourceIdentity().size();
            long output = result.outputBytes();

            if (options.mode == Mode.ESTIMATE) {
                counters.recordEstimate(input, output, result.isSmaller());
                if (options.verbose) {
                    System.out.printf(Locale.ROOT,
                        "ESTIMATE label=%s outcome=%s path=%s input=%d output=%d delta=%d tempReused=%s%n",
                        options.label, result.isSmaller() ? "WOULD_APPLY" : "WOULD_KEEP", file, input, output,
                        input - output, result.reusedTemporary());
                }
                progress.maybePrint(file);
                return;
            }

            if (result.replaced()) {
                checkpoint.record(file, options.level, CheckpointOutcome.APPLIED, null);
                counters.recordApplied(input, output);
                if (options.verbose) {
                    System.out.printf(Locale.ROOT,
                        "APPLIED label=%s path=%s input=%d output=%d saved=%d tempReused=%s%n",
                        options.label, file, input, output, input - output, result.reusedTemporary());
                }
            } else {
                checkpoint.record(file, options.level, CheckpointOutcome.KEPT_NOT_SMALLER, result.sourceIdentity());
                counters.recordKept(input, output);
                if (options.verbose) {
                    System.out.printf(Locale.ROOT,
                        "KEPT_NOT_SMALLER label=%s path=%s input=%d candidate=%d delta=%d tempReused=%s%n",
                        options.label, file, input, output, input - output, result.reusedTemporary());
                }
            }
            progress.maybePrint(file);
        } catch (IOException | RuntimeException exception) {
            counters.recordFailure();
            System.err.printf("ERROR label=%s phase=process path=%s message=%s%n", options.label, file, exception.getMessage());
            progress.maybePrint(file);
            if (!options.continueOnError) throw exception;
        }
    }

    private static void usage() {
        System.out.println("""
            Usage:
              linear-recompressor verify [options] PATH...
              linear-recompressor estimate --level N [options] PATH...
              linear-recompressor apply --level N --checkpoint FILE (--lock-file FILE... | --assume-offline) [options] PATH...

            Options:
              --level N              Zstandard level 1..22
              --threads N            parallel file workers, default 1
              --label TEXT           stable label included in progress output
              --checkpoint FILE      fsynced append-only apply checkpoint
              --lock-file FILE       Minecraft session.lock to hold (repeatable)
              --launcher-state FILE  reject apply while launcher reports server alive
              --assume-offline       bypass lock requirement (copies/tests only)
              --min-free-gib N       reserve free space, default 5
              --max-files N          stop after N unique .linear files
              --progress-every N     progress interval in processed files, default 100
              --min-age-seconds N    defer files modified more recently than N seconds
              --continue-on-error    continue after corrupt/unreadable files
              --verbose              print one result line per file

            Apply mode keeps the original whenever the verified candidate is not smaller.
            Checkpoints include the source identity and resume both replacements and kept files.
            """);
    }

    enum Mode { VERIFY, ESTIMATE, APPLY }

    enum CheckpointOutcome { APPLIED, KEPT_NOT_SMALLER }

    static final class Options {
        Mode mode;
        int level = -1;
        int threads = 1;
        long minFreeBytes = 5L << 30;
        long maxFiles = Long.MAX_VALUE;
        long progressEvery = 100;
        long minAgeSeconds;
        boolean continueOnError;
        boolean assumeOffline;
        boolean verbose;
        boolean help;
        String label = "default";
        Path checkpoint;
        Path launcherState;
        final List<Path> lockFiles = new ArrayList<>();
        final List<Path> roots = new ArrayList<>();

        static Options parse(String[] args) {
            Options result = new Options();
            if (args.length == 0 || args[0].equals("help") || args[0].equals("--help")) {
                result.help = true;
                return result;
            }
            try {
                result.mode = Mode.valueOf(args[0].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown mode: " + args[0]);
            }
            for (int i = 1; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--level" -> result.level = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--threads" -> result.threads = Integer.parseInt(requireValue(args, ++i, arg));
                    case "--label" -> result.label = normalizeLabel(requireValue(args, ++i, arg));
                    case "--checkpoint" -> result.checkpoint = Path.of(requireValue(args, ++i, arg));
                    case "--lock-file" -> result.lockFiles.add(Path.of(requireValue(args, ++i, arg)));
                    case "--launcher-state" -> result.launcherState = Path.of(requireValue(args, ++i, arg));
                    case "--min-free-gib" -> result.minFreeBytes = Long.parseLong(requireValue(args, ++i, arg)) << 30;
                    case "--max-files" -> result.maxFiles = Long.parseLong(requireValue(args, ++i, arg));
                    case "--progress-every" -> result.progressEvery = Long.parseLong(requireValue(args, ++i, arg));
                    case "--min-age-seconds" -> result.minAgeSeconds = Long.parseLong(requireValue(args, ++i, arg));
                    case "--continue-on-error" -> result.continueOnError = true;
                    case "--assume-offline" -> result.assumeOffline = true;
                    case "--verbose" -> result.verbose = true;
                    default -> {
                        if (arg.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + arg);
                        result.roots.add(Path.of(arg));
                    }
                }
            }
            if (result.roots.isEmpty()) throw new IllegalArgumentException("At least one PATH is required");
            if (result.mode != Mode.VERIFY && (result.level < 1 || result.level > 22)) {
                throw new IllegalArgumentException("--level must be 1..22");
            }
            if (result.mode == Mode.APPLY && result.checkpoint == null) throw new IllegalArgumentException("apply requires --checkpoint");
            if (result.mode == Mode.APPLY && result.lockFiles.isEmpty() && !result.assumeOffline) {
                throw new IllegalArgumentException("apply requires --lock-file or explicit --assume-offline");
            }
            if (result.threads < 1 || result.threads > 256) throw new IllegalArgumentException("--threads must be 1..256");
            if (result.maxFiles < 1 || result.progressEvery < 1 || result.minFreeBytes < 0 || result.minAgeSeconds < 0) {
                throw new IllegalArgumentException("numeric options must be positive");
            }
            return result;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
            return args[index];
        }

        private static String normalizeLabel(String value) {
            String normalized = value.trim().replaceAll("\\s+", "_");
            if (normalized.isEmpty()) throw new IllegalArgumentException("--label must not be empty");
            return normalized;
        }
    }

    static final class OfflineGuard implements AutoCloseable {
        private static final Pattern SERVER_BLOCK = Pattern.compile("\\\"server\\\"\\s*:\\s*\\{([^}]*)}", Pattern.DOTALL);
        private static final Pattern ALIVE = Pattern.compile("\\\"alive\\\"\\s*:\\s*(true|false)");
        private static final Pattern PID = Pattern.compile("\\\"pid\\\"\\s*:\\s*(\\d+)");
        private final Path launcherState;
        private final List<FileChannel> channels;
        private final List<FileLock> locks;

        private OfflineGuard(Path launcherState, List<FileChannel> channels, List<FileLock> locks) {
            this.launcherState = launcherState;
            this.channels = channels;
            this.locks = locks;
        }

        static OfflineGuard acquire(Options options) throws IOException {
            List<FileChannel> channels = new ArrayList<>();
            List<FileLock> locks = new ArrayList<>();
            try {
                if (options.mode == Mode.APPLY) {
                    for (Path lockPath : options.lockFiles) {
                        FileChannel channel = FileChannel.open(lockPath.toAbsolutePath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                        channels.add(channel);
                        FileLock lock = channel.tryLock();
                        if (lock == null) throw new IOException("World lock is held; server may be running: " + lockPath);
                        locks.add(lock);
                    }
                }
                OfflineGuard guard = new OfflineGuard(options.mode == Mode.APPLY ? options.launcherState : null, channels, locks);
                guard.checkStopped();
                return guard;
            } catch (IOException | RuntimeException exception) {
                closeAll(locks, channels);
                throw exception;
            }
        }

        void checkStopped() throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Recompression worker was interrupted");
            if (launcherState == null) return;
            String json = Files.readString(launcherState, StandardCharsets.UTF_8);
            Matcher blocks = SERVER_BLOCK.matcher(json);
            while (blocks.find()) {
                String block = blocks.group(1);
                Matcher alive = ALIVE.matcher(block);
                if (!alive.find()) continue;
                Matcher pid = PID.matcher(block);
                String pidText = pid.find() ? pid.group(1) : "unknown";
                if (Boolean.parseBoolean(alive.group(1))) {
                    throw new IOException("Launcher reports server alive (pid=" + pidText + "): " + launcherState);
                }
                if (!pidText.equals("unknown") && ProcessHandle.of(Long.parseLong(pidText)).map(ProcessHandle::isAlive).orElse(false)) {
                    throw new IOException("Launcher server PID is alive: " + pidText);
                }
                return;
            }
            throw new IOException("Cannot find server.alive in launcher state: " + launcherState);
        }

        @Override
        public void close() {
            closeAll(locks, channels);
        }

        private static void closeAll(List<FileLock> locks, List<FileChannel> channels) {
            for (int i = locks.size() - 1; i >= 0; i--) try { locks.get(i).close(); } catch (IOException ignored) {}
            for (int i = channels.size() - 1; i >= 0; i--) try { channels.get(i).close(); } catch (IOException ignored) {}
        }
    }

    static final class Checkpoint implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        private final ConcurrentHashMap<String, Entry> entries;

        private Checkpoint(FileChannel channel, FileLock lock, ConcurrentHashMap<String, Entry> entries) {
            this.channel = channel;
            this.lock = lock;
            this.entries = entries;
        }

        static Checkpoint open(Options options) throws IOException {
            if (options.mode != Mode.APPLY) return new Checkpoint(null, null, new ConcurrentHashMap<>());
            Path path = options.checkpoint.toAbsolutePath();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
            if (Files.exists(path)) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String[] fields = line.split("\\t", -1);
                    if ((fields.length != 6 && fields.length != 7) || !fields[0].equals("OK")) continue;
                    try {
                        String decoded = new String(Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
                        CheckpointOutcome outcome = fields.length == 7
                            ? CheckpointOutcome.valueOf(fields[6])
                            : CheckpointOutcome.APPLIED;
                        entries.put(decoded, new Entry(Integer.parseInt(fields[1]), Long.parseLong(fields[2]),
                            Long.parseLong(fields[3]), fields[4], outcome));
                    } catch (RuntimeException ignored) {}
                }
            }
            FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                throw new IOException("Checkpoint is already locked: " + path);
            }
            channel.position(channel.size());
            if (path.getParent() != null) {
                try (FileChannel directory = FileChannel.open(path.getParent(), StandardOpenOption.READ)) {
                    directory.force(true);
                }
            }
            return new Checkpoint(channel, lock, entries);
        }

        int size() {
            return entries.size();
        }

        Entry match(Path path, int level) throws IOException {
            String key = path.toAbsolutePath().normalize().toString();
            Entry entry = entries.get(key);
            if (entry == null) return null;
            LinearV3File.SourceIdentity current = LinearV3File.SourceIdentity.read(path);
            return entry.level == level && entry.size == current.size() && entry.mtime == current.mtime()
                && entry.fileKey.equals(current.fileKey()) ? entry : null;
        }

        synchronized void record(Path path, int level, CheckpointOutcome outcome,
                                 LinearV3File.SourceIdentity expectedSource) throws IOException {
            LinearV3File.SourceIdentity current = LinearV3File.SourceIdentity.read(path);
            if (expectedSource != null && !expectedSource.equals(current)) {
                throw new IOException("Source changed before checkpoint; refusing stale completion record: " + path);
            }
            String key = path.toAbsolutePath().normalize().toString();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
            String line = "OK\t" + level + "\t" + current.size() + "\t" + current.mtime() + "\t" + current.fileKey()
                + "\t" + encoded + "\t" + outcome + "\n";
            ByteBuffer bytes = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
            entries.put(key, new Entry(level, current.size(), current.mtime(), current.fileKey(), outcome));
        }

        @Override
        public void close() throws IOException {
            if (lock != null) lock.close();
            if (channel != null) channel.close();
        }

        record Entry(int level, long size, long mtime, String fileKey, CheckpointOutcome outcome) {}
    }

    private static final class Counters {
        private final AtomicLong processed = new AtomicLong();
        private final AtomicLong applied = new AtomicLong();
        private final AtomicLong keptNotSmaller = new AtomicLong();
        private final AtomicLong checkpointApplied = new AtomicLong();
        private final AtomicLong checkpointKept = new AtomicLong();
        private final AtomicLong deferred = new AtomicLong();
        private final AtomicLong verified = new AtomicLong();
        private final AtomicLong estimatedSmaller = new AtomicLong();
        private final AtomicLong estimatedNotSmaller = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong inputBytes = new AtomicLong();
        private final AtomicLong candidateOutputBytes = new AtomicLong();
        private final AtomicLong savedBytes = new AtomicLong();

        synchronized void recordScanFailure() {
            failed.incrementAndGet();
        }

        synchronized void recordFailure() {
            failed.incrementAndGet();
            processed.incrementAndGet();
        }

        synchronized void recordDeferred() {
            deferred.incrementAndGet();
            processed.incrementAndGet();
        }

        synchronized void recordCheckpoint(CheckpointOutcome outcome) {
            if (outcome == CheckpointOutcome.APPLIED) checkpointApplied.incrementAndGet();
            else checkpointKept.incrementAndGet();
            processed.incrementAndGet();
        }

        synchronized void recordVerified(long input) {
            verified.incrementAndGet();
            inputBytes.addAndGet(input);
            candidateOutputBytes.addAndGet(input);
            processed.incrementAndGet();
        }

        synchronized void recordEstimate(long input, long output, boolean smaller) {
            if (smaller) estimatedSmaller.incrementAndGet();
            else estimatedNotSmaller.incrementAndGet();
            inputBytes.addAndGet(input);
            candidateOutputBytes.addAndGet(output);
            processed.incrementAndGet();
        }

        synchronized void recordApplied(long input, long output) {
            applied.incrementAndGet();
            inputBytes.addAndGet(input);
            candidateOutputBytes.addAndGet(output);
            savedBytes.addAndGet(input - output);
            processed.incrementAndGet();
        }

        synchronized void recordKept(long input, long output) {
            keptNotSmaller.incrementAndGet();
            inputBytes.addAndGet(input);
            candidateOutputBytes.addAndGet(output);
            processed.incrementAndGet();
        }

        synchronized CounterSnapshot snapshot() {
            return new CounterSnapshot(processed.get(), applied.get(), keptNotSmaller.get(), checkpointApplied.get(),
                checkpointKept.get(), deferred.get(), verified.get(), estimatedSmaller.get(), estimatedNotSmaller.get(),
                failed.get(), inputBytes.get(), candidateOutputBytes.get(), savedBytes.get());
        }
    }

    private record CounterSnapshot(long processed, long applied, long keptNotSmaller, long checkpointApplied,
                                   long checkpointKept, long deferred, long verified, long estimatedSmaller,
                                   long estimatedNotSmaller, long failed, long inputBytes,
                                   long candidateOutputBytes, long savedBytes) {
        long checkpointSkipped() {
            return checkpointApplied + checkpointKept;
        }

        long completed() {
            return applied + keptNotSmaller + checkpointSkipped() + verified + estimatedSmaller + estimatedNotSmaller;
        }
    }

    private static final class ProgressReporter {
        private final Options options;
        private final Counters counters;
        private final long total;
        private final long startedNanos;
        private long lastPrinted;

        private ProgressReporter(Options options, Counters counters, long total, long startedNanos) {
            this.options = options;
            this.counters = counters;
            this.total = total;
            this.startedNanos = startedNanos;
        }

        synchronized void maybePrint(Path current) {
            CounterSnapshot snapshot = counters.snapshot();
            long processed = snapshot.processed();
            if (processed - lastPrinted < options.progressEvery && processed != total) return;
            print(snapshot, current);
        }

        synchronized void finish() {
            CounterSnapshot snapshot = counters.snapshot();
            if (snapshot.processed() != lastPrinted) print(snapshot, null);
        }

        private void print(CounterSnapshot snapshot, Path current) {
            long processed = snapshot.processed();
            if (processed <= lastPrinted) return;
            lastPrinted = processed;
            double elapsed = Math.max(0.001, elapsedSeconds(startedNanos));
            double rate = processed / elapsed;
            double percent = total == 0 ? 100.0 : processed * 100.0 / total;
            double etaSeconds = rate <= 0 || processed >= total ? 0 : (total - processed) / rate;
            String currentText = current == null ? "-" : current.toString();
            System.out.printf(Locale.ROOT,
                "PROGRESS label=%s processed=%d/%d percent=%.2f%% rate=%.2f_files/s eta=%s " +
                    "applied=%d keptNotSmaller=%d checkpointSkipped=%d deferred=%d failed=%d savedMiB=%.2f current=%s%n",
                options.label, processed, total, percent, rate, formatDuration(etaSeconds), snapshot.applied(),
                snapshot.keptNotSmaller(), snapshot.checkpointSkipped(), snapshot.deferred(), snapshot.failed(),
                snapshot.savedBytes() / (1024.0 * 1024.0), currentText);
        }
    }

    private static double elapsedSeconds(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000_000.0;
    }

    private static String formatDuration(double seconds) {
        long rounded = Math.max(0, Math.round(seconds));
        long hours = rounded / 3_600;
        long minutes = (rounded % 3_600) / 60;
        long remaining = rounded % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remaining);
    }
}
