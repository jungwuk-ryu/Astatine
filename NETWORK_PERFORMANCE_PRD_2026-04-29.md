# Network Performance PRD - 2026-04-29

Date: 2026-04-29
Status: Draft, sub-agent reviewed
Owner: ShreddedPaper performance/runtime
Scope: server networking throughput, latency, and network-adjacent packet delivery cost on Java 25

## 1. Document Purpose

This PRD defines safe opportunities to improve ShreddedPaper network performance using Java 25 runtime features and current library options.

The product rule for this work is intentionally aggressive:

- If a change is safe, bug-free, reversible, and produces even a small repeatable network performance improvement, it should be adopted.
- "Small improvement" must still be repeatable above benchmark noise.
- Runtime safety has priority over theoretical throughput.
- Every production change must preserve vanilla protocol compatibility, player-visible behavior, plugin-visible behavior, region ownership safety, and packet ordering semantics.

The initial implementation target is not a rewrite of the networking stack. The target is measured, low-risk improvements around existing Netty usage, native transports, compression/cipher natives, Java 25 runtime behavior, and benchmark visibility.

### 1.1 Sub-Agent Review Reconciliation

One performance sub-agent reviewed the plan against the local networking code paths. The review changed this PRD in three important ways:

- `flushQueueInParallel` validation is now a P0 gate before transport work.
- Netty upgrade guidance explicitly requires full 4.2 Final alignment and bans dynamic latest selectors.
- Structured Concurrency and Vector API are kept out of production networking unless future profiling proves a narrow, reversible case.

## 2. Current Codebase Facts

The following facts were verified in the current working tree and are requirements for the plan.

### 2.1 Java Baseline

- The root Gradle build uses Java toolchain 25.
- Java compilation sets `--enable-preview`.
- The server code therefore can compile Java 25 preview APIs, but production runtime use still needs explicit launch compatibility and fallback rules.

Relevant file:

- `build.gradle.kts`

### 2.2 Current Network Stack

The current server networking stack is Netty based and already uses Netty 4.2 APIs.

Relevant files:

- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/EventLoopGroupHolder.java`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerConnectionListener.java`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/Connection.java`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/CompressionEncoder.java`
- `shreddedpaper-server/src/minecraft/java/net/minecraft/network/CompressionDecoder.java`

Current observed behavior:

- `EventLoopGroupHolder` constructs `MultiThreadIoEventLoopGroup`.
- Remote connections choose native transport in this order:
  - kqueue, when available,
  - epoll, when available,
  - NIO fallback.
- There is no io_uring transport holder today.
- `ServerConnectionListener` sets `TCP_NODELAY`.
- `ServerConnectionListener` installs Netty `FlushConsolidationHandler` unless the Paper setting disables it.
- HAProxy protocol support is handled through `netty-codec-haproxy`.
- `Connection` already has Paper's lazy execute optimization for non-flushing sends.
- `Connection` has ShreddedPaper's parallel flush path controlled by `flushQueueInParallel`.
- Compression and cipher paths already support Velocity native helpers when available.

### 2.3 Current Dependencies

The current server Gradle dependency set includes:

- `io.netty:netty-codec-haproxy:4.2.7.Final`
- Netty runtime artifacts resolved at `4.2.7.Final`
- `com.velocitypowered:velocity-native:3.4.0-SNAPSHOT`
- `com.github.luben:zstd-jni:1.5.7-4`
- `org.jctools:jctools-core:4.0.5`

Relevant file:

- `shreddedpaper-server/build.gradle.kts`

### 2.4 Current Configuration Hooks

The ShreddedPaper optimization config already exposes:

- `lazyExecute = true`
- `processTrackQueueInParallel = true`
- `flushQueueInParallel = true`
- chunk packet caching settings
- virtual thread settings for selected async work

Relevant file:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/config/ShreddedPaperConfiguration.java`

### 2.5 Network-Adjacent Region Paths

Packet flushing is partly integrated with region tick execution.

Relevant files:

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperPlayerTicker.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/config/ShreddedPaperConfiguration.java`

Current behavior:

- `ShreddedPaperChunkTicker.flushQueueInParallel(...)` partitions players and calls each player's `connection.flushQueue()` on the ShreddedPaper tick executor.
- `ShreddedPaperPlayerTicker` sends chunk updates, ticks the connection, handles keepalive, and resumes flushing.
- Chunk packet caching is controlled by ShreddedPaper config and cache expiry is handled during chunk ticking.
- These optimizations are network-adjacent, but they are also tightly coupled to tick ownership and packet visibility. They require stricter safety gates than a pure dependency upgrade.

## 3. External Version Facts As Of 2026-04-29

The following version facts were checked against upstream metadata on 2026-04-29.

| Component | Current | Latest observed stable/candidate | Recommendation |
| --- | ---: | ---: | --- |
| Netty 4.2 line | `4.2.7.Final` | `4.2.12.Final` | Upgrade only within 4.2 Final line first |
| Netty 5 | not used | `5.0.0.Alpha2` | Do not use in production |
| Netty io_uring native transport | not used | `4.2.12.Final` | Add as Linux-only gated experiment |
| zstd-jni | `1.5.7-4` | `1.5.7-7` | Upgrade if smoke and chunk/network benchmarks pass |
| JCTools | `4.0.5` | `4.0.6` | Upgrade if concurrency stress passes |
| Velocity native | `3.4.0-SNAPSHOT` | `3.4.0`, plus newer snapshots | Prefer stable `3.4.0` for production reproducibility |
| zero-allocation-hashing | `0.16` | `2026.0` | Defer; indirect to region IO and likely higher migration risk |

Reference URLs:

- OpenJDK JEP 519 Compact Object Headers: `https://openjdk.org/jeps/519`
- OpenJDK JEP 506 Scoped Values: `https://openjdk.org/jeps/506`
- OpenJDK JEP 505 Structured Concurrency: `https://openjdk.org/jeps/505`
- OpenJDK JEP 508 Vector API: `https://openjdk.org/jeps/508`
- Netty 4.2.0 release notes: `https://netty.io/news/2025/04/03/4-2-0.html`
- Netty native transports wiki: `https://netty.io/wiki/native-transports.html`
- Velocity tuning documentation: `https://docs.papermc.io/velocity/tuning/`
- Netty Maven metadata: `https://repo.maven.apache.org/maven2/io/netty/netty-common/maven-metadata.xml`
- Netty io_uring Maven metadata: `https://repo.maven.apache.org/maven2/io/netty/netty-transport-native-io_uring/maven-metadata.xml`
- zstd-jni Maven metadata: `https://repo.maven.apache.org/maven2/com/github/luben/zstd-jni/maven-metadata.xml`
- JCTools Maven metadata: `https://repo.maven.apache.org/maven2/org/jctools/jctools-core/maven-metadata.xml`
- Velocity native Maven metadata: `https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-native/maven-metadata.xml`

## 4. Problem Statement

ShreddedPaper already contains useful network optimizations, but there are still likely gains in four areas:

1. Dependency drift: the code is on Netty 4.2 but not the latest 4.2 Final patch line.
2. Native transport coverage: Linux epoll is available, but io_uring is not even selectable.
3. Runtime memory/GC overhead: Java 25 Compact Object Headers may reduce heap footprint and cache pressure for allocation-heavy packet, connection, and region networking paths.
4. Measurement visibility: current benchmark automation is strong for region isolation and chunk throughput, but it does not yet isolate networking throughput, event-loop latency, flush behavior, compression cost, or packet queue pressure.

The danger is that network changes can produce impressive local benchmark numbers while breaking subtle production behavior:

- packet order,
- disconnect behavior,
- proxy protocol handling,
- compression threshold correctness,
- encryption/cipher setup,
- Netty event-loop affinity,
- region tick ownership assumptions,
- plugin-visible send timing,
- cross-platform startup.

This PRD therefore requires benchmark-first or gate-first adoption for every non-trivial change.

## 5. Goals

### 5.1 Product Goals

- Increase network throughput when many players are connected.
- Reduce p50, p95, and p99 network latency under packet-heavy workloads.
- Reduce event-loop queuing delay.
- Reduce packet encode, compression, encryption, and flush overhead where possible.
- Preserve existing ShreddedPaper behavior under all supported platforms.
- Make every network performance claim reproducible with local benchmark artifacts.

### 5.2 Engineering Goals

- Keep Netty 4.2 artifacts aligned to one exact version.
- Add io_uring only behind a platform and config gate.
- Keep epoll, kqueue, and NIO fallbacks intact.
- Keep native compression/cipher behavior reversible.
- Add enough instrumentation to detect regressions before enabling new defaults.
- Avoid mixing preview Java APIs into hot production paths unless the code path can be disabled or bypassed.
- Keep all changes split into small PRs so regressions can be bisected.

## 6. Non-Goals

The following are explicitly out of scope for the first production rollout:

- Migrating to Netty 5 alpha.
- Changing the Minecraft wire protocol.
- Adding zstd, lz4, or any non-vanilla compression to client packets.
- Replacing Netty event loops with virtual threads.
- Reordering packets for throughput.
- Changing plugin-visible packet send behavior.
- Changing tick ownership or region ownership semantics.
- Rewriting `Connection` queueing in the first dependency/native transport PR.
- Removing `FlushConsolidationHandler`.
- Treating synthetic microbenchmarks as sufficient proof for production defaults.

## 7. Safety Principles

### 7.1 Preserve Protocol Compatibility

All network packet formats, compression thresholds, encryption setup, HAProxy handling, and connection state transitions must remain compatible with existing Minecraft clients and proxies.

No candidate may require a modified client.

### 7.2 Feature Flags For Risky Paths

All new runtime behavior beyond patch-level dependency upgrades must be guarded by configuration.

Required gates:

- `network.io-uring.enabled`
- `network.io-uring.prefer-over-epoll`
- `network.java25.compact-object-headers.benchmarkProfile` or launch profile documentation
- optional `network.instrumentation.enabled`
- optional `network.instrumentation.sampleRate`

### 7.3 Fail Closed To Existing Behavior

If a new feature is unavailable, misconfigured, unsupported, or throws during initialization, the server must fall back to the existing behavior:

- io_uring unavailable: fall back to epoll, kqueue, or NIO.
- native compression unavailable: fall back to current Java path.
- Java 25 runtime flag unavailable: start without the flag, or fail early in a benchmark-only profile.
- instrumentation unavailable: disable instrumentation, not networking.

### 7.4 No Hidden Platform Regression

The server must still start and accept connections on:

- Linux x86_64,
- Linux aarch64,
- macOS kqueue,
- Windows NIO.

io_uring work is Linux-only. It must not change macOS or Windows behavior except through shared dependency upgrades that pass smoke tests.

### 7.5 Benchmark Noise Guard

"Any improvement" means any repeatable improvement above noise, not a single lucky run.

Default decision rule:

- run at least 5 repeats for standard comparison,
- compare baseline and candidate on the same machine,
- require no survival failures,
- require no p95 or p99 regression above 1% unless throughput gain is explicitly accepted for that workload,
- require a primary metric improvement above the observed run-to-run noise floor.

For very small improvements, accept only when the direction is consistent across most repeats and no safety metric worsens.

## 8. Performance Metrics

### 8.1 Primary Metrics

Network throughput metrics:

- successful joins per second,
- steady-state packets sent per second,
- steady-state packets received per second,
- bytes sent per second,
- bytes received per second,
- chunk packets sent per second,
- disconnect/reconnect cycles per second.

Latency metrics:

- keepalive network latency p50/p95/p99,
- event-loop task delay p50/p95/p99,
- packet encode latency p50/p95/p99,
- compression latency p50/p95/p99,
- flush latency p50/p95/p99,
- login to playable time.

Backpressure metrics:

- pending outbound packet queue length,
- Netty outbound buffer bytes,
- write buffer high/low watermark crossings,
- flush count per second,
- consolidated flush ratio,
- event-loop pending task count,
- dropped or closed connections.

### 8.2 Safety Metrics

The following must not regress:

- server startup success,
- player login success,
- chunk visibility correctness,
- disconnect handling,
- proxy protocol handling,
- compression/decompression correctness,
- encryption correctness,
- no Netty leak detector errors,
- no watchdog failures,
- no async ownership failures,
- no unexpected exceptions in server log,
- no crash reports.

### 8.3 Resource Metrics

Track:

- process RSS,
- Java heap used,
- young/old GC count and pause time,
- direct buffer usage,
- native memory tracking summary when enabled,
- CPU utilization by server, event-loop, compression, and region threads.

## 9. Required Instrumentation

Instrumentation should land before risky behavior changes. It can be disabled by default if overhead is measurable.

### 9.1 Connection And Flush Counters

Add low-overhead counters around `Connection`:

- queued packets,
- packets flushed,
- explicit flush calls,
- skipped tick flushes due to `flushQueueInParallel`,
- flush queue drain nanos,
- pending queue size at drain start and end,
- lazy execute sends,
- lazy execute flushes.

Implementation notes:

- Prefer `LongAdder` or per-thread counters for high-frequency aggregate metrics.
- Avoid allocating event objects on the hot path unless sampling is enabled.
- Emit summaries periodically rather than per-packet by default.

### 9.2 Event-Loop Delay Probe

Add a sampled event-loop delay probe:

- schedule timestamp,
- execution timestamp,
- event-loop group type,
- transport type,
- local address family,
- current active channels.

Purpose:

- distinguish server tick lag from Netty event-loop backlog,
- prove whether io_uring or Netty patch upgrades affect low-latency behavior.

### 9.3 Compression And Cipher Timing

Add sampled timing around:

- Velocity native compression deflate/inflate,
- Java `Deflater` and `Inflater` fallback,
- Velocity native cipher path,
- Java cipher fallback.

Purpose:

- verify whether native dependency changes help,
- catch any regression in compression thresholds,
- identify whether packet CPU cost, not transport, is the bottleneck.

### 9.4 Benchmark Output

Extend benchmark artifacts with a network section:

- `network-summary.json`
- `network-summary.csv`
- a section in `benchmark-summary.md`

Minimum fields:

- transport selected,
- native compression selected,
- native cipher selected,
- Netty version,
- Java runtime flags relevant to Java 25 features,
- packet throughput,
- keepalive latency distribution,
- event-loop delay distribution,
- flush count and consolidated flush ratio,
- `flushQueueInParallel` setting and observed queue-drain success/failure counts,
- error counts.

### 9.5 Mandatory P0 Gate: `flushQueueInParallel` Drain Validation

Sub-agent review identified one safety gate that must precede transport experiments.

Current code facts:

- `ShreddedPaperConfiguration.optimizations.flushQueueInParallel` defaults to `true`.
- `ShreddedPaperChunkTicker.flushQueueInParallel(...)` calls `player.connection.connection.flushQueue()` from the ShreddedPaper tick executor.
- `Connection.flushQueue()` processes the queue on the main-thread path, or on the `isPending` login/status path under synchronization.
- `ServerConnectionListener.addPending()` sets `connection.isPending = false` after the connection is added.
- `Connection.tick()` skips its normal `flushQueue()` call when `flushQueueInParallel` is true.

Risk hypothesis:

- For PLAY connections, parallel flush may call `flushQueue()` from a thread that does not satisfy the main-thread condition while `isPending` is already false.
- In that case, `flushQueue()` returns `false`, and the current parallel flush caller does not observe or compensate for the failure.
- If this is true under real workloads, new transport work could hide or amplify an existing queue-drain issue instead of improving networking.

Required validation:

- Add counters for `flushQueue()` calls by caller mode:
  - main-thread processed,
  - pending synchronized processed,
  - non-main non-pending rejected,
  - disconnected short-circuit.
- Add benchmark comparison with `flushQueueInParallel=false` and `flushQueueInParallel=true`.
- Record pending queue length before and after flush attempts.
- Treat any non-main non-pending rejection during PLAY packet backlog as a P0 finding unless another code path demonstrably drains the same queue in the same tick.

Adoption rule:

- No io_uring default recommendation, Netty performance claim, or flush-related optimization may be accepted until this gate is understood.
- If `flushQueueInParallel=true` is proven to improve throughput safely, keep it.
- If it is neutral, ineffective, or unsafe, switch the default back or add a safe fallback before further network optimization work.

## 10. Candidate Matrix

| Candidate | Type | Expected Risk | Expected Gain | Default Position |
| --- | --- | --- | --- | --- |
| `flushQueueInParallel` drain validation | existing optimization safety gate | High until proven | may recover correctness and real throughput | P0 before transport work |
| Netty 4.2.7 -> 4.2.12 aligned upgrade | library patch-line upgrade | Low to medium | small throughput/latency/security/stability gain possible | recommended after smoke/load |
| Netty io_uring transport | native Linux transport | Medium | possible lower latency and syscall overhead | gated experiment |
| Velocity native snapshot -> stable 3.4.0 | native helper reproducibility | Low | performance neutral or safer deployment | recommended if benchmarks do not regress |
| zstd-jni 1.5.7-4 -> 1.5.7-7 | native compression library | Low to medium | indirect chunk/region IO gain, not direct client packet gain | recommended after IO smoke |
| JCTools 4.0.5 -> 4.0.6 | queue/concurrency library | Low to medium | possible small queue overhead gain | recommended after stress |
| Java 25 Compact Object Headers | JVM runtime feature | Medium operational risk | memory/cache/GC improvement possible | benchmark profile first |
| Java 25 Scoped Values | code-level Java 25 API | Medium | potential safer bounded context passing | experiment only after audit |
| Java 25 Structured Concurrency | preview API | Medium to high | supervision clarity, not guaranteed throughput | exclude from production networking |
| Java 25 Vector API | incubator API | High integration cost | possible CPU gain only in proven encode/bulk hotspots | lab only |

## 11. Candidate A: Netty 4.2 Patch-Line Alignment

Status: Phase 1, recommended.

### 11.1 Problem

The server already resolves Netty 4.2 artifacts, but it is on `4.2.7.Final` while the latest observed 4.2 Final line is `4.2.12.Final`.

Patch-line upgrades can contain transport, allocator, native, and correctness fixes. They may provide small network improvements with less risk than new code.

### 11.2 Design

Upgrade all Netty artifacts to the same exact `4.2.12.Final` version.

Rules:

- Do not mix Netty 4.1, 4.2, and 5 artifacts.
- Do not adopt Netty 5 alpha.
- Do not use dynamic `latest.release` style selectors because Maven metadata can point to Netty 5 alpha.
- Prefer a BOM, dependency constraints, or explicit version alignment so `buffer`, `common`, `transport`, `handler`, `codec`, and native transport artifacts resolve to the same 4.2 Final version.
- Verify dependency lock or resolution output shows one Netty version family.
- Keep `MultiThreadIoEventLoopGroup` code path intact.
- Keep `FlushConsolidationHandler` behavior intact.

### 11.3 Acceptance Criteria

- `./gradlew :shreddedpaper-server:compileJava --no-configuration-cache` passes.
- Server starts on local platform.
- At least one client can login, receive chunks, move, disconnect, reconnect.
- HAProxy protocol smoke passes when enabled.
- Compression threshold smoke passes.
- No Netty leak detector errors under smoke.
- `dependencyInsight` confirms all runtime Netty artifacts are on one exact 4.2 Final version.
- Benchmark standard network metrics show no regression.
- If any primary metric improves repeatably, keep the upgrade.

### 11.4 Rollback

Revert the version alignment only. No code behavior change should be mixed into this PR.

## 12. Candidate B: Linux io_uring Native Transport

Status: Phase 2, gated experiment.

### 12.1 Problem

Linux native transport currently uses epoll. Netty 4.2 also publishes an io_uring native transport line, but this codebase does not select it.

io_uring may reduce syscall overhead or improve latency for some workloads. The improvement is platform, kernel, and workload dependent. It must be measured.

### 12.2 Design

Add an io_uring holder to `EventLoopGroupHolder`.

Expected Netty classes to evaluate:

- `io.netty.channel.uring.IOUring`
- `io.netty.channel.uring.IOUringIoHandler`
- `io.netty.channel.uring.IOUringSocketChannel`
- `io.netty.channel.uring.IOUringServerSocketChannel`

Dependency plan:

- add `io.netty:netty-transport-native-io_uring:4.2.12.Final`
- include Linux classifiers for supported deployment targets
- keep epoll and kqueue dependencies unchanged

Selection policy:

1. If `useNativeTransport` is false, use NIO as today.
2. If address type or platform is unsupported by io_uring, use current kqueue/epoll/NIO selection.
3. If config `network.io-uring.enabled` is false, use current kqueue/epoll/NIO selection.
4. If Linux and io_uring is available, use io_uring only when `network.io-uring.prefer-over-epoll` is true.
5. If io_uring initialization fails, log once and fall back to epoll.

Important:

- Domain socket behavior must be verified before selecting io_uring for non-INET channels.
- The first implementation should target remote TCP server channels only.
- Existing epoll must remain the default until io_uring wins on the supported production kernel matrix.

### 12.3 Configuration

Add under ShreddedPaper config:

```yaml
network:
  io-uring:
    enabled: false
    prefer-over-epoll: false
    log-selection: true
```

For benchmark and emergency override workflows, also support an explicit launch override such as `-Dshreddedpaper.network.transport=auto|nio|native|epoll|io_uring` if it can be added without confusing the normal config path.

Default is disabled because platform-specific transport changes are high impact.

### 12.4 Acceptance Criteria

- Linux with io_uring unavailable falls back to epoll without startup failure.
- Linux with io_uring available starts and accepts connections when enabled.
- macOS still selects kqueue when native transport is enabled.
- Windows still uses NIO.
- No packet ordering or disconnect behavior regression.
- Network benchmark shows repeatable improvement in at least one primary metric with no safety metric regression.
- If io_uring is neutral or slower, keep code available but default disabled.

### 12.5 Rollback

Disable config. If necessary, remove io_uring dependency and holder without touching epoll/kqueue/NIO.

## 13. Candidate C: Velocity Native Stable Pin

Status: Phase 1, recommended if neutral or better.

### 13.1 Problem

The server currently depends on `com.velocitypowered:velocity-native:3.4.0-SNAPSHOT`.

Snapshots can change without a stable coordinate. That weakens reproducibility for a production performance feature, even if runtime behavior is good.

### 13.2 Design

Move from `3.4.0-SNAPSHOT` to stable `3.4.0` unless a measured regression is found.

Rules:

- Do not move to `4.0.0-SNAPSHOT` for production by default.
- Keep native compression and cipher setup behavior unchanged.
- Record selected native paths in benchmark output.

### 13.3 Acceptance Criteria

- Compression encode/decode smoke passes.
- Cipher setup smoke passes for online-mode/encryption path if test harness supports it.
- Velocity forwarding/login smoke passes if the harness supports it.
- Chunk stream smoke records the selected native compression variant.
- Benchmark shows no regression.
- If stable `3.4.0` is neutral, prefer it because reproducibility is safer.

## 14. Candidate D: zstd-jni Patch Upgrade

Status: Phase 1, recommended after IO smoke.

### 14.1 Problem

The current zstd-jni version is `1.5.7-4`; latest observed is `1.5.7-7`.

This is not a direct Minecraft client packet compression upgrade. It is relevant to network performance only indirectly when chunk load/generation/region IO affects packet availability and player-visible chunk send throughput.

### 14.2 Design

Upgrade zstd-jni to `1.5.7-7` in a separate PR.

Rules:

- Do not change region file format behavior.
- Do not change client packet compression.
- Include chunk-load and chunk-generation scenarios in validation.
- Include network chunk-send throughput in validation.

### 14.3 Acceptance Criteria

- Existing region files load.
- New region files write and reload.
- Chunk load benchmark does not regress.
- Network chunk-send throughput does not regress.
- If any chunk availability or chunk-send metric improves repeatably, keep the upgrade.

## 15. Candidate E: JCTools Patch Upgrade

Status: Phase 1, recommended after concurrency stress.

### 15.1 Problem

The current JCTools version is `4.0.5`; latest observed is `4.0.6`.

This may affect queue performance in concurrent region/runtime paths. It is not a pure Netty upgrade, so it needs concurrency stress validation.

### 15.2 Design

Upgrade to `org.jctools:jctools-core:4.0.6` in a separate PR.

Validation must include:

- region mailbox behavior,
- async task scheduling,
- player flush parallel path,
- boundary-torture benchmark scenario,
- high player packet pressure scenario.

### 15.3 Acceptance Criteria

- No async ownership failures.
- No deadlocks.
- No mailbox task loss.
- No packet flush regression.
- If neutral or better and stress-safe, keep the upgrade.

## 16. Candidate F: Java 25 Compact Object Headers

Status: Phase 0 benchmark profile first; production opt-in after evidence.

### 16.1 Problem

Networking allocates and touches many objects:

- packets,
- Netty buffers and handlers,
- queued packet wrappers,
- connection state objects,
- player/chunk send structures,
- temporary compression and encode state.

Java 25 Compact Object Headers may reduce object header size, improving memory footprint and cache locality. This can indirectly reduce GC pressure and latency tails.

### 16.2 Design

Do not change source code first. Add benchmark launch profiles that can run with and without Compact Object Headers.

Required profile metadata:

- Java vendor and exact version,
- all JVM flags,
- heap size,
- direct memory limit if set,
- GC,
- whether compact headers are enabled,
- benchmark scenario and repeat count.

### 16.3 Acceptance Criteria

- Server starts with the selected Java 25 runtime and flag.
- No JVM crash or startup warning requiring unsupported deployment behavior.
- No correctness regression.
- RSS or heap usage improves, or GC pause distribution improves, or network latency tails improve.
- Throughput does not regress.
- If improvement is repeatable, document an opt-in production launch profile.

### 16.4 Rollout

Phase 0:

- benchmark only,
- no default production flag change.

Phase 1:

- document recommended launch profile for operators whose JDK supports it.

Phase 2:

- consider enabling in provided scripts only after multi-platform soak.

## 17. Candidate G: Java 25 Scoped Values

Status: Phase 3 experiment only.

### 17.1 Problem

The codebase uses thread-local style context for region execution, including current ticking region state. Scoped Values can be safer for bounded dynamic scope than mutable thread locals, especially when code later interacts with virtual threads or structured concurrency.

However, network hot paths depend on Netty event-loop affinity and region tick ownership. A careless context propagation change can create subtle bugs.

### 17.2 Design

Do not replace existing thread-local state broadly.

Evaluate Scoped Values only for bounded, synchronous scopes where all of the following are true:

- context lifetime is lexical,
- no mutable value is required,
- no asynchronous callback reads the value after scope exit,
- existing behavior can be shadow-checked,
- fallback to existing thread-local implementation is available.

Potential evaluation sites:

- current ticking region context,
- debug or instrumentation context,
- sampled benchmark context.

### 17.3 Acceptance Criteria

- No async context leak.
- No behavior change under nested region/task execution.
- No performance regression in region tick and network flush benchmarks.
- Clear removal of mutable thread-local risk or measured overhead reduction.

## 18. Candidate H: Java 25 Structured Concurrency

Status: Phase 3 lab only; excluded from production networking defaults.

### 18.1 Problem

Structured Concurrency can improve supervision of bounded fan-out tasks, but it remains a preview API in Java 25 and does not automatically improve Netty throughput.

The current high-risk areas are region-aware executors, async chunk work, and player flush parallelism. These are already controlled by ownership and QoS logic.

### 18.2 Design

Do not use `StructuredTaskScope` in Netty event loops.

Only evaluate it for bounded helper tasks where:

- cancellation behavior is explicit,
- task ownership is region-safe,
- task completion is joined before state is observed,
- errors propagate identically or more safely than current code,
- preview runtime requirements are acceptable.

### 18.3 Acceptance Criteria

- No packet order change.
- No tick ownership change.
- No additional blocked event-loop time.
- No throughput regression.
- Measured improvement or clear safety simplification.

## 19. Candidate I: Java 25 Vector API

Status: Phase 3 lab only.

### 19.1 Problem

The Vector API may improve bulk byte/int operations, but many network encode paths are branchy and small.

Current varint frame encoding/decoding is tiny and branch-driven. It is unlikely to benefit enough to justify Vector API complexity.

### 19.2 Design

Do not vectorize `Varint21FrameDecoder` or `Varint21LengthFieldPrepender` first.

Only evaluate Vector API if JFR proves a bulk operation dominates CPU time, such as:

- packet bulk serialization loops,
- chunk packet copy/transform work,
- bulk bit storage operations feeding chunk packet generation,
- checksum/hash style work adjacent to chunk send.

Rules:

- keep scalar fallback,
- guard with module/runtime detection,
- do not require custom clients,
- do not add Vector API to general server launch unless benchmarked.

### 19.3 Acceptance Criteria

- CPU hotspot is proven before implementation.
- Vector path produces byte-identical output.
- Scalar fallback remains available.
- Network or chunk-send benchmark improves repeatably.

## 20. Explicitly Rejected Or Deferred Ideas

### 20.1 Netty 5 Alpha

Rejected for production.

Reason:

- latest observed Netty metadata points to `5.0.0.Alpha2`,
- alpha dependency risk is not compatible with this server's correctness bar,
- Netty 4.2 already provides modern APIs and native transport options.

### 20.2 zstd/lz4 Client Packet Compression

Rejected unless protocol compatibility changes in upstream Minecraft.

Reason:

- vanilla clients expect vanilla protocol compression behavior,
- changing wire compression requires modified clients or proxy negotiation,
- not safe for this product goal.

### 20.3 Virtual Threads For Netty Event Loops

Rejected.

Reason:

- Netty event loops are intentionally long-lived event-loop threads,
- replacing them with virtual threads would break the model rather than improve it,
- virtual threads may still be useful for isolated blocking tasks outside Netty event loops.

### 20.4 Packet Reordering

Rejected.

Reason:

- ordering is gameplay and protocol visible,
- throughput gains from reordering are not safe.

### 20.5 Aggressive Flush Semantics Rewrite

Deferred.

Reason:

- `lazyExecute`, `FlushConsolidationHandler`, and ShreddedPaper parallel flushing already interact,
- changing flush timing can affect visibility, latency, and plugin behavior,
- instrumentation must land first.

## 21. Benchmark Plan

### 21.1 Existing Benchmark Base

Use the existing benchmark runner:

- `tools/benchmark/run-benchmarks.sh`
- `tools/region-load-test-plugin`

Existing scenarios already useful for network-adjacent validation:

- `baseline`
- `one-hot-region`
- `multi-hotspot`
- `chunk-load`
- `chunk-generation`
- `boundary-torture`

### 21.2 New Network Scenarios

Add benchmark scenarios focused on networking.

#### Scenario: login-burst

Purpose:

- measure connection accept/login throughput and event-loop pressure.

Workload:

- spawn many fake clients or protocol bots,
- connect in controlled bursts,
- authenticate or complete offline-mode login depending on harness support,
- wait for initial chunks,
- disconnect cleanly.

Metrics:

- successful logins per second,
- login failure count,
- login to playable p50/p95/p99,
- event-loop delay,
- bytes sent during login,
- chunk packets sent during login.

#### Scenario: steady-movement

Purpose:

- measure steady-state packet send/receive throughput under realistic movement.

Workload:

- fake players remain connected,
- movement and look packets at controlled rates,
- view distance fixed,
- simulation distance fixed,
- optional split between one region and many regions.

Metrics:

- packets in/out per second,
- keepalive latency,
- event-loop delay,
- flush count,
- pending queue size,
- CPU per event-loop thread.

#### Scenario: chunk-stream

Purpose:

- measure network throughput when chunk packets dominate.

Workload:

- players move across new terrain or pre-generated terrain,
- run both chunk-load and chunk-generation modes,
- compare chunk packet cache on/off if safe.

Metrics:

- chunk packets per second,
- bytes sent per second,
- compression time,
- packet encode time,
- login/movement latency while chunks stream.

#### Scenario: proxy-protocol-smoke

Purpose:

- preserve HAProxy decoder behavior.

Workload:

- connect using proxy protocol header,
- verify address handling,
- login and disconnect.

Metrics:

- pass/fail,
- exceptions,
- login latency.

### 21.3 Test Matrix

Run this matrix for each serious candidate:

| Axis | Values |
| --- | --- |
| Java | baseline Java 25, Java 25 with Compact Object Headers profile |
| Netty | current 4.2.7, upgraded 4.2.12 |
| Transport | NIO, epoll, io_uring where available, kqueue on macOS |
| Native compression/cipher | current snapshot, stable Velocity native |
| Flush mode | `flushQueueInParallel=false`, `flushQueueInParallel=true` |
| Workload | login-burst, steady-movement, chunk-stream, boundary-torture |
| Repeats | 5 standard, 10 for very small claimed wins |

### 21.4 Noise Control

Benchmark rules:

- pin Java path,
- pin heap size,
- pin server properties,
- use a fresh server directory for each run,
- warm up before measurement,
- report raw run artifacts,
- compare median and p95/p99,
- use trimmed means only as secondary context,
- invalidate runs with crash, watchdog, leak detector error, or unexpected exception.

## 22. Validation Plan

### 22.1 Build Validation

Required for dependency PRs:

```bash
./gradlew :shreddedpaper-server:compileJava --no-configuration-cache
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache
./gradlew -p tools/region-load-test-plugin build --no-configuration-cache
```

### 22.2 Smoke Validation

Required smoke:

- server starts,
- one player logs in,
- chunks load,
- movement works,
- chat or command roundtrip works,
- `flushQueueInParallel=false` drains packet queues,
- `flushQueueInParallel=true` either drains packet queues or is blocked from becoming the benchmark baseline,
- disconnect works,
- reconnect works,
- shutdown is clean.

Compression smoke:

- compression disabled,
- low compression threshold,
- normal compression threshold,
- large chunk send under compression.

Proxy smoke:

- normal direct connection,
- HAProxy protocol enabled connection if supported by harness.

### 22.3 Leak And Error Validation

Run at least smoke with:

- Netty resource leak detection enabled,
- high signal log scanner,
- crash report scan,
- watchdog scan.

The benchmark runner already parses high-signal logs. Network extensions should add Netty leak and transport selection lines to the high-signal summary.

### 22.4 Load Validation

Required before enabling any new default:

- `standard` suite with repeat 5,
- network scenarios with repeat 5,
- `flushQueueInParallel=false` versus `true` comparison for packet-heavy scenarios,
- stressed scenario with MCC/fake players when available,
- at least one long soak run for io_uring or Java runtime flags.

## 23. Rollout Plan

### Phase 0: Measurement And Runtime Profiles

Deliverables:

- `flushQueueInParallel` drain validation and true/false benchmark comparison,
- network benchmark summary fields,
- transport/native selection logging,
- connection/flush/event-loop instrumentation,
- Java 25 Compact Object Headers benchmark profile,
- baseline results for current code.

Adoption rule:

- no behavior defaults change except low-overhead logging/counters if proven negligible.

### Phase 1: Low-Risk Library Stabilization

Deliverables:

- Netty 4.2.12 aligned upgrade,
- Velocity native stable `3.4.0` pin,
- zstd-jni `1.5.7-7` upgrade,
- JCTools `4.0.6` upgrade.

PR split:

- one dependency family per PR,
- benchmark before/after,
- no unrelated code cleanup.

Adoption rule:

- adopt if safety passes and performance is neutral or better,
- for Velocity native stable pin, adopt if performance is neutral because reproducibility improves safety.

### Phase 2: io_uring Experiment

Deliverables:

- io_uring dependency,
- config flags,
- event-loop holder,
- transport selection logging,
- Linux benchmark results.

Adoption rule:

- keep disabled by default until target Linux kernels show repeatable benefit,
- enable only for known-good deployment profiles.

### Phase 3: Java 25 Code-Level Experiments

Deliverables:

- Scoped Values proof of concept only for bounded context,
- Structured Concurrency proof of concept only in lab builds, not production networking,
- Vector API proof of concept only after JFR identifies a bulk CPU hotspot.

Adoption rule:

- do not merge enabled hot-path changes without benchmark proof and simple fallback.

## 24. PR Breakdown

### PR 0: Parallel Flush Safety Gate

Files likely touched:

- `Connection.java`
- `ShreddedPaperChunkTicker.java`
- benchmark runner files under `tools/benchmark`

Scope:

- record `flushQueue()` return values and caller modes,
- compare `flushQueueInParallel=false` and `flushQueueInParallel=true`,
- prove whether PLAY pending queues are drained when the optimization is enabled,
- add a safe fallback or disable the default if the queue is not drained.

Acceptance:

- no PLAY packet backlog is left undrained because `flushQueue()` returned `false`,
- network benchmark records queue drain counts,
- further transport work uses the proven-safe flush configuration as baseline.

### PR 1: Network Measurement Foundation

Files likely touched:

- `Connection.java`
- `ServerConnectionListener.java`
- `EventLoopGroupHolder.java`
- benchmark runner files under `tools/benchmark`
- region load test plugin if fake client coordination is added there

Scope:

- transport selection logging,
- network summary output,
- sampled event-loop delay,
- flush counters,
- benchmark artifact fields.

Acceptance:

- overhead under 1% in baseline benchmark or disabled by default.

### PR 2: Netty 4.2.12 Alignment

Files likely touched:

- `shreddedpaper-server/build.gradle.kts`
- version catalog or dependency constraints if introduced

Scope:

- align Netty 4.2 artifacts,
- verify dependency insight,
- no code behavior changes.

Acceptance:

- smoke and network benchmark pass.

### PR 3: Native Helper Patch/Stability Updates

Files likely touched:

- `shreddedpaper-server/build.gradle.kts`

Scope:

- Velocity native stable pin,
- zstd-jni patch update,
- JCTools patch update if kept in same dependency-only batch after separate measurement.

Preferred split:

- Velocity native separate,
- zstd-jni separate,
- JCTools separate.

Acceptance:

- no benchmark or stress regression.

### PR 4: io_uring Gated Transport

Files likely touched:

- `EventLoopGroupHolder.java`
- `ServerConnectionListener.java` only if extra channel selection is needed
- `ShreddedPaperConfiguration.java`
- `shreddedpaper-server/build.gradle.kts`

Scope:

- add holder,
- add config,
- add fallback,
- add logs,
- add Linux-only benchmark docs.

Acceptance:

- default off,
- all platforms smoke,
- Linux enabled benchmark improvement required before recommending opt-in.

### PR 5: Java 25 Runtime Profile Documentation

Files likely touched:

- benchmark config examples,
- launch scripts if applicable,
- documentation.

Scope:

- Compact Object Headers benchmark profile,
- results recording,
- operator opt-in note if beneficial.

Acceptance:

- no default server launch change until soak validates it.

### PR 6: Java 25 Code Experiments

Files likely touched:

- only after hotspot proof.

Scope:

- Scoped Values, Structured Concurrency, or Vector API experiments.

Acceptance:

- disabled or fallback path present,
- benchmark proof,
- correctness proof.

## 25. Configuration Requirements

Add network-specific config only when a feature needs runtime control.

Initial config proposal:

```yaml
network:
  instrumentation:
    enabled: false
    sample-rate: 1024
    event-loop-delay-probe: false
  io-uring:
    enabled: false
    prefer-over-epoll: false
    log-selection: true
```

Do not add config for pure dependency upgrades.

Config compatibility:

- missing config values must default to existing behavior,
- invalid values must be sanitized or rejected with clear logs,
- config reload behavior must be documented if not live-reloadable.

## 26. Observability Requirements

Startup logs must include:

- selected Netty transport,
- native transport availability reason if disabled,
- Netty version,
- Velocity native compression availability,
- Velocity native cipher availability,
- Java version,
- compact object headers profile status when detectable or configured.

Benchmark logs must include:

- selected transport per run,
- native compression/cipher status,
- flush consolidation enabled/disabled,
- ShreddedPaper network optimization toggles,
- io_uring config values.

Operator-facing diagnostics should eventually expose:

- active connections,
- average keepalive latency,
- event-loop delay summary,
- flush queue summary,
- selected transport.

## 27. Risk Register

| Risk | Severity | Mitigation |
| --- | --- | --- |
| `flushQueueInParallel` does not drain PLAY queues | High | P0 true/false benchmark, return-value counters, fallback or default disable |
| Netty patch upgrade changes behavior | Medium | one-version-family alignment, smoke, leak detector, direct rollback |
| io_uring fails on some kernels | High | default off, availability check, epoll fallback |
| Native dependency missing classifier | Medium | CI/runtime startup on each target platform |
| Compact Object Headers exposes JVM/runtime issue | Medium | benchmark profile first, no default change |
| Instrumentation overhead hides gains | Medium | sampling, disabled default if needed |
| Flush metric collection changes timing | Medium | aggregate counters, no allocation per packet |
| Compression/cipher native change breaks edge cases | High | threshold tests, encryption tests, fallback |
| JCTools update changes concurrency behavior | Medium | mailbox stress, ownership stress, boundary torture |
| Benchmark fake clients fail to model real clients | Medium | pair synthetic metrics with real login/chunk smoke |

## 28. Decision Policy

Use the following rule for each candidate:

1. If it fails safety validation, reject or keep disabled.
2. If it changes protocol behavior, reject.
3. If it improves any primary metric repeatably and no safety metric regresses, adopt.
4. If it is performance neutral but improves reproducibility or patch-line stability with no safety regression, adopt for low-risk dependency cases.
5. If it is neutral and operationally risky, keep disabled or defer.
6. If it is faster only in a synthetic benchmark but worse in integrated scenarios, defer.

## 29. Definition Of Done

This PRD is complete when:

- candidates are classified by risk and rollout phase,
- benchmark and safety gates are defined,
- dependency upgrade boundaries are clear,
- Java 25 features are mapped to realistic use cases,
- unsafe ideas are explicitly rejected,
- implementation PRs can be created independently.

Implementation is complete only when:

- chosen PRs pass build, smoke, stress, and benchmark gates,
- raw benchmark artifacts are stored,
- fallback behavior is tested,
- operator-facing defaults are documented,
- rollback steps are simple and verified.

## 30. Initial Recommendation

The recommended first sequence is:

1. Validate `flushQueueInParallel` with return-value counters and true/false benchmark comparison.
2. Add measurement and benchmark fields for network behavior.
3. Align Netty to `4.2.12.Final`.
4. Pin Velocity native to stable `3.4.0` if no regression appears.
5. Upgrade zstd-jni and JCTools in separate small PRs after targeted smoke.
6. Add Java 25 Compact Object Headers benchmark profile.
7. Add io_uring behind config and keep it disabled by default until Linux benchmark evidence is positive.
8. Consider Scoped Values only after bounded-context audit, and keep Structured Concurrency and Vector API in lab-only experiments unless a concrete hotspot or safety case is proven.

This order maximizes the chance of capturing safe small wins quickly while keeping higher-risk transport and Java API experiments reversible.
