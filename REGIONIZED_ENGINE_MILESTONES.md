# Regionized Independent Tick Engine Milestones

This file tracks the productionization work for the Folia-style independent
region tick engine plus hostile-load isolation. Keep it updated as each commit
lands, review feedback arrives, and load-test results change the next step.

## Ground Rules

- Treat every milestone as independently reviewable and buildable.
- Do not hide global barriers behind helper APIs. If a phase waits for all
  regions, record it here as a blocker until removed.
- Keep region ownership explicit. Cross-region work must use mailbox/handoff
  protocols, not silent global locks.
- Prefer correctness first, then hostile-load survival, then micro-optimization.
- Validate with `applyAllPatches`, `compileJava`, and a `D:\worldgen` runtime
  test before calling a milestone complete.
- Each milestone must include: implementation TODO, self-review TODO,
  sub-agent review TODO, compile/runtime verification TODO, and commit TODO.

## Active Granular TODO Ledger

This section is intentionally detailed. Do not collapse it into high-level
milestones; it is the step-by-step guardrail for avoiding missed work.

### A. Repository And Build State

- [x] Create an isolated working branch for the engine work.
- [x] Record that `shreddedpaper-api/build.gradle.kts.patch` had pre-existing
  unrelated local changes and must not be reverted.
- [x] Inspect `D:\worldgen\start.bat` and record the launch jar and JVM flags.
- [x] Inspect `D:\worldgen\plugins` to capture the current compatibility
  surface.
- [x] Add Java 25 toolchain target in the root Gradle build.
- [x] Enable Java preview flags for compile and test tasks.
- [x] Add JCTools dependency for bounded MPSC region mailboxes.
- [x] Patch the generated server Gradle run task to prefer Java 25 and preview.
- [x] Patch the server Gradle patch file so regenerated builds keep Java 25 and
  JCTools.
- [x] Re-run `applyAllPatches` after source compile is stable.
- [x] Record first `applyAllPatches` result: failed because
  `shreddedpaper-server/build.gradle.kts.patch` hunk count was stale after the
  JCTools dependency insertion.
- [x] Fix `shreddedpaper-server/build.gradle.kts.patch` dependency hunk target
  count so later hunks apply.
- [x] Record second `applyAllPatches` result: failed because the Java run-task
  hunk source/target line count was stale.
- [x] Fix Java run-task hunk count in `shreddedpaper-server/build.gradle.kts.patch`.
- [x] Record third `applyAllPatches` result: run-task hunk still failed because
  it had no trailing context for paperweight's single-file patcher.
- [x] Add trailing context to the Java run-task hunk.
- [x] Record fourth `applyAllPatches` result: run-task hunk still failed because
  the source start line was 344, not 347, after upstream patching.
- [x] Fix Java run-task hunk source start line.
- [x] Record fifth `applyAllPatches` result: combined run-task hunk still failed
  despite exact visible context.
- [x] Split Java run-task changes into two smaller hunks for tool stability.
- [x] Record sixth `applyAllPatches` result: split run-task hunks still failed
  in paperweight single-file patching.
- [x] Remove Gradle run-task Java 25 hunk from the server patch for now; compile
  and production runtime still use Java 25 through root toolchain and explicit
  `D:\worldgen` launch command.
- [ ] Verify regenerated `shreddedpaper-server/build.gradle.kts` still contains
  Java 25, preview flags, and JCTools.
- [ ] Run `git diff --check` before every commit.
- [ ] Commit build/toolchain changes only after compile passes.

### B. Planning And Review Workflow

- [x] Create this milestone/TODO document.
- [x] Expand TODOs into implementation, review, verification, and commit
  substeps.
- [x] Reuse existing subagents because the session already hit the agent limit.
- [x] Send Faraday a code-map/runtime integration task.
- [x] Send Aquinas a performance and concurrency review task.
- [x] Send McClintock a `D:\worldgen` runtime/plugin harness task.
- [x] Wait for Faraday's current findings and copy blockers into this ledger.
- [x] Wait for Aquinas's current findings and copy blockers into this ledger.
- [ ] Wait for McClintock's current findings and copy blockers into this ledger.
- [x] Receive Faraday's current compile/integration review.
- [x] Receive Aquinas's current concurrency/performance review.
- [x] Receive McClintock's current `D:\worldgen` runtime harness review.
- [x] Copy first review blockers into this ledger.
- [ ] For every sub-agent blocker, either patch it or explicitly mark it
  deferred with rationale.
- [ ] Before each commit, request or reuse a sub-agent review focused on the
  changed files for that commit.

### C. Scheduler And Mailbox Foundation

- [x] Add `RegionLoadClass` with `GLOBAL`, `NORMAL`, `DEGRADED`, and
  `QUARANTINED`.
- [x] Add `RegionTaskClass` with critical, player, plugin, tracker/broadcast,
  and explosion/physics classes.
- [x] Add `RegionWorkType` for cooperative budget accounting.
- [x] Add immutable `RegionTask` with ready tick and ordering.
- [x] Add bounded `RegionMailbox` using JCTools MPSC queues.
- [x] Add separate critical mailbox capacity.
- [x] Add mailbox rejection counters.
- [x] Add mailbox executed counters.
- [x] Add mailbox JFR queue event on rejection.
- [x] Replace region `ConcurrentLinkedQueue<DelayedTask>` scheduling with
  bounded mailbox ingress.
- [x] Preserve delay 0 semantics as "eligible on next mailbox drain" rather than
  forcing all tasks to wait at least one extra tick.
- [ ] Add task-class-specific overflow behavior beyond logging: plugin
  fail-fast, player quota, broadcast coalescing, and critical reserve policy.
- [ ] Add task affinity fields for location/entity once dynamic split/merge is
  wired.
- [ ] Add mailbox redistribution rules for future split/merge.
- [ ] Add tests or a harness case for delay 0, delay 1, rejected task, and
  exception isolation.

### D. Region Tick Scheduler

- [x] Add `RegionTickScheduler` with independent per-region handles.
- [x] Use per-region ideal schedule and no-catch-up requeue.
- [x] Add normal and degraded delay queues.
- [x] Cap degraded workers by configuration or `threadCount / 8`.
- [x] Allow normal workers to steal degraded work only when normal work is not
  ready.
- [x] Add region snapshot data for diagnostics.
- [x] Add global scheduler shutdown hook in `ShreddedPaperTickThread.stopServer`.
- [x] Keep core tick failures fatal by setting the chunk-system crash state.
- [x] Prevent duplicate queue entries when a region is registered again while
  an old delayed handle is already queued.
- [x] Confirm scheduler worker thread names and `TickThread` ownership checks are
  accepted by existing Paper/Moonrise code.
- [ ] Add lifecycle guard so disabled independent ticking never starts the
  scheduler.
- [ ] Add global-region lane only after world/global ownership paths are mapped.
- [ ] Add NUMA/home-worker affinity only after correctness and hostile-load tests
  pass.

### E. Tick Integration

- [x] Add `ScheduledTickContext` snapshot to carry spawn/timing inputs into
  independent region ticks.
- [x] In independent mode, register active regions and return an already
  completed future instead of waiting for all region futures.
- [x] Add public independent scheduler entry point for one region tick.
- [x] Move tracker processing into the owning region tick when independent mode
  is enabled.
- [x] Move player connection flush into the owning region tick when independent
  mode is enabled.
- [x] Add cooperative budget checks to internal task drain, entity task
  scheduler, chunk ticking, entity ticking, tracker processing, block entity
  ticking, and player ticking.
- [x] Fix current compile errors from mismatched `RegionWorkType` enum names.
- [x] Re-run compile after enum fixes.
- [ ] Inspect whether `ActivationRange.activateEntities(level)` is still a
  world-level coupling and decide whether it needs region-local activation.
- [x] Patch `TickThread.isTickThreadFor(...)` to accept the currently ticking
  independent ShreddedPaper region when no legacy 3x3 lock is held.
- [x] Patch navigating mob collection to accept the currently ticking
  independent region and only include adjacent regions when a legacy lock is
  actually held.
- [ ] Inspect whether `processUnloads(region)` can block tick workers on IO or
  global chunk locks.
- [ ] Inspect whether `level.runBlockEvents(region)` needs budget/defer support.
- [ ] Inspect whether `level.tickBlockEntities(...)` can be partially deferred
  without corrupting vanilla order.
- [ ] Inspect whether `ShreddedPaperChangesBroadcaster.broadcastChanges()` is
  thread-local enough for independent scheduler workers.
- [x] Verify independent mode does not still enter `processTrackQueueInParallel`
  or `flushQueueInParallel` after region registration.
- [ ] Verify legacy mode still uses old future aggregation and behaves as before.
- [x] Replace mutable latest tick-context read with per-handle scheduled context
  plus next-context handoff.
- [x] Route default engine `scheduleTask` work through non-dropping critical
  mailbox ingress.
- [x] Route Bukkit plugin region scheduler work through bounded `PLUGIN`
  mailbox ingress and fail fast on overflow.
- [x] Restore delay-0 task contract as next-tick execution.
- [x] Keep quarantined region handles stable instead of recreating them every
  global tick.
- [x] Clear scheduler queues/maps and reset singleton on shutdown.
- [x] Add an exact-region owner marker lock around independent region ticks so
  legacy ownership assertions and narrow read-only compatibility paths
  serialize with the real owner thread without restoring the old 3x3 tick
  cadence barrier.
- [ ] Re-review exact owner marker lock for starvation and lock-contention
  requeue behavior under cross-region teleport/projectile helper load.
- [x] Patch block-change ownership checks to accept current independent region
  ownership.
- [x] Move player chunk send/keepalive/flush to region-owned player tick and
  skip the global send-chunks phase in independent mode.
- [x] Re-enable `ServerChunkCache.MainThreadExecutor` region-task rescue in
  independent mode after runtime `forceload add 0 0` proved non-ticking chunk
  generation tasks can otherwise have no executor; rescue now requires write
  ownership instead of the old read-only lock.
- [x] Gate `ChunkTaskScheduler.executeMainThreadTask` bordering-region task
  stealing so independent mode only executes tasks for regions currently marked
  owned by that worker.
- [x] Prototype independent-mode `saveAllChunks` owner-mailbox handoff, then
  reject that approach after review
  showed it can wait forever if no scheduler handle services that mailbox;
  keep the old save path but make it safe through exact owner marker locks.
- [x] Replace the reverted `saveAllChunks` retry loop's repeated
  `CompletableFuture.supplyAsync` allocation with direct lock retry plus short
  park to reduce CPU and GC pressure during full saves.
- [x] Upgrade internal-task rescue paths from read-only compatibility locks to
  write ownership because chunk internal tasks now include mutating work.
- [x] Add contention backoff for independent region owner marker lock failures
  to avoid 1 ms queue churn under cross-region helper load.
- [x] Review pending tick-context overwrite under contention and accept it as
  intentional no-catch-up behavior: `nextContext` is the latest global snapshot
  for the next independent tick, not a queue of missed world ticks.
- [x] Add best-effort exact read snapshots for neighboring navigating mobs under
  independent ticking so border path updates do not blindly ignore idle
  neighboring regions.
- [ ] Replace best-effort neighboring navigation snapshots with a true
  regionizer buffer/merge invariant before final release.
- [ ] Replace remaining two-region `ShreddedPaper.ensureSync` compatibility
  helper with a first-class cross-region task protocol; exact owner marker
  locks make the current compatibility path safe enough for initial runtime
  validation but not final architecture.
- [ ] Verify player chunk send fallback is safe with exact owner marker locks
  and decide whether to keep read-only fallback or split packet construction
  from player-connection mutation.

### F. Diagnostics And Observability

- [x] Add JFR `RegionTick` event.
- [x] Add JFR `RegionQueue` event.
- [x] Add JFR `RegionOverBudget` event.
- [x] Add `/region top`.
- [x] Add `/region dump`.
- [x] Add `/region inspect <world> <regionX> <regionZ>`.
- [x] Add command output for next-start lag, rejected task count, mailbox depth,
  load class, and EWMA MSPT.
- [ ] Add JFR `RegionMerge` and `RegionSplit` events when dynamic regionizer
  work starts.
- [ ] Add JFR `ChunkRequest` and `CrossRegionTask` events when those systems are
  wired.
- [ ] Add per-region explosion backlog metric.
- [ ] Add per-region chunk IO/generation debt metric.

### G. Compile And Static Verification Loop

- [x] Run first `shreddedpaper-server:compileJava` after 1st integration.
- [x] Record first compile result: 2 errors, both mismatched `RegionWorkType`
  constants.
- [x] Record second compile result after runtime-state refactor: 1 error,
  `LevelChunkRegion.tickTasks()` still referenced the removed `mailbox` field.
- [x] Fix `LevelChunkRegion.tickTasks()` to use runtime-state mailbox.
- [x] Re-run compile after runtime-state fixes: `shreddedpaper-server:compileJava`
  passed.
- [x] Re-run compile after independent ownership checks: `shreddedpaper-server:compileJava`
  passed.
- [x] Fix `BLOCK_ENTITY_TICK` and `PLAYER_TICK` usage or enum names.
- [x] Re-run `shreddedpaper-server:compileJava --stacktrace`.
- [x] Record owner-save handoff compile result: first retry found 2 Java
  lambda-capture errors in `ChunkHolderManager.saveAllChunks` after moving the
  independent save path to a `CompletableFuture`.
- [x] Fix `ChunkHolderManager.saveAllChunks` lambda capture by using a final
  handoff future.
- [x] Repeat until `compileJava` passes.
- [x] Run `applyAllPatches` after source compile passes.
- [x] Re-run `compileJava` after patch regeneration.
- [x] Re-run `applyAllPatches` after owner-lock/save/rescue patch changes:
  passed.
- [x] Re-run `compileJava` after regenerated patches: passed.
- [x] Run at least a focused server jar build task before runtime testing:
  `shreddedpaper-server:createMojmapPaperclipJar` passed and produced
  `shreddedpaper-server/build/libs/shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar`.

### H. Runtime Harness In `D:\worldgen`

- [x] Inspect `D:\worldgen\start.bat`.
- [x] Record current production-ish launch jar:
  `shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar`.
- [x] Record current max heap: 18G.
- [x] Record plugin directory contains Geyser/Floodgate, EssentialsX,
  ProtocolLib, ViaVersion/ViaBackwards, LuckPerms, Chunky, spark, TabTPS, and
  many server-management plugins.
- [x] Build a new ShreddedPaper paperclip/bundler jar.
- [x] Back up the existing `D:\worldgen\shreddedpaper-paperclip-...jar` before
  replacing it.
- [x] Copy the new jar into `D:\worldgen`.
- [x] Add `--enable-preview` to the runtime start command if Java 25 preview code
  is actually used at runtime.
- [x] Consider adding `-XX:+UseCompactObjectHeaders` to a test start script, but
  keep a fallback script without it.
- [x] Launch the server once with existing plugins.
- [x] Capture startup log, plugin load failures, Java version, and command
  registration status.
- [x] Run `/region top` and `/region dump` after startup.
- [x] Stop the server cleanly and verify scheduler shutdown.
- [x] Capture JFR while running the modified jar:
  `D:\worldgen\logs\codex-region-foundation-rescuefix.jfr`.
- [x] Temporarily enable RCON for command-driven runtime testing and restore
  `server.properties` afterward.
- [x] Record first runtime blocker: `forceload add 0 0` hung in
  `ServerChunkCache.syncLoad` because independent ticking had disabled the main
  thread's region internal-task rescue, leaving non-ticking chunk generation
  tasks with no executor.
- [x] Fix first runtime blocker by restoring `ServerChunkCache.MainThreadExecutor`
  rescue for independent mode, while requiring write ownership of the exact
  region before executing internal tasks.
- [x] Re-test `forceload add 0 0`: command completed in about 0.62s, `/region top`
  reported normal regions at sub-1ms EWMA MSPT, `/tps` stayed near 20 TPS, and
  RCON `stop` shut down cleanly.
- [ ] Remove the temporary forced chunk `[0, 0]` from the `D:\worldgen` test
  world during the next controlled runtime run.

### I. Load-Test Plugin

- [ ] Create a local Bukkit/Paper plugin project for hostile-load testing.
- [ ] Add command: spawn a single-region TNT grid.
- [ ] Add command: spawn distributed TNT grids across multiple independent
  regions.
- [ ] Add command: generate entity/pathfinding load in one region.
- [ ] Add command: create tracker/broadcast update flood.
- [ ] Add command: create plugin scheduler/mailbox flood.
- [ ] Add command: force chunk generation load away from players.
- [ ] Add command: place a normal-region probe that records tick cadence/MSPT.
- [ ] Build the plugin jar.
- [ ] Put the plugin in `D:\worldgen\plugins`.
- [ ] Verify plugin commands load on the modified server.

### J. Hostile-Load Acceptance Tests

- [ ] Baseline idle server TPS/MSPT with current plugins.
- [ ] Single TNT region: make the overloaded region exceed 1000ms MSPT.
- [ ] Single TNT region: verify a far normal region P95 MSPT stays <= 50ms when
  CPU headroom exists.
- [ ] Multi-heavy-region test: heavy regions >= tick worker count.
- [ ] Multi-heavy-region test: verify degraded worker cap protects normal
  regions.
- [ ] Plugin flood test: verify bounded mailbox rejects/delays without OOM.
- [ ] Tracker flood test: verify broadcaster/tracker work does not become a
  global barrier.
- [ ] Chunk generation DoS test: verify normal regions keep chunk priority.
- [ ] Record logs, `/region top`, JFR, spark output, and CPU utilization for each
  test.
- [ ] For every failed acceptance, add a root-cause note and a new patch TODO.
- [ ] Repeat patch/compile/runtime/load-test until acceptance passes.

### K. Compatibility Repair Loop

- [ ] For every plugin startup error, identify plugin jar and version.
- [ ] Check whether the plugin is open source.
- [ ] Fetch source or release repository for open-source incompatible plugins.
- [ ] Patch unsafe main-thread assumptions to use region/entity/global scheduler
  where possible.
- [ ] Build patched plugin jar.
- [ ] Place patched jar in `D:\worldgen\plugins` with old jar backed up.
- [ ] Document source URL, patch summary, and resulting jar path.
- [ ] Keep plugin-specific fixes separate from engine commits when feasible.

### L. Commit Gates

- [ ] Commit 1: Java 25/build config + milestone document after compile passes.
- [ ] Commit 2: scheduler/mailbox foundation after compile and sub-agent review.
- [ ] Commit 3: independent tick integration after compile and runtime startup.
- [ ] Commit 4: diagnostics commands/JFR after runtime command verification.
- [ ] Commit 5: load-test plugin after it builds and loads.
- [ ] Commit 6+: performance fixes from load tests, one coherent fix per commit.
- [ ] Never commit unrelated pre-existing API patch changes unless explicitly
  needed and reviewed.

## Review Blockers Imported From Subagents

- [x] `Blocker`: scheduler runtime state must not use `LevelChunkRegion` object
  lifetime as durable identity. Use stable `(world, RegionPos)` keyed runtime
  state and attach/detach the mutable payload each tick.
- [x] `Blocker`: prevent duplicate delayed-queue entries when `registerRegion`
  is called every world tick for an already queued handle.
- [x] `Blocker`: critical system ingress cannot be a bounded drop path. Chunk
  future completion, handoff, shutdown/drain, unload/save must not be rejected
  by ordinary mailbox pressure.
- [x] `Blocker`: do not move transport flush earlier than the existing
  post-world player/chunk-send/keepalive phase unless those packet producer
  phases move with it. Region tick may tick players, but final transport flush
  should remain phase-consistent.
- [ ] `High`: tracker mutation can only move into region tick if entity owner
  region is the single tracker owner. Until then, keep an explicit compatibility
  path or harden tracker ownership.
- [x] `High`: cooperative budget checks must not yield inside non-resumable
  vanilla phase internals. Budget only before scheduler-owned batch boundaries
  unless the loop has explicit continuation/backlog state.
- [x] `High`: preserve semantic split between read-only immediate work
  (`internalTasks`) and mutating next-tick work (`scheduleTask`) until a full
  task-class protocol replaces both.
- [ ] `Medium`: degraded lanes do not provide isolation if work still uses the
  old 3x3 region lock topology. Remove normal region ticks from shared lock
  waits before claiming hostile-load isolation.
- [ ] `Medium`: JFR emission must stay transition/severe-event based, not
  per-entity or per-packet.
- [x] `Runtime`: testing in `D:\worldgen` should launch the newly built jar
  explicitly; do not rely on similarly named jars in the directory.
- [ ] `Runtime`: PlugManX hot-load should not be used for the load-test plugin;
  place a real `.jar` in `D:\worldgen\plugins` and restart.
- [x] `Blocker`: scheduled region ticks must not read a mutable latest world
  tick context at worker start.
- [x] `Blocker`: bounded ordinary mailbox rejection must not silently drop
  engine handoff work.
- [x] `High`: zero-delay mailbox semantics must match the documented next-tick
  contract.
- [x] `High`: quarantine must be stable and not churn handles every global tick.
- [x] `Medium`: scheduler shutdown must clear maps/queues and reset singleton.
- [x] `Blocker`: block-change ownership checks must understand independent
  current-region ownership.
- [x] `Blocker`: post-world packet flush must not race independent region player
  ticks.
- [x] Re-run `applyAllPatches` after latest Minecraft patch changes.
- [x] Re-run `compileJava` after latest `applyAllPatches`.

## Milestone 1 - Build Baseline And Runtime Harness

- [ ] Move project toolchain and compile target to Java 25.
- [ ] Enable preview flags for compile, tests, and local run tasks.
- [ ] Add bounded mailbox dependency or verify the selected in-tree queue.
- [ ] Inspect `D:\worldgen\start.bat` and document the exact run command.
- [ ] Build a ShreddedPaper server jar and launch it in `D:\worldgen`.
- [ ] Capture baseline logs, startup errors, plugin list, JVM flags, and TPS/MSPT.
- [ ] Commit Java 25/build/runtime-harness changes.
- [ ] Request sub-agent review for build/runtime risks.

## Milestone 2 - Scheduler And Mailbox Foundation

- [ ] Add `RegionTickScheduler` with independent per-region deadlines.
- [ ] Add `GLOBAL`, `NORMAL`, `DEGRADED`, and `QUARANTINED` load classes.
- [ ] Reserve normal workers and cap degraded workers.
- [ ] Use no-catch-up scheduling: advance ideal schedule by elapsed periods and
  requeue at `max(tickEnd, idealDeadline)`.
- [ ] Add bounded per-region MPSC mailbox with per-task-class queues.
- [ ] Add mailbox rejection/coalescing counters and JFR events.
- [ ] Replace linear `DelayedTask` countdown scans with due-tick ordering.
- [ ] Keep plugin/task exceptions region-local; keep core tick exceptions fatal.
- [ ] Compile and unit-smoke the scheduler without changing world behavior.
- [ ] Commit scheduler/mailbox foundation.
- [ ] Request sub-agent code review focused on race conditions and queue bounds.

## Milestone 3 - Independent Region Tick Integration

- [ ] Wire `ShreddedPaperChunkTicker` to register active regions with the
  independent scheduler when enabled.
- [ ] Stop returning a future that waits for all region ticks in independent mode.
- [ ] Move tracker processing out of the global `processTrackQueueInParallel`
  fence and into the owning region tick.
- [ ] Move player connection flush into region-local player processing.
- [ ] Add per-region tick context snapshots for spawn state and timing inputs.
- [ ] Ensure shutdown stops the region scheduler before worker pools are killed.
- [ ] Compile, run local server, and verify regions continue ticking.
- [ ] Commit integration.
- [ ] Request sub-agent review for lifecycle/shutdown/order regressions.

## Milestone 4 - Region-Local Data Correctness

- [ ] Remove exception-tolerant concurrent iteration from `LevelChunkRegion`.
- [ ] Convert hot region state to owner-thread mutation plus mailbox ingress.
- [ ] Fix `LevelTicksRegionProxy.clearArea`, `copyArea`, `copyAreaFrom`, and
  `count`.
- [ ] Implement eager tick deadline offset handling for merge-like data moves.
- [ ] Define deterministic delay semantics for region tasks, including delay 0.
- [ ] Add tests for scheduled ticks, delayed tasks, and cross-region task order.
- [ ] Commit region-local data fixes.
- [ ] Request sub-agent review focused on tick-time semantics.

## Milestone 5 - Hostile-Load Isolation

- [ ] Add region EWMA MSPT, schedule lag, mailbox depth, and deferred-work debt.
- [ ] Move slow regions into `DEGRADED` lane and enforce degraded worker cap.
- [ ] Add cooperative budget hooks to chunk ticks, entity ticks, tracker work,
  player flushing, and internal task draining.
- [ ] Add TNT/explosion backlog hooks or a deferred processing queue.
- [ ] Add broadcast/tracker coalescing where repeated updates can be merged.
- [ ] Add `/region top`, `/region inspect`, and `/region dump`.
- [ ] Commit hostile-load isolation.
- [ ] Request sub-agent performance review.

## Milestone 6 - Chunk IO/Generation QoS

- [ ] Identify tick-worker paths that block on chunk IO/generation.
- [ ] Replace blocking waits with async request plus region continuation where
  safe.
- [ ] Add per-region chunk request metrics.
- [ ] Prioritize normal regions over degraded regions for chunk workers.
- [ ] Test chunk-generation DoS and verify normal regions keep loading chunks.
- [ ] Commit chunk QoS changes.

## Milestone 7 - Load Test Plugin And Soak

- [ ] Create or import a local open-source-compatible load-test plugin.
- [ ] Add commands for TNT grid, distributed TNT grids, entity/pathfinding load,
  chunk-generation load, plugin scheduler flood, and tracker/broadcast flood.
- [ ] Build plugin and place it in `D:\worldgen\plugins`.
- [ ] Run single-heavy-region test: heavy region MSPT > 1000ms while far normal
  region P95 MSPT remains <= 50ms when CPU is available.
- [ ] Run multi-heavy-region test with heavy regions >= tick worker count and
  verify normal worker reservation.
- [ ] Run plugin flood test and verify bounded mailbox prevents OOM.
- [ ] Record logs, JFR recordings, timings, and failure causes.
- [ ] Iterate fixes until acceptance criteria pass.
- [ ] Commit load-test harness and final tuning.

## Milestone 8 - Compatibility Pass

- [ ] Record all plugin compatibility failures from `D:\worldgen`.
- [ ] For open-source plugins, fetch source, patch for region/entity/global
  scheduler use, build, and place fixed jars in the test server.
- [ ] Document plugin-specific patches and upstream URLs.
- [ ] Keep plugin compatibility fixes separate from engine commits when possible.

## Current Blockers

- [ ] Dynamic merge/split regionizer is not yet implemented.
- [ ] Independent scheduler is not yet wired into the world tick path.
- [ ] Global tracker and player flush phases still contain world-level coupling.
- [ ] `LevelTicksRegionProxy` still has stubbed methods.
- [ ] No `D:\worldgen` runtime baseline has been captured yet.
