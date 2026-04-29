# Scanner-Blind Runtime Validation Gaps

Static ownership scanners do not prove these paths are safe at runtime:

- Player packet paths: use item/place block/entity interaction and fast movement clipping.
- Spawn placement paths: `SpawnPlacementTypes` and natural spawning predicates.
- Live lighting: Starlight updates while regions mutate blocks independently.
- Player lifecycle: reconnect, portal traversal, and respawn handoff.
- Cross-boundary world behavior: pathfinding, redstone, fluids, and stale chunk sends.
- Shutdown and cleanup: watchdog stalls and tagged validation entities.

Current validation coverage in this worktree:

- `tools/region-load-test-plugin`: adds RLT probes for pathfinding evidence, redstone/fluid boundary pressure, live lighting, player lifecycle checks, watchdog stalls, and tagged entity cleanup.
- `tools/mcc-chaos/agent-validation-mcc.mjs`: drives the assigned-port validation pass under `run/agent-validation-mcc`.
- `tools/runtime/Invoke-WorldgenSmoke.ps1`: remains the lightweight RCON smoke for existing worldgen servers.

Known fixture gap:

- Natural spawning is enabled and exposed in the MCC/RCON fixture, but not asserted by mob count because vanilla spawn success is intentionally probabilistic and depends on online player geometry and caps.
