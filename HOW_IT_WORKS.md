# How ShreddedPaper Works

ShreddedPaper scales a single Minecraft server process by giving loaded map
areas independent tick loops. The goal is not to make game logic eventually
consistent; the goal is to keep normal Minecraft invariants by making ownership
explicit and moving cross-region work through controlled handoff paths.

## Core Model

The world is divided into fixed region cells. The default cell size is eight
chunks, controlled by `multithreading.region-size`.

Cells are owned by `RegionOwner` handles. In the simplest case one owner maps
to one cell. When nearby cells cannot tick independently without violating
Minecraft's neighbor access rules, owners can be merged or serialized so the
unsafe work is still single-owner work.

The important distinction is:

- `RegionPos` is a fixed cell coordinate.
- `RegionOwner` is the current runtime owner of one or more cells.
- `RegionRuntimeState` holds the owner's mailbox, chunk IO tracker, overload
  controller, and scheduler state.

This lets the engine keep stable diagnostics and queues while ownership changes
around loaded gameplay.

## Independent Region Scheduler

With `multithreading.independent-region-ticking: true`, regions are registered
with `RegionTickScheduler` instead of being ticked as one global world barrier.
Each active owner has its own ideal next tick deadline.

Normal behavior:

- normal regions run on the normal worker lane
- degraded regions are moved to a capped degraded lane
- a slow region is rescheduled from its own deadline without catch-up ticks
- unrelated regions can keep ticking if CPU headroom exists

The scheduler intentionally avoids catch-up storms. If a region is late, it
runs one tick and schedules the next one from the current cadence instead of
trying to replay every missed tick.

## Region Ownership And Locks

Minecraft logic often reaches beyond the chunk currently being ticked:
redstone updates neighbors, rails search connected rails, fluids flow into
adjacent blocks, pistons inspect and move lines of blocks, portals move
entities, and explosions affect nearby chunks.

ShreddedPaper handles this with exact-cell ownership checks and region locks:

- read locks allow safe local inspection
- write locks are required for mutation
- lock promotion is explicit and tracked per thread
- owner epochs prevent stale owner handles from accepting old work after a
  merge, split, or transfer
- unsafe cross-owner work is deferred to the target owner instead of being
  performed directly

The old mental model was "tick a chunk while locking a fixed 3x3 region grid."
That is still useful background, but the current implementation is owner-based:
locks protect the exact cells the owner currently owns, and the owner layer
decides when neighboring cells must be combined.

## Cross-Owner Handoffs

`ShreddedPaperAccess` is the central helper for async ownership-sensitive game
paths. It gives patched Minecraft code a common way to ask:

- is this position/entity/chunk owned by the current region worker?
- can this read happen from already-loaded data without a sync load?
- can this mutation run now with the held write lock?
- should this work be deferred to the target owner mailbox?

This helper is used across block updates, redstone, fluids, pistons, rails,
entity movement, teleport paths, spawning, portals, item/vehicle movement,
chunk reads, and structure or POI access.

The rule is simple: region workers should not silently create global barriers.
They either use already-owned state, defer to the owning region, or fail closed.

## Mailboxes

Every active region owner has bounded mailboxes. Work is categorized by
`RegionTaskClass`:

- `CRITICAL_SYSTEM`
- `PLAYER_ACTION`
- `OWNER_HANDOFF`
- `CHUNK_IO_LOAD`
- `CHUNK_IO_SAVE`
- `PLUGIN`
- `TRACKER_BROADCAST`
- `EXPLOSION_PHYSICS`

Different task classes have different overflow behavior. Plugin work can fail
fast with rejection. Critical system work remains non-dropping but emits
pressure signals. Chunk IO and chunk executor work can be deferred, downgraded,
backpressured, or moved through overflow/emergency paths.

This keeps a local plugin or hostile-load flood from growing unbounded queues
or hiding the reason a region is falling behind.

## Tick Budgets

`multithreading.region-tick-budget-ms` is a cooperative auxiliary-work budget,
not a hard cutoff for the core Minecraft tick. Core phases such as entity tick,
chunk tick, player tick, and block entity tick must not be abandoned halfway
through simply because the region is overloaded.

Budgeting is used for deferrable or repeatable work:

- internal region tasks
- block and fluid scheduled tick drain
- block events
- tracker/broadcast work
- deferred TNT/explosion work
- selected chunk IO follow-up work

This matters under overload. If the core tick is interrupted, mobs and players
can starve while TPS counters still look healthy. The current design keeps core
gameplay phases coherent and moves optional pressure into deferred queues.

## Overload Classes

Each region has an overload controller. It watches EWMA MSPT, schedule lag,
mailbox pressure, and chunk IO pressure.

The region can be:

- `NORMAL`: regular scheduler lane
- `DEGRADED`: capped degraded lane and lower chunk IO/worker admission
- `QUARANTINED`: extreme overload marker for diagnostics and future policy

Degradation is intentionally local. A high-MSPT End region should not make an
unrelated Overworld region tick at one TPS when there are normal workers
available.

## Chunk IO And Generation QoS

Chunk load, generation, save, and internal worker tasks can become hidden global
pressure points. The current branch adds region-aware limits for:

- ticketed async chunk load/generation requests
- internal chunk worker tasks
- per-region executor backlog
- deferred retries
- overflow tasks
- emergency backlog admission
- autosave work

Normal and degraded regions use different caps. Degraded regions can have chunk
work lowered to LOW priority so unrelated healthy regions do not lose all
worker time.

## Plugin Execution

Plugins without `folia-supported: true` can be run through the synchronous
compatibility path when
`multithreading.run-unsupported-plugins-in-sync: true`.

That compatibility path improves survival for traditional Bukkit plugins, but
it is not a license to touch arbitrary world state from any thread. World,
entity, and chunk mutations still need to reach the owning region. The server
has specific guards for common plugin paths such as synchronous teleports and
disconnect handling, but plugin authors should use Paper's region scheduler and
entity scheduler directly.

## Diagnostics

Use `/region` while testing live servers:

- `/region top` shows the top active regions by MSPT, schedule lag, mailbox
  pressure, and chunk IO pressure.
- `/region inspect <world> <regionX> <regionZ>` shows full counters for one
  region.
- `/region ownership` shows global ownership fallback and handoff counters.

TPS alone is not enough for this engine. A server can show 20 TPS while one
local region is overloaded, or a global plugin wait can freeze the main thread
while region workers are idle. Always compare TPS, local region MSPT, schedule
lag, mailbox pressure, and live gameplay behavior.
