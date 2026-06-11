# E2E Production Performance Audit - 2026-05-16

This audit follows the production path from server tick entry to region workers,
ownership locks, mailbox dispatch, chunk I/O, world generation, and network
egress. It is intentionally scoped to improvements that can plausibly move
real production latency or throughput, not cosmetic refactors.

## Executive Summary

The highest-value production targets are:

1. **Shard chunk I/O retry draining by region owner or stripe.** Current chunk
   executor backlog/deferred/backpressure retry queues are coordinated by one
   static single-thread scheduled executor. Historical benchmark data already
   identifies chunk load/generation throughput as the fork's weakest area.
2. **Let normal workers steal already-due degraded ticks when normal work is not
   ready.** Degraded workers can already steal normal work, but normal workers
   block on the normal delay queue while degraded work can be due.
3. **Wire and harden the C2ME density function compiler.** The config defaults
   it on, and the compiler exists, but the audited call graph did not find a
   non-definition `BytecodeGen.compile(...)` or `McToAst.wrapVanilla(...)`
   integration point.
4. **Cache loaded-cell presence for scheduled ticks.** Every scheduled tick
   cell can still scan all chunks in the cell through loaded-chunk lookups.
5. **Treat SIMD and compact object headers as measured experiments, not
   assumptions.** JDK 25 ships Vector API as a tenth incubator and Compact
   Object Headers as a product feature, but both need per-workload gates.

Critical re-review: none of these changes is side-effect-free. The safe path is
to land instrumentation first, ship implementation behind config flags, and only
change defaults after benchmark and runtime evidence show no control-p99,
ownership, watchdog, or log regression.

Already-resolved local improvements observed during this audit include primitive
sorted owner snapshots for the region scheduler, ordinal-array mailbox queues,
async ownership sampling instead of full-stack capture on every fallback, and
runtime state lifecycle diagnostics.

## Critical Re-Review

The first version of this document was directionally useful but too optimistic
in three places:

1. **Chunk retry "owner-local scheduling" is riskier than stated.** If retry
   drains depend on a region tick, retries can be delayed when a region is
   retired, rescheduled, starved, or not currently ticking. The safer first
   implementation is a small sharded scheduled executor keyed by owner id or
   cell key. Owner-local retry drains are only acceptable after a separate wakeup
   design proves they are not coupled to tick cadence.
2. **Normal-worker degraded stealing must not steal quarantined work.** The
   degraded queue currently receives both `DEGRADED` and `QUARANTINED` regions.
   A normal worker taking arbitrary entries from that queue can break the
   intended quarantine isolation. The steal rule must filter to `DEGRADED` only,
   or the scheduler must split degraded and quarantined queues first.
3. **The density function compiler is not safe to just wire and enable.** The
   compiler path is synchronized, uses a strong class cache, creates generated
   classes, and currently dumps class files. It needs opt-in shadow comparison,
   counters, class-dump gating, and metaspace/cache observation before it can be
   considered for default production use.

The practical resolution order is:

1. Add metrics and side-effect detectors first.
2. Implement one feature behind a disabled-by-default or conservative config.
3. Run targeted unit/stress tests.
4. Run benchmark smoke, then the chunk-heavy or worldgen-heavy standard suite.
5. Only then consider changing defaults.

## E2E Path Traced

The independent ticking entry point is
`shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java`.
`tickChunks(...)` activates entities, reconciles players, registers armed
regions with `RegionTickScheduler`, and returns immediately to let region
workers run the actual tick loop (`ShreddedPaperChunkTicker.java:46-64`).

Each region worker then acquires exact owner/isolation locks, ticks region-local
queues, players, scheduled ticks, chunks, block events, entities, tracking,
block entities, broadcasts, and records tick stats
(`ShreddedPaperChunkTicker.java:160-226`). That path crosses:

- `RegionTickScheduler` for normal/degraded delay queues and per-region tick
  admission.
- `ShreddedPaperRegionLocker` for exact write and isolation locks.
- `RegionMailbox` for owner-local task ordering and backpressure.
- `RegionChunkIoTracker` and `RegionChunkExecutorLimiter` for chunk I/O QoS.
- C2ME density-function compiler classes for possible worldgen math speedups.
- Netty transport/dependency selection for network egress.

The audit uses `BENCHMARK_RESULTS_2026-04-27.md` as baseline evidence only.
That run says chunk throughput is a known catch-up area, and
`tools/benchmark/README.md` makes `Chunk/sec` an explicit benchmark metric.

## P0 - Shard Chunk I/O Retry Draining

**Current path.** `RegionChunkExecutorLimiter.createTask(...)` wraps chunk
generation/load/save/compression/region-file tasks in a `PermitTask` when
independent region ticking is enabled (`RegionChunkExecutorLimiter.java:82-98`).
Admission calls `RegionChunkIoTracker.tryAcquireExecutor(...)`; deferred,
overflow, and backpressure paths use retry callbacks
(`RegionChunkExecutorLimiter.java:312-408`).

`RegionChunkIoTracker` currently has one static
`EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR`, a single-thread scheduled executor
(`RegionChunkIoTracker.java:26`). It schedules backlog, deferred, and
backpressure drains (`RegionChunkIoTracker.java:714`, `749`, `783`) over
per-tracker `ConcurrentLinkedQueue` instances
(`RegionChunkIoTracker.java:1017-1019`). Each drain processes bounded batches.

**Production issue.** A single global retry coordinator can become the serialized
tail of otherwise parallel chunk work. Under region-local load, many regions can
have retryable tasks ready, but their retry callbacks must first pass through
one scheduler thread. That is especially relevant because the benchmark docs
already mark chunk load/generation throughput as the area that must catch up.

**Improvement.** Replace the single retry executor with a small fixed shard set
keyed by `ownerId` or `RegionPos.toLong()`. Keep each individual
`RegionChunkIoTracker` queue FIFO, but allow different regions to drain through
different scheduler threads.

Keep the existing per-region cap checks in `RegionChunkIoTracker`; shard only
the retry drain trigger. Preserve current non-dropping semantics by keeping the
same deferred/backlog/backpressure queues and admission result state machine.
Do not make region workers responsible for retry draining unless a later design
adds an independent wakeup path that works for idle, retiring, and detached
regions.

**Blockers and safe resolution.**

- Ordering blocker: cross-region global FIFO cannot be assumed necessary
  because admission is already region-local. Preserve FIFO within each
  `RegionChunkIoTracker` queue, and document that inter-region retry order is
  intentionally best-effort.
- Over-admission blocker: retry callbacks must re-run `tryAcquireExecutor(...)`
  exactly as today. The sharded executor must never grant permits itself.
- Debuggability blocker: add queue depth, oldest retry age, drain batch latency,
  and shard id to the existing chunk I/O JFR/diagnostic path before flipping the
  default.
- CPU contention blocker: more drain threads can increase CAS contention on the
  same per-region counters. Start with 2-4 shards, expose the shard count in
  config, and compare CAS pressure before/after with JFR or async-profiler.
- Shutdown blocker: a sharded scheduler introduces more executors to stop. Tie
  lifecycle to `RegionTickScheduler.shutdownGlobal()` or a server lifecycle hook
  and add a shutdown test that leaves no live retry threads.

**Validation gate.** Add a unit stress test for non-dropping retry execution,
then run `tools/benchmark/run-benchmarks.sh --suite smoke --engine
our-shreddedpaper --skip-build` followed by a standard chunk-heavy suite. Accept
only if `Chunk/sec` improves, control p99 does not regress, oldest retry age
drops, and no retry queue is abandoned during shutdown.

## P0 - Normal Idle Steal of Due Degraded Work

**Current path.** `RegionTickScheduler` owns two `DelayQueue<RegionHandle>`
instances for normal and degraded regions (`RegionTickScheduler.java:42-43`).
The constructor enables normal-worker degraded stealing only when there are no
degraded threads (`RegionTickScheduler.java:56`). With degraded workers
configured, a normal worker calls `normalQueue.take()` and waits for normal work
(`RegionTickScheduler.java:229-237`). A degraded worker already polls degraded
work, then polls normal work if degraded work is unavailable
(`RegionTickScheduler.java:240-246`).

**Production issue.** The worker-lane policy is asymmetric. If normal regions
are waiting on future delay deadlines and degraded regions are already due,
normal threads can be idle while work is pending. That wastes CPU exactly when a
local overload needs help.

**Improvement.** Change normal workers to:

1. poll the normal queue until the next normal deadline or a small cap,
2. if no normal handle is ready, steal only already-due handles whose current
   load class is exactly `DEGRADED`,
3. enforce a steal budget per worker per second.

This keeps normal latency as the priority while allowing otherwise idle capacity
to drain degraded regions.

**Blockers and safe resolution.**

- Normal p99 blocker: never steal a degraded task while a normal task is due.
  Use `DelayQueue.peek().getDelay(...) <= 0` checks and a bounded steal budget.
- Quarantine blocker: `QUARANTINED` entries currently share the degraded queue.
  Either split `DEGRADED` and `QUARANTINED` queues, or re-check
  `handle.state.overloadController().loadClass()` after poll and immediately
  requeue quarantined handles without ticking them on a normal worker.
- Overload accounting blocker: record stolen tick count, stolen duration, and
  victim overload class in the existing region tick diagnostics.
- Diagnostic blocker: `RegionSchedulerEvent.sourceQueue` currently collapses
  non-`DEGRADED` classes to `normal`, which can mislabel quarantined handles.
  Fix this before using scheduler events to judge steal safety.
- Starvation blocker: if normal p99 rises in benchmark or live diagnostics,
  reduce the steal budget or disable the feature by config.

**Validation gate.** Add a scheduler test with one future normal handle and one
due degraded handle, plus a benchmark scenario where one region is degraded and
neighboring normal regions remain latency-sensitive. Add a negative test proving
normal workers do not tick quarantined handles.

## P0 - Wire and Harden the Density Function Compiler

**Current path.** The config defaults `enable-density-function-compiler` to true
(`ShreddedPaperConfiguration.java:376-379`), and Divine config mirrors the same
default. The C2ME compiler exists in
`com/ishland/c2me/opts/dfc/common/gen/BytecodeGen.java`. `McToAst.wrapVanilla`
can wrap a vanilla density function into an AST-backed density function, and
`BytecodeGen.compile(...)` can create a `CompiledDensityFunction`.

The audited repository search found only definitions for `wrapVanilla` and
`BytecodeGen.compile(...)`, not a production call site that wires the compiler
into vanilla worldgen density function construction.

**Production issue.** World generation is one of the measured weak points, but a
default-enabled compiler that is not wired provides no production speedup. If it
is wired later without hardening, first-use compilation also writes generated
classes under `./cache/c2me-dfc` and deletes that directory in a static
initializer (`BytecodeGen.java:210-240`), which is not acceptable as silent
production I/O.

**Improvement.**

1. Keep production default conservative until integration evidence exists. The
   current config default says enabled, but the audited call graph did not prove
   active use.
2. Wire `McToAst.wrapVanilla(...)` and `BytecodeGen.compile(...)` at the actual
   Minecraft density-function construction/map point for 1.21.11 behind an
   opt-in flag.
3. Gate class dumping behind an explicit debug config and leave it off in
   production.
4. Add a startup diagnostic counter: requested density functions, compiled
   density functions, skipped constants, compile failures, fallback invocations.
5. Keep fallback to the original density function for unsupported AST nodes and
   blending edge cases.
6. Add a shadow-compare mode that computes both vanilla and compiled outputs for
   sampled positions and reports divergence without changing world output.

**Blockers and safe resolution.**

- Determinism blocker: add golden tests comparing vanilla and compiled output
  over sampled seeds, dimensions, and coordinates. Floating-point differences
  must be explicitly bounded or rejected.
- Version-drift blocker: wire through a small adapter class isolated to the
  Mojang generation mapping point, so future version updates fail near one file.
- Startup I/O blocker: class dump is disabled unless a debug config is set.
- Safety blocker: compile exceptions must return the original density function,
  not fail world creation.
- Compilation bottleneck blocker: `compile0(...)` is synchronized and still
  performs AST/codegen work on the cached path to derive constructor arguments.
  Warm up at datapack/worldgen initialization, record compile wall time, and do
  not compile from latency-sensitive chunk worker paths.
- Metaspace/cache blocker: generated classes are cached strongly by AST. Track
  compiled class count and metaspace growth across reloads; if datapack reload
  can create unbounded AST variants, add cache invalidation or keep the feature
  opt-in only.
- Visitor mutation blocker: compiled density functions rewrite argument lists in
  `mapAll(...)`. Add tests for vanilla visitors, blending-aware visitors, and
  datapack reload before accepting the integration.

**Validation gate.** Add a worldgen microbenchmark and a `RegionLoadTest`
chunk-generation scenario. Accept only with compile counters proving that the
compiler is actually used, shadow comparison showing no unacceptable divergence,
and metaspace/RSS staying neutral across repeated world creation or reload.

## P1 - Cache Loaded-Cell Presence for Scheduled Ticks

**Current path.** Region ticks call `processScheduledTicks(...)`
(`ShreddedPaperChunkTicker.java:181`). It iterates owner cells, verifies write
ownership, checks scheduled tick data, and then calls
`hasLoadedChunkInCell(...)` (`ShreddedPaperChunkTicker.java:275-323`). That
method scans every chunk coordinate in the cell until it finds a loaded chunk
(`ShreddedPaperChunkTicker.java:330-339`).

**Production issue.** On worlds with many owner cells and sparse loaded chunks,
scheduled tick processing can perform many repeated loaded-chunk lookups per
tick even when the answer changes only on chunk load/unload or ownership
layout changes.

**Improvement.** Maintain a loaded chunk count per region cell that matches the
same definition as `getChunkAtIfLoadedImmediately(...)`. Update it from the
chunk-source load/unload lifecycle or prove that the `LevelChunkRegion` add/remove
path is exactly equivalent. Rebuild it on split/merge. Then
`hasLoadedChunkInCell(...)` becomes an O(1) counter check.

**Blockers and safe resolution.**

- Chunk lifecycle blocker: make the count derived from the same add/remove path
  that updates the region's chunk collection. Add assertions in dev builds that
  a periodic full scan matches the counter.
- Definition blocker: `LevelChunkRegion` membership may not always mean the
  same thing as `getChunkAtIfLoadedImmediately(...)`. The counter must be
  validated against the current slow scan before replacing it.
- Split/merge blocker: rebuild counts from the new owner cell sets during
  layout transitions instead of trying to transfer partial deltas.
- Correctness blocker: the counter only replaces the loaded-presence guard; it
  must not change block/fluid tick ordering or ownership checks.
- Benefit blocker: the existing `scheduledTickPresenceGuard` can skip many
  empty cells before loaded scans. Profile the slow-scan count first; do not
  ship a new cache if the guard already removes the hot path.

## P1 - Reduce Contention in Chunk QoS Counters

**Current path.** `RegionChunkIoTracker` uses multiple adjacent `AtomicInteger`
and `AtomicLong` counters for in-flight, deferred, rejected, downgraded,
backlog, emergency, and retry accounting (`RegionChunkIoTracker.java:975-1013`).

**Production issue.** These counters are touched from chunk worker threads,
region workers, and retry drainers. Under chunk-heavy load, adjacent atomics can
create avoidable cache-line contention. This is a secondary target after retry
sharding because the single retry coordinator is the clearer serialization
point.

**Improvement.**

- Keep exact `AtomicInteger` counters for admission caps; do not replace them
  with `LongAdder`.
- Convert diagnostic-only `AtomicLong` counters to `LongAdder` where exact
  instantaneous values are not needed.
- Manually pad or group high-frequency cap counters if profiling shows false
  sharing. `@Contended` is only useful when JVM flags allow it, so do not depend
  on it as the only solution.

**Validation gate.** Use async-profiler/JFR before and after. Accept only if CAS
or cache-miss pressure drops without changing cap enforcement.

**Side-effect review.** `LongAdder` increases memory footprint and returns
non-atomic snapshots across multiple counters. It is acceptable for cumulative
diagnostics, not for pressure calculations, queue limits, or admission caps.
Manual padding also increases memory footprint per active region, so it must be
justified by allocation/cache-miss profiles.

## P1 - SIMD for Pure Density Arithmetic

**External basis.** OpenJDK JEP 508 delivers the Vector API in JDK 25 as the
tenth incubator. It is designed to express vector computations that compile to
efficient vector instructions on supported CPUs, with graceful degradation when
vector hardware is not available.

**Improvement.** After the density function compiler is wired and measured, add
a vectorized `evalMulti` path only for pure arithmetic AST nodes such as add,
mul, min, max, square, cube, and y-clamped gradients. Do not vectorize calls
that cross into noise samplers until a benchmark proves the call overhead and
data layout are favorable.

**Blockers and safe resolution.**

- Incubator blocker: JDK 25 still requires the incubator module path for Vector
  API usage. Keep scalar code as the default and gate vector code behind build
  and runtime flags.
- Numerical blocker: compare scalar and vector outputs over the same golden
  samples used for compiled density functions.
- Complexity blocker: implement only lane-wise arithmetic nodes first. Avoid
  broad AST rewrites until this narrow path proves a measurable win.

## P2 - JDK 25 Compact Object Headers Profile

**External basis.** OpenJDK JEP 519 changes Compact Object Headers from an
experimental feature to a product feature in JDK 25, while explicitly not making
it the default object-header layout. The JEP cites lower heap and CPU usage in
object-heavy benchmarks, which makes it relevant to a Minecraft server with many
entities, blocks, chunks, packets, tasks, and plugin objects.

**Improvement.** Use and extend the existing
`tools/runtime/java25-network-profile.mjs` profile tool, which already owns the
`-XX:+UseCompactObjectHeaders` flag and has tests. The remaining work is to
connect that profile to the benchmark runner and collect comparable smoke and
standard-suite evidence. This is a deployment/runtime tuning candidate, not a
source-code change.

**Blockers and safe resolution.**

- Compatibility blocker: run the same plugin and chaos gates as normal because
  native agents, profilers, or plugins can have JVM-layout assumptions.
- Evidence blocker: accept only if RSS max, GC count/time, and control p99
  improve or stay neutral under the benchmark suite.
- Rollout blocker: expose it as an operator profile, not a default, until live
  production data confirms the benchmark result.

## P2 - Network and Compression Dependency Patch Line

**Current path.** The build uses JCTools `4.0.6`, Netty BOM `4.2.12.Final`,
io_uring natives pinned to the same Netty patch line, zstd-jni `1.5.7-7`, and
fastutil through the upstream dependency graph (`shreddedpaper-server/build.gradle.kts:178-210`).

**External basis checked on 2026-05-16.**

- Maven Central metadata reports Netty BOM `4.2.13.Final` as latest/release.
- Maven Central metadata reports zstd-jni `1.5.7-8` as latest/release.
- Maven Central metadata reports JCTools `4.0.6` as latest/release.
- Maven Central metadata reports fastutil `8.5.18` as latest/release.

**Improvement.** Bump Netty from `4.2.12.Final` to `4.2.13.Final`, including
io_uring native artifacts, and zstd-jni from `1.5.7-7` to `1.5.7-8`. Do not
change JCTools or fastutil just for churn.

**Blockers and safe resolution.**

- Native transport blocker: smoke epoll/kqueue/io_uring selection and fallback
  behavior. Keep the full Netty line aligned.
- Compression blocker: run chunk/network compression smoke and startup tests.
- Regression blocker: treat this as P2 because dependency patch bumps are less
  likely to beat the chunk retry and DFC fixes.

## P2 - Primitive-Key Region Lock Map Experiment

**Current path.** The hot exact-lock overload already accepts sorted `long[]`
owner and isolation snapshots (`RegionTickScheduler.java:495-507`), but
`ShreddedPaperRegionLocker` still stores locks in
`ConcurrentHashMap<RegionPos, LockedRegion>` and creates `RegionPos` wrappers
inside long-key lock acquisition paths (`ShreddedPaperRegionLocker.java:34`,
`303-318`, `371`, `613`).

**Improvement.** Prototype a primitive-long keyed lock table or striped lock map
to remove wrapper allocation and hash overhead from the region tick lock path.

**Blockers and safe resolution.**

- Concurrency blocker: the current `ConcurrentHashMap` semantics are simple and
  mature. Keep this behind a branch/prototype until stress tests cover dynamic
  split, merge, unload, and leaked-lock cleanup.
- Risk blocker: only proceed if allocation profiles show `RegionPos` wrappers
  are a real contributor after P0/P1 fixes.

## Rejected or Deferred Ideas

- Broadly parallelizing tracker/network flush inside independent region ticking
  is deferred. Packet ordering and player connection latency are too easy to
  regress without a focused design.
- Replacing exact admission counters with approximate counters is rejected.
  Approximate counters are fine for diagnostics, not for chunk I/O caps.
- Large lock-map rewrites are deferred until profiling proves the lock wrapper
  allocation is still visible after chunk retry sharding.

## Verification Addendum - 2026-05-16

This second pass verifies the claims that can be checked locally now and marks
the remaining items that still need new tests or production-style benchmarks.

### Verified Locally

Commands run:

1. `node --test tools/runtime/java25-network-profile.test.mjs`
2. `java -version`
3. `node tools/runtime/java25-network-profile.mjs --profile
   compact-object-headers --verify --metadata /tmp/shreddedpaper-coh-profile.json`
4. `node tools/async-audit/scan-async-ownership.mjs --fail-on-critical`
5. `./gradlew :shreddedpaper-server:test --rerun-tasks
   --no-configuration-cache --stacktrace`
6. `./gradlew -p tools/region-load-test-plugin build --no-configuration-cache
   --stacktrace`
7. `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks
   --no-configuration-cache --stacktrace`
8. `bash tools/benchmark/run-benchmarks.sh --config
   /tmp/shreddedpaper-benchmark-astatine.json --suite smoke --engine
   our-shreddedpaper --scenario chunk-generation --repeat 1 --skip-build
   --fail-on-error`

Results:

- Java 25 is available locally: Temurin `25.0.2`.
- The compact-object-headers profile unit test passed: 9/9 Node tests.
- The compact-object-headers runtime verification accepted
  `-XX:+UseCompactObjectHeaders` on the local JDK and wrote metadata to
  `/tmp/shreddedpaper-coh-profile.json`.
- Async ownership scanner reported `critical=0 high=0 medium=0`.
- `:shreddedpaper-server:test --rerun-tasks` passed. The first attempt to run
  individual JUnit classes failed because this project includes only
  `**/**TestSuite.class`; rerunning through the configured suite task passed.
- `tools/region-load-test-plugin` built successfully.
- `:shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks` passed.
- Smoke benchmark `chunk-generation` ran successfully with a temporary config
  that points `our-shreddedpaper` to the generated `astatine-paperclip` jar.
  Result artifact:
  `run/benchmarks/results/2026-05-15T174954Z/benchmark-summary.md`.

Smoke benchmark result:

| Engine | Scenario | Survival | Clean shutdown | Control p99 | Tick p99 | Server MSPT p99 | Chunk/sec | High signal |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| our-shreddedpaper | chunk-generation | yes | yes | 0.20 ms | 1.78 ms | 1.16 ms | 11.77 | 0 |

This benchmark proves the current tree can run the chunk-generation smoke
without high-signal failures. It does not prove the P0 chunk retry sharding
improvement, because no sharding implementation exists yet and this was not a
comparative run against Paper/Folia/Purpur/DivineMC.

### Verified Static Findings

- Chunk retry drains still use one static single-thread scheduled executor:
  `RegionChunkIoTracker.EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR`.
- Backlog, deferred, and backpressure drain scheduling still route through that
  executor. The P0 retry sharding proposal remains valid.
- Normal workers still block on `normalQueue.take()` when degraded threads
  exist. The asymmetric steal opportunity remains valid.
- `RegionTickScheduler.enqueue(...)` places both `DEGRADED` and `QUARANTINED`
  regions into `degradedQueue`. Any future normal-worker stealing must filter or
  split those classes.
- `RegionSchedulerEvent.sourceQueue` currently reports only `DEGRADED` as
  `degraded` and all other load classes as `normal`; this can mislabel
  quarantined handles in diagnostics.
- The audited search still found no non-definition call site for
  `BytecodeGen.compile(...)` or `McToAst.wrapVanilla(...)`. DFC appears present
  but not wired into production worldgen.
- `BytecodeGen.compile0(...)` is synchronized, class dumping is still called on
  class generation, and generated classes are stored in a strong
  `compilationCache`.
- Scheduled ticks still use `getChunkAtIfLoadedImmediately(...)` for the loaded
  cell guard. `LevelChunkRegion.add/remove` exists, but this pass did not prove
  that region membership is exactly equivalent to loaded-chunk presence.
- Maven Central metadata still reports Netty BOM `4.2.13.Final` and zstd-jni
  `1.5.7-8` as newer than the checked-in `4.2.12.Final` and `1.5.7-7`.
  JCTools `4.0.6` and fastutil `8.5.18` remain current.

### Remaining Unverified Items

- No existing test covers chunk retry sharding because the implementation does
  not exist. Required next test: deterministic non-dropping retry execution with
  multiple retry shards and forced shutdown.
- No existing test covers normal-worker degraded stealing because that behavior
  is not implemented. Required next test: due `DEGRADED` can be stolen when no
  normal work is due; `QUARANTINED` must never be ticked by a normal worker.
- DFC still needs new golden/shadow-compare tests. The current local checks only
  prove that it is unwired and has the risk points described above.
- Scheduled tick loaded-cell caching still needs an equivalence assertion that
  compares any proposed counter against the current slow scan during chunk
  load/unload and split/merge.
- Counter padding/`LongAdder` changes still need JFR or async-profiler evidence.
  No profiling run was performed in this pass.
- Compact Object Headers has flag/tool verification, but not a full benchmark
  comparison. It still needs baseline-vs-COH smoke and standard suite results
  before changing any runtime recommendation.
- The benchmark runner config is stale for the current artifact name: it expects
  `shreddedpaper-paperclip-...jar`, while the current build produces
  `astatine-paperclip-...jar`. The smoke run used a temporary config that only
  corrected this path.

## Validation Plan

Minimum gates for any implementation from this document:

1. `./gradlew applyAllPatches --no-configuration-cache`
2. `./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace`
3. Targeted unit tests for the touched area, especially
   `RegionMailboxTest`, `RegionOwnerTest`, scheduler tests, and chunk I/O retry
   tests.
4. `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`
   for any code touching region ownership, chunk tasks, or async callbacks.
5. `tools/benchmark/run-benchmarks.sh --suite smoke --engine our-shreddedpaper
   --skip-build`, followed by a standard chunk-heavy suite for P0 chunk work.
6. JFR/diagnostic review: region tick p95/p99, control p99, chunk/sec, retry
   queue oldest age, retry drain latency, and server log exceptions.

Success must be judged by production-like outcomes: no ownership failures, no
watchdog/log errors, no control p99 regression, and measurable improvement in
the target metric.

## Source Notes

Local code and docs audited:

- `README.md`
- `PERFORMANCE.md`
- `BENCHMARK_RESULTS_2026-04-27.md`
- `tools/benchmark/README.md`
- `tools/async-audit/ASYNC_OWNERSHIP_TODO.md`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionTickScheduler.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkIoTracker.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkExecutorLimiter.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionMailbox.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperRegionLocker.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/RegionOwner.java`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/ShreddedPaperAccess.java`
- `shreddedpaper-server/src/main/java/com/ishland/c2me/opts/dfc/common/ast/McToAst.java`
- `shreddedpaper-server/src/main/java/com/ishland/c2me/opts/dfc/common/gen/BytecodeGen.java`
- `shreddedpaper-server/build.gradle.kts`

External references checked:

- OpenJDK JEP 508, Vector API tenth incubator for JDK 25:
  https://openjdk.org/jeps/508
- OpenJDK JEP 519, Compact Object Headers product feature for JDK 25:
  https://openjdk.org/jeps/519
- Maven Central metadata:
  `io.netty:netty-bom`, `com.github.luben:zstd-jni`,
  `org.jctools:jctools-core`, and `it.unimi.dsi:fastutil`.
