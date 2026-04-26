# Async Ownership Audit - 2026-04-27

## Incident

Player teleport and chunk generation could crash an independent region worker with:

```text
Thread failed main thread check: block onPlace
LevelChunk.postProcessGeneration -> FluidState.tick -> FlowingFluid.spread -> Level.setBlock
```

The failing block was in a neighboring region cell held only as an isolation lock.

## Root Cause

Independent ticking writes only owner cells and holds nearby cells as isolation locks. Vanilla chunk post-processing is a special case: a chunk entering `BLOCK_TICKING` can run fluid and neighbor-shape updates that mutate blocks across the owner-cell boundary.

That means `handleFullStatusChange()` can start correctly on the target chunk's owner thread, then `postProcessGeneration()` can legally touch a neighboring cell. The neighboring cell is safe from concurrent local mutation because it is already locked by the same thread, but `TickThread.isTickThreadFor()` rejected it because the lock was read/isolation-only.

## Fix

- Added `ShreddedPaperRegionLocker.ScopedWriteAccess`.
- Added `promoteCurrentThreadLocksToWrite()`, which temporarily marks already-held local locks as writable.
- Added `ShreddedPaper.postProcessGeneration(ServerLevel, LevelChunk)` as the only approved wrapper for chunk post-processing.
- Routed all discovered direct `LevelChunk.postProcessGeneration()` call sites through the wrapper:
  - `BaseChunkSystemHooks.onChunkTicking`
  - `RegionizedPlayerChunkLoader` immediate send path
  - `RegionizedPlayerChunkLoader` deferred owner-region send path
- Strengthened `NewChunkHolder.ensureFullStatusChangeWithWriteLock()` to use `TickThread.isTickThreadFor(world, chunkX, chunkZ)` instead of the weaker "currently ticking region" predicate.

The promotion does not acquire new regions and is not a global bypass. It only upgrades locks already owned by the current thread, then restores their previous read/write classification when the scope closes.

## Audited References

Use this check after future async/chunk changes:

```powershell
rg -n "\.postProcessGeneration\(" shreddedpaper-server/src/minecraft/java shreddedpaper-server/src/main/java -g "*.java"
```

Expected result: direct calls should exist only inside `ShreddedPaper.postProcessGeneration`; external callers should call the wrapper.

## Regression Commands

Validated on `D:\worldgen` with the production plugin set loaded:

```text
rlt at world 0 64 0 chunkgen 4 true
rlt at world 32000 80 32000 chunkgen 4 true
rlt at world 64000 80 64000 chunkgen 12 true
```

Observed results:

```text
centerChunk=0,0 total=81 success=81 failure=0
centerChunk=2000,2000 total=81 success=81 failure=0
centerChunk=4000,4000 total=625 success=625 failure=0
```

No `Thread failed main thread check`, `wrong thread`, `Chunk system crash`, or server crash occurred in `D:\worldgen\logs\latest.log`.
