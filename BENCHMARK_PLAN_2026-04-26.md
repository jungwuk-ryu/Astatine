# Astatine Fork Benchmark Plan - 2026-04-26

Historical note: this plan describes the first Windows benchmark pass that
preceded the current Astatine 1.21.11 integration branch. The scenario design is
still useful, but paths, jar names, and remote repositories should be updated to
the active validation machine before a new run.

## Goal

Compare Astatine against currently available 1.21.11-capable server builds:

- Current fork build from this repository.
- Original MultiPaper/ShreddedPaper `ver/1.21.11`.
- Folia 1.21.11.
- DivineMC 1.21.11.
- Paper 1.21.11.
- Purpur 1.21.11.

The benchmark must measure global server health and local region/chunk impact. The key question is not only "how high is average TPS", but whether one overloaded area damages unrelated areas.

## Fixed Environment

- Host: local Windows machine.
- Java: `C:\Program Files\Java\jdk-25.0.2\bin\java.exe`.
- Runtime flags: fixed G1 profile based on `D:\worldgen\start.bat`, with `--enable-preview` for this fork.
- Server root: `D:\server-benchmarks`.
- One isolated server directory per engine.
- Same seed: `astatine-benchmark-20260426`.
- Same properties:
  - `online-mode=false`
  - `view-distance=12`
  - `simulation-distance=7`
  - `spawn-protection=0`
  - `generate-structures=false`
  - `max-tick-time=-1`
  - RCON enabled with a unique port per engine.
- Plugin set: `RegionLoadTest` only.
- No player client is required for the benchmark run.

## Downloads

| Engine | Source | Version/Build |
| --- | --- | --- |
| Astatine | local build artifact | `astatine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar` |
| Original ShreddedPaper | GitHub branch build | `MultiPaper/ShreddedPaper ver/1.21.11` |
| Folia | PaperMC downloads API | `1.21.11 build 14` |
| Paper | PaperMC downloads API | `1.21.11 build 130` |
| Purpur | Purpur downloads API | `1.21.11 build 2568` |
| DivineMC | official `files.bxteam.org` jar | `1.21.11 build 24` |

## Common Metrics

These metrics are valid for every engine:

- Server 5s TPS from `/tps`.
- Server 5s MSPT average/min/max from `/mspt`, when the command is available.
- Chunk-local tick cadence lag from `RegionLoadTest probe`:
  - average lag in ms
  - p95 lag in ms
  - max lag in ms
- Chunk generation elapsed time and derived chunks/sec from `RegionLoadTest scenario gen`.
- Chunk load-only elapsed time and derived chunks/sec from `RegionLoadTest scenario load`.
- Benchmark errors:
  - server crash
  - command timeout
  - plugin exception
  - RCON disconnect
  - watchdog stop

## Current Fork Extra Metrics

These are only available on the current fork and must be kept separate from cross-engine comparison:

- `/region` per-region EWMA MSPT.
- Region load class: `NORMAL`, `DEGRADED`, `QUARANTINED`.
- Region mailbox pressure, deferred work, chunk IO debt, backpressure counters.

## Scenarios

### Baseline

Purpose: measure idle overhead and plugin probe overhead.

Commands:

- `/rlt at world 0 96 0 probe 600 1`
- Poll `/tps` and `/mspt` every 10 seconds.

Duration: 30 seconds after probe start.

### Villager Entity Load

Purpose: measure entity AI/brain pressure.

Preparation:

- Create a stone platform around the load chunk.
- Keep a far control probe in another chunk group.

Commands:

- `/rlt at world 0 96 0 probe 600 1`
- `/rlt at world 8192 96 0 probe 600 1`
- `/rlt at world 0 96 0 villagers 300 32 600`
- Poll `/tps` and `/mspt` every 10 seconds.

Expected reading:

- Load probe shows direct impact.
- Control probe shows cross-region/cross-area impact.

### Pathfinding Entity Load

Purpose: measure pathfinding and navigation pressure.

Commands:

- `/rlt at world 0 96 0 probe 600 1`
- `/rlt at world 8192 96 0 probe 600 1`
- `/rlt at world 0 96 0 path 300 48 600`
- Poll `/tps` and `/mspt` every 10 seconds.

### Chunk Generation

Purpose: measure worldgen throughput and whether worldgen harms a far probe.

Commands:

- `/rlt at world 8192 96 0 probe 1200 1`
- `/rlt at world 200000 96 0 scenario gen 4 128 6 false 600 1`
- Poll `/tps` and `/mspt` every 10 seconds.

Derived metric:

- chunks/sec = total successful chunks / elapsed seconds.

### Chunk Load-Only

Purpose: measure hot chunk IO path after generation.

Commands:

- Run the same area once with `scenario gen`.
- Run `/rlt at world 200000 96 0 scenario load 4 128 6 false 600 1`.

Derived metric:

- chunks/sec = total successful chunks / elapsed seconds.

### TNT Isolation Smoke

Purpose: measure whether one explosion-heavy area damages an unrelated probe.

Commands:

- `/rlt at world 0 96 0 probe 800 1`
- `/rlt at world 8192 96 0 probe 800 1`
- `/rlt at world 0 96 0 tntsingle 18 18 2 40 8`
- Poll `/tps` and `/mspt` every 10 seconds.

This is intentionally smaller than destructive manual TNT tests because all six engines need to complete in a single automated run.

## Result Files

The harness writes:

- `D:\server-benchmarks\results\benchmark-results.csv`
- `D:\server-benchmarks\results\benchmark-summary.md`
- `D:\server-benchmarks\results\<engine>\<scenario>\server.log`
- `D:\server-benchmarks\results\<engine>\<scenario>\rcon.log`
- `D:\server-benchmarks\results\<engine>\<scenario>\parsed.json`

## Interpretation Rules

- Compare common metrics across all engines.
- Do not treat this fork's `/region` counters as available on other engines.
- A server that lacks `/mspt` keeps TPS/probe/chunk metrics; the missing MSPT field is marked `n/a`.
- Probe lag is not exact MSPT. It is a chunk-local scheduler cadence metric. It is the common portable proxy for region/chunk-local responsiveness.
- Chunk generation benchmark is affected by disk cache and OS background load. The first full run should be treated as a primary run; repeated runs should use fresh directories or fresh coordinate ranges.
