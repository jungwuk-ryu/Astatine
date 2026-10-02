# Tracker and box ownership validation

The normal tracker previously scanned candidate players, then copied `seenBy`
and looked each connection up in the candidate set before checking visibility.
It repeated this work for every entity on every tick. The new pass checks both
existing and prospective viewers directly in candidate order. Distance, vertical
range, sent chunks, broadcast eligibility and Bukkit visibility remain live.
No visibility result is cached and no tick frequency or tracking limit is reduced.

Changes to candidate or viewer membership retain the original snapshot cleanup.
This matters when a pairing callback changes an earlier viewer's eligibility,
when an external tracker update adds a viewer outside the candidate list,
or when a player leaves the candidate list during the pass. The bounded tracker
selection path keeps its existing distance, permission and entity-ID ordering.

## Build and correctness

Run patch application before compilation, and finish builds before timing:

```bash
./gradlew applyAllPatches --no-configuration-cache
./gradlew :shreddedpaper-server:test --no-configuration-cache
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache
```

`TrackerVisibilityTest` runs the actual `ChunkMap.TrackedEntity` implementation
against controlled dependencies. It covers immediate visibility and broadcast
changes, movement, view distance, vertical range, sent chunks, candidate
replacement, clearing, callback invalidation, mutations during traversal,
bounded selection and randomized comparisons with an independent full scan.

## Detached tracker benchmark

Build the optional plugin after starting an isolated server once to populate
its libraries:

```bash
node tools/benchmark/tracker/build.mjs /absolute/path/to/isolated-server
```

Copy `run/tracker-plugin/tracker-benchmark.jar` into that server's `plugins`
directory. On the empty isolated server, run these commands individually,
waiting for `TRACKER_BENCH_DONE` after each:

```text
trackerbench 20 0
trackerbench 40 0
trackerbench 80 0
trackerbench 80 1
```

The plugin calls the installed server's real tracker. It constructs real
players, connections and chunk loaders without adding them to the world or
opening sockets. Each detached loader uses applied send distance 2 and marks
the same chunk as sent; players occupy varied positions within that chunk. Each
tracker begins with the correct viewer set. The hidden case creates one
visibility override per viewer. Setup and full membership validation occur
outside timing. It runs 20 warmup samples and nine measured samples, publishes
viewer counts through a volatile sink, and records CPU time, wall time and
allocation per entity tracker tick. Settings are restored after completion.

Use the same plugin JAR on baseline and candidate server JARs in separate,
sequential JVMs. Results describe the stable tracker path, not complete server
ticks, packet transport, maximum player capacity or frame rates.

## Socket acceptance and timing

Install the pinned local client dependency with `npm install` in this directory.
Prepare isolated flat-world servers with identical generated configs/worlds:

- Java 25, `--enable-preview -Xms1G -Xmx3G -XX:ActiveProcessorCount=2`.
- Listen only on `127.0.0.1:25685`, offline mode, creative mode, flight allowed.
- RCON on `127.0.0.1:25686`, password `local-tracker-benchmark` (test only).
- View and simulation distance 2, compression disabled, two region threads.
- Tracker limit 500, full-update frequency 20, vanish API enabled.
- No other online clients. Remove the detached benchmark plugin for socket runs.

Once the selected server is ready:

```bash
node tools/benchmark/tracker/clients.mjs 80 60 /absolute/path/to/stationary.json
node tools/benchmark/tracker/clients.mjs 80 60 /absolute/path/to/moving.json moving
node tools/benchmark/tracker/rcon.mjs stop
```

The harness waits 90 seconds after connecting all clients, then records region
and main-thread MSPT once per second. Every client must receive every other
player's spawn packet and retain that visibility throughout the measured run.
It fails on incomplete population, disconnects or missing visible players.
Moving clients send positions along a small circle every 100 ms; stationary
clients acknowledge positions once per second. Team collision is disabled.
On Linux, when the output JSON is in the server directory, the harness also
records CPU consumed by all `AstatineRegion*` threads during the sample interval
from `/proc`. This excludes time when those threads were descheduled by the
host; it still includes any cache or memory contention.

Repeat in alternating baseline/candidate order with no simultaneous compiler
or benchmark. Region MSPT values are overlapping five-second moving averages;
percentiles of those samples are not individual-tick p99. Shared-host results
must be reported as observations for this workload.

## Box ownership benchmark

The same plugin also measures the installed `ShreddedPaper.isSync(level, box)`
method on a real region worker. Run on an empty isolated server:

```text
ownershipbench 20
ownershipbench 40
ownershipbench 80
```

Each case checks a batch of varied, preconstructed entity-sized boxes inside
the current owner. It performs one million queries per sample, 20 warmup samples
and nine measured samples, verifies every result and publishes a volatile sink.
CPU time and allocated bytes come from the executing JVM thread. This isolates
the ownership query; it excludes box construction and the rest of entity ticks.
It is not a claim about that many connected players. Repeat using the same plugin
in sequential baseline and candidate JVMs. Results are written to
`plugins/TrackerBenchmark/ownership-<count>.json`.

The optimized path checks every packed region key, preserves explicit write-lock
and shutdown access, and creates scheduling arrays only when work must be
deferred. [ShreddedPaperBoxOwnershipTest](../../../shreddedpaper-server/src/test/java/io/multipaper/shreddedpaper/region/ShreddedPaperBoxOwnershipTest.java)
covers negative coordinates, inclusive edges, merged-owner holes, different
worlds, read-only locks and promotions, mixed owner/lock access, large queries,
inline execution, and scheduling the complete box plus an outlying entity.
