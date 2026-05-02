# Chunk Generation Computation Caching PRD

Date: 2026-04-29
Status: Draft
Owner: ShreddedPaper performance/runtime
Scope: chunk generation throughput under independent region ticking

## 1. Background

ShreddedPaper currently preserves global TPS and unrelated-region responsiveness during chunk generation, but measured chunk generation throughput is lower than Paper/Folia in the latest documented benchmark.

Observed benchmark from `BENCHMARK_RESULTS_2026-04-27.md`:

| Engine | Chunk generation throughput |
| --- | ---: |
| custom-shreddedpaper | 31.17 chunks/sec |
| original-shreddedpaper | 36.37 chunks/sec |
| folia | 40.67 chunks/sec |
| paper | 40.18 chunks/sec |

The current architecture intentionally routes chunk generation work through region-aware admission control:

- `ChunkUpgradeGenericStatusTask` wraps parallel-capable generation work with `RegionChunkExecutorLimiter.WorkType.GENERATION`.
- `RegionChunkIoTracker` enforces per-region executor admission caps.
- Default internal chunk worker caps are currently:
  - normal region owner: `chunkIoExecutorMaxInflightNormalPerRegion = 8`
  - degraded region owner: `chunkIoExecutorMaxInflightDegradedPerRegion = 2`

This means raw throughput is affected by both:

1. CPU time spent inside each generation task.
2. Queue/permit time caused by region-local QoS.

The goal of computation caching is not to remove QoS isolation. The goal is to reduce CPU time per admitted generation task so permits turn over faster while preserving region isolation and correctness.

## 2. Problem Statement

Chunk generation performs deterministic, repeated computations across nearby chunks and within repeated status transitions. Some of these computations are already optimized by C2ME-derived code paths, but the current codebase does not contain a general worldgen computation cache for high-reuse deterministic values.

However, naive caching can reduce throughput or corrupt worldgen if:

- lookup overhead exceeds saved compute time,
- cache keys omit datapack, dimension, seed, generator, or eval-mode context,
- mutable world/chunk state is cached,
- cross-thread shared maps become contended,
- memory growth increases GC pause time,
- stale cached values survive datapack/worldgen reload,
- region-owner migration interacts poorly with region-local cache ownership,
- cache fill runs while region QoS is already degraded,
- cache hit-rate assumptions are not measured.

The PRD therefore prioritizes measured, bounded, deterministic, primitive-heavy caches and explicitly avoids broad global density-function caching until there is evidence that it wins.

## 3. Goals

- Improve chunk generation throughput without weakening region-local QoS isolation.
- Reduce CPU time per generation task for deterministic repeated worldgen computations.
- Keep cache-induced latency variance bounded.
- Provide enough telemetry to prove benefit or automatically disable ineffective caches.
- Make all caches safe under multi-threaded generation, async callbacks, region split/merge, and datapack/world reload.
- Ensure memory usage is bounded by explicit configuration and runtime pressure controls.
- Support incremental rollout with per-cache feature flags.

## 4. Non-Goals

- Do not bypass `RegionChunkExecutorLimiter`.
- Do not increase default per-region executor caps as part of this PRD.
- Do not cache chunk mutable state, block mutation results, scheduled tick side effects, or plugin-visible objects.
- Do not introduce a broad global `DensityFunction` sample cache in the first implementation phase.
- Do not rely on `SoftReference` or GC-driven cache eviction for primary cache correctness or performance.
- Do not change generated terrain output.
- Do not make cache correctness depend on task execution order.

## 5. Current Codebase Facts

The following facts are true in the current working tree and must be considered during implementation:

- Parallel chunk generation is wrapped by `RegionChunkExecutorLimiter.createTask(..., WorkType.GENERATION)`.
- `RegionChunkIoTracker.tryAcquireExecutor(...)` gates internal chunk worker tasks and returns `DEFERRED` when the per-region executor cap is saturated.
- `RegionTickEvent` already exposes region-level chunk IO/executor pressure metrics.
- `DensityFunctions.Marker` implements `IFastCacheLike`, but its cache methods are no-ops.
- `CacheLikeNode` can call `c2me$getCached` and `c2me$cache`, but currently marker-backed runtime caching is effectively disabled.
- `Aquifer` currently precomputes per-chunk aquifer random grid positions into arrays during aquifer construction.
- `TheEndBiomeSource` has a thread-local LRU-style biome cache keyed only by `biomeX` and `biomeZ`, which is valid only for that specific End biome source behavior.
- `BytecodeGen` caches compiled DFC classes, so compilation itself is not the primary runtime cache target.

## 6. Success Metrics

Primary metrics:

- Chunk generation throughput improves by at least 8% on the benchmark scenario after warm-up.
- No regression in far control probe average lag.
- No increase in region quarantines under the same workload.
- No generated terrain mismatch in deterministic comparison tests.

Secondary metrics:

- Generation task run time p50/p95 decreases.
- `chunkIoExecutorInFlight / chunkIoExecutorCapacity` pressure decreases or remains stable.
- `chunkIoExecutorDeferred` decreases or remains stable.
- Cache lookup overhead stays below 5% of generation task CPU time.
- Cache memory usage remains below configured hard cap.
- Cache hit-rate remains above per-cache minimum thresholds after warm-up.

Initial target:

- Conservative target: `31.17 -> 34.0+ chunks/sec`.
- Stretch target: `31.17 -> 36.0+ chunks/sec`.
- Any estimate above this requires measured evidence from instrumentation.

## 7. Required Instrumentation First

No new shared computation cache should ship enabled by default until instrumentation can answer whether the cache is useful.

### 7.1 Generation Task Timing

Add JFR/event counters around `RegionChunkExecutorLimiter.WorkType.GENERATION` delegate execution:

- `workType`
- `world`
- `dimension`
- `regionX`
- `regionZ`
- `chunkX`
- `chunkZ`
- requested priority
- admitted priority
- owner id at admission
- owner id at execution
- queue wait nanos
- run nanos
- permit held nanos
- result: completed, cancelled, overflow, emergency

Purpose:

- Separate queue/permit wait from actual worldgen run time.
- Confirm whether caching has a chance to improve throughput.

### 7.2 Candidate Operation Timing

Add sampled counters for candidate operations:

- aquifer grid random position compute count and nanos
- aquifer fluid status compute count and nanos
- biome source sample count and nanos
- DFC `NoiseHolder#getValue` count and nanos by noise holder id, sampled
- `CacheLikeNode` hit/miss count if runtime cache is enabled
- structure/beardifier compute count and nanos, if structure optimizer is enabled

Sampling rule:

- Default sample rate: 1 out of 1024 calls for very hot single-point noise calls.
- Default sample rate: 1 out of 64 calls for aquifer/biome/structure operations.
- Never allocate on sampled hot path.
- Use per-thread counters and aggregate periodically.

### 7.3 Simulated Hit-Rate

Before enabling a cache, support shadow-mode key tracking:

- Compute the proposed cache key.
- Do not store values, only count repeated keys using a small approximate per-thread table.
- Record:
  - observed repeated-key ratio,
  - key cardinality per chunk,
  - cardinality per region,
  - approximate bytes if stored.

This avoids implementing large caches based on assumptions.

## 8. Cache Candidate Selection Criteria

A computation may be cached only if all required criteria are true.

Required:

- Deterministic from explicit inputs.
- No side effects.
- Result is immutable or primitive.
- Full key can include all semantic inputs.
- Hit-rate is measured or strongly bounded by local geometry.
- Lookup cost is lower than recomputation cost.
- Eviction cannot affect correctness.
- Stale entry cannot survive world/datapack/noise settings epoch changes.
- Cache is safe if accessed concurrently or bypassed.

Rejected by default:

- `Level`, `ChunkAccess`, `LevelChunk`, `StructureManager`, mutable collections, mutable random sources.
- Block/fluid mutation decisions that schedule updates.
- Values affected by current chunk contents.
- Plugin callback results.
- Any result that depends on non-idempotent random source state instead of coordinates and seed.
- Broad `DensityFunction` samples with unmeasured hit-rate.

## 9. Proposed Cache Families

### 9.1 Phase 0: No Behavioral Cache, Instrumentation Only

Purpose:

- Prove actual bottlenecks.
- Establish baseline overhead.

Implementation:

- Add JFR/counter events.
- Add config to enable/disable sampled instrumentation.
- Add command output or JFR fields for summarized counters.

Default:

- Enabled with low sampling rate, or disabled by default if overhead is measurable.

Risks:

- Counter overhead in hot loops.
- JFR event allocation if emitted per call.

Mitigation:

- Use thread-local counters.
- Emit aggregated events once per region tick or every N seconds.

### 9.2 Phase 1: Aquifer Grid Position Cache

Rationale:

- Aquifer grid positions are deterministic from positional random factory seed and grid coordinates.
- Neighboring chunks overlap aquifer grid coordinates.
- Current code precomputes aquifer positions per chunk, so cross-chunk reuse is plausible.

Cache target:

- Packed aquifer block position or packed `(offsetX, offsetY, offsetZ)`.

Preferred value:

- `short packedOffset = (offsetX << 8) | (offsetY << 4) | offsetZ`

Key:

- `worldgenEpoch`
- `dimensionId`
- `randomFactoryType`
- `randomFactorySeedHi`
- `randomFactorySeedLo` or legacy seed
- `gridX`
- `gridY`
- `gridZ`

Recommended structure:

- L0 thread-local fixed-size primitive table.
- Optional L1 region-owner primitive table after L0 proves useful.

L0 details:

- Open-addressed arrays:
  - `long[] keyHi`
  - `long[] keyLo`
  - `short[] value`
  - `byte[] state`
- Power-of-two capacity, e.g. 4096 or 8192 entries per thread.
- Linear or quadratic probe with small max probe count.
- On probe overflow, bypass cache.
- Clear on epoch change or periodically.

Why not global first:

- The computed value is cheap enough that lock/shared-map overhead may exceed savings.
- L0 has no contention and very predictable overhead.

Invalidation:

- `worldgenEpoch` mismatch invalidates without clearing.
- Thread-local table can lazily overwrite old epoch entries.

Side effects:

- More memory per generation thread.
- Potentially lower CPU cache locality if table is too large.

Mitigation:

- Keep table small.
- Adaptive disable when hit-rate is below threshold.

Enable criteria:

- Shadow-mode repeated-key ratio >= 20%.
- Measured aquifer grid random compute time >= 2% of generation task run time.
- L0 lookup p95 < direct recompute p50.

Disable criteria:

- Hit-rate < 10% for 10 consecutive windows.
- Cache overhead > 1% generation run time.
- GC pressure or memory pressure enters high state.

### 9.3 Phase 2: Biome/Climate Sample Cache

Rationale:

- Biome sampling can repeat within chunk generation and neighboring chunks.
- The End already uses a thread-local biome cache, but it is specialized and keyed by X/Z only.

Important correctness warning:

- Do not generalize the End cache key to all dimensions.
- General biome sampling may depend on X, Y, Z, biome source, climate sampler, dimension, and datapack configuration.

Cache target:

- `Holder<Biome>` or stable biome holder id.

Preferred value:

- Store `Holder<Biome>` only if holder lifecycle is stable for the worldgen epoch.
- Otherwise store a small integer biome id resolved through an epoch-local registry lookup.

Key:

- `worldgenEpoch`
- `dimensionId`
- `biomeSourceIdentity`
- `climateSamplerIdentity`
- `quartX`
- `quartY`
- `quartZ`

Structure:

- L0 thread-local LRU/open-address cache.
- Use full key equality, not just packed hash.

Invalidation:

- Increment `worldgenEpoch` on datapack reload, world unload, dimension registry reload, or biome source rebuild.

Side effects:

- Holder retention may keep old registry objects alive after reload.
- General Overworld cache may have poor hit-rate due to high spatial variation.

Mitigation:

- Include epoch in key.
- Clear thread-local tables on reload hook.
- Prefer storing stable ids where feasible.
- Keep End-specific cache separate.

Enable criteria:

- Biome sampling is measured above 3% of generation run time.
- Shadow-mode repeated-key ratio >= 25%.
- No deterministic terrain mismatch in biome-sensitive test worlds.

### 9.4 Phase 3: Selected Marker-Local Density Runtime Cache

Rationale:

- The code has `IFastCacheLike` and `CacheLikeNode`, but marker cache methods are no-op.
- This is an existing extension seam, but using it broadly is risky.

This cache must be narrow and adaptive.

Cache target:

- Only selected high-cost density subtrees where hit-rate is proven.
- Never cache all `NoiseHolder#getValue` calls globally by default.

Allowed candidates:

- Expensive marker-wrapped density functions that are re-entered at the same coordinates and eval type.
- Functions where `CacheLikeNode` already wraps the delegate and can preserve semantics.

Key:

- `worldgenEpoch`
- `functionId`
- `evalType`
- `blockX`
- `blockY`
- `blockZ`

Function id assignment:

- Assign per `RandomState` or per density router initialization.
- Use `IdentityHashMap<DensityFunction, int>`.
- Do not use `hashCode()` alone.
- Do not use class name alone.

Value:

- `double`
- Store raw long bits to preserve NaN distinctions.

Structure:

- L0 thread-local primitive table only for first implementation.
- Optional task-local table attached to generation execution context.
- No shared global density sample cache in initial release.

Correctness exclusions:

- If `FunctionContext.getBlender() != Blender.empty()`, bypass runtime cache.
- If eval type is not understood, bypass.
- If function was transformed by a visitor and identity changed, function id must change.
- Do not store mutable cache arrays directly inside shared `DensityFunction` or `DensityFunctions.Marker` instances.
- `DensityFunctions.Marker` may be the hook object, but the actual mutable cache storage must be task-local, thread-local, or otherwise concurrency-safe.

Adaptive policy:

- Each function id tracks hit/miss in small counters.
- Enable cache only after warm-up confirms:
  - repeated-key ratio >= 30%,
  - average compute cost above threshold,
  - lookup overhead below saved compute time.
- Disable function id when:
  - hit-rate < 15%,
  - negative time savings for N windows,
  - table collision/probe failure rate > threshold.

Side effects:

- Hot-loop lookup overhead may reduce throughput.
- Per-thread memory grows with number of active function ids.
- Storing many doubles can hurt CPU cache locality.

Mitigation:

- Limit active cached function ids.
- Limit total density table bytes per thread.
- Use admission by measured cost.
- Implement bypass fast path when disabled.

### 9.5 Phase 4: Structure/Beardifier Influence Cache

Rationale:

- `Beardifier` computes terrain adaptation contributions from nearby rigid pieces and junctions.
- Structure-heavy worlds may repeat influence calculations.

Why this is late:

- Structure data can be complex and tied to chunk status and structure manager state.
- Many worlds have sparse structures and low reuse.

Cache target:

- Immutable per-chunk/per-y-section influence slabs only when structures exist.

Key:

- `worldgenEpoch`
- `dimensionId`
- `structureEpoch`
- `chunkX`
- `chunkZ`
- `sectionY`
- `terrainAdjustmentMask`
- affected bounding box signature

Value:

- Small primitive array or compressed slab of influence values.

Invalidation:

- `structureEpoch` increments when structure data source changes.
- Bypass cache during structure generation phases where source data may still be incomplete.

Side effects:

- High memory cost for low hit-rate worlds.
- Risk of stale structure influence if key is incomplete.

Mitigation:

- Disabled by default.
- Enable only when structure timing is a measured bottleneck.
- Require deterministic structure-heavy test coverage.

## 10. Cache Architecture

### 10.1 L0 Thread-Local Cache

Primary first implementation layer.

Properties:

- One cache bundle per generation worker thread.
- No locks.
- Fixed memory.
- Bypass on collision pressure.
- Cleared or epoch-bumped on reload.

Use cases:

- Aquifer grid offset cache.
- Biome sample cache.
- Selected marker-local density runtime cache.

Pros:

- Minimal concurrency risk.
- Predictable latency.
- Easy fallback.

Cons:

- Lower cross-thread reuse.
- Memory multiplied by worker thread count.

### 10.2 Task-Local Cache

Optional for dense per-chunk reuse.

Properties:

- Attached to a single generation task.
- Reused within task only.
- Released in `finally` block.

Use cases:

- Short-lived coordinate arrays.
- Within-chunk repeated density/biome coordinates.

Pros:

- No invalidation beyond task lifetime.
- Very safe.

Cons:

- No cross-chunk reuse.
- Only useful for intra-task repetition.

### 10.3 Region-Owner Cache

Second-stage shared cache, not first-stage.

Properties:

- Attached to `RegionRuntimeState`.
- Lives with region owner.
- Recreated on owner split/merge; existing entries may be discarded.

Use cases:

- Aquifer grid values with local spatial reuse.
- Maybe biome samples after L0 proves benefit.

Owner migration rule:

- A cache may be discarded on split/merge.
- Do not try to move cache entries between owners in the first implementation.
- Evict/discarding is safe because cache affects performance only.

Concurrency:

- Generation tasks can execute on non-region worker threads.
- Region-owner cache must be thread-safe.
- Use shard-local primitive maps with striped locks or lock-free read/bypass.

Preferred policy:

- `tryLock` for writes.
- If lock is contended, bypass.
- No generation thread should block on cache fill.

### 10.4 Global ServerLevel Cache

Not recommended for initial implementation.

Use only after:

- L0/L1 prove useful.
- Hit-rate requires cross-region reuse.
- Contention can be measured and bounded.

Risks:

- Cross-region lock contention.
- Memory retention across reloads.
- CPU cache locality regression.
- More complex memory accounting.

## 11. Key Design and Collision Strategy

### 11.1 General Requirements

All cache keys must include:

- worldgen epoch,
- dimension identity,
- generator/noise settings identity,
- computation-specific identity,
- coordinates,
- eval mode where applicable.

Never key only by:

- coordinate,
- chunk position,
- object `hashCode()`,
- class name,
- `toString()`.

### 11.2 Full Equality After Hash

Hash collisions are acceptable only if every entry stores enough data for full equality confirmation.

Required:

- Store at least `keyHi` and `keyLo` for compact full key.
- For caches with more semantic fields, either pack all fields losslessly or store a compact fingerprint plus an exact identity id.
- A matching hash with mismatched key fields must be treated as miss.

### 11.3 Epochs

Introduce `WorldgenCacheEpoch`.

Epoch increments on:

- datapack reload,
- registry reload affecting dimensions, biomes, density functions, configured features, structures, or noise settings,
- world unload,
- server shutdown,
- dimension generator rebuild.

Epoch may also increment manually through a debug command.

Epoch behavior:

- Old entries are treated as misses.
- Thread-local tables may lazily overwrite old entries.
- Shared caches should clear asynchronously or drop whole table references.

### 11.4 Identity Assignment

For function/source identity:

- Use epoch-scoped integer ids assigned from object identity.
- Store id in a manager associated with the relevant `ServerLevel` or `RandomState`.
- Do not share ids across epochs.

For dimensions:

- Use a stable registry key hash plus epoch.
- Avoid retaining full registry objects in cache entries.

## 12. Eviction and Memory Management

### 12.1 Configuration

Add configuration under chunk performance settings only after instrumentation proves at least one cache is useful:

```yaml
optimizations:
  chunks:
    worldgen-computation-cache:
      enabled: false
      instrumentation-enabled: false
      instrumentation-sample-rate: 1024
      max-bytes: 268435456
      soft-bytes: 201326592
      per-thread-max-bytes: 4194304
      aquifer-grid-cache-enabled: true
      biome-sample-cache-enabled: false
      density-runtime-cache-enabled: false
      structure-influence-cache-enabled: false
      min-hit-rate-to-enable: 0.30
      min-hit-rate-to-keep: 0.15
      disable-on-high-gc-pressure: true
```

Defaults:

- New caches disabled until benchmarked.
- Instrumentation can be enabled independently.
- Aquifer cache may become first enabled-by-default cache only after deterministic tests and benchmark evidence.

### 12.2 L0 Memory Limit

Per thread:

- Fixed-size tables.
- No dynamic growth on hot path.
- Capacity controlled by config.

If capacity is exceeded:

- overwrite by simple replacement,
- or bypass on probe failure.

### 12.3 L1/L2 Memory Limit

If shared caches are introduced:

- Maintain approximate byte counters per cache family.
- Use weighted LRU or segmented FIFO with primitive entries.
- Evict on soft cap in background.
- Evict synchronously on hard cap.

Eviction must never block generation for long:

- per-operation eviction batch cap,
- no full-map scan in generation worker hot path,
- no allocation-heavy eviction callbacks.

### 12.4 GC Pressure Controls

Define memory pressure states:

- NORMAL: cache operates normally.
- ELEVATED: stop warm-up/prefetch, reduce insert rate.
- HIGH: disable density and structure caches, keep only tiny L0 caches.
- CRITICAL: disable all worldgen computation caches and clear shared caches.

Inputs:

- free heap ratio,
- recent GC time ratio,
- allocation rate if available,
- cache hard-cap pressure.

Actions:

- Never wait for GC to save memory.
- Explicitly reduce cache admission before heap exhaustion.
- Emit event when cache family is disabled due to pressure.

## 13. Concurrency Model

### 13.1 Generation Threads

Generation work can run on internal chunk worker executors and is not guaranteed to be the owning region tick thread.

Therefore:

- Do not use unsynchronized shared mutable cache state unless thread-local.
- Do not access region-owned mutable structures without existing safety guarantees.
- Do not assume current thread is stable owner for a region-local cache.
- Do not put mutable cache tables inside worldgen singleton objects that are shared across generation workers.
- If a cache is exposed through an existing interface such as `IFastCacheLike`, the implementation must delegate to a per-thread or per-task storage owner.

### 13.2 ThreadLocal Lifecycle

Thread-local caches are safer for concurrency, but they can leak memory for the lifetime of long-lived executor threads.

Requirements:

- Track all thread-local cache bundles through a weak registry or epoch manager.
- Clear or epoch-invalidate thread-local caches on world unload and datapack reload.
- Bound per-thread memory independently of global memory.
- Do not create one thread-local table per function id without a per-thread aggregate cap.
- Avoid large `ThreadLocal` values if chunk workers may be created dynamically.

Failure mode:

- If a thread-local cache cannot be cleared immediately, epoch mismatch must still prevent stale reads.

### 13.3 Region Split/Merge

`RegionChunkExecutorLimiter` already handles owner id changes for tracker ownership. Caches must follow a simpler rule:

- L0: unaffected.
- Task-local: unaffected.
- Region-owner cache: discard on split/merge or owner id change.

Do not migrate region-owner cache entries initially.

Reason:

- Cache rebuild cost is acceptable.
- Migration adds correctness and memory-retention risks.

### 13.4 QoS and Fairness Interaction

Caching can reduce generation run time, but it can also shift bottlenecks:

- Faster hot regions may request more generation work sooner.
- More completed generation work can increase downstream chunk status, lighting, save, or packet pressure.
- Cache warm-up can compete with active generation if not gated.

Rules:

- Cache warm-up must observe region load class and chunk IO pressure.
- Cache implementation must not increase `chunkIoExecutorMaxInflight*` defaults.
- Cache benefit must be evaluated together with `chunkIoExecutorDeferred`, `chunkIoExecutorBacklogQueued`, and far control probe lag.
- If throughput improves but control probe lag regresses, the cache is not acceptable by default.

### 13.5 Locking Rules

Shared cache locking:

- Reads may use optimistic read if available.
- Writes use shard-local lock.
- If lock is contended, bypass cache.
- No task should block waiting for another thread to compute a cache value.
- Duplicate computation is acceptable.

Forbidden:

- `computeIfAbsent` on a shared map if the computation is expensive or may re-enter worldgen.
- Blocking cache fill futures.
- Awaiting warm-up completion.
- Calling plugin-visible APIs from cache code.

### 13.6 Async Callback Safety

Cache values may be used in async generation tasks only if:

- they are immutable,
- they do not capture mutable chunk/world state,
- they can be discarded at any time.

Callbacks that complete chunk status must not depend on cache cleanup.

## 14. Cold Start and Warm-Up

### 14.1 Cold Start

At server/world start:

- Do not eagerly populate large caches.
- Initialize epoch and identity registries.
- Preallocate small L0 table templates if needed.

Reason:

- Startup should not pay for caches that may not be used.

### 14.2 Warm-Up

Allowed warm-up:

- Passive warm-up through normal generation.
- Optional shadow-mode key tracking.
- Optional tiny prefetch for next chunk ring only when region load is NORMAL.

Warm-up must stop when:

- region load class is DEGRADED or QUARANTINED,
- executor deferred retries are non-zero,
- cache memory pressure is ELEVATED or higher,
- control probe or schedule lag worsens.

Warm-up priority:

- Always lower than active generation.
- Never consume chunk IO executor permits unless it is itself normal generation work.

## 15. Correctness Requirements

### 15.1 Deterministic Terrain Output

With caches enabled, generated chunk data must match caches disabled for fixed:

- seed,
- world preset,
- dimension,
- datapack set,
- generator settings,
- chunk coordinate range.

Required comparisons:

- block states,
- biomes,
- heightmaps,
- structures where applicable,
- fluid states,
- scheduled fluid update behavior if output captures it.

### 15.2 Cache Bypass Equivalence

Every cache lookup must have a direct recompute fallback.

Fallback must be:

- side-effect equivalent,
- safe to call repeatedly,
- independent of cache state.

### 15.3 Reload Safety

After datapack reload:

- old cached values must not be returned,
- old biome/density/structure objects must not be strongly retained by shared caches,
- thread-local caches must be epoch-invalidated.

### 15.4 Failure Mode

If cache code throws unexpectedly:

- disable the cache family,
- log/JFR event with cache family and reason,
- recompute directly,
- do not crash the generation task unless direct recompute also fails.

Exception:

- Assertion failures in test/debug mode may fail fast.

## 16. Observability

Add aggregated metrics per cache family:

- enabled
- active entries
- approximate bytes
- hits
- misses
- inserts
- evictions
- bypasses
- probe failures
- stale epoch misses
- lock contention bypasses
- adaptive disables
- compute nanos saved estimate
- lookup nanos estimate

Expose through:

- JFR events,
- `/mp region` diagnostics summary if appropriate,
- benchmark log summary,
- optional debug command for cache stats.

Important:

- Metrics must not allocate per hot call.
- Metrics should be aggregated by thread and periodically merged.

## 17. Adaptive Enable/Disable Policy

Each cache family has a state machine:

- DISABLED
- SHADOW
- WARMING
- ENABLED
- COOLDOWN
- DISABLED_BY_PRESSURE
- DISABLED_BY_NEGATIVE_GAIN

Transition rules:

- DISABLED -> SHADOW when config enables instrumentation.
- SHADOW -> WARMING when repeated-key ratio passes threshold.
- WARMING -> ENABLED when measured hit-rate and time savings pass thresholds.
- ENABLED -> COOLDOWN when hit-rate falls below keep threshold.
- COOLDOWN -> ENABLED if hit-rate recovers.
- COOLDOWN -> DISABLED_BY_NEGATIVE_GAIN if overhead exceeds savings.
- Any state -> DISABLED_BY_PRESSURE on HIGH memory/GC pressure.

No adaptive transition should happen more frequently than once per observation window.

Recommended observation window:

- 10 seconds or 2048 generated chunks, whichever comes first.

## 18. Test Plan

### 18.1 Unit Tests

Key packing:

- negative coordinates,
- large coordinates,
- min/max Y,
- epoch mismatch,
- dimension mismatch,
- function id mismatch,
- eval type mismatch,
- deliberate hash collisions.

Eviction:

- soft cap pruning,
- hard cap pruning,
- stale epoch entries,
- probe overflow fallback.

Concurrency:

- concurrent get/put on shared shard,
- lock contention bypass,
- duplicate computation safety,
- region owner id change discards region-local cache.

### 18.2 Determinism Tests

Generate fixed chunk ranges twice:

- cache disabled,
- cache enabled.

Compare serialized chunk outputs.

World presets:

- default overworld,
- large biomes,
- amplified,
- nether,
- end,
- structure-heavy seed,
- aquifer-heavy cave terrain,
- datapack-modified worldgen if available.

Coordinate patterns:

- contiguous square,
- spiral,
- random sparse,
- negative coordinates,
- region boundary crossing,
- split/merge stress if available.

### 18.3 Runtime Stress Tests

Scenarios:

- benchmark `scenario gen`,
- concurrent player movement generating chunks,
- one overloaded region plus far control probe,
- repeated world reload/datapack reload if supported,
- low heap setting,
- high thread-count setting,
- degraded region lane active.

Assertions:

- no crash,
- no cache stale entry after reload,
- no unbounded memory growth,
- no increase in p95 schedule lag,
- no increase in rejected critical system work.

### 18.4 Performance Tests

Benchmark matrix:

- baseline no new cache,
- instrumentation only,
- aquifer L0 cache,
- aquifer L0 + biome L0,
- selected density cache shadow,
- selected density cache enabled only if thresholds pass.

Report:

- chunks/sec,
- generation run nanos p50/p95/p99,
- queue wait nanos p50/p95/p99,
- permit held nanos,
- cache hit-rate,
- cache memory,
- GC pause/time,
- control probe lag,
- region load class transitions.

## 19. Rollout Plan

### Phase A: Instrumentation

Deliver:

- generation task timing,
- candidate operation timing,
- shadow hit-rate tables,
- no behavior-changing cache.

Exit criteria:

- overhead < 1% on benchmark,
- counters visible in JFR/log summary,
- no throughput regression beyond noise.

### Phase B: Aquifer L0 Cache

Deliver:

- thread-local primitive aquifer grid offset cache,
- adaptive disable,
- deterministic tests.

Exit criteria:

- terrain output identical,
- throughput improves or overhead is neutral,
- hit-rate and saved-time evidence supports keeping it.

### Phase C: Biome L0 Cache Generalization

Deliver:

- general biome cache only if instrumentation proves benefit,
- End cache remains separate,
- full key includes Y and sampler identity where required.

Exit criteria:

- no biome mismatch,
- no registry retention after reload,
- measurable benefit in worlds where enabled.

### Phase D: Selected Density Runtime Cache

Deliver:

- marker-local runtime cache for measured high-value functions,
- per-function adaptive admission,
- blender bypass,
- no global density cache.

Exit criteria:

- no terrain mismatch,
- positive net time savings,
- function-level hit-rate stable above threshold.

### Phase E: Optional Shared Region-Owner Cache

Deliver only if L0 is insufficient.

Exit criteria:

- no measurable lock contention regression,
- discard-on-owner-change behavior proven,
- memory cap behavior proven.

## 20. Side Effects and Mitigations

### 20.1 Increased Heap Usage

Impact:

- More memory per generation worker.
- More retained primitive arrays.

Mitigation:

- fixed-size L0 tables,
- hard byte caps,
- adaptive pressure disable,
- no unbounded maps.

### 20.2 GC Regression

Impact:

- Large object maps or boxed keys can increase GC time.

Mitigation:

- primitive arrays,
- avoid boxed keys,
- avoid per-call allocations,
- no `SoftReference` primary policy,
- clear/drop shared caches on pressure.

### 20.3 CPU Cache Locality Regression

Impact:

- Large tables can make hot worldgen loops slower.

Mitigation:

- small L0 capacity,
- measure lookup overhead,
- disable low-hit caches,
- avoid global maps in hot path.

### 20.4 Lock Contention

Impact:

- Shared cache locks can reduce parallel throughput.

Mitigation:

- L0 first,
- try-lock bypass,
- shard shared caches,
- no blocking compute.

### 20.5 Stale Worldgen Data

Impact:

- Wrong terrain/biomes/structures after reload.

Mitigation:

- epoch in every key,
- reload hooks increment epoch,
- clear shared caches on reload,
- deterministic reload tests.

### 20.6 Incorrect Key Design

Impact:

- Cross-dimension, cross-seed, or cross-sampler collisions produce wrong output.

Mitigation:

- full equality after hash,
- key tests with deliberate collisions,
- conservative semantic fields,
- no coordinate-only keys except proven special cases like existing End X/Z behavior.

### 20.7 Changed Randomness Semantics

Impact:

- Reusing values derived from mutable random state could alter generation.

Mitigation:

- cache only coordinate-derived deterministic outputs,
- key by positional random factory seed/type,
- never cache mutable `RandomSource`.

### 20.8 Region Owner Migration

Impact:

- Region-local cache may be associated with old owner after split/merge.

Mitigation:

- include owner id in region cache object,
- discard on owner change,
- use L0 for first implementation.

### 20.9 Negative Throughput

Impact:

- Cache lookup overhead can exceed savings.

Mitigation:

- shadow mode,
- adaptive disable,
- per-cache net-savings counters,
- keep caches disabled by default until proven.

### 20.10 Debuggability Loss

Impact:

- Caches can hide where time is spent or make profiles harder to read.

Mitigation:

- clear metrics,
- cache disable command/config,
- JFR events per cache family,
- deterministic compare tooling.

## 21. Acceptance Criteria

Must pass before enabling any cache by default:

- Deterministic output tests pass for all required world presets.
- No stale cache values after datapack/world reload test.
- No unbounded memory growth in long-running generation stress.
- Control probe lag does not regress.
- Cache family shows positive measured net savings.
- Cache can be disabled at runtime or at least on next config reload.
- Failure of cache code falls back to direct computation.

Must pass before merging instrumentation:

- Instrumentation overhead is measured.
- Hot path does not allocate per sampled call beyond configured event aggregation.
- JFR/log output is documented enough for benchmark interpretation.

## 22. Open Questions

- What is the actual fraction of generation task time spent in aquifer grid random position generation?
- Does current C2ME DFC compilation already remove most density-function overhead for the benchmark workload?
- What is the true repeated-key ratio for density samples across current generation statuses?
- Are region-owner cache benefits worth the added split/merge lifecycle complexity?
- Should worldgen cache stats be exposed through `/mp region`, a dedicated command, or only JFR?
- Which reload hook is the authoritative place to increment `worldgenEpoch`?

## 23. Recommendation

The implementation should proceed in this order:

1. Add instrumentation and shadow-mode hit-rate measurement.
2. Evaluate whether the current throughput gap is CPU-run-time dominated or QoS-wait dominated.
3. Implement only L0 thread-local aquifer grid caching if measurements support it.
4. Consider general biome caching only with a full semantic key and measured hit-rate.
5. Treat density runtime caching as experimental, marker-local, allowlisted, and adaptive.
6. Avoid global shared worldgen caches until L0/L1 data proves shared reuse is worth contention and memory cost.

This approach keeps the design compatible with ShreddedPaper's core priority: preserving region isolation and server responsiveness while improving throughput where measurement proves that caching helps.
