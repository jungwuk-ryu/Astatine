import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class AsyncOwnershipCallScanner {
    private static final List<String> START_DIRS = List.of(
        "shreddedpaper-server/src/minecraft/java",
        "shreddedpaper-server/src/main/java",
        "paper-server/src/main/java",
        "tools/region-load-test-plugin/src/main/java"
    );

    private static final Set<String> SKIP_DIR_NAMES = Set.of("build", ".gradle", "run", "out", "target");

    public static void main(String[] args) throws Exception {
        Config config = Config.parse(args);
        Path root = config.root().toAbsolutePath().normalize();
        List<Path> javaFiles = collectJavaFiles(root);
        List<Candidate> candidates = scan(root, javaFiles);
        candidates.sort(Comparator
            .comparingInt((Candidate c) -> severityRank(c.severity())).reversed()
            .thenComparing(Candidate::category)
            .thenComparing(Candidate::path)
            .thenComparingInt(Candidate::line)
        );

        Files.createDirectories(config.json().toAbsolutePath().normalize().getParent());
        Files.writeString(config.json(), renderJson(candidates), StandardCharsets.UTF_8);

        if (config.markdown() != null) {
            Files.createDirectories(config.markdown().toAbsolutePath().normalize().getParent());
            Files.writeString(config.markdown(), renderMarkdown(candidates), StandardCharsets.UTF_8);
        }

        Map<String, Integer> bySeverity = new HashMap<>();
        for (Candidate candidate : candidates) {
            bySeverity.merge(candidate.severity(), 1, Integer::sum);
        }
        System.out.printf(
            Locale.ROOT,
            "JDK AST call-site candidates: %d%ncritical=%d high=%d medium=%d%nJSON: %s%n",
            candidates.size(),
            bySeverity.getOrDefault("critical", 0),
            bySeverity.getOrDefault("high", 0),
            bySeverity.getOrDefault("medium", 0),
            root.relativize(config.json().toAbsolutePath().normalize())
        );
        if (config.markdown() != null) {
            System.out.printf(Locale.ROOT, "Markdown: %s%n", root.relativize(config.markdown().toAbsolutePath().normalize()));
        }
    }

    private static List<Path> collectJavaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        for (String startDir : START_DIRS) {
            Path start = root.resolve(startDir);
            if (!Files.isDirectory(start)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(start)) {
                stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> !hasSkippedSegment(root.relativize(path)))
                    .forEach(files::add);
            }
        }
        files.sort(Comparator.naturalOrder());
        return files;
    }

    private static boolean hasSkippedSegment(Path relative) {
        for (Path segment : relative) {
            if (SKIP_DIR_NAMES.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<Candidate> scan(Path root, List<Path> files) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK compiler not found. Run with a JDK, not a JRE.");
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjectsFromPaths(files);
            JavacTask task = (JavacTask) compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("-proc:none", "-XDshouldStopPolicy=PARSE", "-XDcompilePolicy=simple"),
                null,
                fileObjects
            );
            Iterable<? extends CompilationUnitTree> parsed = task.parse();
            Trees trees = Trees.instance(task);
            SourcePositions positions = trees.getSourcePositions();
            List<Candidate> candidates = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (CompilationUnitTree unit : parsed) {
                Path file = Path.of(unit.getSourceFile().toUri()).toAbsolutePath().normalize();
                String relPath = root.relativize(file).toString();
                new CallVisitor(root, relPath, unit, positions, candidates, seen).scan(unit, null);
            }

            return candidates;
        }
    }

    private static final class CallVisitor extends TreePathScanner<Void, Void> {
        private final Path root;
        private final String relPath;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final List<Candidate> candidates;
        private final Set<String> seen;
        private final Deque<String> classStack = new ArrayDeque<>();
        private final Deque<String> methodStack = new ArrayDeque<>();

        private CallVisitor(
            Path root,
            String relPath,
            CompilationUnitTree unit,
            SourcePositions positions,
            List<Candidate> candidates,
            Set<String> seen
        ) {
            this.root = root;
            this.relPath = relPath;
            this.unit = unit;
            this.positions = positions;
            this.candidates = candidates;
            this.seen = seen;
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            classStack.push(node.getSimpleName().toString());
            try {
                return super.visitClass(node, unused);
            } finally {
                classStack.pop();
            }
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            methodStack.push(node.getName().toString());
            try {
                return super.visitMethod(node, unused);
            } finally {
                methodStack.pop();
            }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            CallSite callSite = callSite(node);
            Sink sink = classify(callSite, relPath, context());
            if (sink != null) {
                long start = positions.getStartPosition(unit, node);
                long line = unit.getLineMap().getLineNumber(start);
                String evidence = node.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim();
                String id = "AO-AST-" + sha1(relPath + "\n" + line + "\n" + evidence).substring(0, 8).toUpperCase(Locale.ROOT);
                String key = relPath + ":" + line + ":" + sink.sinkName() + ":" + evidence;
                if (seen.add(key)) {
                    String category = categoryFor(relPath, context(), evidence);
                    candidates.add(new Candidate(
                        id,
                        "AST call " + callSite.methodName() + " in " + Path.of(relPath).getFileName(),
                        boostSeverity(sink.severity(), relPath, category),
                        category,
                        sink.sinkName(),
                        relPath,
                        Math.toIntExact(line),
                        context(),
                        evidence,
                        sink.why(),
                        sink.fix(),
                        callSite.receiver(),
                        callSite.methodName(),
                        callSite.arguments()
                    ));
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        private String context() {
            String className = classStack.peek() == null ? "(top-level)" : classStack.peek();
            String methodName = methodStack.peek() == null ? "(initializer)" : methodStack.peek();
            return className + "#" + methodName;
        }
    }

    private static CallSite callSite(MethodInvocationTree tree) {
        ExpressionTree select = tree.getMethodSelect();
        String receiver = "";
        String methodName = select.toString();
        if (select instanceof MemberSelectTree memberSelect) {
            receiver = memberSelect.getExpression().toString();
            methodName = memberSelect.getIdentifier().toString();
        } else if (select instanceof IdentifierTree identifier) {
            methodName = identifier.getName().toString();
        }

        List<String> arguments = new ArrayList<>();
        for (ExpressionTree argument : tree.getArguments()) {
            arguments.add(argument.toString().replace('\n', ' ').replaceAll("\\s+", " ").trim());
        }
        return new CallSite(receiver, methodName, arguments);
    }

    private static Sink classify(CallSite callSite, String relPath, String context) {
        String method = callSite.methodName();
        String args = String.join(", ", callSite.arguments());

        if (method.equals("syncLoad") || method.equals("syncLoadNonFull")) {
            return new Sink(
                "sync-load-call",
                "critical",
                "Direct sync chunk loading is forbidden from independent region workers.",
                "Replace with async chunk loading and resume on the owning region, or use already-loaded chunk access when absence is acceptable."
            );
        }
        if ((method.equals("getChunk") || method.equals("getChunkAt")) && containsBooleanTrue(callSite.arguments())) {
            return new Sink(
                "getchunk-load-true",
                "critical",
                "load=true can enter ServerChunkCache.syncLoad when the target chunk is absent.",
                "Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region."
            );
        }
        if (method.equals("getChunkAt") || method.equals("loadChunk")) {
            return new Sink(
                "bukkit-chunk-load",
                "critical",
                "Chunk access APIs commonly synchronously load chunks unless guarded.",
                "Guard with isChunkLoaded or route through async chunk loading before chunk access."
            );
        }
        if (method.equals("getBlockState") && isWorldLikeReceiver(callSite.receiver(), relPath, context)) {
            return new Sink(
                "blockstate-read",
                "high",
                "Block state reads can synchronously load chunks when the target is outside the owned/loaded region.",
                "Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading."
            );
        }
        if (method.equals("getFluidState") && isWorldLikeReceiver(callSite.receiver(), relPath, context)) {
            return new Sink(
                "fluidstate-read",
                "high",
                "Fluid reads can synchronously load chunks on cross-boundary block logic.",
                "Use getFluidIfLoaded or derive fluid state from a loaded BlockState."
            );
        }
        if (method.equals("setType")) {
            return new Sink(
                "bukkit-block-write",
                "high",
                "Bukkit block writes may touch unloaded chunks or trigger off-owner physics.",
                "Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region."
            );
        }
        if (method.equals("setBlockEntity")) {
            return new Sink(
                "set-block-entity",
                "high",
                "Block entity writes require target chunk ownership and should not force load from packet/region workers.",
                "Ensure the target chunk is loaded and owned, or resume on the owning region before mutation."
            );
        }
        if (method.equals("scheduleTick")) {
            return new Sink(
                "schedule-tick",
                "medium",
                "Scheduling a tick for another isolation cell can violate tick ownership.",
                "Use the scheduled tick owner handoff path for cross-cell future ticks."
            );
        }
        if (method.equals("getBlockEntity") && isWorldLikeReceiver(callSite.receiver(), relPath, context)) {
            return new Sink(
                "block-entity-read",
                "medium",
                "Block entity reads can imply chunk access and stale owner assumptions.",
                "Prefer non-loading access and owner validation before block entity reads."
            );
        }

        return null;
    }

    private static boolean containsBooleanTrue(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.equals("true") || argument.endsWith(", true")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWorldLikeReceiver(String receiver, String relPath, String context) {
        if (receiver == null || receiver.isBlank()) {
            return relPath.contains("/world/") || relPath.contains("/server/level/") || context.contains("Level") || context.contains("World");
        }
        String probe = receiver.toLowerCase(Locale.ROOT);
        return probe.contains("level")
            || probe.contains("world")
            || probe.contains("serverlevel")
            || probe.contains("blockgetter")
            || probe.contains("accessor")
            || probe.contains("context.getlevel()")
            || probe.contains("entity.level()")
            || probe.contains("player.level()");
    }

    private static String categoryFor(String relPath, String context, String evidence) {
        String probe = (relPath + "\n" + context + "\n" + evidence).toLowerCase(Locale.ROOT);
        if (probe.contains("redstone") || probe.contains("signalg") || probe.contains("neighbor") || probe.contains("scheduledtick")
            || probe.contains("levelticks") || probe.contains("flowingfluid") || probe.contains("leavesblock") || probe.contains("piston")
            || probe.contains("wire")) {
            return "scheduled tick/neighbor/fluid-leaf-redstone";
        }
        if (probe.contains("entity") || probe.contains("livingentity") || probe.contains("serverplayer") || probe.contains("playerlist")
            || probe.contains("servergamepacketlistenerimpl") || probe.contains("packet") || probe.contains("teleport")
            || probe.contains("disconnect") || probe.contains("vehicle") || probe.contains("movement")) {
            return "entity movement/teleport/player tick";
        }
        if (probe.contains("naturalspawner") || probe.contains("spawnutil") || probe.contains("worldgen") || probe.contains("structure")
            || probe.contains("chunkgenerator") || probe.contains("summoncommand") || probe.contains("regionloadtest") || probe.contains("spawn")) {
            return "natural spawning/entity add/structure sync load";
        }
        if (probe.contains("playerchunk") || probe.contains("chunkholder") || probe.contains("chunkmap") || probe.contains("trackedentity")
            || probe.contains("broadcast") || probe.contains("regionizedplayerchunkloader") || probe.contains("chunksender")) {
            return "player chunk send/post-processing/stale holder broadcast";
        }
        return "uncategorized sync-load risk";
    }

    private static String boostSeverity(String base, String relPath, String category) {
        if (base.equals("critical")) {
            return base;
        }
        if (!category.equals("uncategorized sync-load risk")
            || relPath.contains("/server/network/")
            || relPath.contains("/server/level/")
            || relPath.contains("/world/entity/")
            || relPath.contains("/world/level/redstone/")
            || relPath.contains("/world/level/block/")) {
            return base.equals("medium") ? "high" : base;
        }
        return base;
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "critical" -> 3;
            case "high" -> 2;
            case "medium" -> 1;
            default -> 0;
        };
    }

    private static String renderJson(List<Candidate> candidates) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        jsonField(builder, "generatedAt", Instant.now().toString(), 2, true);
        builder.append("  \"mode\": \"jdk-ast-method-invocation\",\n");
        builder.append("  \"candidates\": [\n");
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            builder.append("    {\n");
            jsonField(builder, "id", candidate.id(), 6, true);
            jsonField(builder, "title", candidate.title(), 6, true);
            jsonField(builder, "severity", candidate.severity(), 6, true);
            jsonField(builder, "category", candidate.category(), 6, true);
            jsonField(builder, "sink", candidate.sink(), 6, true);
            jsonField(builder, "path", candidate.path(), 6, true);
            builder.append("      \"line\": ").append(candidate.line()).append(",\n");
            jsonField(builder, "context", candidate.context(), 6, true);
            jsonField(builder, "evidence", candidate.evidence(), 6, true);
            jsonField(builder, "why", candidate.why(), 6, true);
            jsonField(builder, "fix", candidate.fix(), 6, true);
            jsonField(builder, "receiver", candidate.receiver(), 6, true);
            jsonField(builder, "method", candidate.method(), 6, true);
            builder.append("      \"arguments\": [");
            for (int j = 0; j < candidate.arguments().size(); j++) {
                if (j > 0) {
                    builder.append(", ");
                }
                builder.append('"').append(escapeJson(candidate.arguments().get(j))).append('"');
            }
            builder.append("]\n");
            builder.append("    }");
            if (i < candidates.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
        }
        builder.append("  ]\n");
        builder.append("}\n");
        return builder.toString();
    }

    private static String renderMarkdown(List<Candidate> candidates) {
        Map<String, Integer> byCategory = new HashMap<>();
        Map<String, Integer> bySeverity = new HashMap<>();
        for (Candidate candidate : candidates) {
            byCategory.merge(candidate.category(), 1, Integer::sum);
            bySeverity.merge(candidate.severity(), 1, Integer::sum);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# JDK AST Async Ownership Call Sites\n\n");
        builder.append("Generated: ").append(Instant.now()).append("\n\n");
        builder.append("This report is built from the JDK compiler API over real `.java` files. It records method invocation AST nodes, so comments and unrelated text do not count as call sites. It is syntax-aware, not full IntelliJ-grade symbol resolution yet.\n\n");
        builder.append("## Summary\n\n");
        builder.append("- Candidates: ").append(candidates.size()).append("\n");
        builder.append("- Critical: ").append(bySeverity.getOrDefault("critical", 0)).append("\n");
        builder.append("- High: ").append(bySeverity.getOrDefault("high", 0)).append("\n");
        builder.append("- Medium: ").append(bySeverity.getOrDefault("medium", 0)).append("\n\n");
        builder.append("| Category | Count |\n| --- | ---: |\n");
        byCategory.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
            builder.append("| ").append(entry.getKey()).append(" | ").append(entry.getValue()).append(" |\n")
        );
        builder.append("\n## Candidates\n\n");
        for (Candidate candidate : candidates) {
            builder.append("### ").append(candidate.id()).append(" - ").append(candidate.title()).append("\n");
            builder.append("- Severity: ").append(candidate.severity()).append("\n");
            builder.append("- Category: ").append(candidate.category()).append("\n");
            builder.append("- Sink: ").append(candidate.sink()).append("\n");
            builder.append("- Location: `").append(candidate.path()).append(':').append(candidate.line()).append("`\n");
            builder.append("- Context: `").append(candidate.context()).append("`\n");
            builder.append("- Receiver: `").append(candidate.receiver()).append("`\n");
            builder.append("- Method: `").append(candidate.method()).append("`\n");
            builder.append("- Evidence: `").append(candidate.evidence()).append("`\n");
            builder.append("- Suggested fix: ").append(candidate.fix()).append("\n\n");
        }
        return builder.toString();
    }

    private static void jsonField(StringBuilder builder, String name, String value, int indent, boolean comma) {
        builder.append(" ".repeat(indent))
            .append('"').append(name).append("\": \"")
            .append(escapeJson(value))
            .append('"');
        if (comma) {
            builder.append(',');
        }
        builder.append('\n');
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Config(Path root, Path json, Path markdown) {
        private static Config parse(String[] args) {
            Path root = Path.of(".").toAbsolutePath().normalize();
            Path json = Path.of("tools/async-audit/java-call-sites.json");
            Path markdown = Path.of("tools/async-audit/JAVA_CALL_SITES.md");

            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--root" -> root = Path.of(args[++i]).toAbsolutePath().normalize();
                    case "--json" -> json = Path.of(args[++i]);
                    case "--markdown" -> markdown = Path.of(args[++i]);
                    case "--no-markdown" -> markdown = null;
                    case "--help", "-h" -> {
                        System.out.println("""
                            Usage: java AsyncOwnershipCallScanner [options]

                            Options:
                              --root <path>       Repository root
                              --json <path>       JSON output path
                              --markdown <path>   Markdown output path
                              --no-markdown       Skip markdown output
                            """);
                        System.exit(0);
                    }
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }

            if (!json.isAbsolute()) {
                json = root.resolve(json).normalize();
            }
            if (markdown != null && !markdown.isAbsolute()) {
                markdown = root.resolve(markdown).normalize();
            }
            return new Config(root, json, markdown);
        }
    }

    private record CallSite(String receiver, String methodName, List<String> arguments) {
    }

    private record Sink(String sinkName, String severity, String why, String fix) {
    }

    private record Candidate(
        String id,
        String title,
        String severity,
        String category,
        String sink,
        String path,
        int line,
        String context,
        String evidence,
        String why,
        String fix,
        String receiver,
        String method,
        List<String> arguments
    ) {
    }
}
