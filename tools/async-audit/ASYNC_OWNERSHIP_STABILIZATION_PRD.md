# Async Ownership Stabilization PRD

Generated: 2026-04-27
Last updated: 2026-04-28
Owner: Codex
Related backlog: `tools/async-audit/ASYNC_OWNERSHIP_TODO.md`
Related scanner output: `tools/async-audit/async-ownership-candidates.json`

## Purpose

Ship independent region ticking with release-grade async ownership safety while
preserving Minecraft gameplay semantics and ShreddedPaper's async performance
advantage.

The backlog has many individual findings, but most are repeated instances of a
small number of unsafe access families. This document turns the backlog into a
parallelizable stabilization program with clear ownership, acceptance criteria,
helper contracts, codemod rules, verification gates, worktree split rules, and
handoff prompts for other agents.

## Current Snapshot

Initial snapshot on 2026-04-27 reported 1661 active candidates, dominated by
loaded-only reads and critical sync chunk load roots. After the root/helper pass,
the active scanner count is:

| Sink | Count | Work Type |
| --- | ---: | --- |
| active scanner candidates | 0 | no unexplained ownership hazards |
| tracked completed fixes | 28 | root/helper/grouped closures plus shutdown, Starlight, player interaction, and runtime validation gates |
| critical candidates | 0 | release gate satisfied at scanner level |
| latest integrated release gate | passed | `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen` passed on 2026-04-28 |

The scanner is a guardrail, not a release definition. Release readiness requires
the active scanner count to fall, the remaining count to be justified, and real
server evidence to show that hostile workloads no longer crash, hang, corrupt
region ownership, or leave the JVM stuck during shutdown. A clean compile or
`Active candidates: 0` must never be treated as sufficient by itself.

Current release status: **integrated async release gate passed for the current
branch**. Gate19, Gate36, and Gate37 all found runtime-only async ownership bugs
after the scanner reported zero candidates, so scanner output is still treated
only as a guardrail. The current branch now has a no-skip integrated gate pass
covering build, Paperclip jar, RLT plugin, scanner, root baseline, MCC chaos,
watchdog shutdown, managed isolated worldgen, and high-signal log scans.

Recent runtime blockers and closure:

- Gate19 found minecart boundary movement reaching `Entity.move` without write
  ownership and passenger path navigation calling a sync-load path from an
  independent region tick worker.
- Gate36 found `ServerboundUseItemPacket -> Level.fastClip` sync-loading during
  player use ray tracing.
- Gate37 found `Zombie.hurtServer -> SpawnPlacementTypes -> Level#getBlockState`
  sync-loading during entity spawn-position checks.
- Gate38 passed with natural spawns enabled, movement warnings fatal, and a
  clean high-signal log scan.
- The 2026-04-28 integrated gate then caught a Starlight live-lighting palette
  race and a worldgen smoke evidence parser gap. Both are fixed and the full
  `--isolated-worldgen` gate now passes.

## Product Requirements

### Must Have

- Independent region ticking remains enabled and useful under hostile load.
- A region worker never synchronously loads an absent chunk outside its safe
  ownership context.
- Cross-owner mutation is explicit: execute on the owning region, defer to the
  owning region, or fail hard with a clear ownership error.
- Read paths that can tolerate missing/unowned data use loaded-only access with
  an explicit fallback chosen by the call site's gameplay semantics.
- Read paths that cannot tolerate missing/unowned data prefetch/resume or defer
  to owner instead of substituting fake data.
- Bukkit API entry points reached from region workers either validate ownership,
  use already-loaded access, or schedule async load + owner resume.
- Existing `TickThread` and sync-load guards are not silenced to close TODOs.
- Every completed workstream has source patch, generated source, build, scanner,
  and targeted runtime evidence.

### Must Not Have

- No hidden global lock that serializes ordinary region ticking.
- No broad `try/catch` that turns ownership failures into ignored work.
- No blanket replacement of gameplay-critical reads with `AIR` or `null`.
- No change that makes vanilla/Paper commands appear successful while dropping
  the underlying block/entity operation.
- No false-positive closure without a reason recorded in the backlog item or
  grouped closure note.

### Non-Goals

- Full Folia API compatibility is not required for this stabilization pass.
- Replacing ShreddedPaper's region model with Folia's full `ThreadedRegionizer`
  is not required.
- Plugin ecosystem migration is not solved here beyond making unsafe access fail
  clearly or route through supported scheduler paths.

## Core Design

The work should follow a small set of reusable primitives instead of one-off
patches at every scanner finding.

### Access Intents

Each audited call site must be assigned exactly one intent:

| Intent | Meaning | Allowed Fix |
| --- | --- | --- |
| `LOADED_READ_OPTIONAL` | Missing/unowned data can be skipped without changing core outcome | loaded-only read helper with explicit fallback |
| `LOADED_READ_CONSERVATIVE` | Missing/unowned data should behave as no signal/no power/no fluid/no neighbor | loaded-only read helper with conservative fallback |
| `OWNER_REQUIRED_READ` | Correct result requires current owner context | owner check + owner handoff or async prefetch/resume |
| `OWNER_REQUIRED_WRITE` | Operation mutates chunk/entity/block/POI data | run immediately only if owned, else defer to owner |
| `ASYNC_PREFETCH_RESUME` | Operation legitimately needs a chunk that may be absent | async chunk load, then resume on owner, with epoch revalidation |
| `GLOBAL_REGION` | Operation owns global state, not chunk-local state | global queue or global thread check |
| `FALSE_POSITIVE` | Scanner matched but code is definition, generated-only, or safe by invariant | record reason and update scanner ignore if general |

### Helper Layer Contract

Create a small helper layer under `io.multipaper.shreddedpaper` before large
codemods. Names may change during implementation, but the contracts must stay
stable.

Proposed files:

- `io/multipaper/shreddedpaper/threading/ownership/ShreddedPaperAccess.java`
- `io/multipaper/shreddedpaper/threading/ownership/OwnershipIntent.java`
- `io/multipaper/shreddedpaper/threading/ownership/OwnerTaskResult.java`

Required helper contracts:

- `isOwned(ServerLevel level, BlockPos pos)`
  - True only when the current thread owns write access to the target cell or
    the current ticking region owner includes that cell.
- `isOwned(ServerLevel level, ChunkPos pos)`
  - Chunk variant of the same check.
- `getBlockStateIfSafe(Level level, BlockPos pos, BlockState fallback, OwnershipIntent intent)`
  - May only be used for `LOADED_READ_OPTIONAL` or `LOADED_READ_CONSERVATIVE`.
  - Uses loaded-only chunk access.
  - Does not sync-load.
  - Records optional debug counters when fallback is used on a region worker.
- `getFluidStateIfSafe(Level level, BlockPos pos, FluidState fallback, OwnershipIntent intent)`
  - Same contract as block-state read.
- `getBlockEntityIfOwnedOrLoaded(Level level, BlockPos pos)`
  - Returns null when unloaded or not safe to access.
  - Must not create block entities or sync-load chunks.
- `runOrDeferToOwner(ServerLevel level, BlockPos pos, RegionTaskClass taskClass, Runnable task)`
  - Runs immediately when owned.
  - Otherwise enqueues to the owner mailbox with affinity cell.
  - Revalidates ownership at execution time.
  - Does not silently drop critical/system work.
- `runOrDeferToOwner(ServerLevel level, ChunkPos pos, RegionTaskClass taskClass, Runnable task)`
  - Chunk variant.
- `setBlockEntityIfOwnedOrDefer(ServerLevel level, BlockEntity blockEntity, RegionTaskClass taskClass)`
  - Runs block-entity writes only against an already-loaded owned chunk.
  - Otherwise defers to the owner region and revalidates before writing.
  - Never calls a sync-loading chunk accessor.
- `prefetchFullChunkThenOwner(ServerLevel level, ChunkPos pos, boolean gen, Runnable task)`
  - Performs async chunk load through the existing chunk scheduler.
  - Resumes on the owner region.
  - Revalidates chunk presence and owner epoch before mutation.
- `assertNoRegionWorkerSyncLoad(ServerLevel level, ChunkPos pos, String reason)`
  - Fails hard if a region worker tries to sync-load a missing or unowned chunk.
  - Used at root sync-load methods so future regressions are caught early.

Fallback rules:

- `AIR` is valid only when vanilla/Paper already treats missing neighbor state as
  no block/no signal/no collision for that path.
- `Fluids.EMPTY.defaultFluidState()` is valid only for optional or conservative
  fluid reads.
- `null` is valid for optional block entity reads and skipped neighbor updates.
- Mandatory operations must use owner handoff or async prefetch/resume.

## Workstreams

### WS0 - Audit Tooling And Ledger

Goal: make the backlog actionable by helper family, not by thousands of flat
items.

Write scope:

- `tools/async-audit/scan-async-ownership.mjs`
- `tools/async-audit/ASYNC_OWNERSHIP_TODO.md`
- `tools/async-audit/*.json`
- optional new `tools/async-audit/codemods/*`

Tasks:

- [x] Add `family` to every scanner candidate.
- [x] Add default `intent` field support to candidates.
- [x] Add TODO summary by `family`.
- [ ] Add summary by `intent`, `path`, and `generated-vs-patch`.
- [x] Add scanner ignore support for helper definitions.
- [x] Add scanner ignore support for already-safe helper usage.
- [x] Add a `--fail-on-critical` mode for CI.
- [ ] Add a `--changed-files-only` mode for quick iteration.
- [ ] Add an output mode that lists candidates safe for mechanical rewrite.
- [x] Add a grouped close mechanism so one helper/root fix can close many TODO
  entries with shared evidence.
- [ ] Document scanner commands in the TODO file.

Acceptance:

- `node tools/async-audit/scan-async-ownership.mjs --write-todo` still works.
- The TODO document reports both raw count and grouped family count.
- Helper definitions do not appear as active candidates.

### WS1 - Helper Layer Foundation

Goal: implement the reusable ownership/read/write primitives before codemods.

Write scope:

- new helper files under `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/`
- minimal call sites needed to compile
- matching patch files only if generated source requires them

Tasks:

- [x] Add `OwnershipIntent`.
- [x] Add loaded-only block/fluid/block-entity access helpers.
- [x] Add owner check helpers for `BlockPos` and `ChunkPos`.
- [x] Add owner handoff helper using `LevelChunkRegionMap.scheduleTask`.
- [x] Add async prefetch/resume helper using existing Moonrise chunk scheduler.
- [x] Add basic counters for fallback and handoff volume.
- [ ] Add region-cell owner check overload if call sites need it.
- [ ] Wire counters into JFR fields/events if needed.
- [ ] Add unit-style tests if test harness can compile against helper layer.
- [x] Compile generated source.

Acceptance:

- No gameplay call sites are migrated yet except tiny compile smoke sites.
- `./gradlew applyAllPatches` passes.
- `./gradlew :shreddedpaper-server:compileJava --stacktrace` passes.

### WS2 - Critical Sync-Load Root Closure

Goal: remove or guard all critical sync chunk load roots reachable from region
workers.

Write scope:

- `CraftWorld.java.patch`
- `CraftChunk.java.patch`
- `Level.java.patch`
- `LevelReader.java.patch`
- `ServerChunkCache.java.patch`
- `ChunkTaskScheduler.java.patch`
- `FeatureHooks.java`
- `PlayerChunkSender.java`
- `RegionizedPlayerChunkLoader.java`

Tasks:

- [x] Guard `CraftWorld#getChunkAt` for region workers with owned-or-loaded
  behavior; keep Bukkit sync semantics only where safe.
- [x] Guard `CraftChunk#getHandle` and tile entity access.
- [x] Split `Level#getChunkAt` into root-owned, loaded-only, and sync-load
  variants.
- [ ] Add fail-hard guard at `ServerChunkCache#syncLoad` for region workers.
- [x] Convert player chunk load/unload events to use already-loaded `CraftChunk`
  or skip with evidence when the chunk is no longer loaded/sent.
- [x] Convert `FeatureHooks#getSentChunks` to already-loaded chunk snapshots.
- [x] Convert plugin chunk ticket reporting to avoid sync-loading chunks.
- [x] Convert command-block packet block entity mutation to owner-required
  mutation.
- [x] Re-scan and ensure `sync-load family` critical count is zero or every
  remaining candidate is a documented root definition false-positive.

Acceptance:

- `sync-load-call`, `getchunk-load-true`, and `bukkit-chunk-load` active
  candidates are closed or explicitly false-positive.
- MCC chaos sync-load bucket runs without `Thread failed main thread check` or
  sync-load crash.

### WS3 - Level Root Read/Write Semantics

Goal: make root `Level` methods safe so many leaf call sites become safe by
construction.

Write scope:

- `Level.java.patch`
- `SignalGetter.java.patch`
- related root accessor patch files

Tasks:

- [x] Audit `Level#getBlockState`.
- [x] Audit `Level#getFluidState`.
- [x] Audit `Level#getBlockEntity`.
- [x] Audit `Level#setBlock`.
- [x] Audit `Level#setBlockEntity`.
- [x] Audit `Level#removeBlockEntity`.
- [x] Audit `Level#blockEntityChanged`.
- [x] Add explicit owner-required variants where game logic must not fallback.
- [x] Add loaded-only variants where optional logic is intended.
- [x] Make scanner distinguish root method declarations from unsafe calls.

Acceptance:

- Root methods do not sync-load from region workers.
- Mandatory writes either own the target or enqueue to the owner.
- Leaf codemods can call helper/root variants without duplicating ownership
  logic.

### WS4 - Mechanical Loaded-Only Read Migration

Goal: safely eliminate the large read family using AST-guided codemods.

Write scope:

- Only files listed in codemod batch manifests.
- Do not mix unrelated manual fixes into mechanical batches.

Batch order:

- [ ] WS4-A redstone/neighbor/rail/tripwire/diode/piston read probes.
- [ ] WS4-B entity movement/collision/inside-block reads.
- [ ] WS4-C natural spawning and structure/worldgen optional probes.
- [ ] WS4-D Bukkit wrapper read probes.
- [ ] WS4-E uncategorized reads after manual intent review.

Codemod rules:

- [ ] Replace `level.getBlockState(pos)` only when intent is known.
- [ ] Replace `level.getFluidState(pos)` only when intent is known.
- [ ] Replace `level.getBlockEntity(pos)` only when null is valid.
- [ ] Insert fallback constants through helper calls, not ad hoc literals.
- [ ] Never rewrite inside helper definitions.
- [ ] Never rewrite code already using `getBlockStateIfLoaded`,
  `getFluidIfLoaded`, or the new helper.
- [ ] Generate a manifest listing every changed call site and its chosen intent.

Acceptance:

- Each batch has a manifest, build, scanner delta, and targeted chaos bucket.
- Scanner count falls by family.
- No batch changes more than one family unless explicitly approved.

### WS5 - Owner-Required Mutation Migration

Goal: make block/entity/POI mutations obey owner handoff without global
serialization.

Write scope:

- Piston/redstone write paths.
- Command packet mutation paths.
- ItemStack block entity restoration path.
- Worldgen-to-world block entity paths only after intent review.

Tasks:

- [x] Classify every `set-block-entity` candidate.
- [x] Classify every `bukkit-block-write` candidate.
- [x] Use `runOrDeferToOwner` for cross-owner mutations.
- [x] Add execution-time revalidation before mutation.
- [ ] Preserve event ordering where Bukkit/Paper APIs expect it.
- [ ] Add failure logging when mandatory mutation cannot be safely applied.

Acceptance:

- `set-block-entity` and `bukkit-block-write` candidates are closed or
  documented.
- Piston/redstone and command-block chaos fixtures do not crash.

### WS6 - Scheduled Tick And Neighbor Dispatch

Goal: finish scheduled tick handoff and remove remaining ownership holes.

Write scope:

- `LevelTicksRegionProxy.java`
- `ScheduledTickAccess.java.patch`
- `ShreddedPaperChunkTicker.java`
- block files with `scheduleTick` candidates

Tasks:

- [x] Revalidate loaded/full target chunk before block tick dispatch.
- [x] Revalidate loaded/full target chunk before fluid tick dispatch.
- [x] Ensure cross-cell scheduling always targets the owner region.
- [ ] Preserve relative tick deadlines across owner merge/split.
- [x] Add scanner false-positive handling for safe `LevelTicksRegionProxy`
  scheduling internals.
- [ ] Add chaos fixtures for leaf/fluid/redstone plus split/merge pressure.

Acceptance:

- `schedule-tick` active candidates are closed or routed through the proxy.
- Scheduled tick chaos bucket runs clean.

### WS7 - Entity Movement, Teleport, Player Chunk Send

Goal: close the highest-risk player/entity ownership races.

Write scope:

- `Entity.java.patch`
- `ServerGamePacketListenerImpl.java.patch`
- `RegionizedPlayerChunkLoader.java.patch`
- `PlayerChunkSender.java`
- `ShreddedPaperPlayerTicker.java`
- `ShreddedPaperChangesBroadcaster.java`

Tasks:

- [ ] Complete entity movement loaded-only read migration.
- [ ] Ensure teleport target chunk access uses async prefetch/resume.
- [x] Add owner/current-loader validation to stale player chunk loader cleanup.
- [x] Revalidate current owner immediately before chunk send.
- [x] Requeue dirty chunk holder broadcasts to current owner instead of
  skipping.
- [x] Verify retired player schedulers cannot tick from stale regions.
- [ ] Add MCC boundary movement, teleport, disconnect/reconnect, and chunk send
  chaos cases.
- [ ] Re-run strict MCC vehicle/boundary gate after the Gate19 minecart movement
  fix.
- [ ] Re-run strict MCC mob passenger/path-navigation gate after the Gate19
  loaded-only navigation fix.

Acceptance:

- Player/entity chaos bucket runs clean.
- No stale holder broadcast loss is observed in targeted instrumentation.

### WS8 - Backpressure, Epoch, And Retry Safety

Goal: ensure async pressure cannot replay stale work against moved owners.

Write scope:

- `RegionMailbox.java`
- `RegionChunkExecutorLimiter.java`
- `RegionChunkIoTracker.java`
- `ShreddedPaperRegionSchedulerApiImpl.java`

Tasks:

- [ ] Add owner epoch to all delayed/retry work that mutates region-owned data.
- [ ] Add enqueue timestamp and expiry for stale async retry work.
- [x] Add bounded retry/backoff for plugin mailbox rejection where semantics
  require a retry rather than cancellation.
- [x] Ensure critical work remains non-dropping but observable under pressure.
- [ ] Add chaos scenario for mailbox pressure + split/merge + chunk IO pressure.

Acceptance:

- No stale retry mutates an owner after epoch mismatch.
- Queue pressure is visible without log spam.

### WS9 - Verification And Release Gates

Goal: turn "looks fixed" into repeatable release evidence.

Current status: **full integrated async release gate passed**. Gate38 superseded
the Gate36/Gate37 blockers for the touched paths, and the current branch now has
a no-skip integrated gate pass with build, MCC, watchdog, and managed worldgen
coverage.

Required commands before release:

- [x] `node --check tools/runtime/invoke-async-release-gates.mjs`
- [x] `node --check tools/runtime/verify-async-root-baseline.mjs`
- [x] `node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --skip-worldgen`
- [x] `node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen`
- [x] `./gradlew applyAllPatches`
- [x] `./gradlew :shreddedpaper-server:compileJava --stacktrace`
- [x] `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace`
- [x] `./gradlew -p tools/region-load-test-plugin build`
- [x] `node tools/async-audit/scan-async-ownership.mjs --write-todo`
- [x] `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`
- [x] `node tools/runtime/verify-async-root-baseline.mjs`
- [x] `node --check tools/runtime/scan-server-log.mjs`
- [x] `node --check tools/runtime/invoke-worldgen-smoke.mjs`
- [x] `node tools/runtime/scan-server-log.mjs run/mcc-chaos/results/cycle-01`
- [x] `node tools/runtime/scan-server-log.mjs /Users/jungwuk/Documents/works/worldgen/logs/latest.log`
- [x] MCC chaos scheduled tick bucket, minimum 10 minutes.
- [x] MCC chaos entity/player bucket, minimum 10 minutes.
- [x] MCC chaos sync-load/chunkgen bucket, minimum 10 minutes.
- [x] MCC chaos player chunk send/stale holder bucket, minimum 10 minutes.
- [x] MCC Gate38 targeted regression gate after Gate36/Gate37 fixes:
  `node tools/mcc-chaos/invoke-mcc-chaos-loop.mjs --root run/mcc-chaos-release-gate-38 --mcc-path run/mcc-chaos-release-gate-16/cache/mcc/MinecraftClient-20260415-429-osx-arm64 --bot-count 4 --min-bots 4 --duration-sec 180 --server-port 25567 --rcon-port 25577 --websocket-base-port 8070 --skip-build --enable-natural-spawns --agent-mode ReportOnly --fail-on-movement-warnings --seed 20260428073838`
- [x] RegionLoadTest chunkgen smoke at near and far coordinates.
- [x] RegionLoadTest redstone/fluid/leaf boundary fixtures.
- [x] Isolated managed worldgen smoke through `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`
  - The live `worldgen` server was already reachable on RCON port `25575`, so
    the smoke correctly refused to attach by default. Destructive RLT probes
    were run only in the isolated server directory on ports `25567`/`25577`.
- [x] Full integrated gate with build, Paperclip jar, RLT plugin, scanner, root
  baseline, MCC chaos, watchdog shutdown, managed isolated worldgen, and log
  scans: `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`
  passed on 2026-04-28 at 13:10 KST.
- [ ] Benchmark isolation smoke from `BENCHMARK_PLAN_2026-04-26.md`
  - Blocked in this macOS workspace: the benchmark plan targets the fixed
    Windows `D:\server-benchmarks` environment and the PowerShell runner is not
    available here.

Release thresholds:

- [x] Zero unexplained `critical` candidates.
- [x] Zero region worker sync-load crashes in Gate38 chaos logs after Gate36/Gate37 fixes.
- [x] Zero `Thread failed main thread check` crashes in Gate38 chaos logs after Gate36/Gate37
  fixes.
- [x] Zero wrong-owner mutation crashes in Gate38 chaos logs after Gate36/Gate37 fixes.
- [x] Zero high-signal async/watchdog/shutdown patterns in Gate38 MCC server log
  after Gate36/Gate37 fixes.
- [x] Zero high-signal async/watchdog/shutdown patterns in current worldgen log
  after Gate36/Gate37 fixes.
- [x] Repeat the full release gate without `--skip-build`, `--skip-mcc`, or
  `--skip-worldgen` after the current branch fixes merged.
- [x] Managed worldgen smoke refuses to attach to an already-running live server
  unless both `--attach-existing` and `--allow-live-probes` are passed intentionally.
- [x] Isolated managed worldgen smoke exits cleanly and releases port `25567`,
  RCON port `25577`, and `world/session.lock`.
- [x] Release runner labels skipped runs as partial, not release-ready.
- [x] MCC RCON setup errors are hard failures, not ignored log lines.
- [x] MCC log failure segment starts at server boot so join/setup failures are included.
- [x] Root-covered critical scanner findings are locked to an audited baseline.
- [x] Remaining high/medium candidates are either grouped false positives,
  optional reads waiting on manual classification, or explicitly deferred with
  low blast radius.
- [x] Residual risk note exists for every deferred group.

Verification evidence:

Latest integrated evidence:

- `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`:
  passed on 2026-04-28 at 13:10 KST.
- `./gradlew applyAllPatches --no-configuration-cache`: passed.
- `./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace`:
  passed with existing deprecation warnings only.
- `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks --no-configuration-cache --stacktrace`:
  passed and produced `shreddedpaper-server/build/libs/shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar`.
- `./gradlew -p tools/region-load-test-plugin build --no-configuration-cache`:
  passed.
- `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`:
  active candidates 0, critical/high/medium 0.
- `node tools/runtime/verify-async-root-baseline.mjs`: root-covered critical
  baseline unchanged at 57 audited candidates.
- `tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 7 --min-bots 7 --duration-sec 180 --chaos-profile anarchy-smp --chaos-intensity 2 --server-port 25566 --rcon-port 25576 --websocket-base-port 8060 --skip-build --agent-mode ReportOnly`:
  passed in `run/mcc-chaos/results/cycle-01`, seed `20260428125953`.
- `node tools/runtime/scan-server-log.mjs run/mcc-chaos/results/cycle-01`:
  no high-signal patterns.
- `node tools/runtime/invoke-watchdog-smoke.mjs --mode global ...`: passed in
  `run/watchdog-smoke-global`.
- `node tools/runtime/invoke-watchdog-smoke.mjs --mode region ...`: passed in
  `run/watchdog-smoke-region`.
- `node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/ShreddedPaper/run/worldgen-smoke-isolated/server --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G --server-port 25567 --rcon-port 25577 --require-cached-mcc`:
  passed with 56 commands and player lifecycle enabled.
- Gate38 passed in `run/mcc-chaos-release-gate-38/results/cycle-01`.
- `node tools/runtime/scan-server-log.mjs run/mcc-chaos-release-gate-38/runs/cycle-01/server/logs/latest.log`:
  no high-signal patterns.
- `node tools/runtime/scan-server-log.mjs /Users/jungwuk/Documents/works/worldgen/logs/latest.log`:
  no high-signal patterns.

Recent blockers now fixed or superseded by Gate38:

- Gate19 failed in `run/mcc-chaos-release-gate-19/results/cycle-01` with a
  minecart cross-owner movement failure and a passenger path-navigation
  synchronous chunk load.
- Gate36 failed with `ServerboundUseItemPacket -> Level.fastClip` sync-loading
  during player use ray tracing.
- Gate37 failed with `Zombie.hurtServer -> SpawnPlacementTypes -> Level#getBlockState`
  sync-loading during spawn-position checks.
- Gate19 also exposed one MCC client process `System.AccessViolationException`;
  that is tracked as external client instability unless a server-side pattern is
  found in the paired logs.

Historical evidence before the Gate19 regression hunt:

- `./gradlew applyAllPatches --stacktrace`: passed, 177 Minecraft source patches and 29 Paper server patches applied.
- `./gradlew :shreddedpaper-server:compileJava --stacktrace`: passed with deprecation warnings only.
- `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace`: passed.
- `./gradlew -p tools/region-load-test-plugin build`: passed.
- `node tools/async-audit/scan-async-ownership.mjs --write-todo`: active candidates 0.
- `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`: passed.
- `node tools/async-audit/scan-async-ownership.mjs --fail-on-critical`: passed.
- `node --check tools/runtime/scan-server-log.mjs`: passed.
- `node --check tools/runtime/invoke-worldgen-smoke.mjs`: passed.
- `node --check tools/runtime/invoke-async-release-gates.mjs`: passed.
- `node --check tools/runtime/verify-async-root-baseline.mjs`: passed.
- `node tools/runtime/verify-async-root-baseline.mjs`: root-covered critical baseline unchanged at 57 audited candidates.
- `node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --skip-worldgen`: passed scanner and runtime tool gates without re-running long build/chaos gates.
- `node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen`: passed scanner, runtime syntax gates, and managed isolated worldgen smoke; output explicitly said skipped build/MCC means it is not a release-ready claim.
- `node tools/runtime/invoke-async-release-gates.mjs --skip-build --isolated-worldgen --bot-count 2 --min-bots 2 --mcc-duration-sec 60 --server-port 25568 --rcon-port 25578 --websocket-base-port 8070`: passed scanner, root baseline, MCC join/setup/play smoke, MCC log scan, and managed isolated worldgen smoke; output explicitly said skipped build means it is not a release-ready claim.
- Hard-fail validation caught stale MCC `gamerule` setup commands that were previously hidden in `rcon.log`; those commands were removed because equivalent spawn/gamemode behavior is already set in `server.properties`.
- `tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --duration-sec 600 --agent-mode ReportOnly --skip-build`: passed, seed `20260427155207`, duration `600`, recoveries `0`, controller exit code `0`.
- `tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --min-bots 1 --duration-sec 180 --server-port 25566 --rcon-port 25576 --websocket-base-port 8060 --skip-build --agent-mode ReportOnly`: passed, results in `run/mcc-chaos/results/cycle-01`.
- `node tools/runtime/scan-server-log.mjs run/mcc-chaos/results/cycle-01`: passed with no high-signal server log patterns.
- `node tools/runtime/scan-server-log.mjs /Users/jungwuk/Documents/works/worldgen/logs/latest.log`: passed with no high-signal server log patterns.
- `node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/worldgen --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G --post-command-wait-sec 20 --stop-timeout-sec 90`: refused to attach because RCON was already reachable on `25575`; the smoke now requires both `--attach-existing` and `--allow-live-probes` before destructive live-server probes.
- `node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/ShreddedPaper/run/worldgen-smoke-isolated/server --server-port 25567 --rcon-port 25577 --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G --ready-timeout-sec 240 --post-command-wait-sec 20 --stop-timeout-sec 120`: passed 17 RCON/RLT probes.
- `node tools/runtime/scan-server-log.mjs /Users/jungwuk/Documents/works/ShreddedPaper/run/worldgen-smoke-isolated/server/logs/latest.log`: passed with no high-signal server log patterns.
- `lsof -Pan -iTCP:25567 -sTCP:LISTEN`, `lsof -Pan -iTCP:25577 -sTCP:LISTEN`, and `lsof run/worldgen-smoke-isolated/server/world/session.lock`: no holders after smoke shutdown.
- Subagent review follow-up closed: live attach now needs two flags, isolated worldgen state is generated by the runner, RLT cleanup counters are parsed, result directories including `jcmd-*.txt` are scanned, MCC setup RCON errors fail the run, MCC shutdown resources are asserted, `MinecraftServer.hasFullyShutdown` is set on successful shutdown completion, and watchdog worker dumps include `ShreddedPaperRegionNormal-*` / `ShreddedPaperRegionDegraded-*`.
- MCC scenario counts: `pvp-arena=271`, `fluid-leaf-boundary=109`, `base-grief=98`, `redstone-raid=94`, `vehicle-passenger=93`, `mobfarm-cram=81`, `panic-kite=61`, `region-boundary=38`.
- MCC action counts covered movement, combat, block place/use/dig, entity polling, boundary movement, sprint/sneak/hotbar/animate, and panic behavior. Bot command errors were `433`; these were MCC client command-level errors and did not produce server failures.
- The 600 second MCC run wrote no `failure-report.md`, no crash files, and log scan found no async ownership, sync-load, watchdog, crash, or wrong-owner failure signatures.
- `BENCHMARK_PLAN_2026-04-26.md` isolation smoke was not run in this workspace
  because it requires the fixed Windows benchmark environment. Release
  correctness is not covered by scanner/build alone; it requires scanner,
  build, Paperclip jar, MCC chaos, high-signal log scan, managed real-server
  smoke, and shutdown/lock cleanup evidence.

## Parallel Work Plan

Use disjoint write scopes. Agents may investigate across the repository, but
their edits must stay inside their assigned scope. Each agent must not revert
work by others.

For local parallelism on the same machine, use separate `git worktree`
directories so Gradle outputs, generated patch state, MCC run directories, RCON
ports, and server ports do not collide. Every agent still has to pull the current
base and must not copy its whole working tree over another agent's branch. Do
not run two server/MCC probes against the same `run/**` root or port allocation.
If Gradle cache locks become a bottleneck, assign each worktree its own
`GRADLE_USER_HOME`, but keep the repository worktree itself separate either way.

| Lane | Can Run In Parallel | Write Scope | Depends On |
| --- | --- | --- | --- |
| A. Scanner/tooling | Yes | `tools/async-audit/**` | none |
| B. Helper foundation | Yes, but review before codemods | new helper package only | none |
| C. Critical Bukkit/chunk API | After helper API skeleton | Bukkit/chunk API files | WS1 helper names |
| D. Level root semantics | After helper API skeleton | `Level*`, `ServerChunkCache`, root patches | WS1 helper names |
| E. Read codemods | After WS1 and WS3 | one manifest batch at a time | WS1, WS3 |
| F. Scheduled tick/redstone | Partly parallel with C/D | scheduled tick and redstone files | WS1 |
| G. Entity/player/stale send | Parallel with F | entity/player/chunk-send files | WS1, C |
| H. Verification | Always parallel | test scripts/docs only | current build |

Merge protocol:

- [ ] Every lane updates this PRD with completed tasks and evidence.
- [ ] Every lane runs `git diff --check`.
- [ ] Every lane runs at least `applyAllPatches` or explains why not.
- [ ] Code lanes run compile or provide a clear blocker.
- [ ] No lane edits another lane's files without coordination.

Suggested local worktrees:

```bash
git worktree add ../ShreddedPaper-agent-entity-player -b codex/async-agent-entity-player
git worktree add ../ShreddedPaper-agent-redstone-fluid -b codex/async-agent-redstone-fluid
git worktree add ../ShreddedPaper-agent-ai-teleport -b codex/async-agent-ai-teleport
git worktree add ../ShreddedPaper-agent-validation -b codex/async-agent-validation
git worktree add ../ShreddedPaper-agent-chunk-watchdog -b codex/async-agent-chunk-watchdog
```

Port and run-root allocation:

| Worktree | MCC Root | Server | RCON | WebSocket Base |
| --- | --- | ---: | ---: | ---: |
| entity/player lifecycle | `run/agent-entity-player-mcc` | 25610 | 25611 | 8160 |
| redstone/fluid/piston | `run/agent-redstone-fluid-mcc` | 25620 | 25621 | 8260 |
| AI/pathfinding/teleport | `run/agent-ai-teleport-mcc` | 25630 | 25631 | 8360 |
| validation gates | `run/agent-validation-mcc` | 25640 | 25641 | 8460 |
| chunk lifecycle/watchdog | `run/agent-chunk-watchdog-mcc` | 25650 | 25651 | 8560 |

Worktree merge order:

1. Validation-only worktree merges first if it only changes `tools/**`.
2. Runtime bug-fix worktrees merge one at a time after their targeted gate and
   compile pass.
3. Docs worktree merges last and records final evidence from the integrated
   branch.
4. After each merge, run `./gradlew :shreddedpaper-server:compileJava
   --rerun-tasks --no-configuration-cache --stacktrace` and at least one strict
   MCC smoke on the integrated branch.

## Prompts For Other Agents

Use the following current prompts for any further parallel hardening pass. They
assume each agent is running from the current integrated-gate-passing base, in
its own worktree, and using its allocated MCC ports from the table above.

### Current Agent - Entity/Player Lifecycle

```text
You are working in your own ShreddedPaper git worktree. You are not alone in the codebase; do not revert or overwrite changes by other agents.

Task: audit and fix entity/player lifecycle async ownership hazards only. Your write scope is limited to ServerGamePacketListenerImpl.java.patch, Entity.java.patch, LivingEntity.java.patch, TamableAnimal.java.patch, RegionizedPlayerChunkLoader.java.patch, PlayerChunkSender.java.patch, ShreddedPaperPlayerTicker.java, ShreddedPaperEntityTicker.java, and directly matching generated sources when needed for compile debugging.

Focus on player join/use-item/block interaction, disconnect/reconnect, entity movement across region boundaries, portal/respawn handoff, and stale player chunk-send ownership. Do not edit redstone/fluid/piston/pathfinding/chunk scheduler/watchdog files.

Verification: run ./gradlew applyAllPatches --no-configuration-cache --stacktrace, compile if feasible, node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical, and one MCC run using server port 25610, RCON 25611, websocket base 8160, root run/agent-entity-player-mcc. Report exact files changed and any remaining runtime blockers.
```

### Current Agent - Redstone/Fluid/Piston

```text
You are working in your own ShreddedPaper git worktree. You are not alone in the codebase; do not revert or overwrite changes by other agents.

Task: audit and fix redstone, neighbor update, fluid, piston, rail, diode, and scheduled tick async ownership hazards only. Your write scope is limited to redstone/fluid/piston/scheduled-tick patch files and their generated counterparts when needed for compile debugging.

Use loaded-only reads for optional neighbor/signal/fluid probes and owner handoff for mandatory cross-cell mutation. Do not edit player packet, pathfinding, chunk scheduler, watchdog, or validation harness files.

Verification: run ./gradlew applyAllPatches --no-configuration-cache --stacktrace, compile if feasible, node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical, and one MCC run using server port 25620, RCON 25621, websocket base 8260, root run/agent-redstone-fluid-mcc. Report scanner deltas by family.
```

### Current Agent - AI/Pathfinding/Teleport

```text
You are working in your own ShreddedPaper git worktree. You are not alone in the codebase; do not revert or overwrite changes by other agents.

Task: audit and fix AI, pathfinding, teleport, and mob special movement async ownership hazards only. Your write scope is limited to PathNavigation.java.patch, AI/navigation/entity monster or animal teleport files such as EnderMan.java.patch, Shulker.java.patch, TamableAnimal.java.patch, and their direct generated counterparts when needed for compile debugging.

Classify each path as loaded-only fallback, owner-required handoff, or async prefetch/resume. Be especially careful with paths that return success before a deferred teleport or path mutation has actually happened. Do not edit redstone/fluid/player packet/chunk scheduler/watchdog files.

Verification: run ./gradlew applyAllPatches --no-configuration-cache --stacktrace, compile if feasible, node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical, and one MCC run using server port 25630, RCON 25631, websocket base 8360, root run/agent-ai-teleport-mcc. Report exact risks that should become new chaos fixtures.
```

### Current Agent - Validation Harness

```text
You are working in your own ShreddedPaper git worktree. You are not alone in the codebase; do not revert or overwrite changes by other agents.

Task: improve validation only. Your write scope is limited to tools/mcc-chaos/**, tools/runtime/**, tools/region-load-test-plugin/**, and tools/async-audit documentation.

Focus on reproducing scanner-blind runtime bugs like Gate36 ServerboundUseItemPacket/fastClip and Gate37 SpawnPlacementTypes sync-load. Add or document chaos/RLT coverage for reconnect, portal/respawn, natural spawn combat, pathfinding, redstone/fluid, stale chunk sends, watchdog shutdown, and cleanup of transformed tagged entities.

Do not edit server engine code. Verification: build any changed plugin/tool, run node --check for changed .mjs files, and run one MCC validation pass using server port 25640, RCON 25641, websocket base 8460, root run/agent-validation-mcc.
```

### Current Agent - Chunk Lifecycle/Watchdog

```text
You are working in your own ShreddedPaper git worktree. You are not alone in the codebase; do not revert or overwrite changes by other agents.

Task: audit chunk lifecycle, ticket propagation, shutdown, and watchdog containment only. Your write scope is limited to ChunkHolderManager.java.patch, ChunkTaskScheduler.java.patch, ServerChunkCache.java.patch, RegionTickScheduler.java, ShreddedPaperRegionScheduler.java, WatchdogThread.java.patch, MinecraftServer.java.patch, and directly matching generated sources when needed for compile debugging.

Focus on lock inversion, shutdown progress, region worker dumps, hasFullyShutdown, stuck RCON/MCC cleanup, and ensuring watchdog fatal handling cannot block forever inside normal close paths. Do not edit gameplay leaf logic such as redstone, player packet handling, or pathfinding.

Verification: run ./gradlew applyAllPatches --no-configuration-cache --stacktrace, compile if feasible, node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical, and one MCC or watchdog smoke using server port 25650, RCON 25651, websocket base 8560, root run/agent-chunk-watchdog-mcc.
```

### Agent A - Scanner And Codemod Tooling

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: improve the async ownership audit tooling only. Your write scope is strictly tools/async-audit/**. Do not edit server source or patch files.

Read tools/async-audit/ASYNC_OWNERSHIP_STABILIZATION_PRD.md and tools/async-audit/ASYNC_OWNERSHIP_TODO.md. Implement scanner changes that group candidates by family and support intent tagging. Add output for family counts, critical-only failures, and a mechanical-rewrite manifest. Preserve the existing --write-todo behavior.

Do not mark TODO items done. Do not silence scanner candidates by broad ignores. Helper definitions and already-safe helper calls may be ignored only with precise patterns.

Verification: run node tools/async-audit/scan-async-ownership.mjs --write-todo and include the before/after family counts. List every file changed.
```

### Agent B - Helper Layer Design Review

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: review the proposed helper layer before implementation. Prefer no code edits. If you must edit, only add notes under tools/async-audit/ and do not touch server code.

Read tools/async-audit/ASYNC_OWNERSHIP_STABILIZATION_PRD.md, io/multipaper/shreddedpaper/ShreddedPaper.java, ShreddedPaperRegionLocker.java, ShreddedPaperChunkTicker.java, RegionMailbox.java, and LevelChunkRegionMap.java.

Deliver a concise review answering:
1. Which helper contracts are sound with the current lock/owner model?
2. Which contracts risk changing gameplay semantics?
3. Which existing APIs should the helper layer reuse?
4. What execution-time revalidation is mandatory?
5. What should be fail-hard rather than fallback?

Do not implement broad fixes. List exact file/line references for concerns.
```

### Agent C - Critical Bukkit/Chunk API Worker

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: fix critical Bukkit/chunk API sync-load hazards after the helper layer exists. Your write scope is limited to:
- shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch
- shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftChunk.java.patch
- shreddedpaper-server/src/minecraft/java/io/papermc/paper/FeatureHooks.java
- shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/PlayerChunkSender.java
- shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/player/RegionizedPlayerChunkLoader.java
- matching patch files only if this repository requires them

You are not alone in the codebase. Do not revert changes by others.

Goal: region workers must not sync-load chunks through Bukkit chunk APIs. Use already-loaded CraftChunk when possible, owner checks when necessary, and async prefetch/resume only when the operation truly requires the chunk. Do not silently fake successful mandatory operations.

Verification: run ./gradlew applyAllPatches and the async ownership scanner. Report scanner deltas for bukkit-chunk-load/getchunk-load-true/sync-load-call.
```

### Agent D - Level Root Semantics Worker

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: fix root Level/LevelReader/ServerChunkCache sync-load and ownership semantics after the helper layer exists. Your write scope is limited to:
- shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/Level.java.patch
- shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/LevelReader.java.patch
- shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/level/ServerChunkCache.java.patch
- shreddedpaper-server/minecraft-patches/sources/ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkTaskScheduler.java.patch
- generated source counterparts only if needed for compile/debug

You are not alone in the codebase. Do not revert changes by others.

Goal: root methods must not sync-load from independent region workers. Reads that can tolerate missing data should use loaded-only helpers. Writes must require owner context or defer to owner. Root method declarations should not remain active scanner candidates.

Verification: run ./gradlew applyAllPatches, ./gradlew :shreddedpaper-server:compileJava --stacktrace, and the scanner. Report every remaining critical root candidate with rationale.
```

### Agent E - Redstone/Scheduled Tick Worker

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: finish redstone, neighbor, fluid, and scheduled tick ownership fixes. Your write scope is limited to scheduled tick/redstone/fluid/piston/rail/tripwire related files and their patch counterparts.

Read the completed fixes AO-FIX-001 through AO-FIX-008 in tools/async-audit/ASYNC_OWNERSHIP_TODO.md before editing. Continue the same patterns: loaded-only neighbor reads, owner handoff for cross-cell future ticks, and no sync-load guards disabled.

You are not alone in the codebase. Do not revert changes by others.

Verification: run ./gradlew applyAllPatches, compile if feasible, and scanner. Provide before/after counts for schedule-tick, blockstate-read, fluidstate-read in this category.
```

### Agent F - Entity/Player Race Investigator

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: investigate entity movement, teleport, player ticking, and player chunk send ownership races. Prefer a read-only report unless explicitly asked to patch.

Read:
- tools/async-audit/ASYNC_OWNERSHIP_STABILIZATION_PRD.md
- Entity.java.patch
- ServerGamePacketListenerImpl.java.patch
- RegionizedPlayerChunkLoader.java.patch
- ShreddedPaperPlayerTicker.java
- ShreddedPaperChangesBroadcaster.java
- RegionMailbox.java

Deliver:
1. Top 10 race risks by severity.
2. Required owner epoch or execution-time revalidation points.
3. Which paths need async prefetch/resume rather than loaded-only fallback.
4. Suggested chaos fixtures for each risk.
5. Exact file/line references.

Do not edit code unless assigned a narrow write scope.
```

### Agent G - Verification Harness Worker

```text
You are working in /Users/jungwuk/Documents/works/ShreddedPaper.

Task: improve verification only. Your write scope is limited to tools/mcc-chaos/**, tools/region-load-test-plugin/**, and documentation under tools/async-audit/**.

Goal: create or document targeted chaos runs for each async ownership family:
- sync chunk load
- scheduled tick/neighbor/fluid/redstone
- entity movement/teleport/player tick
- player chunk send/stale holder broadcast
- mailbox pressure/owner epoch retry

Do not edit server engine code.

Verification: run builds for any changed tools if feasible and provide exact commands for manual Windows D:\\worldgen testing.
```

## Immediate Next Steps For Codex

1. Keep `Active candidates: 0` as a scanner invariant, not a release claim.
2. Run the full release gate list after any async ownership or watchdog change.
3. Run managed worldgen smoke only when the `worldgen` server is idle; otherwise
   the smoke must fail before issuing RCON probes.
4. Treat any new `latest.log` high-signal match as a release blocker and add a
   concrete AO-FIX entry before claiming completion.
5. Re-run MCC chaos and log scan after every blocker fix.

## Open Questions

- Should Bukkit `World#getChunkAt` from a region worker fail hard when unowned,
  or return already-loaded only and otherwise throw a clearer unsupported sync
  load exception? Current direction: fail hard for unowned/missing sync load.
- Should optional neighbor reads use direct `getBlockStateIfLoaded` or always
  route through ShreddedPaper helper for counters? Current direction: helper for
  migrated code, existing explicit safe reads may remain.
- Should scanner codemods edit patch files directly or edit generated sources
  and regenerate patches? Current direction: direct patch edits for already
  patch-owned files; generated source edits only for source-owned files.
- What is the acceptable residual high candidate count at release? Current
  direction: zero unexplained critical; high candidates allowed only with
  grouped false-positive/deferred rationale and chaos evidence.
