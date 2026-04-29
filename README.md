# Astatine

Astatine is a 1.21.11 [Purpur](https://github.com/PurpurMC/Purpur)
fork derived from ShreddedPaper and focused on vertical scaling: one Minecraft
server process can tick different loaded regions independently while preserving
explicit ownership for world, chunk, entity, and plugin mutations.

The working remote is:

```bash
https://github.com/jungwuk-ryu/Astatine.git
```

## Astatine Features

### Regionized Execution

- Independent deadline-based region ticking, with normal and degraded worker
  lanes so one overloaded area does not automatically stall unrelated regions.
- Dynamic region ownership over fixed region cells. Neighboring cells are
  merged, split, or serialized when required to keep unsafe access from running
  concurrently.
- Exact-cell region lock APIs, region owner epochs, stale-owner retirement, and
  cross-owner handoff queues for block, fluid, redstone, piston, rail, portal,
  entity, player, spawn, POI, and chunk mutations.
- Async ownership guards in hot game paths. Region workers avoid hidden sync
  chunk loads and defer unsafe writes through `ShreddedPaperAccess` instead of
  taking global locks.
- Compatibility handling for plugin lifecycle, global scheduler work, teleports,
  disconnects, player login, vehicle movement, and cross-region command paths.
- Region-aware lag compensation for low local TPS/MSPT, including movement
  check suppression only when the player's own region is measurably behind.

### Workload Isolation And QoS

- Bounded per-region mailboxes for critical work, player actions, plugin tasks,
  tracker broadcasts, chunk IO, autosave work, owner handoffs, and
  explosion/physics work.
- Per-region chunk IO and chunk worker QoS, including normal/degraded admission
  caps, backlog limits, overflow reserves, backpressure, priority downgrade, and
  emergency counters.
- Budgeted auxiliary queues for broadcasts, pending TNT, block events, and
  tick-phase cursors while keeping core entity/chunk/player/block-entity ticking
  coherent.
- Region-local autosave isolation and stale-owner retirement so merges, splits,
  and disconnected owners do not keep unsafe work alive indefinitely.
- Hostile-load validation tooling, MCC chaos/runtime gates, RCON anchors, async
  ownership scanner support, and region load-test plugin probes.

### Network And Runtime Performance

- Parallel network flush queue and tracking queue processing behind
  `shreddedpaper.yml` optimization flags, with lazy execution when a connection
  is not currently flushing.
- `NetworkFlushDiagnostics` accounting for direct vs queued flush decisions, so
  future network changes can verify that parallel flushing stays observable.
- Opt-in Netty `io_uring` transport for Linux TCP listeners. It is disabled by
  default, only activates when native transport is enabled and available, keeps
  KQueue priority on macOS/BSD, and leaves Unix domain sockets on Epoll domain
  channels.
- Netty aligned on the 4.2 patch line, `velocity-native` pinned to a release,
  `zstd-jni` updated for region compression, and `jctools-core` updated for
  bounded region mailboxes.
- Java 25 Compact Object Headers benchmark profile tooling for controlled
  baseline-vs-compact runs without changing production defaults.

### DivineMC, C2ME, And Lithium Optimizations

- DivineMC async pathfinding bridge and async entity tracker configuration keys,
  with ownership-safe behavior while independent region ticking is active.
- DivineMC virtual-thread integration for Bukkit async scheduler and `MCUtil`
  async executor paths, plus documented configuration keys for chat,
  tab-complete, command builder, and server text filtering.
- DivineMC DAB support, player near-chunk range tuning, projectile chunk-load
  limits, and general optimization toggles such as orb clumping, hopper
  throttling, suffocation optimization, sleeping block entities, equipment
  tracking, and command-block parse result caching.
- DivineMC linear region format support, including `LINEAR` and `B_LINEAR`,
  compression level controls, IO thread counts, flush delay, and virtual-thread
  IO mode.
- C2ME chunk optimizations, including pending chunk NBT/data cache limits,
  C2ME-style chunk worker selection, aquifer and beardifier optimizations, End
  biome caching, density-function compiler, structure layout optimizer, and
  shuffled template-pool deduplication.
- Lithium-derived combined heightmap updates, compact bit storage, and reduced
  chunk-load/lookup overhead.
- Chunk packet caching, threaded chunk change broadcasting, and region-aware TPS
  bar/network ping metrics for live operator feedback.

See [HOW_IT_WORKS.md](HOW_IT_WORKS.md) for the architecture and
[ASTATINE_YAML.md](ASTATINE_YAML.md) for configuration.

## Current Stability Expectations

The branch is intended for pre-PR validation against 1.21.11, not as a generic
drop-in replacement for every Paper plugin stack. The server is designed to
fail closed when an access path would violate region ownership. Unsupported
plugins can be run through the synchronous compatibility path, but plugins that
move entities, teleport players, load chunks, or mutate blocks should still use
Paper's region-aware APIs.

Important residual risks to validate on every release candidate:

- plugin teleports and disconnect handling under live player churn
- high-load End/Nether region behavior with unrelated Overworld regions active
- chunk generation throughput under QoS limits
- async ownership scanner regressions after upstream or DivineMC merges
- watchdog behavior when the main thread is blocked by plugin sync APIs

## Building

Requirements:

- Git with a configured user name and email.
- GNU `diff` on macOS. Install with `brew install diffutils` if
  `diff --version` reports Apple diff.
- JDK 25. This branch sets the Gradle Java toolchain and compiler release to 25
  and enables preview features for compile and test tasks.

Useful commands:

```bash
./gradlew applyAllPatches --no-configuration-cache
./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache --stacktrace
```

The runnable jar is written to:

```text
shreddedpaper-server/build/libs/astatine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar
```

## Release Validation

At minimum, run these before cutting a PR or copying a jar into a live
`worldgen` server:

```bash
./gradlew applyAllPatches --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache --stacktrace
./gradlew :shreddedpaper-server:test --no-configuration-cache --stacktrace
```

When the local-only validation tooling is present in your checkout, also run
the async ownership scanner and runtime gates under an isolated server root:

```bash
node tools/async-audit/scan-async-ownership.mjs --fail-on-critical
node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen
```

For hostile-load testing, build the region load-test plugin and run the
benchmark or MCC chaos harness from `tools/` against the candidate jar.

### Java 25 Network Profiles

Java 25 Compact Object Headers are supported as an opt-in benchmark/runtime
profile, not as a default server flag. Generate a reproducible command line and
metadata before comparing throughput, latency tails, heap, RSS, or GC behavior:

```bash
node tools/runtime/java25-network-profile.mjs \
  --profile compact-object-headers \
  --heap 8G \
  --jar shreddedpaper-server/build/libs/astatine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar \
  --scenario boundary-torture \
  --repeat 3 \
  --verify \
  --print-command \
  --metadata run/java25-network-profile.json
```

Use `--profile baseline` with the same heap, GC, scenario, and repeat count as
the control run. The compact profile intentionally uses
`-XX:+UseCompactObjectHeaders` without changing production defaults.

## Runtime Operations

Primary operator commands:

- `/region` or `/region top` shows the worst active independent regions by
  MSPT, schedule lag, mailbox pressure, and chunk IO pressure.
- `/region inspect <world> <regionX> <regionZ>` shows full counters for one
  region, including mailbox, chunk request, executor, backlog, overflow,
  emergency, and waiter pressure.
- `/region dump` prints all active region snapshots.
- `/region ownership` prints async ownership fallback and handoff counters.

The command permission is `astatine.command.region`, with
`shreddedpaper.command.region` kept as a legacy alias. It defaults to operators.

## Plugin Development

Plugins should use Paper's region scheduler, entity scheduler, and
`teleportAsync` APIs. A task scheduled on the global scheduler is not
automatically safe for world access.

If a plugin is Folia-compatible, declare `folia-supported: true` and avoid
hard-coded checks for `io.papermc.paper.threadedregions.RegionizedServer`.
Prefer checking for Paper's region scheduler API:

```java
try {
    Bukkit.class.getMethod("getRegionScheduler");
    return true;
} catch (NoSuchMethodException ex) {
    return false;
}
```

See [DEVELOPING_A_MULTITHREAD_PLUGIN.md](DEVELOPING_A_MULTITHREAD_PLUGIN.md)
for concrete examples.

## API Dependency

For local plugin development, publish the API to your local Maven repository:

```bash
./gradlew publishToMavenLocal
```

Then depend on:

```kotlin
dependencies {
    compileOnly("io.astatine:shreddedpaper-api:1.21.11-R0.1-SNAPSHOT")
}
```

## Related Documents

- [HOW_IT_WORKS.md](HOW_IT_WORKS.md): current architecture.
- [ASTATINE_YAML.md](ASTATINE_YAML.md): configuration reference.
- [DEVELOPING_A_MULTITHREAD_PLUGIN.md](DEVELOPING_A_MULTITHREAD_PLUGIN.md):
  plugin compatibility guide.
- [REGIONIZED_ENGINE_MILESTONES.md](REGIONIZED_ENGINE_MILESTONES.md):
  historical implementation and release-readiness ledger.
- [ASYNC_OWNERSHIP_AUDIT_2026-04-27.md](ASYNC_OWNERSHIP_AUDIT_2026-04-27.md):
  ownership audit notes.
- [BENCHMARK_PLAN_2026-04-26.md](BENCHMARK_PLAN_2026-04-26.md) and
  [BENCHMARK_RESULTS_2026-04-27.md](BENCHMARK_RESULTS_2026-04-27.md):
  historical benchmark plan and first comparative results.

## Licensing

All code is licensed under [GPLv3](LICENSE.txt).

## Acknowledgements

Astatine is derived from ShreddedPaper, Purpur, Paper, and the wider Minecraft
server performance ecosystem. It uses PaperMC's paperweight framework:
<https://github.com/PaperMC/paperweight>.
