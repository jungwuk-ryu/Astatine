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
- [x] Add task-class-specific overflow behavior beyond logging: plugin
  fail-fast, player quota, broadcast coalescing, and critical reserve policy.
- [x] Reject bounded critical-system queue after sub-agent review: too many
  existing internal callers treat `CRITICAL_SYSTEM` as non-dropping and ignore
  `offer(false)`, so a hard cap could silently lose world-state handoff work.
- [x] Restore `CRITICAL_SYSTEM` as a non-dropping queue while keeping a critical
  reserve threshold that emits operator-visible log/JFR signals when exceeded.
- [x] Add class-specific mailbox capacities for player actions, plugin tasks,
  tracker/broadcast work, and explosion/physics work.
- [x] Count ingress plus delayed tasks in the same per-class queued quota so
  delayed plugin/task floods cannot bypass the ingress queue cap.
- [x] Preserve plugin fail-fast behavior: `RegionScheduler` receives `false`
  from mailbox `offer` and throws `RejectedExecutionException`.
- [x] Rate-limit mailbox rejection logs and JFR rejection events to the first
  and every 256th rejection per bounded task class while including class
  capacity and rejection counters.
- [x] Re-run compile after class-specific mailbox overflow policy.
- [x] Receive sub-agent correctness/performance review for class-specific
  mailbox overflow policy.
- [x] Patch every class-specific mailbox overflow blocker before commit:
  Faraday and Aquinas both rejected bounded `CRITICAL_SYSTEM` because existing
  internal callers treat it as non-dropping; the queue was restored to
  non-dropping with reserve-threshold observability, then re-reviewed with no
  release blocker.
- [x] Run `git diff --check` for class-specific mailbox overflow policy.
- [x] Commit class-specific mailbox overflow policy.
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

### D2. Dynamic Region Ownership Foundation

- [x] Receive Faraday's dynamic regionizer code-map review.
- [x] Accept review direction: keep `RegionPos` as immutable fixed cell
  coordinates and add a separate dynamic owner-handle layer above cells.
- [x] Add `RegionOwner` as the future dynamic ownership handle.
- [x] Keep initial owner behavior as one owner per fixed cell.
- [x] Preserve pending mailbox/runtime identity by using the cell key as the
  initial single-cell owner id.
- [x] Route `LevelChunkRegion` runtime state creation through `RegionOwner`.
- [x] Replace `LevelChunkRegionMap`'s direct cell-to-region map with
  cell-to-owner and owner-id-to-owner maps while preserving current behavior.
- [x] Route `RegionTickScheduler` registration keys through
  `RegionRuntimeState.ownerId()`.
- [x] Add explicit single-cell-owner guards to fail closed until exact-cell-set
  locking and owner aggregation are implemented.
- [x] Compile owner abstraction foundation with
  `./gradlew shreddedpaper-server:compileJava --stacktrace`.
- [x] Receive sub-agent review for owner abstraction foundation and patch every
  blocker before commit.
- [x] Re-run sub-agent review after guard fixes: Faraday reported no blockers.
- [x] Run `git diff --check` for owner abstraction foundation.
- [x] Commit owner abstraction foundation as a no-behavior-change migration
  step.
- [x] Add exact-cell-set owner locking APIs while preserving existing
  radius-based lock behavior.
- [x] Compile exact-cell-set owner locking APIs with
  `./gradlew shreddedpaper-server:compileJava --stacktrace`.
- [x] Receive sub-agent review for exact-cell-set owner locking APIs and patch
  every blocker before commit.
- [x] Faraday reported no blockers for exact-cell-set owner locking APIs.
- [x] Run `git diff --check` for exact-cell-set owner locking APIs.
- [x] Commit exact-cell-set owner locking APIs.
- [x] Add owner cell snapshots and `ownsCell(...)` checks for multi-cell owner
  read paths.
- [x] Convert independent scheduler owner locking from single primary cell to
  exact owned-cell set locking.
- [x] Convert current independent-region ownership checks to accept any cell
  owned by the current owner.
- [x] Convert block/fluid scheduled tick dispatch to iterate every cell owned by
  the region owner.
- [x] Convert empty-region cleanup to remove the whole owner, not just the
  primary cell.
- [x] Convert unload processing to scan each owned cell and drain only unloads
  belonging to that cell.
- [x] Convert main-thread internal task rescue to use exact owned-cell locking.
- [x] Add merge-only quiescent owner aggregation API.
- [x] Require source mailbox/internal-task quiescence before merge data
  absorption.
- [x] Require exact owner write lock across target and source cells before
  merge data absorption.
- [x] Gate merge-only owner aggregation behind independent region ticking so
  merged owners never enter the legacy single-cell tick path.
- [x] Absorb source chunks, players, entities, block entities, block events,
  navigation mobs, unload queue, and redstone torch state into target region.
- [x] Clear absorbed source region collections after transfer to avoid stale
  retention and accidental double processing from lingering references.
- [x] Clear transferred source owner cell set after remapping to make source
  owner detachment destructive.
- [x] Remove the runtime-state construction single-cell assertion; multi-cell
  owners keep the primary cell only as a metrics/debug label.
- [x] Remap every source cell to the target owner and detach the source runtime
  state after successful merge.
- [x] Update entity/player move paths so crossing cells inside one merged owner
  updates previous-position bookkeeping without remove/add churn.
- [x] Keep `LevelChunkRegion#getRegionPos` fail-closed for paths that still
  assume single-cell ownership.
- [x] Replace owner-cell unload queue rescans with a one-pass cell-batched
  unload drain.
- [x] Re-run `applyAllPatches` after multi-cell patch-file changes: passed.
- [x] Re-run `compileJava` after multi-cell owner aggregation changes: passed.
- [x] Receive sub-agent correctness review for merge-only owner aggregation:
  Faraday reported no remaining blockers after re-review.
- [x] Receive sub-agent concurrency/performance review for merge-only owner
  aggregation.
- [x] Aquinas reported no remaining blockers after re-review.
- [x] Patch or explicitly defer every merge-only owner aggregation review
  blocker.
- [x] Run `git diff --check` for merge-only owner aggregation.
- [x] Commit merge-only owner aggregation.
- [x] Add owner isolation-cell snapshots: owned cells plus radius-1 buffer.
- [x] Cache owner cell and radius-1 isolation snapshots and invalidate them on
  merge/transfer.
- [x] Add split owner/isolation lock acquisition so owner cells become write
  locks while buffer cells remain isolation/read markers.
- [x] Use owner isolation-cell lock for independent scheduler ticks so
  adjacent owners cannot mutate concurrently.
- [x] Add rate-limited opportunistic quiescent neighbor merge before owner
  ticks instead of probing every tick.
- [x] Stagger first merge probes across handles to avoid a startup merge-probe
  burst.
- [x] Add a conservative merged-owner cell cap; capped neighboring owners remain
  separate but serialized by isolation locks.
- [x] Add failed-candidate tracking so a locked/non-quiescent neighbor does not
  spin the merge loop.
- [x] Keep non-quiescent adjacent owners separate but serialized by the
  isolation-cell lock.
- [x] Add `RegionMerge` JFR event for successful dynamic owner merges.
- [x] Guard `ChunkTaskScheduler.executeMainThreadTask` so independent-mode
  isolation buffer locks do not execute neighboring owner internal tasks.
- [x] Patch tick-thread ownership checks so independent-mode buffer locks do not
  count as ownership.
- [x] Use owner isolation-cell lock for main-thread internal task rescue.
- [x] Compile automatic merge/isolation changes with
  `./gradlew shreddedpaper-server:compileJava --stacktrace`.
- [x] Re-run `applyAllPatches` after automatic merge/isolation patch changes.
- [x] Re-run `compileJava` after automatic merge/isolation patch regeneration.
- [x] Receive sub-agent correctness review for automatic merge/isolation:
  Faraday reported no blockers after the split owner/isolation lock fixes.
- [x] Receive sub-agent performance review for automatic merge/isolation:
  Aquinas reported no blockers after rate limiting, caching, and owner caps.
- [x] Patch or explicitly defer every automatic merge/isolation review blocker.
- [x] Run `git diff --check` for automatic merge/isolation.
- [x] Commit automatic merge/isolation.
- [x] Add split cooldown metadata to `RegionOwner` so split probes are
  suppressed after recent merge/split activity.
- [x] Add `RegionSplit` JFR event for successful owner deaggregation.
- [x] Add active-cell discovery for chunks, player ticking requests, ticking
  entities, tracked entities, players, unload queue, ticking block entities,
  pending block entities, navigating mobs, and block events.
- [x] Treat non-empty scheduled block/fluid tick containers as active cells so
  split does not orphan future scheduled work.
- [x] Make split fail closed while the redstone torch update queue is non-empty
  because queued toggle state has no cheap cell-affinity accessor yet.
- [x] Abort split if the existing owner's primary cell is not currently active,
  preserving owner identity until the owner can be safely removed instead.
- [x] Partition active owned cells into 4-neighbor connected components.
- [x] Keep the primary-cell component on the existing owner and create new
  owners only for disconnected non-primary components.
- [x] Limit split work to one disconnected component per probe to avoid large
  deaggregation bursts.
- [x] Move per-cell runtime state destructively into the new split region:
  chunks, player ticking requests, ticking entities, tracked entities, players,
  unload queue, block entities, pending block entities, navigating mobs, and
  block events.
- [x] Update moved players' `currentRegion` pointer during split migration.
- [x] Hold the region map write lock plus exact owner/isolation locks during
  split so map-routed ingress cannot target a half-migrated owner.
- [x] Patch stale-owner ingress race by holding the region map read/write lock
  across lookup plus enqueue/mutation for mailbox tasks, internal tasks,
  chunk/entity/player membership updates, and block events.
- [x] Patch sub-agent stale-ingress blockers in Minecraft source patches:
  chunk task creation/queueing, block entity ticker ingress, nearby-player
  ticking requests, and chunk unload queue updates now route through
  `LevelChunkRegionMap` helper APIs.
- [x] Patch sub-agent same-tick split blocker by returning child regions from
  split and registering them immediately with the current scheduler context and
  scheduled start.
- [x] Patch child self-contention follow-up by deferring child scheduler
  registration in `RegionHandle.pendingSplitRegions` until the parent owner
  acquires its tick lock and completes its current tick.
- [x] Patch staged-child global-scan race by adding a `RegionOwner`
  scheduler-armed flag; split children remain unarmed until the parent
  successfully completes and arms/registers them.
- [x] Patch child activation publish race by combining current-tick scheduler
  registration and final scheduler-armed publication in one activation API.
- [x] Patch child activation early-return leak by publishing pending split
  children before parent failure/retire/empty handling after a completed tick
  attempt.
- [x] Apply split cooldown stamps to both source and child owners.
- [x] Compile split-by-cell owner deaggregation with
  `./gradlew shreddedpaper-server:compileJava --stacktrace`.
- [x] Re-run `applyAllPatches` after split and ingress patch-file changes.
- [x] Re-run `compileJava` after `applyAllPatches`.
- [x] Receive sub-agent correctness review for split-by-cell owner
  deaggregation.
- [x] Receive sub-agent performance/race review for split-by-cell owner
  deaggregation.
- [x] Patch or explicitly defer every split-by-cell owner deaggregation review
  blocker.
- [x] Run `git diff --check` for split-by-cell owner deaggregation.
- [x] Commit split-by-cell owner deaggregation.

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
- [x] Inspect whether `level.runBlockEvents(region)` needs budget/defer support.
- [x] Add first-pass budget/defer support to `level.runBlockEvents(region)` so
  block events are processed between cooperative budget checks and unprocessed
  events remain region-local for the next tick.
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
- [x] Re-run `applyAllPatches` after `LevelTicksRegionProxy` no-op removal:
  passed.
- [x] Re-run `shreddedpaper-server:compileJava --stacktrace` after
  `LevelTicksRegionProxy` no-op removal: passed.
- [x] Add focused `LevelTicksRegionProxyTest` coverage for cross-region count,
  clear, and copy sub-tick order semantics.
- [x] Run `shreddedpaper-server:compileTestJava --stacktrace`: passed.
- [x] Run focused `LevelTicksRegionProxyTestSuite`; the Gradle task failed only
  because existing unrelated suite classes discover no tests under this filter,
  while `TEST-io.multipaper.shreddedpaper.region.LevelTicksRegionProxyTest.xml`
  reports 3 tests, 0 failures, 0 errors.
- [ ] Decide separately whether to mark the existing empty suite classes
  `failIfNoTests = false` or adjust the Gradle test include/filter behavior.

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
- [x] Remove the temporary forced chunk `[0, 0]` from the `D:\worldgen` test
  world during the next controlled runtime run: RCON reported no chunks were
  currently force-loaded.

### I. Load-Test Plugin

- [x] Create a local Bukkit/Paper plugin project for hostile-load testing.
- [x] Add command: spawn a single-region TNT grid.
- [x] Add command: spawn distributed TNT grids across multiple independent
  regions.
- [x] Add command: generate entity/pathfinding load in one region.
- [x] Add command: create tracker/broadcast update flood.
- [x] Add command: create plugin scheduler/mailbox flood.
- [x] Add command: force chunk generation load away from players.
- [x] Add command: place a normal-region probe that records tick cadence/MSPT.
- [x] Build the plugin jar with
  `./gradlew -p tools/region-load-test-plugin clean build`.
- [x] Request sub-agent review for the load-test plugin before committing.
- [x] Fix load-test plugin review blockers: remove cross-region player target
  access, make queued work cancellable/tracked, avoid caller-thread entity
  cleanup access, and add batch-generation abort checks so old queued work does
  not revive after a new test starts.
- [x] Rebuild the load-test plugin after review fixes:
  `./gradlew -p tools/region-load-test-plugin clean build` passed.
- [x] Request final load-test plugin sub-agent re-review after review fixes:
  Carver reported no blockers.
- [x] Put the plugin in `D:\worldgen\plugins` as
  `D:\worldgen\plugins\region-load-test-plugin-0.1.0-SNAPSHOT.jar`.
- [x] Verify plugin commands load on the modified server with RCON:
  `/regionloadtest help`, `/regionloadtest scheduler global 4 5000`,
  `/regionloadtest cleanup`, `/tps`, and clean `stop` all responded.
- [x] Fix first runtime plugin smoke issue: add `folia-supported: true` so the
  load-test plugin does not enter `SynchronousPluginExecution` and distort
  scheduler/load measurements.
- [x] Re-run plugin smoke after `folia-supported: true`; `RegionLoadTest`
  enabled without a synchronous-execution warning. Existing third-party plugins
  still reported Folia support warnings and are tracked in compatibility TODOs.
- [x] Add RCON/console anchor support to the load-test plugin:
  `/rlt at <world> <x> <y> <z> <subcommand> [args...]`.
- [x] Reject implicit console world-spawn fallback after sub-agent review:
  RCON/console load commands now require explicit `/rlt at <world> <x> <y>
  <z> ...` coordinates unless run by a player.
- [x] Rebuild the load-test plugin after RCON anchor support:
  `./gradlew -p tools/region-load-test-plugin clean build` passed.
- [x] Receive sub-agent review for RCON anchor support.
- [x] Patch every RCON anchor support blocker before runtime deployment:
  implicit console world-spawn fallback was removed and re-reviewed with no
  blocker.
- [x] Copy rebuilt RCON-capable plugin jar into `D:\worldgen\plugins`.
- [x] Restart `D:\worldgen` after plugin replacement.
- [x] Verify `/rlt at world ... probe`, `tntsingle`, `scheduler region`, and
  `chunkgen` from RCON before hostile-load acceptance tests.
- [x] RCON smoke result after anchor support: `probe`, `scheduler region`,
  `chunkgen`, `tntsingle`, `/region top`, and `/tps` all responded; the
  independent scheduler started with normal/degraded workers and reported
  active normal regions.
- [x] Record load-test harness issue from first single-region TNT run: running
  `probe` before a later load command cancelled the probe because every
  subcommand started a new batch.
- [x] Patch load-test batch semantics so `cleanup` is the only batch boundary:
  `probe` and hostile-load commands can now run in either order without
  cancelling each other, while explicit cleanup still aborts tracked work.
- [x] Patch async probe completion visibility for RCON: `replyLater` now writes
  completion messages to the plugin logger before scheduling player/console
  delivery, so long-running probe results are recoverable from
  `D:\worldgen\logs\latest.log`.
- [x] Patch single-region TNT load generation after runtime mailbox evidence:
  group TNT spawn locations by chunk and submit one region task per chunk
  instead of one plugin task per TNT entity, so the plugin mailbox cap measures
  engine behavior instead of the test harness flooding itself.
- [x] Rebuild the load-test plugin after probe batch/logging and chunk-batched
  TNT scheduling changes:
  `./gradlew -p tools/region-load-test-plugin clean build` passed.
- [x] Request sub-agent review for probe batch semantics and chunk-batched TNT
  scheduling; Faraday and Aquinas reported no blockers.
- [x] Patch probe latency reporting to include sorted P95 lag in addition to
  average and max lag, because acceptance is P95 based and a single scheduling
  spike should not fail an otherwise isolated region.
- [x] Rebuild the load-test plugin after P95 probe reporting:
  `./gradlew -p tools/region-load-test-plugin clean build` passed.
- [x] Receive sub-agent review for P95 probe reporting and patch every blocker:
  Aquinas found the first P95 index fix could underflow on the second tick and
  that later load commands still cancelled an already-running probe; both were
  patched and re-reviewed with no blockers. Faraday also reported no blockers.
- [x] Deploy the P95-capable load-test plugin to
  `D:\worldgen\plugins\region-load-test-plugin-0.1.0-SNAPSHOT.jar`.
- [x] Restart `D:\worldgen` after the P95-capable plugin replacement.
- [x] Rerun `/rlt at world ... probe` from RCON and confirm the final log line
  includes `p95LagMs`.
- [x] Capture plugin smoke JFR:
  `D:\worldgen\logs\codex-regionload-plugin-foliaflag-20260425-064052.jfr`.
- [x] Restore `D:\worldgen\server.properties` after temporary RCON testing.

### J. Hostile-Load Acceptance Tests

- [x] Baseline idle server TPS/MSPT with current plugins: RCON `/tps` reported
  20.0 before hostile-load runs.
- [x] Single TNT region: make the overloaded region exceed 1000ms MSPT.
- [x] Runtime evidence: `tntsingle 128 128 1 40 8` in a fresh isolated region
  produced a degraded heavy region with `/region top` reporting approximately
  `mspt=1093.89` while `/tps` still reported 20.0.
- [x] Single TNT region: verify a far normal region P95 MSPT stays <= 50ms when
  CPU headroom exists.
- [x] Runtime evidence before P95 reporting patch: during the 16384 TNT run,
  the far normal probe logged `avgLagMs=0.050` and `maxLagMs=53.953`; this
  shows isolation, but P95 must be rerun with the patched probe output.
- [x] Rerun single TNT region acceptance with P95 probe output and record
  `avgLagMs`, `p95LagMs`, `maxLagMs`, `/region top`, and `/tps`.
- [x] Runtime evidence after P95 reporting patch: start probe first, then run
  `tntsingle 128 128 1 40 8` at a fresh region. Heavy region
  `world RegionPos[384, 0]` reached `DEGRADED mspt=2448.36`, `/tps` stayed
  20.0, and the far normal probe logged `avgLagMs=0.107`,
  `p95LagMs=1.421`, `maxLagMs=108.297`.
- [x] Threshold investigation gate for this run: no scheduler lane or mailbox
  threshold change needed because P95 was below 50ms; keep the max-lag spike
  visible for later multi-heavy regression comparison.
- [ ] Multi-heavy-region test: heavy regions >= tick worker count.
- [ ] Multi-heavy-region setup: choose at least `tickWorkers` disjoint region
  centers, aligned to 8x8 chunk region boundaries, and clear prior TNT/backlog
  with `rlt cleanup` before the run.
- [ ] Multi-heavy-region evidence: record degraded lane worker occupancy,
  normal lane active count, per-region schedule lag, and CPU utilization.
- [ ] Multi-heavy-region test: verify degraded worker cap protects normal
  regions.
- [ ] Plugin flood test: verify bounded mailbox rejects/delays without OOM.
- [ ] Plugin flood evidence: capture `RegionQueue` JFR events and rejected task
  counters for `PLUGIN`, `PLAYER_ACTION`, `TRACKER_BROADCAST`, and
  `EXPLOSION_PHYSICS` classes.
- [ ] Tracker flood test: verify broadcaster/tracker work does not become a
  global barrier.
- [ ] Tracker flood evidence: run normal-region probe concurrently and record
  `RegionOverBudget` events for tracker/broadcast work.
- [ ] Chunk generation DoS test: verify normal regions keep chunk priority.
- [ ] Chunk generation DoS evidence: run attacker-region `chunkgen` and a far
  normal region chunk request/probe concurrently; record chunk wait lag and
  `/region top`.
- [ ] Record logs, `/region top`, JFR, spark output, and CPU utilization for each
  test.
- [ ] For every failed acceptance, add a root-cause note and a new patch TODO.
- [ ] Repeat patch/compile/runtime/load-test until acceptance passes.

### K. Compatibility Repair Loop

- [ ] For every plugin startup error, identify plugin jar and version.
- [ ] Investigate Folia/ShreddedPaper synchronous-execution warnings seen during
  `D:\worldgen` smoke for LuckPerms, Essentials, EssentialsSpawn,
  UltimateAntiBot, Parties, and OldCombatMechanics; decide which are true
  compatibility/performance blockers for hostile-load testing.
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
- [x] Re-run `applyAllPatches` after multi-cell owner unload/rescue patch
  updates: passed.
- [x] Re-run `compileJava` after multi-cell owner merge safety updates: passed.
- [x] Re-run `applyAllPatches` after cell-batched unload optimization: passed.
- [x] Re-run `compileJava` after cell-batched unload optimization: passed.

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
- [x] Fix `LevelTicksRegionProxy.clearArea`, `copyArea`, `copyAreaFrom`, and
  `count`.
- [x] Add focused tests for `LevelTicksRegionProxy` region-spanning count,
  clear, and copy behavior.
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
- [ ] Add continuation cursors before allowing per-item cooperative budget
  checks for chunk ticks, entity task ticks, entity ticks, tracker work, and
  player ticks; sub-agent review rejected cursorless partial iteration because
  it can starve tail entries.
- [x] Implement first-pass continuation cursor fields for entity task ticks,
  entity ticks, chunk ticks, tracker work, player ticks, and scheduled-tick
  owner cells.
- [x] Add snapshot-based round-robin phase walker that resumes from the exact
  rejected index when a budget check trips.
- [x] Preserve full-pass behavior so phases that complete within budget restart
  from the first item on the next tick.
- [x] Reject raw index cursor after sub-agent review: hash/list churn can make
  an index point at a different logical entity/player/chunk/cell on the next
  tick.
- [x] Replace raw index cursors with stable continuation-key cursors for
  chunks, ticking entities, tracked entities, players, and owner cells.
- [x] Sort owner-cell snapshots and switch player/tracker sets to insertion
  ordered sets so continuation order is deterministic under normal churn.
- [x] Add scheduled tick phase continuation so a budget trip between block and
  fluid work resumes the fluid phase for the same owner cell.
- [ ] Review continuation cursor snapshot allocation cost and replace with
  lower-allocation indexed iteration if profiling or sub-agent review shows it
  is too expensive.
- [x] Review block/fluid scheduled-tick accounting and split block/fluid phase
  continuation for one owner cell.
- [x] Re-run `compileJava` after first continuation cursor patch.
- [x] Receive sub-agent correctness/performance review for continuation cursor
  patch.
- [x] Patch every continuation cursor blocker before commit.
- [x] Run `git diff --check` for continuation cursor patch.
- [x] Commit continuation cursor patch.
- [x] Add owner-cell continuation cursor before allowing partial block/fluid
  scheduled tick dispatch; sub-agent review rejected cursorless cell-loop
  breaks because merged owners can starve later cells.
- [x] Add first-pass block-event cooperative budget checks and `BLOCK_EVENT`
  work accounting.
- [x] Re-run `applyAllPatches` after first budget hardening patch.
- [x] Re-run `compileJava` after first budget hardening patch.
- [x] Receive sub-agent correctness/performance review for first budget
  hardening patch.
- [x] Patch or defer every first budget hardening blocker before commit.
- [x] Run `git diff --check` for first budget hardening patch.
- [x] Commit first budget hardening patch.
- [x] Add TNT/explosion backlog hooks or a deferred processing queue.
- [x] Add first TNT explosion budget gate: when a region is already over
  `EXPLOSION` budget, keep the primed TNT alive with fuse 1 instead of
  discarding it, so pending explosions remain region-local backlog.
- [x] Reject live-fuse TNT backlog after sub-agent review: it can create
  client/server lifecycle divergence and drift the eventual explosion
  location through one extra physics tick.
- [x] Replace live-fuse TNT backlog with a region-local `EXPLOSION_PHYSICS`
  task that captures the original explosion position, fire flag, radius, and
  portal damage calculator choice, while firing `ExplosionPrimeEvent` only
  once at fuse expiry.
- [x] Reject removed-entity deferred task after sub-agent review: delayed
  `EntityExplodeEvent` source semantics and mailbox rejection behavior are not
  release-grade.
- [x] Replace task backlog with a live frozen TNT pending-explosion state that
  preserves the source entity until actual explosion, stores original
  explosion parameters, persists pending state, and skips normal TNT physics
  while over budget.
- [x] Re-run `compileJava` after TNT explosion budget gate.
- [x] Re-run `compileJava` after deferred TNT explosion task patch.
- [x] Re-run `applyAllPatches` after frozen TNT pending-explosion patch.
- [x] Fix frozen TNT compile blocker by using the entity movement sync flags
  available in this mapping (`hurtMarked`/`needsSync`) instead of the
  unavailable `hasImpulse` field.
- [x] Re-run `compileJava` after frozen TNT pending-explosion patch.
- [x] Receive first post-compile sub-agent review for frozen TNT pending state:
  Faraday found the Spigot TNT cap could starve pending TNT, and pure deadline
  gating had no guaranteed deferred-drain progress.
- [x] Patch Faraday blocker by handling pending deferred TNT before the Spigot
  active TNT ticking cap.
- [x] Patch Faraday forward-progress risk by adding
  `RegionTickBudget.canDrainDeferred`, allowing one already-deferred work item
  per work type to drain even after the region deadline.
- [x] Receive second post-compile sub-agent review for frozen TNT pending state:
  Aquinas found the deferred flag was server-private, pending TNT did not
  re-check `TNT_EXPLODES`, and delayed `ExplosionPrimeEvent` semantics needed
  explicit compatibility tracking.
- [x] Patch Aquinas client divergence blocker by syncing deferred TNT state via
  `SynchedEntityData` and making client ticks use the same frozen branch.
- [x] Patch Aquinas gamerule risk by re-checking `TNT_EXPLODES` before draining
  a pending deferred explosion and discarding without explosion if disabled.
- [x] Document TNT deferred compatibility behavior: `ExplosionPrimeEvent` is
  fired once at fuse expiry and its cancellation/radius/fire result is the
  persisted decision for a later budget-drained explosion.
- [x] Re-run `applyAllPatches` after TNT review blocker fixes.
- [x] Re-run `compileJava` after TNT review blocker fixes.
- [x] Receive second Aquinas re-review: live deferred TNT entities still formed
  an unbounded backlog under hostile sustained TNT injection.
- [x] Add a bounded per-world deferred TNT backlog cap
  (`deferredTntBacklogPerWorld`) with explicit overflow policy: overflow TNT
  is discarded without exploding to stop entity iteration, memory, and save
  payload debt from growing without bound.
- [x] Track loaded deferred TNT slots and release them on drain, gamerule
  discard, normal removal, and reload/restore failure paths.
- [x] Re-run `applyAllPatches` after deferred TNT backlog cap.
- [x] Re-run `compileJava` after deferred TNT backlog cap.
- [x] Receive Aquinas high-severity follow-up: the cap was bounded but the
  default of 8192 live frozen TNT entities was still too high for hostile-load
  defaults and overflow was not operator-visible.
- [x] Lower default `deferredTntBacklogPerWorld` to 1024.
- [x] Add operator-visible overflow logging every 256 dropped deferred TNT
  explosions per process, including world name and configured cap.
- [x] Re-run `applyAllPatches` after cap default/logging hardening.
- [x] Re-run `compileJava` after cap default/logging hardening.
- [x] Receive final Faraday/Aquinas delta review after cap/logging hardening;
  no remaining release blocker or high-confidence serious regression found.
- [x] Receive sub-agent correctness/performance review for TNT explosion
  budget gate.
- [x] Patch every TNT explosion budget blocker before commit.
- [x] Run `git diff --check` for TNT explosion budget gate.
- [x] Commit TNT explosion budget gate.
- [x] Identify the tracker/broadcast flush path:
  `ShreddedPaperChangesBroadcaster.broadcastChanges()` drains a thread-local
  `ReferenceOpenHashSet<ChunkHolder>` after each region tick.
- [x] Preserve existing repeated-update coalescing by keeping
  `ReferenceOpenHashSet` as the holder backlog instead of introducing a
  per-update queue.
- [x] Add broadcast/tracker budget gating: when `BROADCAST` budget is
  exhausted, requeue the current and remaining holders into the same
  thread-local holder set for the next owner tick.
- [x] Preserve wrong-thread skip behavior so changes are still picked up by
  the correct owner thread, matching the pre-budget path.
- [x] Verify `RegionTickBudget.current()` is bound by `RegionTickScheduler`
  during region ticks and falls back to full draining when called outside a
  budgeted region tick.
- [x] Re-run `compileJava` after broadcast/tracker budget gating.
- [x] Receive sub-agent correctness/performance review for broadcast/tracker
  budget gating.
- [x] Patch every broadcast/tracker budget blocker before commit: Faraday and
  Aquinas found no blockers; both recommended filtering wrong-thread holders
  before budget requeue and explicitly passing the tick budget from
  `ShreddedPaperChunkTicker`, which was patched and re-reviewed with no
  release blocker.
- [x] Run `git diff --check` for broadcast/tracker budget gating.
- [x] Commit broadcast/tracker budget gating.
- [x] Add `/region top`, `/region inspect`, and `/region dump`.
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

- [x] Create or import a local open-source-compatible load-test plugin.
- [x] Add commands for TNT grid, distributed TNT grids, entity/pathfinding load,
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
- [x] Independent scheduler is wired into the world tick path for current fixed
  ShreddedPaper regions.
- [x] Global tracker and player flush phases are skipped globally in independent
  mode and run from owner region ticks.
- [x] `LevelTicksRegionProxy` no longer has stubbed area/copy/count methods.
- [x] `D:\worldgen` runtime baseline has been captured for startup,
  `/region top`, `/tps`, forceload, JFR, and clean shutdown.
