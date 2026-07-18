package io.astatine.tools.linear;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

        try (OfflineGuard guard = OfflineGuard.acquire(options);
             Checkpoint checkpoint = Checkpoint.open(options)) {
            Counters counters = new Counters();
            for (Path root : options.roots) {
                if (counters.seen >= options.maxFiles) break;
                visit(root, options, guard, checkpoint, counters);
            }
            System.out.printf(Locale.ROOT,
                "SUMMARY mode=%s seen=%d completed=%d skipped=%d deferred=%d failed=%d input=%d output=%d saved=%d%n",
                options.mode, counters.seen, counters.completed, counters.skipped, counters.deferred, counters.failed,
                counters.inputBytes, counters.outputBytes, counters.inputBytes - counters.outputBytes);
            return counters.failed == 0 ? 0 : 1;
        }
    }

    private static void visit(Path root, Options options, OfflineGuard guard, Checkpoint checkpoint, Counters counters) throws IOException {
        Path absolute = root.toAbsolutePath().normalize();
        if (Files.isRegularFile(absolute)) {
            if (absolute.getFileName().toString().endsWith(".linear")) process(absolute, options, guard, checkpoint, counters);
            return;
        }
        Files.walkFileTree(absolute, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (counters.seen >= options.maxFiles) return FileVisitResult.TERMINATE;
                if (file.getFileName().toString().endsWith(".linear")) process(file, options, guard, checkpoint, counters);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) throws IOException {
                if (!options.continueOnError) throw exception;
                counters.failed++;
                System.err.println("ERROR path=" + file + " message=" + exception.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void process(Path file, Options options, OfflineGuard guard, Checkpoint checkpoint, Counters counters) throws IOException {
        counters.seen++;
        try {
            if (options.mode == Mode.APPLY && checkpoint.matches(file, options.level)) {
                counters.skipped++;
                progress(options, counters, "SKIP", file, 0, 0);
                return;
            }
            if (options.minAgeSeconds > 0) {
                long ageMillis = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
                if (ageMillis < options.minAgeSeconds * 1_000L) {
                    counters.deferred++;
                    System.out.printf("DEFER path=%s ageSeconds=%d requiredSeconds=%d%n", file, Math.max(0, ageMillis / 1_000L), options.minAgeSeconds);
                    return;
                }
            }
            guard.checkStopped();
            long input = Files.size(file);
            long output;
            int formatVersion = LinearLegacyFile.formatVersion(file);
            if (options.mode == Mode.VERIFY) {
                LinearV3File.Verification verification = formatVersion <= 2
                    ? LinearLegacyFile.verify(file)
                    : LinearV3File.verify(file, null);
                output = input;
                System.out.printf("OK path=%s version=%d chunks=%d buckets=%d levels=%s%n", file, formatVersion, verification.chunkCount(), verification.nonEmptyBuckets(), verification.levels());
            } else {
                LinearV3File.RewriteResult result = formatVersion <= 2
                    ? LinearLegacyFile.rewrite(file, options.level, options.minFreeBytes, options.mode == Mode.APPLY, guard::checkStopped)
                    : LinearV3File.rewrite(file, options.level, options.minFreeBytes, options.mode == Mode.APPLY, guard::checkStopped);
                output = result.outputBytes();
                System.out.printf(Locale.ROOT, "%s path=%s input=%d output=%d saved=%d temp=%s%n",
                    options.mode == Mode.APPLY ? "APPLIED" : "ESTIMATE", file, input, output, input - output, result.reusedTemporary());
                if (options.mode == Mode.APPLY) checkpoint.record(file, options.level);
            }
            counters.completed++;
            counters.inputBytes += input;
            counters.outputBytes += output;
            progress(options, counters, "PROGRESS", file, input, output);
        } catch (IOException | RuntimeException exception) {
            counters.failed++;
            System.err.println("ERROR path=" + file + " message=" + exception.getMessage());
            if (!options.continueOnError) throw exception;
        }
    }

    private static void progress(Options options, Counters counters, String label, Path file, long input, long output) {
        if (counters.seen % options.progressEvery == 0) {
            System.out.printf("%s seen=%d completed=%d failed=%d path=%s input=%d output=%d%n",
                label, counters.seen, counters.completed, counters.failed, file, input, output);
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
              --checkpoint FILE      fsynced append-only apply checkpoint
              --lock-file FILE       Minecraft session.lock to hold (repeatable)
              --launcher-state FILE  reject apply while launcher reports server alive
              --assume-offline       bypass lock requirement (copies/tests only)
              --min-free-gib N       reserve free space, default 5
              --max-files N          stop after N .linear files
              --progress-every N     periodic progress interval, default 100
              --min-age-seconds N    defer files modified more recently than N seconds
              --continue-on-error    continue after corrupt/unreadable files
            """);
    }

    enum Mode { VERIFY, ESTIMATE, APPLY }

    static final class Options {
        Mode mode;
        int level = -1;
        long minFreeBytes = 5L << 30;
        long maxFiles = Long.MAX_VALUE;
        long progressEvery = 100;
        long minAgeSeconds;
        boolean continueOnError;
        boolean assumeOffline;
        boolean help;
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
                    case "--checkpoint" -> result.checkpoint = Path.of(requireValue(args, ++i, arg));
                    case "--lock-file" -> result.lockFiles.add(Path.of(requireValue(args, ++i, arg)));
                    case "--launcher-state" -> result.launcherState = Path.of(requireValue(args, ++i, arg));
                    case "--min-free-gib" -> result.minFreeBytes = Long.parseLong(requireValue(args, ++i, arg)) << 30;
                    case "--max-files" -> result.maxFiles = Long.parseLong(requireValue(args, ++i, arg));
                    case "--progress-every" -> result.progressEvery = Long.parseLong(requireValue(args, ++i, arg));
                    case "--min-age-seconds" -> result.minAgeSeconds = Long.parseLong(requireValue(args, ++i, arg));
                    case "--continue-on-error" -> result.continueOnError = true;
                    case "--assume-offline" -> result.assumeOffline = true;
                    default -> {
                        if (arg.startsWith("--")) throw new IllegalArgumentException("Unknown option: " + arg);
                        result.roots.add(Path.of(arg));
                    }
                }
            }
            if (result.roots.isEmpty()) throw new IllegalArgumentException("At least one PATH is required");
            if (result.mode != Mode.VERIFY && (result.level < 1 || result.level > 22)) throw new IllegalArgumentException("--level must be 1..22");
            if (result.mode == Mode.APPLY && result.checkpoint == null) throw new IllegalArgumentException("apply requires --checkpoint");
            if (result.mode == Mode.APPLY && result.lockFiles.isEmpty() && !result.assumeOffline) throw new IllegalArgumentException("apply requires --lock-file or explicit --assume-offline");
            if (result.maxFiles < 1 || result.progressEvery < 1 || result.minFreeBytes < 0 || result.minAgeSeconds < 0) throw new IllegalArgumentException("numeric options must be positive");
            return result;
        }

        private static String requireValue(String[] args, int index, String option) {
            if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
            return args[index];
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
            if (launcherState == null) return;
            String json = Files.readString(launcherState, StandardCharsets.UTF_8);
            Matcher blocks = SERVER_BLOCK.matcher(json);
            while (blocks.find()) {
                String block = blocks.group(1);
                Matcher alive = ALIVE.matcher(block);
                if (!alive.find()) continue;
                Matcher pid = PID.matcher(block);
                String pidText = pid.find() ? pid.group(1) : "unknown";
                if (Boolean.parseBoolean(alive.group(1))) throw new IOException("Launcher reports server alive (pid=" + pidText + "): " + launcherState);
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
        private final Map<String, Entry> entries;

        private Checkpoint(FileChannel channel, FileLock lock, Map<String, Entry> entries) {
            this.channel = channel;
            this.lock = lock;
            this.entries = entries;
        }

        static Checkpoint open(Options options) throws IOException {
            if (options.mode != Mode.APPLY) return new Checkpoint(null, null, Map.of());
            Path path = options.checkpoint.toAbsolutePath();
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            Map<String, Entry> entries = new HashMap<>();
            if (Files.exists(path)) {
                for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                    String[] fields = line.split("\\t", -1);
                    if (fields.length != 6 || !fields[0].equals("OK")) continue;
                    try {
                        String decoded = new String(Base64.getUrlDecoder().decode(fields[5]), StandardCharsets.UTF_8);
                        entries.put(decoded, new Entry(Integer.parseInt(fields[1]), Long.parseLong(fields[2]), Long.parseLong(fields[3]), fields[4]));
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

        boolean matches(Path path, int level) throws IOException {
            String key = path.toAbsolutePath().normalize().toString();
            Entry entry = entries.get(key);
            if (entry == null) return false;
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return entry.level == level && entry.size == attrs.size() && entry.mtime == attrs.lastModifiedTime().toMillis()
                && entry.fileKey.equals(String.valueOf(attrs.fileKey()));
        }

        void record(Path path, int level) throws IOException {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            String key = path.toAbsolutePath().normalize().toString();
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes(StandardCharsets.UTF_8));
            String line = "OK\t" + level + "\t" + attrs.size() + "\t" + attrs.lastModifiedTime().toMillis() + "\t" + attrs.fileKey() + "\t" + encoded + "\n";
            java.nio.ByteBuffer bytes = java.nio.ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
            entries.put(key, new Entry(level, attrs.size(), attrs.lastModifiedTime().toMillis(), String.valueOf(attrs.fileKey())));
        }

        @Override
        public void close() throws IOException {
            if (lock != null) lock.close();
            if (channel != null) channel.close();
        }

        record Entry(int level, long size, long mtime, String fileKey) {}
    }

    private static final class Counters {
        long seen;
        long completed;
        long skipped;
        long deferred;
        long failed;
        long inputBytes;
        long outputBytes;
    }
}
