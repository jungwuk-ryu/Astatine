# Safe Performance Optimization PRD

Date: 2026-04-29
Source: `PERFORMANCE.md`
Scope: low-side-effect performance work only
Experimental companion: `EXPERIMENTAL_PERFORMANCE_OPTIMIZATION_TRACK_2026-04-29.md`

## Goal

Reduce single-hot-region and many-region overhead without changing game logic, tick order, ownership fail-closed behavior, plugin callback order, chunk admission caps, or chunk generation determinism.

This PRD intentionally accepts small memory increases when they remove repeated allocation, repeated sorting, false sharing, or diagnostic work on hot paths.

## Non-Goals

* No scheduler algorithm rewrite in this PRD.
* No timing wheel for Bukkit/Folia delayed tasks in this PRD.
* No two-phase merge/split rewrite in this PRD.
* No permit state-machine rewrite in this PRD.
* No redstone/fluid/piston/explosion batching change in this PRD.
* No new third-party library unless a later benchmark proves it is necessary.
* No virtual threads for region workers, mailbox consumers, or owner-affine tick loops.

## Safety Principles

1. Preserve existing public APIs and add optimized overloads behind them.
2. Keep old code paths available until tests prove parity.
3. Optimize representation and diagnostics before changing scheduling policy.
4. For every hot-path change, add a parity test or an invariant assertion before relying on profiler wins.
5. Treat chunk admission and exact-cell ownership as correctness mechanisms, not just performance mechanisms.

## Accepted Backlog

### SAFE-PERF-001: Packed Owner Snapshot Fast Path

Source: PERF-001, PERF-003

Current code reality:

* `RegionOwner.cellPositionsSnapshot()` already caches a sorted `List<RegionPos>`.
* `RegionOwner.isolationCellPositionsSnapshot(1)` caches a list but does not sort before storing.
* `ShreddedPaperRegionLocker.internalTryTakeExactLockNow(Collection, Collection)` still copies/sorts/de-dupes on every exact lock attempt.

Implementation:

* Add `volatile long[] sortedCellKeysSnapshot` to `RegionOwner`.
* Add `volatile long[] sortedIsolationRadiusOneKeysSnapshot` to `RegionOwner`.
* Invalidate both arrays in the existing `invalidateSnapshots()` path.
* Sort packed keys by the same ordering as `RegionPos.toLong()`.
* Preserve existing `List<RegionPos>` methods for compatibility.
* Add locker overloads that accept already sorted unique packed keys.
* Keep the existing `ConcurrentHashMap<RegionPos, LockedRegion>` in this PRD. Creating `RegionPos` wrappers during lock acquisition is acceptable for the first safe step; replacing the lock map key type is a later, higher-risk project.

Acceptance criteria:

* Existing collection-based lock APIs still behave identically.
* Exact lock acquisition order is byte-for-byte equivalent by packed key ordering.
* Radius-1 isolation includes the same set of cells as before.
* Owner layout changes cannot leave stale packed snapshots.

Validation:

* Unit test: owner cell snapshot parity before/after merge/split/remove.
* Property test: packed isolation radius one equals object-list isolation radius one.
* Stress: randomized owner split/merge with exact lock acquisition.
* Build gate: `./gradlew applyAllPatches --no-configuration-cache --stacktrace`.
* Compile gate: `./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace`.

Rollback trigger:

* Any deadlock, duplicate/missing cell, changed owner layout epoch behavior, or exact-lock parity mismatch.

### SAFE-PERF-002: Diagnostic and JFR Allocation Gates

Source: PERF-011, PERF-017

Implementation:

* Replace `RegionTickScheduler.snapshots()` stream pipeline with a pre-sized explicit loop and sort.
* Add bounded top-N helper for commands that only need the hottest regions.
* In `RegionTickScheduler.commitTickEvent`, avoid filling event fields when the event is disabled or when sampling policy says not to commit.
* In `RegionChunkIoTracker`, keep `ChunkRequestEvent.shouldCommitSample()` ahead of event object creation and ahead of action string construction.
* In `RegionMailbox`, add the same `event.isEnabled()` style guard to `RegionQueueEvent` and `CrossRegionTaskEvent` before filling string-heavy fields.
* Replace repeated chunk action string concatenation with constants or a small action table.
* Preserve event field names and event action labels used by existing tools.

Acceptance criteria:

* `/region`, `/region top`, and TPS diagnostics keep the same visible ordering for equal input.
* JFR event counts are equal when sampling is enabled.
* JFR event schema remains compatible.

Validation:

* Unit test or command fixture for snapshot ordering.
* Allocation profile under chunk stress with JFR on and off.
* Event parity check from a short MCC/RLT smoke.

Rollback trigger:

* Missing JFR events when diagnostics are enabled, changed command output semantics, or any tooling break from renamed event labels.

### SAFE-PERF-003: Loaded-Read Fallback Sampling Guard

Source: PERF-012

Implementation:

* Add a cheap sampling gate before stack trace capture in `ShreddedPaperAccess.recordLoadedReadFallbackSample()`.
* Use a fixed-size ring buffer instead of synchronized growable diagnostic structures.
* Capture caller details only when debug sampling is enabled or JFR diagnostics request it.
* Do not change loaded-read ownership checks, defer behavior, or fail-closed behavior.

Acceptance criteria:

* A fallback storm no longer captures a stack trace for every fallback.
* Diagnostic command still reports recent representative samples.
* Ownership guard behavior is unchanged.

Validation:

* Forced fallback storm test.
* Allocation profile on fallback storm.
* Existing async ownership scanner remains green.

Rollback trigger:

* Missing all fallback diagnostics, changed guard outcome, or hidden sync-load regression.

### SAFE-PERF-004: Runtime State Lifecycle Diagnostics

Source: PERF-018

Implementation:

* Track state creation/removal reason counters in `RegionRuntimeState`.
* Add orphan runtime state count: states with no current region and no pending mailbox/chunk work.
* Add parity check helper comparing active owners and runtime state count after split/merge churn.
* Surface the count through existing region diagnostics without forcing a full heap walk.

Acceptance criteria:

* No state is removed while mailbox/chunk work is pending.
* Diagnostics can identify leaked/orphan states after a long run.
* Normal tick path only pays cheap counter reads/writes.

Validation:

* Teleport/disconnect churn.
* Split/merge fuzz.
* Heap dump or state count check after MCC/RLT smoke.

Rollback trigger:

* Premature state removal, lost mailbox work, or chunk future hang.

### SAFE-PERF-005: Deterministic `RegionOwner.absorbCellsFrom` Lock Ordering

Source: PERF-015

Implementation:

* Enter nested `cells` monitors in deterministic owner-id order.
* Handle `this == source` defensively.
* Keep existing global owner-map/exact-lock caller assumptions.
* Optionally add development assertion documenting expected caller ownership.

Acceptance criteria:

* Merge results are identical.
* No inverse-order deadlock is possible if future callers use this method concurrently.
* Snapshot invalidation and layout epoch bump remain unchanged.

Validation:

* Concurrent merge fuzz.
* Owner cell set parity test.

Rollback trigger:

* Merge throughput regression with no deadlock-risk reduction, or false-positive assertion in valid merge paths.

### SAFE-PERF-006: Overload Controller Diagnostic Snapshot

Source: PERF-016

Implementation:

* Keep `loadClass` visibility exactly as-is.
* Keep `ewmaMspt` accumulation and `quarantineStrikes` semantics exactly as-is because they control degrade/quarantine decisions.
* Only consider moving display-only last-observation fields, such as `lastMailboxDepth`, `lastMailboxClassPressure`, `lastChunkIo*`, and `lastDeferredWork`, into an immutable snapshot or periodic volatile publish.
* Keep admission/scheduler decisions reading fields with the same freshness guarantees as today.
* Document that diagnostic values may be slightly stale.

Acceptance criteria:

* Degraded/quarantined transitions happen at the same tick boundary as before.
* `/region top` and TPS diagnostics remain accurate enough for operators.
* Per-tick volatile writes are reduced for non-control fields.

Validation:

* `recordTick` microbenchmark.
* Load-class transition fixture.
* Diagnostic freshness check under load.

Rollback trigger:

* Delayed quarantine/degrade response, stale metrics that mislead scheduler decisions, or command output regression.

### SAFE-PERF-007: Mailbox Ordinal Arrays

Source: PERF-007

This is accepted only as a narrow representation change.

Implementation:

* Replace `EnumMap<RegionTaskClass, Queue<RegionTask>>` and class counter maps with ordinal-indexed arrays.
* Preserve `DRAIN_ORDER` exactly.
* Preserve queue implementation per class:
  * `CRITICAL_SYSTEM`: `ConcurrentLinkedQueue`.
  * Other classes: current bounded `MpscArrayQueue`.
* Preserve capacity, reject, fail, queued count behavior.
* Keep public diagnostics returning values by `RegionTaskClass`.

Acceptance criteria:

* Multi-producer offer/drain behavior is unchanged.
* Per-class FIFO and existing priority order are unchanged.
* Reject counts and capacity behavior match baseline.

Validation:

* Mailbox JMH: offer/drain under 1, 4, 16 producers.
* Task ordering replay by class.
* Plugin scheduler storm.
* Critical non-dropping invariant.

Rollback trigger:

* Task reorder, false rejection, critical task loss, or plugin callback order mismatch.

### SAFE-PERF-008: Critical/Transferred Queue Pressure Diagnostics

Source: PERF-008

Implementation:

* Add peak depth, oldest age, and producer-context diagnostics for `CRITICAL_SYSTEM` and transferred queues.
* Keep current non-dropping behavior.
* Keep plugin fail-fast behavior.
* Do not coalesce gameplay, player action, owner handoff, or chunk I/O tasks.

Acceptance criteria:

* Diagnostics identify pressure before memory blow-up.
* No critical task is dropped.
* No producer is blocked in a way that changes tick order.

Validation:

* Plugin task storm.
* Cross-region handoff storm.
* Memory/allocation profile.

Rollback trigger:

* Any task loss, changed plugin callback behavior, or new deadlock/backpressure wait.

### SAFE-PERF-009: Chunk I/O Counter Layout and Event Guard Cleanup

Source: PERF-004, PERF-017

Implementation:

* Group hot admission counters and diagnostic counters separately.
* Use manual padding or grouped holder classes where profiler shows false sharing.
* Keep exact admission counters as `AtomicInteger` where cap correctness depends on exact values.
* Do not replace cap counters with `LongAdder`.
* Move event action construction behind sampling guards.

Acceptance criteria:

* In-flight caps are never exceeded.
* Deferred retry counts never go negative.
* Event labels remain compatible.

Validation:

* Chunk generation/load stress.
* Admission cap invariant test.
* Allocation profile around event path.
* Crash recovery save/load replay.

Rollback trigger:

* Cap violation, negative counters, chunk future hang, event schema break, or world corruption.

### SAFE-PERF-010: Measurement-Only Work

Source: PERF-002, PERF-005, PERF-010, PERF-013, PERF-014, PERF-020

Implementation:

* Lock metrics:
  * exact-lock wait nanos,
  * hold nanos,
  * failed try count,
  * cells per lock,
  * `parkNanos` count.
* Chunk retry metrics:
  * single executor queue depth,
  * drain latency,
  * backlog age,
  * emergency executor fallback count.
* Scheduler metrics:
  * normal/degraded queue wait,
  * degraded backlog age,
  * worker lane utilization,
  * wakeup latency.
* Handoff metrics:
  * requeue count,
  * owner epoch mismatch,
  * max requeue failures.
* Region map metrics:
  * merge/split write lock hold time,
  * abort/retry count,
  * connected component size.
* Redstone/fluid/piston/explosion audit:
  * inventory of order-sensitive paths,
  * fixture list,
  * no code optimization until fixtures exist.

Acceptance criteria:

* Metrics are sampled and cheap when disabled.
* No scheduling, queue, lock, chunk, or redstone behavior changes.
* Metrics are sufficient to decide whether a high-risk optimization deserves a separate PRD.

Validation:

* JFR disabled overhead check.
* Short live server smoke.
* One high-load region plus far control region profile.

Rollback trigger:

* Metrics introduce measurable overhead when disabled or alter any control path.

## Rejected or Deferred Work

The table below means "excluded from this safe PRD", not "the idea is permanently invalid". Controlled experiments that still deserve a feature-flagged path are tracked in `EXPERIMENTAL_PERFORMANCE_OPTIMIZATION_TRACK_2026-04-29.md`.

| Source | Decision | Reason |
| --- | --- | --- |
| PERF-006 full `PermitTask` state rewrite | Deferred | Primary lifecycle is already bit-packed. Remaining booleans encode ownership of permits/backlog/emergency state; merging them risks leaks and duplicate releases. |
| PERF-009 timing wheel | Rejected for this PRD | Same-readyTick order and plugin scheduler timing are too sensitive for the safe track; an order-proof bucket experiment belongs in the experimental track. |
| PERF-010 scheduler idle-steal or wheel | Deferred | It changes cadence/fairness between normal and degraded regions; gated idle-steal may be tested only after measurement. |
| PERF-013 typed handoff rewrite | Deferred | A metadata wrapper without pooling is an experimental candidate, but callback order and delegate lifetime must remain untouched. |
| PERF-014 two-phase merge/split | Rejected for this PRD | Owner remap correctness is too central; only feasibility design and lock-hold metrics are allowed outside the safe track. |
| PERF-020 redstone/fluid/piston batching | Rejected for this PRD | Order-sensitive fixtures must exist first. Audit only. |
| New libraries | Rejected/deferred | Existing JCTools/fastutil are enough for this safe pass. New dependencies require separate justification. |
| Virtual threads for region work | Rejected | Region affinity and low-latency scheduling matter more than cheap blocking. |
| VarHandle weaker memory modes | Deferred | Field-by-field happens-before proof required, especially on ARM. |

## Suggested Implementation Order

1. SAFE-PERF-002 diagnostic/JFR allocation gates.
2. SAFE-PERF-005 deterministic owner lock ordering.
3. SAFE-PERF-001 packed owner snapshots and locker overload.
4. SAFE-PERF-003 fallback sampling guard.
5. SAFE-PERF-004 runtime state diagnostics.
6. SAFE-PERF-010 measurement-only events.
7. SAFE-PERF-009 chunk I/O event/counter cleanup.
8. SAFE-PERF-007 mailbox ordinal arrays.
9. SAFE-PERF-008 critical/transferred queue pressure diagnostics.
10. SAFE-PERF-006 overload diagnostic snapshot.

The first six steps are preferred before changing mailbox or chunk counter layout because they improve observability and reduce obviously safe allocation first.

## Global Validation Gates

Every implementation PR from this PRD must run:

```bash
./gradlew applyAllPatches --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace
node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical
```

When the change touches runtime behavior rather than docs/tests only, also run:

```bash
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache --stacktrace
./gradlew -p tools/region-load-test-plugin build --no-configuration-cache --stacktrace
```

Runtime smoke:

* One hot End region plus one far control region.
* Teleport/disconnect churn.
* Chunk generation stress.
* Plugin scheduler storm.
* Watchdog smoke if any lock/scheduler code is touched.

## Release Criteria

The PRD is complete when:

* Accepted items are either implemented or explicitly closed with benchmark evidence.
* Measurement-only items have enough data for a later high-risk PRD.
* No accepted item changes game logic or plugin callback order.
* No accepted item regresses scanner/build/runtime smoke gates.
* At least one before/after profile confirms reduced allocation, CPU, or p99 latency in the targeted path.
