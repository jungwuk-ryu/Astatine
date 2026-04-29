# Experimental Performance Optimization Track

Date: 2026-04-29
Source: `PERFORMANCE.md`
Relationship to safe PRD: this document preserves promising but higher-risk ideas that must not be merged through the safe track.

## Purpose

Some performance proposals are not wrong; they are just too risky for immediate release-track implementation. This track keeps those ideas alive under stricter conditions:

* feature flag default off,
* measurement before implementation,
* ordering/cap/admission proof before runtime smoke,
* rollback threshold before merge consideration,
* no gameplay semantics change while the flag is off.

## Global Rules

1. Every experiment starts with a baseline profile and a disabled-flag overhead check.
2. Every experiment must be reversible with one config flag.
3. A feature flag may not hide corrupted state, lost tasks, leaked permits, or broken futures.
4. Global ordering across independent regions is not assumed to be gameplay semantics, but per-region ordering, due-time, chunk admission caps, future completion, and crash-safe chunk state are invariants.
5. Any experiment that touches plugin callback timing must include a Bukkit/Folia scheduler replay.

## EXP-001: Sharded Chunk Retry Executor

Source: PERF-005

Current concern:

`RegionChunkIoTracker` has one static single-thread `EXECUTOR_BACKPRESSURE_RETRY_EXECUTOR`. That may serialize retry admission across otherwise independent regions and weaken region isolation under chunk pressure.

Experiment:

* Measure queue depth, drain latency, backlog age, and starvation first.
* Add a feature flag for sharded retry execution.
* Candidate shard keys:
  * `worldId + ownerId`,
  * scheduler lane,
  * bounded hash of owner id.
* Keep same-owner FIFO.
* Keep existing `RegionChunkIoTracker` cap checks.
* Keep task drop forbidden.
* Keep future completion invariant.
* Worker-local drain, if tested, must run only after tick logic through auxiliary budget.

Required proof:

* No cap violation.
* No negative counters.
* No chunk future hang.
* No crash-recovery save/load regression.
* Far-control region p99 does not regress when another region is chunk-saturated.

Rollback:

* Any future hang, cap violation, starvation, or chunk data mismatch.

## EXP-002: Order-Preserving Delayed Task Bucket

Source: PERF-009

Current concern:

`RegionMailbox` delayed tasks use `PriorityQueue<RegionTask>`, ordered by `readyTick` then sequence. A bucket/timing-wheel replacement can be faster under plugin scheduler storms, but only if it exactly preserves observable ordering.

Experiment:

* Build golden tests for the current comparator:
  * never run before `readyTick`,
  * preserve sequence order within the same `readyTick`,
  * preserve ordering between short-delay bucket and long-delay fallback heap.
* Add feature-flagged short-delay buckets.
* Keep long-delay fallback heap.
* Use existing `RegionTask.sequence` semantics or an exactly equivalent sequence.

Required proof:

* Same-readyTick replay parity.
* Bukkit scheduler callback replay parity.
* Folia-compatible scheduler replay parity.
* No early execution.

Rollback:

* Any callback order mismatch or early/late execution beyond current behavior.

## EXP-003: Gated Degraded-Lane Idle Steal

Source: PERF-010

Current concern:

`RegionTickScheduler` only lets normal workers steal degraded work when degraded worker count is zero. With degraded workers present, normal workers may sit idle while already-due degraded regions accumulate backlog.

Experiment:

* Measure normal/degraded queue wait, backlog age, lane utilization, and wakeup latency first.
* Add feature flag for normal worker idle steal.
* Allow steal only when:
  * normal queue has no ready work,
  * degraded task is already due,
  * earliest deadline is not violated,
  * steal budget is bounded,
  * far-control normal region p99 is protected.

Required proof:

* No catch-up storm.
* No due-before-scheduled execution.
* Normal lane p99 does not regress past threshold.
* Degraded backlog age improves under saturation.

Rollback:

* Normal region contamination, early tick execution, or p99 regression.

## EXP-004: Owner Handoff Metadata Wrapper

Source: PERF-013

Current concern:

Owner handoff currently hides important diagnostic state inside lambda/future continuations. A typed wrapper can expose metadata without changing delegate lifetime or callback order.

Experiment:

* Wrap the existing `Runnable` delegate.
* Add metadata:
  * owner id,
  * owner epoch,
  * requeue count,
  * task class,
  * affinity cell key.
* Do not pool wrappers.
* Do not reuse captured delegates.
* Do not coalesce gameplay mutation tasks.

Required proof:

* Callback order parity.
* Max requeue behavior parity.
* Owner epoch mismatch diagnostics improved.
* No retained delegate leak.

Rollback:

* Any callback ordering change, retained state leak, or wrong-owner mutation.

## EXP-005: Merge/Split Two-Phase Feasibility

Source: PERF-014

Current concern:

`LevelChunkRegionMap` merge/split can hold the global write lock across candidate discovery, exact lock, owner absorption/remap, and detach. That is a plausible p99 lock-hold source, but a direct two-phase rewrite is too risky.

Experiment:

* First add phase-level lock-hold metrics.
* Split timing into:
  * candidate discovery,
  * exact lock acquisition,
  * remap commit,
  * runtime state detach,
  * connected component calculation.
* Design a read-snapshot candidate discovery prototype.
* Commit phase must revalidate:
  * source owner id,
  * target owner id,
  * owner epochs,
  * cell snapshot version,
  * no detached/retired owner.
* Mismatch aborts without unbounded retry.

Required proof:

* No lost cell.
* No duplicate owner mapping.
* No retired owner tick.
* No stale runtime state.

Rollback:

* Any owner map invariant failure.

## EXP-006: Conditional Task Classification

Source: PERF-008

Current concern:

Critical and transferred queues are intentionally non-dropping, but not every high-pressure engine task is equally non-coalescible.

Experiment:

* Keep critical, owner handoff, player action, and chunk I/O non-dropping and non-coalesced.
* Identify idempotent tracker/broadcast candidates only.
* Add metrics for peak depth, oldest age, and producer context.
* Consider a separate task class only after proof that callback order and visibility are unchanged.

Required proof:

* No critical task loss.
* No owner handoff coalescing.
* No player action delay/backpressure wait.
* Plugin callback order unchanged.

Rollback:

* Any lost or reordered user-visible task.

## Hard Rejects For This Cycle

These remain rejected for both safe and experimental tracks in this cycle:

* Full `PermitTask` flag bit-pack rewrite without a separate formal state-machine proof.
* Caffeine caches for region tick/game-state paths without a pure deterministic cache target.
* Netty Recycler in game logic.
* LMAX Disruptor as a general per-region mailbox.
* io_uring network/storage changes in this region-tick performance cycle.
* Virtual threads for region workers, mailbox consumers, or tick loops.
* VarHandle weaker access modes without field-by-field happens-before proof.

## Promotion Criteria

An experiment can move toward a normal PRD only when:

* flag-off overhead is negligible,
* golden ordering tests pass,
* live smoke passes with the flag on,
* a before/after profile shows a real win,
* rollback thresholds are documented,
* the implementation does not depend on unproven global region ordering.
