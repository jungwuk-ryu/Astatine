# Region Tick Caching PRD - 2026-04-29

## 1. 문서 목적

이 문서는 ShreddedPaper의 independent region ticking 경로에서 throughput을 개선하기 위한 캐싱 기반 최적화 요구사항을 정의한다.

핵심 기준은 다음과 같다.

- 메모리 사용량 증가는 허용한다.
- 게임 로직, tick order, ownership, packet visibility, plugin-visible behavior는 변경하지 않는다.
- 캐시는 authoritative state가 아니라 반복 조회와 반복 snapshot 생성을 줄이는 보조 자료여야 한다.
- 캐시가 틀렸거나 의심스러운 경우 기존 코드 경로로 fail closed 해야 한다.
- 성능 개선 폭을 정확히 예측하는 것이 목표가 아니다. 대신 부작용 없이 반복 비용이 줄어드는 구조인지가 목표다.

## 2. 현재 코드 기준 요약

현재 주요 경로는 다음 파일에 있다.

- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java`
  - `_tickRegion(...)`
  - `processScheduledTicks(...)`
  - `hasLoadedChunkInCell(...)`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegion.java`
  - region membership collections
  - snapshot methods
  - merge/split data movement
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelChunkRegionMap.java`
  - cell to owner routing
  - entity/chunk/player add, remove, move hooks
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelTicksRegionProxy.java`
  - scheduled block/fluid tick storage split by `RegionPos`
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/RegionOwner.java`
  - owner cell snapshot cache already exists
  - owner layout epoch already exists
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionMailbox.java`
  - owner epoch based stale task redirect already exists
- `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/ShreddedPaperAccess.java`
  - owner handoff and loaded-read safety helpers exist

Important current observations:

- `RegionOwner.cellPositionsSnapshot()` and `RegionOwner.isolationCellPositionsSnapshot(radius=1)` are already cached. They are not new cache candidates.
- `_tickRegion` currently performs entity task execution with `region.forEachTickingEntity(...)`, not `getTickingEntitiesSnapshot()`. This weakens the earlier "same snapshot reused multiple times" assumption.
- Snapshot allocation still exists for chunk tick, entity tick, tracker tick, and player tick.
- `processScheduledTicks` still calls `hasLoadedChunkInCell(level, cell)` for each owned cell before ticking block/fluid scheduled ticks.
- `hasLoadedChunkInCell` scans all chunks in a region cell via `getChunkAtIfLoadedImmediately(...)`.
- `LevelChunkRegion.levelChunks` should not be assumed to mean "any loaded chunk in the cell". It is used for ticking chunks. Replacing `hasLoadedChunkInCell` with `levelChunks` count would be semantically unsafe unless the lifecycle is proven equivalent.

## 3. Problem Statement

Region ticking performs repeated structural work that does not change gameplay results:

1. It repeatedly constructs list snapshots for large region-owned collections.
2. It scans chunk coordinates inside a cell to answer a yes/no loaded-chunk question.
3. It lacks phase-level timing, making it hard to prove which cache is beneficial.

The optimization target is not AI/pathfinding/tracker result caching. The target is repeated structural work around iteration, membership, and empty-cell checks.

## 4. Goals

### 4.1 Product Goals

- Improve region tick throughput for busy regions without changing game behavior.
- Reduce allocation pressure from repeated snapshot construction.
- Reduce pointless scheduled-tick cell work when a cell has no scheduled tick data.
- Make performance impact observable through JFR and `/region` diagnostics.
- Provide runtime and config guardrails so caches can be disabled automatically when they do not help.

### 4.2 Engineering Goals

- Preserve current tick order and snapshot semantics.
- Preserve owner isolation and split/merge correctness.
- Preserve current fallback behavior on null, unloaded, removed, unowned, or moved objects.
- Keep all new cached state region-local or owner-local.
- Avoid global concurrent caches on hot paths.
- Use long epochs and immutable snapshots.
- Keep cache invalidation explicit and testable.

## 5. Non-Goals

The following are explicitly out of scope for this PRD's first production rollout:

- Tracker audience cross-tick result caching.
- Entity AI, brain, sensor, pathfinding result caching.
- Block or fluid tick result caching.
- Entity activation result caching.
- Plugin-visible result caching.
- Packet visibility decision caching.
- Distributed cross-server cache coherence.
- Replacing `hasLoadedChunkInCell` with a ticking-chunk counter.
- Caching mutable collections directly.
- Caching object liveness assumptions such as "this entity is still valid next tick".

## 6. Safety Principles

### 6.1 Cache Is Not Authority

Cached data must never be the source of truth for world state. It can only avoid rebuilding data that can be rebuilt from authoritative collections.

If validation fails, the code must recompute from existing authoritative structures or use the existing code path.

### 6.2 Fail Closed

All cache use must fail closed:

- Missing cache: recompute.
- Epoch mismatch: recompute.
- Owner layout mismatch: recompute or skip cache.
- Suspicious null: skip entry or recompute.
- Unsupported mode: disable cache.

### 6.3 No Gameplay Result Cache In Phase 1

Phase 1 may cache collection snapshots and structural presence. It must not cache decisions such as:

- whether an entity should tick
- which players should see an entity
- whether a mob path is valid
- whether a block update should happen
- whether a plugin callback should run

### 6.4 Immutable Snapshot Only

All collection caches must store immutable snapshot lists. Consumers must not mutate cached lists. Cached snapshots must not expose live backing collections.

### 6.5 Same Order As Existing Code

Snapshot caches must preserve the iteration order of the current source collection:

- `ArrayList<LevelChunk>` order for chunks.
- `IteratorSafeOrderedReferenceSet<Entity>` order for ticking entities.
- `ObjectLinkedOpenHashSet` order for players and tracked entities.

No sorting or re-bucketing is allowed unless the existing path already does it.

## 7. Current Region Tick Flow And Cache Relevance

### 7.1 `_tickRegion(...)`

Current flow:

1. Set current ticking region.
2. Drain internal tasks.
3. Process unloads.
4. Execute entity Bukkit task scheduler using `forEachTickingEntity(...)`.
5. Drain region mailbox tasks.
6. Process scheduled block/fluid ticks.
7. Tick chunks through `getChunksSnapshot()`.
8. Run block events.
9. Tick entities through `getTickingEntitiesSnapshot()`.
10. Run tracker through `getTrackedEntitiesSnapshot()`.
11. Tick block entities.
12. Tick players through `getPlayers()`.
13. Drain internal tasks again.
14. Broadcast changes.
15. Record tick stats and possibly remove empty region.

Cache relevance:

- Step 6 has a safe empty-data guard opportunity.
- Steps 7, 9, 10, and 12 have snapshot allocation opportunities.
- Step 4 is not a current snapshot cache candidate because it iterates directly.
- Step 11 should not be cached in Phase 1 because block entity ordering and pending ticker movement are sensitive.
- Step 14 should not cache result decisions. Existing chunk packet cache remains separate.

### 7.2 `processScheduledTicks(...)`

Current behavior:

- Iterate every owner cell.
- Skip if owner no longer owns cell.
- Skip if write lock is not held.
- Skip if `hasLoadedChunkInCell(...)` is false.
- Tick block ticks.
- Tick fluid ticks.

Safe improvement:

- Before scanning loaded chunks, check whether block or fluid scheduled tick region data exists for the cell.
- If neither block nor fluid tick data exists, skip the cell without calling `hasLoadedChunkInCell(...)`.
- This does not skip scheduled work because no scheduled region data exists.

Unsafe improvement:

- Do not replace `hasLoadedChunkInCell(...)` with `LevelChunkRegion.levelChunks` count. `levelChunks` is a ticking-chunk list, not proven loaded-full chunk membership.

## 8. Cache Candidates

### 8.1 Candidate A: Scheduled Tick Region-Data Guard

Status: Phase 1, recommended.

Type:

- Structural presence cache.
- Uses existing `LevelTicksRegionProxy` region map as the source.

Problem:

- `processScheduledTicks` calls `hasLoadedChunkInCell` even when there is no block/fluid scheduled tick data in that cell.
- `hasLoadedChunkInCell` scans `REGION_SIZE * REGION_SIZE` chunk coordinates.

Design:

- Add a helper:
  - `hasScheduledTickData(level, cell)`
  - Returns true if `level.blockTicks` or `level.fluidTicks` is a `LevelTicksRegionProxy` and `hasRegionData(cell)` is true.
  - If ticks are not `LevelTicksRegionProxy`, return true to preserve existing behavior.
- In `processScheduledTicks`, skip `hasLoadedChunkInCell` when helper returns false.

Pseudo-code:

```java
if (!hasScheduledTickData(level, cell)) {
    advanceCursor();
    continue;
}
if (!region.getOwner().ownsCell(cell) || !hasWriteLock(cell) || !hasLoadedChunkInCell(level, cell)) {
    advanceCursor();
    continue;
}
```

Correctness:

- If no scheduled tick data exists for the region cell, there is no block/fluid scheduled work to run.
- If data exists, existing loaded-chunk scan remains.
- No gameplay result is cached.

Invalidation:

- Delegated to existing `LevelTicksRegionProxy.addContainer`, `removeContainer`, `schedule`, and internal `LevelTicks` state.
- No new long-lived invalidation state is required in Phase 1.

Side effects:

- Additional map lookups in `LevelTicksRegionProxy`.
- For cells that do have scheduled data, the new guard adds overhead before the existing scan.

Mitigation:

- Keep helper cheap.
- Only use helper when `level.blockTicks` or `level.fluidTicks` is `LevelTicksRegionProxy`.
- Add counters for guard hits and misses.

Acceptance:

- No skipped scheduled ticks.
- `scheduledTickEmptyCellSkips > 0` in workloads with sparse scheduled ticks.
- No increase in scheduled tick correctness failures.

### 8.2 Candidate B: Region Membership Snapshot Cache

Status: Phase 2, conditional.

Type:

- Epoch-guarded immutable snapshot cache.

Problem:

- `getChunksSnapshot`, `getTickingEntitiesSnapshot`, `getTrackedEntitiesSnapshot`, and `getPlayers` allocate new lists on each request.
- In large regions, these allocations are predictable and repeated.

Scope:

- Cache only snapshots that already exist today.
- Do not introduce new snapshot semantics for entity task scheduler step.

Snapshot classes:

```java
record CachedSnapshot<T>(
    long epoch,
    int size,
    List<T> entries
) {}
```

Required epochs in `LevelChunkRegion`:

- `chunkMembershipEpoch`
- `tickingEntityMembershipEpoch`
- `trackedEntityMembershipEpoch`
- `playerMembershipEpoch`

Required cached fields:

- `cachedChunksSnapshot`
- `cachedTickingEntitiesSnapshot`
- `cachedTrackedEntitiesSnapshot`
- `cachedPlayersSnapshot`

Build rules:

- Build snapshot under the same synchronization or iterator discipline as current methods.
- Store as immutable list.
- Reuse only if epoch matches.
- If size is below configured threshold, do not cache.
- On cache miss, result must be identical to current method result.

Default thresholds:

- Chunks: cache only when size >= 16.
- Ticking entities: cache only when size >= 64.
- Tracked entities: cache only when size >= 64.
- Players: cache only when size >= 8.

These are initial defaults and must be configurable.

Correctness:

- A cached snapshot is equivalent to the `new ArrayList` or `ObjectArrayList` the current code would have returned at the same epoch.
- Membership mutation increments epoch and nulls the relevant cached snapshot.
- Consumers keep existing object validity checks.

Important limitations:

- Snapshot cache does not guarantee entity liveness.
- Snapshot cache does not guarantee chunk is still loaded.
- Snapshot cache does not guarantee player is still connected.
- Existing consumers must continue to check removed/unloaded/ownership conditions as they do today.

Side effects:

- More objects retained beyond young generation.
- More memory retained per active region.
- Epoch increments add write overhead to membership mutation paths.
- Cache checks add read overhead to small regions.
- If membership churn is high, cache can thrash.

Mitigation:

- Threshold-based activation.
- Per-region hit-rate tracking.
- Automatic disable when hit rate is low.
- Never cache below threshold.
- Clear cached snapshots on split/merge.

Acceptance:

- No change in iteration order.
- No additional NPEs in entity, tracker, player, or chunk tick paths.
- Cache hit rate >= 60 percent for enabled regions.
- Auto-disable triggers when hit rate < 40 percent over 5 seconds.

### 8.3 Candidate C: Exact Loaded-Chunk Cell Counter

Status: Phase 3, research first.

Type:

- Cell-level structural counter.

Problem:

- `hasLoadedChunkInCell` scans chunk coordinates to decide whether any loaded chunk exists in a cell.

Potential design:

- Maintain `loadedFullChunkCountByCell` keyed by `RegionPos.longKey`.
- Increment when a full loaded chunk enters the relevant loaded state.
- Decrement when the full loaded chunk leaves that state.
- Replace `hasLoadedChunkInCell` with count > 0 only after lifecycle hooks are proven exact.

Critical warning:

- `LevelChunkRegion.add(LevelChunk)` is not sufficient proof of loaded-full chunk presence.
- `LevelTicksRegionProxy.addContainer/removeContainer` tracks scheduled tick containers, not full chunk loaded state.
- Using the wrong lifecycle hook can skip valid scheduled ticks or run ticks for unloaded chunks.

Required research:

- Trace full chunk lifecycle in Paper/Moonrise patches.
- Identify exact hook that corresponds to `getChunkAtIfLoadedImmediately(...) != null`.
- Verify chunk status downgrade, unload, async load completion, generation promotion, and failure paths.

Correctness gate:

- For every cell, counter result must match a debug sampled scan:
  - `counter > 0` iff scan finds at least one loaded chunk.
- If mismatch occurs, disable counter globally and log/JFR sample.

Acceptance:

- Zero mismatches under benchmark, async release gates, chunk gen/load smoke, teleport churn, and unload churn.
- Only then may this replace `hasLoadedChunkInCell`.

### 8.4 Candidate D: Region Routing Memoization

Status: Not recommended for Phase 1 or Phase 2.

Reason:

- Current code already has owner epoch in mailbox tasks.
- `ShreddedPaperAccess` introduces owner handoff and safety paths.
- Adding another routing cache could create stale owner or detached region bugs.

Allowed future form:

- Worker-local best-effort memo only.
- Must validate `ownerId`, `layoutEpoch`, and `owner.ownsCell(cell)`.
- Must fallback to `LevelChunkRegionMap` lookup on any mismatch.
- Must not be used for write authorization.

### 8.5 Candidate E: Tracker Audience Cache

Status: Explicitly rejected for this PRD.

Reason:

- Tracker audience depends on player visibility, world, distance maps, metadata, entity tracker state, plugin behavior, vanish, permissions, team state, and chunk watch state.
- Stale visibility bugs are worse than the expected performance gain.

Allowed future form:

- Same-tick local scratch data only.
- No cross-tick cache.

## 9. Invalidation Requirements

### 9.1 `LevelChunkRegion` Membership Epochs

The following methods must invalidate relevant snapshots if Candidate B is implemented.

| Method | Required invalidation |
| --- | --- |
| `add(LevelChunk)` | `chunkMembershipEpoch++`, clear chunk snapshot |
| `remove(LevelChunk)` | `chunkMembershipEpoch++`, clear chunk snapshot |
| `addTickingEntity(Entity)` | `tickingEntityMembershipEpoch++`, clear ticking entity snapshot |
| `removeTickingEntity(Entity)` | `tickingEntityMembershipEpoch++`, clear ticking entity snapshot |
| `addTrackedEntity(Entity)` | `trackedEntityMembershipEpoch++`, clear tracked entity snapshot |
| `removeTrackedEntity(Entity)` | `trackedEntityMembershipEpoch++`, clear tracked entity snapshot |
| `addPlayer(ServerPlayer)` | `playerMembershipEpoch++`, clear player snapshot |
| `removePlayer(ServerPlayer)` | `playerMembershipEpoch++`, clear player snapshot |
| `removePlayerIfPresent(ServerPlayer)` | Increment only if removal happened |
| `absorbFrom(LevelChunkRegion)` | Increment all affected epochs on target and source |
| `extractCellsTo(LevelChunkRegion, LongOpenHashSet)` | Increment all affected epochs on source and target |

### 9.2 Move Paths

Move methods in `LevelChunkRegionMap` must rely on the above add/remove methods to invalidate snapshots:

- `moveTickingEntity`
- `moveTrackedEntity`
- `movePlayer`

If the source and target region are the same owner, no membership invalidation is required unless the source collection is actually mutated.

### 9.3 Split And Merge

Merge and split are high-risk invalidation points.

Requirements:

- After `absorbFrom`, both source and target snapshots must be invalid.
- After `extractCellsTo`, both source and target snapshots must be invalid.
- After owner cells change, layout epoch already changes through `RegionOwner`.
- Caches must not be transferred by reference from source to target.
- Source caches must be cleared before source runtime state is detached.

### 9.4 Scheduled Tick Presence

Candidate A does not require new invalidation state.

If a future scheduled tick presence counter is added:

- `addContainer` must mark region data present.
- `removeContainer` must mark region data absent when empty.
- `schedule` may transition empty to non-empty.
- `tick` may transition non-empty to empty.
- `clearArea`, `copyArea`, and `copyAreaFrom` may change emptiness across multiple regions.
- The counter must be debug-checked against `LevelTicksRegionProxy.hasRegionData`.

## 10. Consistency Model

### 10.1 Single JVM Multi-Threaded Model

The supported model for this PRD is single JVM, multi-threaded independent region ticking.

Rules:

- Region owner thread is the only authority for gameplay mutation in its owned cells.
- Cached snapshots are immutable read artifacts.
- Snapshot publication must be safe under Java memory model.
- Epochs must be `volatile long` or read/written under the same monitor that guards the source collection.
- Source collection mutation and cache invalidation must happen in the same synchronized section when practical.

### 10.2 Read Consistency

Snapshot cache provides collection snapshot consistency, not object state consistency.

That means:

- The list of entries corresponds to a point-in-time membership epoch.
- The object referenced by an entry can still become removed, unloaded, disconnected, or moved later.
- Consumers must keep existing liveness and ownership checks.

### 10.3 Owner Layout Consistency

Any cache keyed by region owner must be invalid if:

- owner layout epoch changes
- owner is detached
- owner cell set changes
- scheduler handle retires
- split child is not scheduler-armed

Phase 1 and Phase 2 should avoid new owner routing caches.

### 10.4 Distributed Consistency

Distributed cache coherence is out of scope.

If the project later introduces multi-server distributed ownership, caches in this PRD must be treated as local-only and invalidated on remote owner handoff events.

## 11. Failure Modes And Mitigations

### 11.1 NPE From Stale Object Reference

Risk:

- Cached snapshot contains an entity, chunk, or player that is later removed or disconnected.

Mitigation:

- Cache immutable list membership only.
- Do not cache liveness.
- Keep existing checks such as `entity.isRemoved()`, loaded chunk lookup, and valid player checks.
- Add optional debug assertions that sample ownership/liveness but do not run by default.

Residual risk:

- Existing code may already assume non-null tracker state after snapshot. Snapshot cache can extend retention and make latent bugs more reproducible.

Required guard:

- If NPE increases in tracker path, disable tracked entity snapshot cache first.

### 11.2 Stale Owner After Split Or Merge

Risk:

- Cached data references a source region after owner cells moved.

Mitigation:

- Invalidate all caches in `absorbFrom` and `extractCellsTo`.
- Never transfer cached snapshot objects between regions.
- Use existing owner layout epoch for any owner-keyed cache.

### 11.3 Tick Order Change

Risk:

- Cached snapshots reorder entries.

Mitigation:

- Preserve existing collection order exactly.
- Do not sort snapshots.
- Add tests comparing cached and uncached order.

### 11.4 Cache Thrashing

Risk:

- High entity movement or chunk churn causes frequent invalidation.
- The region pays both cache maintenance and rebuild costs.

Mitigation:

- Threshold-based cache enablement.
- Hit-rate EWMA per region.
- Auto-disable cache class per region when hit rate is poor.
- Cooldown before re-enable.

Default policy:

- Disable if hit rate < 40 percent over 5 seconds.
- Re-enable only after 30 seconds and when region size remains above threshold.

### 11.5 Memory Pressure And GC Regression

Risk:

- Strong references in snapshots keep objects live longer.
- Large snapshots promote to old gen.

Mitigation:

- Region-local snapshot cap.
- Clear snapshots on region empty, detach, split, merge, and low hit rate.
- Do not cache small collections.
- Add memory counters per cache class.

Default budget:

- Phase 2 soft cap: 64 MiB process-wide for membership snapshots.
- Hard cap: 128 MiB. Above hard cap, disable snapshot cache globally until memory falls below soft cap.

### 11.6 Lock Contention

Risk:

- Building snapshots under synchronized methods increases lock hold time.

Mitigation:

- Build using same current lock/iterator discipline.
- Do not add global locks.
- Use cached volatile reference for fast hit path.
- On miss, accept current synchronized cost because current code already takes it.

### 11.7 False Sharing On Epoch Counters

Risk:

- Multiple worker threads update neighboring epoch fields.

Mitigation:

- Epoch writes happen on membership mutation, not every tick.
- Avoid AtomicLong unless cross-thread atomic increments are required.
- Prefer synchronized increment inside existing mutation lock.

### 11.8 Plugin Side Effects

Risk:

- Plugin code mutates world state through paths not covered by invalidation hooks.

Mitigation:

- Only cache region-owned collections maintained by existing region APIs.
- Do not cache plugin-visible decisions.
- Add emergency config to disable all new caches.
- In debug mode, sample compare cached snapshot sizes against rebuilt snapshots.

### 11.9 Incorrect Loaded-Chunk Counter

Risk:

- Replacing `hasLoadedChunkInCell` with a wrong counter can skip scheduled ticks.

Mitigation:

- Do not implement loaded-chunk counter until exact lifecycle hook is proven.
- Candidate A keeps existing scan when scheduled data exists.
- Candidate C requires debug scan validation and automatic global disable.

### 11.10 Cache Miss Overhead

Risk:

- Cache checks cost more than recomputation for small regions.

Mitigation:

- Thresholds.
- Hit-rate auto-disable.
- Per-cache-class configuration.
- A/B benchmark before enabling by default.

## 12. Observability Requirements

### 12.1 Phase Timing

Add phase-level timing before enabling non-trivial caches.

Minimum phases:

- internal task drain before mailbox
- unload
- entity task scheduler
- region mailbox
- scheduled block/fluid ticks
- chunk tick
- block events
- entity tick
- tracker
- block entity tick
- player tick
- internal task drain after player
- broadcast

Implementation options:

- Add fields to `RegionTickEvent`.
- Or add a new sampled `RegionTickPhaseEvent`.

Preferred first step:

- Add a compact per-region phase aggregate in `RegionTickEvent` if event size remains reasonable.
- Otherwise add sampled phase events only when phase exceeds threshold.

### 12.2 Cache Metrics

Required counters per cache class:

- enabled regions
- hits
- misses
- builds
- invalidations
- auto-disables
- estimated retained bytes
- stale rejects
- debug mismatches

Suggested JFR event:

`RegionCacheEvent`

Fields:

- world
- regionX
- regionZ
- cacheClass
- action
- hits
- misses
- builds
- invalidations
- retainedBytes
- epoch
- ownerEpoch
- reason

### 12.3 Admin Visibility

`/region inspect` should eventually show:

- cache enabled/disabled per class
- hit rate
- retained bytes
- last disable reason
- scheduled tick empty-cell skips
- debug mismatch count

## 13. Configuration Requirements

Add a conservative config section under `multithreading` or `optimizations.region-cache`.

Suggested options:

```yaml
region-cache:
  enabled: true
  scheduled-tick-presence-guard: true
  membership-snapshot-cache: false
  loaded-chunk-cell-counter: false
  debug-validate: false
  auto-disable-on-low-hit-rate: true
  hit-rate-disable-threshold: 0.40
  hit-rate-enable-threshold: 0.70
  min-chunks-for-snapshot-cache: 16
  min-entities-for-snapshot-cache: 64
  min-tracked-entities-for-snapshot-cache: 64
  min-players-for-snapshot-cache: 8
  snapshot-soft-memory-mib: 64
  snapshot-hard-memory-mib: 128
```

Default policy:

- `scheduled-tick-presence-guard` may be enabled by default after tests pass.
- `membership-snapshot-cache` starts disabled by default.
- `loaded-chunk-cell-counter` remains disabled and experimental.

## 14. Rollout Plan

### Phase 0: Instrumentation

Deliverables:

- Region tick phase timing.
- Cache metric framework with no behavior change.
- Benchmark harness updates to capture JFR or region diagnostics.

Exit criteria:

- Phase timings show stable data.
- No functional behavior changes.

### Phase 1: Scheduled Tick Region-Data Guard

Deliverables:

- Add `hasScheduledTickData` helper.
- Skip `hasLoadedChunkInCell` when both block/fluid region data are absent.
- Add skip/hit counters.

Exit criteria:

- Compile passes.
- Scheduled tick tests pass.
- No regression in chunk generation/load smoke.
- JFR shows non-zero empty-cell skips in sparse workloads.

### Phase 2: Membership Snapshot Cache Behind Flag

Deliverables:

- Add epochs and cached snapshots to `LevelChunkRegion`.
- Invalidate in all membership mutation methods.
- Add thresholds and hit-rate auto-disable.
- Keep default disabled until benchmarks pass.

Exit criteria:

- Cached and uncached snapshot order tests pass.
- Split/merge invalidation tests pass.
- No additional NPEs under async ownership release gates.
- Hit-rate and retained memory are visible.

### Phase 3: Exact Loaded-Chunk Cell Counter Research

Deliverables:

- Identify exact loaded chunk lifecycle hooks.
- Implement counter in debug-only mode.
- Compare counter against scan.

Exit criteria:

- Zero mismatches across stress tests.
- Only then consider replacing `hasLoadedChunkInCell`.

## 15. Test Plan

### 15.1 Unit Tests

Add or extend tests for:

- `LevelTicksRegionProxy.hasRegionData` behavior after add/remove container.
- Scheduled tick guard skips only empty scheduled tick cells.
- Snapshot cache returns same order as uncached rebuild.
- Snapshot cache invalidates on add/remove.
- `removePlayerIfPresent` increments player epoch only on real removal.
- Split invalidates source and target caches.
- Merge invalidates source and target caches.

### 15.2 Concurrency Tests

Scenarios:

- Entity moves between regions while snapshots are used.
- Player disconnects during player snapshot iteration.
- Chunk unload happens while chunk snapshot cache exists.
- Split child activation while source cache exists.
- Cross-region owner handoff task runs after layout epoch change.

Expected:

- No NPE increase.
- No stale owner mutation.
- Existing fallback paths handle invalid entries.

### 15.3 Runtime Smoke

Use existing tools:

- async release gates
- worldgen smoke
- watchdog smoke
- region load test plugin

Required scenarios:

- sparse scheduled ticks
- dense scheduled ticks
- entity movement churn
- tracker-heavy player crowd
- chunk load-only
- chunk generation
- split/merge churn
- teleport/projectile cross-owner handoff
- unload churn

### 15.4 Benchmark Acceptance

The project does not require exact improvement prediction.

Still, a cache should remain enabled only if:

- it does not increase p95 region tick time in neutral workloads
- it has measurable hit rate in intended workloads
- it does not increase GC pause enough to harm throughput
- it does not increase stale rejects or debug mismatches

## 16. Acceptance Criteria

Phase 1 acceptance:

- No behavior change in tests.
- No scheduled tick correctness failures.
- No new ownership violations.
- Empty scheduled cell skip counter works.
- Can disable through config.

Phase 2 acceptance:

- No additional NPEs in release gates.
- No stale owner mutations.
- Snapshot order matches uncached order.
- Snapshot cache auto-disables under churn.
- Memory cap works.
- `/region` or JFR exposes hit/miss/build/invalidation metrics.

Phase 3 acceptance:

- Exact loaded-chunk counter matches scan in all debug validation.
- Counter auto-disables on any mismatch.
- Counter remains disabled by default until proven.

## 17. Explicit Rejection Criteria

A cache must be removed or kept disabled if any of the following occur:

- It caches gameplay decisions instead of structural data.
- It requires relaxing ownership checks.
- It changes tick order.
- It changes plugin-visible behavior.
- It increases stale task redirects materially.
- It creates NPEs under teleport, unload, disconnect, split, or merge churn.
- It needs a global lock on hot path.
- It has poor hit rate and no clear workload where it helps.
- It lacks a config kill switch.

## 18. Open Questions

1. Which exact hook represents `getChunkAtIfLoadedImmediately(...) != null` for a full loaded chunk?
2. Can `LevelTicksRegionProxy` cheaply expose "any block or fluid region data exists" without extra allocations?
3. Should phase timing be added to `RegionTickEvent` or emitted as separate sampled events?
4. Should snapshot cache be per-region only, or should it live in `RegionRuntimeState` to survive region object replacement?
5. What is the acceptable default memory budget on common server heaps such as 8 GiB?
6. Can tracked entity snapshot caching safely survive current tracker assumptions, or should tracked cache remain disabled until tracker code adds null-safe guards?

## 19. Recommended First Implementation

Implement only the following first:

1. Add phase timing instrumentation.
2. Add scheduled tick region-data guard.
3. Add counters and JFR fields for guard hits/skips.
4. Run benchmarks and async release gates.

Do not implement membership snapshot cache until the phase timing proves snapshot allocation is a meaningful cost.

Do not implement loaded-chunk cell counter until lifecycle equivalence is proven.

Do not implement tracker audience cache.

## 20. Summary

The safe production path is narrow:

- Cache structural facts, not gameplay decisions.
- Use existing scheduled tick region data to avoid empty-cell scans.
- Use epoch-guarded snapshots only when measured allocation cost justifies them.
- Treat loaded-chunk counters as research until exact lifecycle hooks are proven.
- Keep all caches observable, bounded, optional, and fail-closed.

This approach does not promise a specific percent improvement. It is designed so that when the cache is active, it removes repeated structural work while preserving existing game semantics and ownership safety.
