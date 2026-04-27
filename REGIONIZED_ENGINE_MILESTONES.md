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

## Release Completion Gate

The TODO ledger is a guardrail, not the definition of done. Even if every
granular item below is checked, this project is not release-complete until the
core design goals are demonstrably true in `D:\worldgen` under hostile load:

- [ ] Dynamic region ownership preserves the Folia-style buffer invariant:
  neighboring unsafe owners are merged/transient/serialized before concurrent
  ticks, ticking owners do not grow while ticking, and split hysteresis prevents
  merge/split churn.
- [ ] Independent tick cadence is proven: a region with extreme MSPT does not
  block unrelated worlds/regions when CPU headroom exists, and this is measured
  with P95 probe lag rather than only `/tps`.
- [ ] Hostile-load isolation is proven beyond a single TNT case: degraded lane
  cap, normal worker reservation, bounded mailbox policy, TNT/physics backlog,
  broadcast/tracker budgeting, and chunk-generation pressure all have runtime
  evidence.
- [ ] Cross-region mutation is protocol-based: no hidden global lock or
  blocking sync-load path can silently turn a local plugin mistake into a global
  tick or chunk-system barrier.
- [ ] Observability is production-grade: `/region top`, `/region inspect`,
  `/region dump`, and JFR events identify region tick, queue, over-budget,
  merge/split, chunk request, and cross-region task pressure without per-entity
  or per-packet spam.
- [ ] Plugin compatibility risks are triaged with the actual `D:\worldgen`
  plugin set; open-source plugin patches are separated from engine commits and
  rebuilt into the test server when they are true blockers.
- [ ] Final acceptance includes a clean build, regenerated patches, runtime
  startup, stress-test evidence, sub-agent review, and a written residual-risk
  note for every deferred item.

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
- [x] Re-open autosave/save-all after sub-agent review identified the remaining
  spin/park save barrier as a hostile-load isolation blocker for
  `saveAllChunks(false,false,false,false)`.
- [x] Add a dedicated bounded `CHUNK_IO_SAVE` mailbox class so background chunk
  saves do not use the non-dropping `CRITICAL_SYSTEM` lane.
- [x] Add `chunkIoSaveRegionMailboxCapacity`,
  `chunkIoSaveMaxAutoSavesPerRegion`, and
  `chunkIoSaveAutoSaveScanMultiplier` configuration knobs.
- [x] Add low `CHUNK_IO_SAVE` drain quantum after critical/player work and
  before plugin/tracker/physics work.
- [x] Add `RegionTick` JFR queued count for `CHUNK_IO_SAVE`.
- [x] Add `LevelChunkRegionMap.scheduleTaskIfSchedulerArmed` so background save
  handoff cannot enqueue work into an unarmed owner mailbox that no scheduler
  handle will service.
- [x] Add `LevelChunkRegionMap.ownerIdForCellOr` so autosave can enforce a
  per-owner admission cap instead of letting one hot region consume the full
  autosave producer budget.
- [x] Add per-holder background save coalescing fields in `NewChunkHolder` so
  autosave/save-all bursts admit at most one outstanding background save per
  holder.
- [x] Change `ChunkHolderManager.autoSave` from fire-and-forget critical
  scheduling to bounded `CHUNK_IO_SAVE` dispatch with scan cap, per-owner cap,
  scheduler-armed admission, and short retry requeue.
- [x] Self-review autosave retry behavior and fix same-pass queue churn by
  requeueing skipped saves for the next manager tick instead of immediately
  due again.
- [x] Reject async `saveAllChunks(false,false,false,false)` after sub-agent
  review showed default `/save-all` has caller-visible completion semantics;
  keep explicit save-all, flush, shutdown, emergency, and progress saves as
  synchronous durability paths.
- [x] Upgrade `saveAllChunks` non-owner saves from read-lock waiting to write
  lock waiting so `NewChunkHolder.save` satisfies independent-mode tick-thread
  ownership checks.
- [x] Add immediate non-waiting write-lock fallback for `CHUNK_IO_SAVE` when
  owner lookup, scheduler arming, or mailbox admission cannot accept the task;
  if the write lock is not immediately available, requeue for the next manager
  pass instead of spinning.
- [x] Add completion-aware retry for background chunk saves: if
  `holder.save(false)` throws before completion, clear the in-flight flag,
  requeue the holder, and log the immediate fallback failure.
- [x] Preserve empty-server pause `MinecraftServer.autoSave()` as an intentional
  strong save barrier because it protects old pause-before-save and scoreboard
  persistence semantics when no online-player hostile-load isolation is at
  stake.
- [x] Receive sub-agent review for the autosave/save-all isolation patch and
  patch every release blocker before commit: stale holder requeue, tree key
  mutation, default save-all durability, unarmed scheduler retry, read-lock
  ownership, and failed async save retry were all addressed; the empty-server
  pause save barrier is explicitly retained with rationale.
- [x] Runtime-test `save-all` and `save-all flush` in `D:\worldgen` after a jar
  rebuild: final smoke on the rebuilt jar queued/executed 600/600
  cross-region tasks, completed `save-all` and `save-all flush`, kept TPS at
  20.0/20.1, and logged no new `ERROR`/`Exception`/immediate autosave fallback
  failures in `D:\worldgen\logs\latest.log`.
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
- [x] Add JFR `RegionMerge` and `RegionSplit` events for dynamic owner
  merge/split work.
- [x] Add sampled JFR `ChunkRequest` event for rejected region-worker sync chunk
  loads; runtime evidence file:
  `D:\worldgen\logs\codex-syncload-guard-20260425-1546.jfr`.
- [x] Add threshold-only JFR `CrossRegionTask` events for region-thread to
  region-thread task hops, with source/target owner id, owner epoch,
  affinity cell key, task class, queued-after/capacity, and explicit
  `delayClock=region-local`.
- [x] Add owner layout epoch tracking and execution-time stale-owner detection
  for admitted region tasks; redirected admitted tasks are re-resolved through
  the live `LevelChunkRegionMap` and moved through a non-dropping transfer lane
  instead of being re-admitted through bounded plugin/player queues.
- [x] Add per-class `RegionTick` mailbox counts and max class pressure so small
  class caps, such as `PLUGIN`, can move a region into `DEGRADED` before total
  mailbox depth looks dangerous.
- [ ] Add per-region explosion backlog metric.
- [x] Add per-region chunk IO/generation debt metric: `RegionTick`,
  `/region top`, `/region inspect`, and overload scoring now include chunk IO
  in-flight count, deferred retry count, rejected count, and pressure.

### F2. Chunk IO And Generation QoS

- [x] Map every region-thread chunk load/generation path before patching:
  `ServerChunkCache.syncLoad`, `ServerChunkCache.getChunkFuture`,
  `ChunkTaskScheduler.scheduleChunkLoad`, player chunk loader scheduling,
  `MoonriseRegionFileIO.loadData/loadDataAsync`, entity/POI data load tasks,
  and plugin `CraftWorld#getChunkAtAsync`; sub-agent review recorded remaining
  deep executor ingress risks in `ChunkLoadTask`, `ChunkUpgradeGenericStatusTask`,
  `MoonriseRegionFileIO`, and `NewChunkHolder` save paths.
- [x] Classify each path as a hard reject, async continuation, bounded retry,
  priority downgrade, or intentional blocking durability path. Current patch
  treats region-worker full sync loads as hard reject, ticketed async plugin/API
  loads as bounded retry, degraded owners as priority downgrade, player loader
  no-ticket requests as intentionally preserved, and shutdown/save-all as
  durability exceptions.
- [x] Add a distinct `CHUNK_IO_LOAD` mailbox class so deferred chunk
  load/generation retries cannot consume `CRITICAL_SYSTEM`, plugin, tracker, or
  save-lane capacity.
- [x] Add per-region chunk IO/generation tracker state with in-flight count,
  deferred retry count, rejected count, downgraded count, completed count, and
  pressure calculation.
- [x] Add config knobs for normal/degraded per-region async chunk request caps,
  deferred retry delay, deferred mailbox capacity, and degraded priority
  downgrade.
- [x] Wire `ChunkTaskScheduler.scheduleChunkLoad` admission so plugin/external
  ticketed async chunk loads above the target region quota are retried through a
  bounded region-local `CHUNK_IO_LOAD` mailbox instead of flooding the global
  chunk executor.
- [x] Preserve player chunk loader and already-loaded fast paths so normal
  player movement does not regress under chunkgen DoS protection.
- [x] Preserve `BLOCKING`/shutdown/durability paths as explicit exceptions and
  document why they are not hostile-load scheduling paths.
- [x] Emit sampled JFR `ChunkRequest` events for deferred, rejected, downgraded,
  admitted-over-pressure, and completed chunk IO/generation requests without
  per-chunk spam.
- [x] Include chunk IO in `/region top`, `/region inspect`, `RegionTick`, and
  overload/degraded-lane decisions.
- [x] Self-review for deadlocks: no deferred retry may wait on the same region
  mailbox or exact owner marker lock while holding chunk scheduler/ticket locks.
- [x] Sub-agent review: correctness/race review of chunk request admission,
  retry completion semantics, and merge/split owner routing.
- [x] Sub-agent review: performance review of caps, pressure math, sampling, and
  normal-region priority preservation.
- [x] Compile: run `applyAllPatches`, `compileJava --rerun-tasks`, load-test
  plugin build, and paperclip jar build.
- [x] Runtime smoke: verify new `rlt status`, `rlt scenario gen`,
  `rlt scenario load`, `region top`, RCON `tps`, and clean RCON `stop` in
  `D:\worldgen`.
- [x] Hostile-load test: `rlt at world 0 100 0 scenario gen 12 1024 6 false
  1200 1` queued 12 independent generation regions while the control probe
  logged `avgLagMs=0.667`, `p95LagMs=1.412`, `maxLagMs=186.410`; `/tps`
  remained near 20 after the run.
- [x] Load-dominant test: after pregeneration, `scenario load 4 256 6 false
  600 1` completed with control `avgLagMs=0.418`, `p95LagMs=1.299`,
  `maxLagMs=146.032`.
- [x] Patch Nash lifecycle blocker: chunk IO debt is now part of runtime-state
  idleness, merge quiescence, and region emptiness. `RegionRuntimeState`
  refuses to detach idle cells while `RegionChunkIoTracker` has in-flight or
  deferred retry work, and `LevelChunkRegion` split/merge/empty checks include
  the same pending-work guard.
- [x] Patch Newton cold-region QoS blocker: chunk IO admission now creates or
  reuses the target cell runtime state through
  `LevelChunkRegionMap.getOrCreateRuntimeStateForCell(...)`, so detached/cold
  target regions no longer bypass per-region caps before the first owner tick.
- [x] Re-run `applyAllPatches` after lifecycle and cold-region QoS fixes:
  passed on 2026-04-25.
- [x] Re-run `compileJava --stacktrace` after lifecycle and cold-region QoS
  fixes: passed on 2026-04-25.
- [x] Rebuild `createMojmapPaperclipJar` and the load-test plugin after F2
  fixes: both passed on 2026-04-25.
- [x] Verify deferred/downgrade activation under an intentionally low runtime
  cap in `D:\worldgen`: temporarily set normal cap `16`, degraded cap `4`, and
  retry delay `1`, then ran
  `rlt at world 0 100 0 scenario gen 1 4096 8 false 400 1`.
- [x] Runtime cap evidence: `/region top` reported target regions as
  `DEGRADED` with examples like `chunkIO=16/4 deferred=48 1200%` and later
  `chunkDowngraded=48`; control region stayed normal and `/tps` returned to
  `20.0` after the burst.
- [x] Runtime latency evidence for the low-cap run: `RegionLoadTest` logged
  `scenario control finished at chunk=0,0: samples=400 periodTicks=1
  avgLagMs=0.360 p95LagMs=1.557 maxLagMs=188.548`, while the remote generation
  batch completed `289/289` chunks in `7787.32ms`.
- [x] JFR cap evidence:
  `D:\worldgen\logs\codex-region-foundation.jfr` contains `RegionTickEvent`
  count `1549`, `ChunkRequest` count `79`, `RegionOverBudgetEvent` count `45`,
  and `RegionMergeEvent` count `35`; `ChunkRequest` grouped output showed
  `75` `async-load-deferred` events and `4` `async-load-downgraded` events.
- [x] Restore `D:\worldgen\shreddedpaper.yml` chunk IO caps to release defaults
  after the low-cap proof run: normal `256`, degraded `32`, retry delay `2`.
- [x] Receive Euler performance re-review: no blocker for the implemented
  external ticketed async-request QoS slice, but two high-quality follow-ups
  were accepted: scenario control probes must cover the entire hostile run, and
  internal chunk executor ingress still needs its own F2b isolation layer.
- [x] Patch scenario proof harness: `/rlt scenario` now treats the `samples`
  argument as a minimum and keeps the control probe running until every scenario
  chunk batch finishes, so p95/max evidence covers the whole hostile burst.
- [x] Receive Fermat correctness re-review: previous lifecycle/cold-region
  blockers were closed, but a split-created owner could be visible before its
  scheduler was armed, turning a capped deferred retry into a transient hard
  reject.
- [x] Patch split-window chunk IO retry race: chunk load retry admission now
  queues through `scheduleTaskIfRegionExists(...)`, allowing retry work to wait
  in the just-split region mailbox before the scheduler is armed. Autosave and
  other armed-only paths still use `scheduleTaskIfSchedulerArmed(...)`.
- [x] Re-run `applyAllPatches`, `compileJava`, load-test plugin build, and
  `createMojmapPaperclipJar` after the scenario probe and split-window fixes:
  all passed on 2026-04-25.
- [x] Runtime full-burst proof after scenario probe fix: with low caps
  normal `16`, degraded `4`, retry delay `1`, ran
  `rlt at world 0 100 0 scenario gen 1 8192 8 false 400 1`; remote batch
  finished `289/289` chunks in `10233.98ms`, and the control probe finished
  after the batch with `samples=400 avgLagMs=0.358 p95LagMs=1.537
  maxLagMs=76.110`.
- [x] Runtime probe-extension proof: ran
  `rlt at world 0 100 0 scenario gen 1 16384 8 false 100 1`; remote batch
  finished in `10814.90ms`, and the control probe extended beyond the minimum
  to `samples=206`, finishing at the same timestamp with `avgLagMs=2.681
  p95LagMs=1.157 maxLagMs=577.851`.
- [x] JFR proof after re-review fixes:
  `D:\worldgen\logs\codex-chunkqos-probe-20260425-220338.jfr` contains
  `RegionTickEvent` count `3980`, `ChunkRequest` count `253`,
  `RegionMergeEvent` count `101`, and `RegionOverBudgetEvent` count `40`;
  grouped `ChunkRequest` output showed `245` sampled `async-load-deferred`
  events and `8` sampled `async-load-downgraded` events. These are sampled
  activation events, not exact total request counts.
- [x] Restore `D:\worldgen\shreddedpaper.yml` chunk IO caps to release defaults
  again after the probe-extension proof run: normal `256`, degraded `32`, retry
  delay `2`.
- [x] Investigate patch-file whitespace review item: replacing unified-diff
  blank context marker lines (`" "`) with truly empty lines makes
  `applyAllPatches` fail with `Failed to apply 5/648 hunks`. Keep those marker
  lines because paperweight patch syntax requires them; use `git diff --check`
  excluding Minecraft `.patch` hunk context when checking ordinary source
  whitespace.
- [x] Commit chunk IO/generation QoS as one coherent patch after review and
  runtime evidence: commit `b956994 Add region chunk IO load QoS`.

#### F2b. Internal Chunk Executor Ingress QoS

- [x] Collect sub-agent design feedback for internal executor ingress. Laplace
  accepted the main starvation surface (`parallelGenExecutor` and off-main
  load/decode) and warned against hiding region `internalTasks` behind another
  queue or dropping internal engine work.
- [x] Narrow first F2b patch scope to safety-first worker ingress:
  `ChunkUpgradeGenericStatusTask` parallel generation and `ChunkLoadTask`
  off-main chunk/entity/POI decode. Region-file IO, compression, save
  serialization, radius-aware generation, and async generation `join()` tail
  measurement remain explicit follow-up items in this same milestone.
- [x] Add internal executor deferred retry accounting. Initial mailbox-based
  retry was rejected after performance review because it tick-gated worker
  replenishment; the patch now uses a non-dropping, permit-release-triggered
  per-region wait queue with a warning reserve.
- [x] Add config knobs for internal executor deferred retry reserve,
  normal/degraded in-flight caps, and degraded priority downgrade.
- [x] Extend per-region chunk IO tracker with internal executor in-flight,
  deferred retry, rejected/fallback, downgraded, completed, and pressure
  counters.
- [x] Add a permit-aware `PrioritisedTask` wrapper that creates the real
  executor task immediately, gates only `queue()`/`execute()` admission,
  preserves cancel/priority/sub-order/stream delegation, and releases permits
  on completion or successful pre-run cancellation.
- [x] Make deferred internal engine work non-lossy: retry immediately when an
  executor permit is released instead of waiting for the next region tick; keep
  deferred retry counts in region pending-work accounting.
- [x] Count deferred internal executor work as region pending work so
  split/merge/detach/empty checks cannot strand it.
- [x] Expose internal executor pressure in `RegionTick` JFR events and
  `/region top|dump|inspect` as separate `exec=` fields while keeping combined
  chunk IO pressure in degraded-lane scoring.
- [x] Patch parallel-capable worldgen tasks to use the internal executor QoS
  wrapper; leave radius-aware generation unchanged until its area ordering and
  cancellation semantics are reviewed separately.
- [x] Patch off-main chunk/entity/POI load-decode tasks to use the internal
  executor QoS wrapper without changing on-main region task rescue behavior.
- [x] Regenerate Minecraft source patches and inspect that only the intended
  `ChunkLoadTask` and `ChunkUpgradeGenericStatusTask` source patches are added
  or updated.
- [x] Static verification: run `applyAllPatches`, `compileJava --stacktrace`,
  load-test plugin build, and paperclip jar build.
- [x] Self-review `PrioritisedTask` semantics: double queue, cancel before
  queue, cancel while deferred, cancel after permit, priority changes during
  deferral, and permit underflow.
- [x] Sub-agent correctness review: request focused review on cancellation,
  non-lossy fallback, split/merge ownership, and lifecycle accounting.
- [x] Sub-agent performance review: request focused review on normal-region
  throughput, degraded caps, JFR sampling, and queue-pressure behavior.
- [x] Patch performance review high finding: executor in-flight pressure alone
  no longer demotes a healthy region; only request-side pressure, deferred work,
  or normal tick/mailbox overload moves the owner to `DEGRADED`.
- [x] Patch performance review high finding: deferred executor work is no
  longer region-tick-gated; permit release and enqueue both drain a per-region
  waiter queue immediately.
- [x] Patch correctness review blocker: deferred waiter enqueue now self-drains
  to close the lost-wakeup window where the last permit could be released
  before the waiter was visible.
- [x] Patch correctness review blocker: stale cancelled waiters are skipped
  until capacity fills or the queue is empty, so cancelled callbacks cannot
  consume a full wakeup batch and strand live work.
- [x] Patch correctness review medium: `PermitTask.cancel()` now returns success
  for wrapper-cancelled `NEW`/`WAITING` tasks and preserves `COMPLETED` state.
- [x] Sub-agent re-review after fixes: correctness and performance reviewers
  reported no blocker/high/medium findings.
- [x] Add lifetime visible waiting registration for wrapped executor tasks:
  a queued `PrioritisedTask` now counts as per-region pending work before it
  reaches the real Moonrise worker executor, closing the hidden WAITING handoff
  hole found in review.
- [x] Reject producer parking after runtime/thread-dump review: the first
  non-dropping design could keep Moonrise region-file callbacks waiting for
  executor permits. Replace it with non-blocking admission, deferred retry, and
  overflow/backpressure accounting.
- [x] Add tracker-owned bounded backlog admission queue. When the visible
  waiting cap is full, the task enters `executorBacklogWaiters` and remains in
  region pending-work accounting via `executorBacklogQueuedTasks`.
- [x] Add async-load backpressure coupling: new async chunk-load admissions are
  deferred with `async-load-deferred-executor-backlog` while internal executor
  waiting plus backlog queue is saturated, preventing unbounded upstream ticket
  growth.
- [x] Add last-resort emergency executor for non-dropping engine work after the
  per-region backlog queue and overflow/backpressure reserves are full. This is
  bounded globally and reported as `executor-*-backlog-emergency` plus
  `backlogEmergencyInFlight` in JFR and `/region top`.
- [x] Patch sub-agent blocker from Curie/Anscombe: emergency executor rejection
  no longer recursively calls `deferBacklogAdmission(...)` on the producer
  thread. It registers a coalesced detached emergency retry token instead.
- [x] Patch sub-agent blocker from Curie: emergency retry visibility is now a
  lifetime bridge. `executorBacklogEmergencyInFlight` remains visible until the
  retry token is registered, and the retry token remains visible until waiting,
  backlog, or emergency visibility is reacquired.
- [x] Patch sub-agent high from Anscombe: static emergency executor and retry
  executor are drained/shut down before `RegionTickScheduler` clears region
  queues and runtime state.
- [x] Patch sub-agent high from Anscombe: emergency in-flight and emergency
  retry waiters force `DEGRADED` classification so the last-resort path remains
  isolated from normal-lane reservation.
- [x] Rate-limit executor backlog/backpressure saturation logs to the first and
  every 256th event so forced hostile load does not become log IO load.
- [x] Extend observability with `executorBacklogQueued`,
  `executorBacklogEmergencyInFlight`, `executorBacklogEmergencyRetries`,
  `executorBacklogEmergencyRejected`, backlog pressure, deferred/backpressure
  counters, and emergency counters in `RegionTickEvent` and `/region`.
- [x] Runtime forced low-cap proof in `D:\worldgen`: with executor normal cap
  `1`, degraded cap `1`, overflow `4`, effective backlog cap `64`, and load
  caps `512`, ran
  `rlt at world 0 100 0 scenario gen 2 700000 10 false 80 1`. Both remote
  441-chunk generation batches completed; TPS stayed at `20.0, 20.0, 20.0,
  20.0` early and `16.8, 19.7, 19.9, 20.0` during the burst.
- [x] Forced low-cap `/region top` evidence after the run:
  `execBacklogEmergency=47` in region `87501,-1` and
  `execBacklogEmergency=26` in region `175001,0`; both showed backlog
  backpressure/deferred/overflow activity and `execBacklogEmergencyRejected=0`.
- [x] Forced low-cap JFR proof:
  `D:\worldgen\logs\codex-f2b-emergency-latest-20260426-032347.jfr`.
  Sampled `ChunkRequest` actions included
  `executor-load-decode-backlog-emergency=27`,
  `executor-load-decode-backlog-deferred=39`,
  `executor-load-decode-backlog-backpressure=40`,
  `executor-load-decode-overflow-fallback=68`,
  `executor-load-decode-overflow-backpressure=68`,
  `executor-generation-deferred=25`, and
  `async-load-deferred-executor-backlog=18`.
- [x] Forced low-cap thread-dump proof:
  `D:\worldgen\logs\codex-f2b-emergency-latest-thread-dump.txt` had no
  `RegionChunkExecutorLimiter` or `RegionChunkIoTracker` producer-thread wait
  frames after completion.
- [x] Record invalid stress input artifact: the attempted
  `rlt ... scenario gen 2 1500000 10 ...` generated center chunk `3000000,0`
  and crashed with vanilla/Paper's "Trying to create chunk out of reasonable
  bounds" guard. This was a test-coordinate error, not an F2b engine blocker.
- [x] Restore `D:\worldgen\shreddedpaper.yml` internal executor caps to release
  defaults after forced proof: executor normal `8`, degraded `2`, overflow `64`,
  backlog `8192`, deferred reserve `1024`, backpressure reserve `4096`, load
  normal `256`, load degraded `32`.
- [x] Runtime release smoke in `D:\worldgen`: with release caps, restarted the
  real plugin set and ran
  `rlt at world 0 100 0 scenario gen 1 600000 6 false 60 1`. The 169-chunk
  generation batch completed immediately enough that `activeChunkBatches=0` on
  the first poll; TPS reported `17.6, 19.7, 19.7, 19.7`.
- [x] Release-smoke JFR proof:
  `D:\worldgen\logs\codex-f2b-release-latest-20260426-032730.jfr` sampled only
  `executor-load-decode-deferred=4` and `executor-load-decode-downgraded=1`;
  no emergency path was taken under release caps.
- [x] Sub-agent final re-review after blocker/high fixes: Curie reported no
  blocker/high findings; Anscombe reported no blocker/high findings and noted
  runtime verification as the remaining non-static evidence, now satisfied by
  the forced and release `D:\worldgen` runs above.
- [x] Static verification after final fixes: `applyAllPatches`,
  `:shreddedpaper-server:compileJava --rerun-tasks --stacktrace`,
  `:shreddedpaper-server:createMojmapPaperclipJar --stacktrace`, and
  `./gradlew.bat -p tools/region-load-test-plugin build --stacktrace` all
  passed on 2026-04-26.
- [x] Commit F2b worker-ingress QoS as a separate coherent patch after final
  `git diff --check`.
- [ ] F2b follow-up: gate or account for region-file IO queue ingress after
  reviewing `MoonriseRegionFileIO` coalescing/cancellation semantics.
- [ ] F2b follow-up: gate compression and save serialization workers only after
  proving shutdown/save-all durability paths cannot be throttled into data loss.
- [ ] F2b follow-up: instrument generation `join()` tail time before changing
  behavior around async generation futures.

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
- [x] Run `applyAllPatches` after `CHUNK_IO_SAVE` autosave/save-all isolation:
  first two attempts failed on stale paperweight hunk counts in
  `ChunkHolderManager.java.patch`; repaired the patch counts with a standard
  `git apply --check` sanity pass against the paperweight setup source.
- [x] Re-run `applyAllPatches` after autosave/save-all patch-count repair:
  passed with 160 Minecraft source patches applied.
- [x] Re-run `shreddedpaper-server:compileJava --stacktrace` after
  autosave/save-all isolation and next-tick retry fix: passed with only
  existing deprecation/removal warnings.
- [x] Re-run `applyAllPatches` after final autosave/save-all review fixes
  (`saveAllChunks` write-lock ownership and async save failure retry): passed.
- [x] Re-run `shreddedpaper-server:compileJava --stacktrace` after final
  autosave/save-all review fixes: passed with only existing
  deprecation/removal warnings.
- [x] Re-run `shreddedpaper-server:createMojmapPaperclipJar --stacktrace` after
  final autosave/save-all review fixes: passed.
- [x] Re-run `applyAllPatches` after `CHUNK_IO_LOAD` admission patch was
  manually added to Minecraft source patches: passed with 160 Minecraft source
  patches applied.
- [x] Re-run `shreddedpaper-server:compileJava --rerun-tasks --stacktrace`
  after `CHUNK_IO_LOAD` admission patch: passed with only existing
  deprecation/removal warnings.
- [x] Re-run `shreddedpaper-server:createMojmapPaperclipJar --stacktrace` after
  `CHUNK_IO_LOAD` admission patch: passed and produced
  `shreddedpaper-server/build/libs/shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar`.
- [x] Deploy the final rebuilt jar to `D:\worldgen`, restart through
  `D:\worldgen\codex-server-launch.ps1`, and verify RCON smoke:
  `rlt ... crossqueue 64 600 0`, `region top`, `save-all`,
  `save-all flush`, and `tps` all completed successfully.
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
- [x] Deploy chunk IO QoS build to `D:\worldgen` on 2026-04-25, replacing
  `shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar` after creating a
  timestamped backup, and restart through `codex-server-launch.ps1`.
- [x] Runtime startup after chunk IO QoS build: server reached `Done` in
  44.080s on the final F2 verification run, `RegionLoadTest` loaded, and RCON
  `/rlt status` responded.
- [x] Runtime shutdown after chunk IO QoS tests: RCON `stop` returned
  `Stopping the server`; server save/shutdown completed through all three
  worlds, Java exited, and the stale launcher pid file was removed after
  verifying no process remained.

### I. Load-Test Plugin

- [x] Create a local Bukkit/Paper plugin project for hostile-load testing.
- [x] Add command: spawn a single-region TNT grid.
- [x] Add command: spawn distributed TNT grids across multiple independent
  regions.
- [x] Add command: generate entity/pathfinding load in one region.
- [x] Add command: create tracker/broadcast update flood.
- [x] Add command: create plugin scheduler/mailbox flood.
- [x] Add command: force chunk generation load away from players.
- [x] Add command: force load-only async chunk requests away from players:
  `/rlt chunkload <radiusChunks> [urgent]`.
- [x] Add command: one-shot chunk QoS scenario combining control probe plus N
  remote chunkgen/chunkload regions:
  `/rlt scenario <gen|load> <regions> <strideChunks> <radiusChunks> [urgent]
  [samples] [periodTicks]`.
- [x] Add command: report active harness state with `/rlt status`, including
  managed tasks, entities, chunk tickets, and active chunk batches.
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
- [x] Rebuild the load-test plugin after `chunkload`, `scenario`, and `status`
  command additions: `..\..\gradlew.bat build` from
  `tools\region-load-test-plugin` passed.
- [x] Deploy the rebuilt plugin to
  `D:\worldgen\plugins\region-load-test-plugin-0.1.0-SNAPSHOT.jar` and verify
  RCON `/rlt status` reports the new active chunk-batch counters.

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
- [x] Multi-heavy-region test: heavy regions >= tick worker count.
- [x] Multi-heavy-region setup: choose at least `tickWorkers` disjoint region
  centers, aligned to 8x8 chunk region boundaries, and clear prior TNT/backlog
  with `rlt cleanup` before the run.
- [x] Multi-heavy-region evidence: record degraded lane worker occupancy,
  normal lane active count, per-region schedule lag, and CPU utilization.
- [x] Multi-heavy-region test: verify degraded worker cap protects normal
  regions.
- [x] Multi-heavy-region first attempt: `tntspread 12 64 64 1 40 8 2`
  produced many degraded regions above 1000ms MSPT while the far normal probe
  region stayed near sub-ms MSPT and `/tps` stayed around 20 before the run hit
  a separate chunk-load crash.
- [x] Record crash from first multi-heavy attempt:
  `D:\worldgen\crash-reports\crash-2026-04-25_13.31.31-server.txt` failed in
  `CraftWorld.getHighestBlockYAt()` from the load-test plugin, which triggered
  sync chunk/POI loading on `ShreddedPaperRegionDegraded-14`.
- [x] Patch load-test plugin to stop using `getHighestBlockYAt()` in hostile
  spawn paths; TNT, pathfinding mobs, and tracker stands now use the caller's
  explicit Y coordinate so the harness measures hostile entity work instead of
  accidental sync terrain lookup.
- [x] Rebuild and sub-agent review the fixed-y hostile spawn patch before
  redeploying.
- [x] Rerun the 12-region TNT spread after fixed-y spawn deployment.
- [x] Add engine-side guard for region tick worker sync chunk/POI loads:
  `ServerChunkCache.syncLoad` now fails fast on independent region workers
  before scheduling chunk-system work, emits sampled `ChunkRequest` JFR evidence,
  and turns plugin misuse into controlled task failure instead of a fatal
  chunk-system crash.
- [x] Add `/rlt syncload <chunkOffsetX> [attempts]` runtime harness to reproduce
  unloaded sync height/chunk lookup from a region worker without reintroducing
  the old TNT spawn bug.
- [x] Sub-agent review for sync-load guard: Faraday and Aquinas both reported no
  release blocker and confirmed already-loaded chunk fast paths bypass
  `syncLoad`; Aquinas requested JFR/log sampling, which was patched.
- [x] Compile verification for sync-load guard: `applyAllPatches`,
  `:shreddedpaper-server:compileJava`, load-test plugin `build`, and
  `:shreddedpaper-server:createMojmapPaperclipJar` passed.
- [x] Runtime sync-load guard evidence: `D:\worldgen` command
  `/rlt at world 0 80 0 syncload 50000 3` reported
  `guardRejections=3`, `unexpectedSuccess=0`, `unexpectedFailure=0`; `/tps`
  stayed 20.0 and `/region top` showed no degraded regions.
- [x] JFR sync-load guard evidence:
  `D:\worldgen\logs\codex-syncload-guard-20260425-1546.jfr` contains sampled
  `io.multipaper.shreddedpaper.region.ChunkRequest` with
  `action=sync-load-rejected`, `chunkX=50000`, `status=minecraft:full`, and
  stack through `CraftWorld#getHighestBlockYAt`.
- [x] Runtime loaded-chunk fast-path evidence: after `forceload add 0 0` and
  urgent `/rlt at world 0 80 0 chunkgen 0 true`, `/rlt at world 0 80 0
  syncload 0 1` reported `loadedBefore=true`, `guardRejections=0`,
  `unexpectedSuccess=1`, proving already-loaded region-worker chunk reads still
  bypass `syncLoad`.
- [x] Runtime sync-load sampling evidence:
  `/rlt at world 0 80 0 syncload 50001 300` reported `guardRejections=300`
  with `/tps` at 20.0, and
  `D:\worldgen\logs\codex-syncload-sampling-20260425-1549.jfr` contains only
  sampled `ChunkRequest` counts `1` and `256`.
- [x] Chunk generation QoS smoke: `rlt at world 0 100 0 scenario gen 4 256 6
  false 600 1` completed four 169-chunk generation batches in roughly
  13.8s-16.0s; the control probe logged `avgLagMs=4.499`,
  `p95LagMs=2.090`, `maxLagMs=1518.175`.
- [x] Chunk load-only QoS smoke: `scenario load 4 256 6 false 600 1`
  completed in roughly 0.96s-1.26s per batch, with expected `null` results for
  chunks that were not already loadable without generation; the control probe
  logged `avgLagMs=0.418`, `p95LagMs=1.299`, `maxLagMs=146.032`.
- [x] Chunk generation hostile-load run with regions >= tick workers:
  `scenario gen 12 1024 6 false 1200 1` queued 12 independent generation
  regions. Eleven remote batches finished around 68.2s-68.3s, one previously
  generated batch finished in 25.6s, and the control probe logged
  `avgLagMs=0.667`, `p95LagMs=1.412`, `maxLagMs=186.410`.
- [x] Rerun after fixed-y spawn deployment: no chunk-load crash; far normal
  probe logged `avgLagMs=0.052`, `p95LagMs=1.150`, `maxLagMs=91.308`, and
  `/tps` stayed 20.0 during the run.
- [x] Record new multi-heavy warning: parallel TNT entity spawn logged
  `Entity uuid already exists`. Static review noted the exact root cause is not
  proven because `Mth.createInsecureUUID(RandomSource)` is synchronized, but
  entity UUID generation should still not depend on an entity RNG object under
  region-parallel creation.
- [x] Patch entity UUID initialization to use a thread-local UUID generator
  instead of `Mth.createInsecureUUID(this.random)`.
- [x] Run `applyAllPatches` and `compileJava` after the entity UUID patch.
- [x] Request sub-agent review for the entity UUID patch and rerun the
  multi-heavy TNT spread to confirm duplicate UUID warnings disappear.
- [x] Runtime evidence after entity UUID patch: fresh 12-region
  `tntspread 12 64 64 1 40 8 2` logged no duplicate UUID warning, kept `/tps`
  at 20.0, and the far normal probe logged `avgLagMs=0.052`,
  `p95LagMs=1.249`, `maxLagMs=99.441`.
- [x] Plugin flood test: verify bounded mailbox rejects/delays without OOM for
  the plugin task class.
- [ ] Plugin flood evidence: capture `RegionQueue` JFR events and rejected task
  counters for `PLUGIN`, `PLAYER_ACTION`, `TRACKER_BROADCAST`, and
  `EXPLOSION_PHYSICS` classes.
- [x] Plugin flood `PLUGIN` evidence: `scheduler regionlocal 5000 250000`
  queued `1024`, rejected `3976`, logged `RegionMailbox` capacity-full warnings
  with `queued=1024/1024`, kept `/tps` at 20.0, and a far normal probe logged
  `avgLagMs=-0.008`, `p95LagMs=1.206`, `maxLagMs=1.882`.
- [x] Plugin flood JFR file evidence: `D:\worldgen\logs\codex-plugin-flood-queue-20260425-1440.jfr`
  contains `16` `RegionQueueEvent` entries for `taskClass=PLUGIN`, all at
  `capacity=1024`, with rejected samples from `1` through `3840`.
- [x] Cross-region task observability harness: add `/rlt crossqueue
  <chunkOffsetX> <tasks> [payloadIterations]`, which schedules from one region
  worker into another region worker and reports queued/rejected/executed/failed
  counts.
- [x] Cross-region task runtime evidence after owner-epoch and transfer-lane
  fixes: `D:\worldgen\logs\codex-crossregion-task-20260425-1706.jfr` contains
  exactly two threshold `CrossRegionTask` events for 600 accepted cross-region
  plugin tasks (`queuedAfter=1` and `512`, `capacity=1024`), includes
  source/target owner epoch and `delayClock=region-local`, completed
  `queued=600 rejected=0 executed=600 failed=0` in `164.22ms`, and `/tps`
  stayed at `20.0`.
- [x] Sub-agent review loop for `CrossRegionTask`/mailbox fairness:
  Meitner and Rawls blocked the first heavy per-task metadata draft, then
  blocked the unbounded drain loop and lossy redirect edge case; both blockers
  were patched with threshold-only enqueue JFR, bounded repeated drain rounds,
  primitive owner epoch binding, and a non-dropping transfer queue.
- [x] Add load-test harness mode for per-region plugin mailbox saturation:
  `scheduler regionlocal` should queue all tasks into one target region and
  report queued/rejected counts instead of throwing out of the command.
- [x] Build, review, deploy, and smoke-test `scheduler regionlocal`.
- [x] Run `scheduler regionlocal` above `pluginRegionMailboxCapacity` and
  confirm rejected count increases while `/tps` and a far normal probe stay
  healthy.
- [x] Tracker/broadcast flood test: verify playerless tracker/block-change
  pressure does not become a global barrier.
- [ ] Tracker/broadcast evidence: run normal-region probe concurrently and record
  `RegionOverBudget` events for tracker/broadcast work.
- [x] Tracker flood first RCON-only attempt: `tracker 900 300 16` kept `/tps`
  at 20.0 and far probe region healthy, but did not create enough broadcaster
  pressure without connected players.
- [x] Add load-test harness mode for playerless broadcaster pressure:
  `broadcast <chunks> <blocksPerChunk> [ticks]` toggles blocks inside one
  8x8 chunk region to drive chunk-holder broadcast work from RCON.
- [x] Build, review, deploy, and smoke-test `broadcast`.
- [x] Harden `broadcast` after sub-agent review: fail closed unless chunks are
  already loaded, install plugin chunk tickets during the test, schedule one
  region task per target chunk, release tickets on completion/abort/cleanup, and
  rollback tickets/tasks if scheduling fails partway through.
- [x] Run `broadcast` with a far normal probe and capture `RegionOverBudget`
  work type plus `/region top`, `/tps`, and JFR evidence.
- [x] Broadcast runtime evidence: with 64 force-loaded chunks and
  `broadcast 64 2048 100`, target region reached `DEGRADED mspt=46.43` while
  the far normal probe logged `avgLagMs=0.000`, `p95LagMs=1.067`,
  `maxLagMs=45.050`; `/tps` returned to 20.0 after the setup window.
- [x] Add work-type-specific over-budget JFR events so `REGION_TASK` no longer
  hides later over-budget work types in the same region tick.
- [x] Work-type JFR evidence: `D:\worldgen\logs\codex-broadcast-worktype-20260425-1513.jfr`
  contains `960` `RegionOverBudgetEvent` entries split across work types
  (`REGION_TASK`, `CHUNK_TICK`, `BLOCK_TICK`, `FLUID_TICK`, `ENTITY_TICK`,
  `BLOCK_ENTITY`, `PLAYER`, `TRACKER`, and `INTERNAL_TASK`).
- [ ] True `BROADCAST` work-type evidence still requires a connected watcher or
  fake-player harness; playerless block changes did not produce a `BROADCAST`
  over-budget event even under heavy block-change pressure.
- [x] Chunk generation DoS test: verify normal regions keep chunk priority for
  the current async chunk request path.
- [x] Chunk generation DoS evidence: run attacker-region `chunkgen` and a far
  normal region chunk request/probe concurrently; record chunk wait lag and
  `/region top`.
- [x] Chunk generation DoS first attempt: `chunkgen 20 false` in attacker
  region plus far probe and `chunkgen 2 true` in normal region exposed a
  scheduler lifecycle crash before QoS could be measured.
- [x] Chunk generation DoS root cause: a stale source `RegionHandle` could start
  after dynamic owner merge detached and cleared its owner cells, then pass an
  empty owner-cell snapshot into `ShreddedPaperRegionLocker`.
- [x] Patch stale-owner scheduler guard: retire detached or empty-owner handles
  before lock acquisition, revalidate after exact owner/isolation lock, and
  requeue when live owner cell layout changes under the handle.
- [x] Request Faraday/Aquinas review for the stale-owner scheduler guard; both
  reported no blockers and recommended keeping the guard in
  `RegionTickScheduler` without adding map-lock reads.
- [x] Rebuild/deploy the stale-owner guard jar and rerun the same chunkgen
  reproduction until no crash occurs.
- [x] Chunk generation DoS rerun evidence after stale-owner guard: fresh
  `chunkgen 20 false` queued `1681` attacker chunks while a far normal region
  ran `probe 2000 1` plus urgent `chunkgen 2 true`; urgent normal chunkgen
  finished `25/25` in `2943.42ms`, attacker chunkgen finished `1681/1681` in
  `48806.29ms`, `/tps` stayed at 20.0 except one 5s sample at 19.2, and the far
  probe logged `avgLagMs=0.646`, `p95LagMs=1.481`, `maxLagMs=434.575`.
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

## Milestone 9 - Region-Aware Low TPS Lag Compensation

- [x] Reproduce the reported class of failure from logs: strict
  `moved-wrongly-threshold` and `moved-too-quickly-multiplier` still emit
  movement warnings when the player is in a lagging independent region.
- [x] Inspect DivineMC's low-TPS lag-compensation patches and separate the
  useful behavior from direct code identity.
- [x] Add ShreddedPaper-owned `lag-compensation` config defaults for block
  entity, block breaking, eating, potion, fluid, pickup, portal, time, random
  tick speed, and movement-warning suppression.
- [x] Add compatibility wiring in `DivineConfig` so existing DivineMC-derived
  hooks resolve to ShreddedPaper config values.
- [x] Add TPS accounting helpers for server/world fallback compensation.
- [x] Add region-aware compensation helper that prefers the current or target
  `LevelChunkRegion` overload controller before falling back to world/server
  TPS.
- [x] Patch movement validation for both vehicle movement and normal player
  movement: scale allowed move ticks and suppress moved-too-quickly /
  moved-wrongly only while the player's own region or server is lagging.
- [x] Patch low-TPS gameplay acceleration for block entities, block breaking,
  eating, potion effects, fluid tick delay, item pickup delay, portal time,
  daylight time, and random tick speed.
- [x] Keep `always-allow-weird-movement` available but disabled by default, so
  the default path fixes low-TPS false positives without fully disabling
  movement enforcement.
- [x] Preserve feature patch regeneration by placing Minecraft-source edits in
  `shreddedpaper-server/minecraft-patches/features/0035-Region-aware-lag-compensation.patch`.
- [x] Run `applyAllPatches` after introducing the feature patch.
- [x] Run `:shreddedpaper-server:compileJava --rerun-tasks`; build passed.
- [x] Build `:shreddedpaper-server:createMojmapPaperclipJar`; build passed.
- [x] Deploy the rebuilt paperclip jar to `D:\worldgen`.
- [x] Boot `D:\worldgen` with `start.bat` and verify clean startup on the new
  jar.
- [x] Verify `D:\worldgen\shreddedpaper.yml` generated the new
  `lag-compensation` section with all default options enabled except
  `always-allow-weird-movement`.
- [x] Verify player auto-login succeeds on the new jar.
- [x] Run RCON smoke checks: `version`, `list`, `tps`, `mspt`, `region`, and
  `rlt status`.
- [x] Run a short `RegionLoadTest` region-local scheduler flood and cleanup.
- [x] Scan runtime logs after the smoke test for `moved too quickly`,
  `moved wrongly`, packet handling errors, wrong-thread errors, sync chunk
  load guard failures, and crash reports; no new matches were found.
- [x] Catch final-jar boot regression: independent scheduler worker could crash
  while merging a source owner that became non-quiescent between candidate
  selection and merge execution.
- [x] Patch quiescent owner merge to abort and retry later instead of throwing
  from `LevelChunkRegion.absorbFrom`.
- [x] Self-review movement compensation for vehicle and non-vehicle paths.
- [x] Self-review acceleration hooks for bounded caps and feature toggles.
- [ ] Request a focused sub-agent review for movement semantics and low-TPS
  acceleration side effects before the next release candidate.
- [ ] Add a stronger interactive reproduction where the client moves through a
  deliberately lagging player region, then confirm no false positive movement
  warnings are logged.
- [x] Commit region-aware low-TPS lag compensation.

## Milestone 10 - Chunk Ticking Stability And Hidden Bug Sweep

- [x] Triage `/tp 0 ~ 0` crash from `D:\worldgen`: deferred region broadcast
  called `ChunkHolder.broadcastChanges(null)` after teleport/unload churn
  downgraded the full chunk.
- [x] Verify `ChunkHolder.getFullChunkNowUnchecked()` is allowed to return
  `null` under the Paper/Moonrise chunk system even when the holder still has
  dirty block/light flags.
- [x] Patch `ShreddedPaperChangesBroadcaster` so stale queued holders with no
  current full chunk are skipped instead of crashing the region worker.
- [x] Compile after the stale broadcast-holder guard.
- [x] Build `:shreddedpaper-server:createMojmapPaperclipJar` after the stale
  broadcast-holder guard.
- [x] Deploy the rebuilt paperclip jar to `D:\worldgen`.
- [ ] Reproduce the reported teleport path with player auto-login and
  `execute as Hancho1577 at @s run tp @s 0 ~ 0`.
- [ ] Confirm no new `cachedChunkPacket`, packet handling, wrong-thread,
  sync-load, merge, or movement-warning signatures appear after teleport churn.
- [x] Add `tools/runtime/Invoke-WorldgenSmoke.ps1` to start `D:\worldgen`,
  run RCON smoke commands, exercise teleport/chunk-load/sync-load probes, and
  scan only new log content plus new crash reports.
- [x] Make the smoke script fail instead of silently passing when the configured
  test player does not join; `-SkipPlayerTeleport` is now required for
  non-interactive runs.
- [x] Run non-interactive smoke with `-SkipPlayerTeleport`: startup, chunk-load
  probe, sync-load guard probe, log-signature scan, and clean shutdown passed.
- [x] Attempt player-required smoke; it correctly failed because no Minecraft
  client was online within the wait window, so the reported player `/tp` churn
  still needs an interactive rerun.
- [ ] Extend the smoke script with `/world` and `/back` command loops once the
  exact installed command semantics are stable.
- [ ] Add a repeated churn mode for login, teleport, world change, chunk send,
  unload, light update, and region merge interactions.
- [ ] Add a crash-signature corpus from `D:\worldgen\logs\latest.log` and
  `D:\worldgen\crash-reports` covering:
  `Thread failed main thread check`, `Synchronous chunk load`, `cachedChunkPacket`,
  `Cannot merge non-quiescent`, `Failed to handle packet`, `moved too quickly`,
  and chunk-system propagated crashes.
- [ ] Add a CI/local task that runs compile plus the smoke script against a
  disposable runtime world before release-candidate jars are accepted.
- [ ] Commit chunk ticking stability guard and smoke tooling.

## Current Blockers

- [ ] Dynamic merge/split regionizer is not yet implemented.
- [x] Independent scheduler is wired into the world tick path for current fixed
  ShreddedPaper regions.
- [x] Global tracker and player flush phases are skipped globally in independent
  mode and run from owner region ticks.
- [x] `LevelTicksRegionProxy` no longer has stubbed area/copy/count methods.
- [x] `D:\worldgen` runtime baseline has been captured for startup,
  `/region top`, `/tps`, forceload, JFR, and clean shutdown.
