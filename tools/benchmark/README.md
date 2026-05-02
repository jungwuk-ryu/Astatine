# ShreddedPaper Benchmark Automation

This directory contains the cross-platform benchmark runner for Minecraft
`1.21.11` server engines. The primary runner is Node-based and works on macOS
and Linux:

```bash
tools/benchmark/run-benchmarks.sh --suite smoke --engine our-shreddedpaper
```

The older `Run-Benchmarks.ps1` script is kept for the original fixed Windows
benchmark environment, but new benchmark work should use `run-benchmarks.sh`.

## What The Runner Does

For every selected engine, scenario, and repeat, it:

1. prepares a fresh isolated server directory under `run/benchmarks/runs`;
2. copies the selected `1.21.11` server jar and `RegionLoadTest` plugin;
3. writes `server.properties` with fixed seed, view distance, simulation
   distance, RCON, and offline-mode settings;
4. starts the server with the same Java and heap settings;
5. initializes gamerules and forceload anchors;
6. optionally starts MCC fake players and pins them at scenario anchors;
7. runs RCON-driven `RegionLoadTest` scenarios;
8. polls `tps`, `mspt`, and `/region top` where available;
9. parses RLT probe lag, chunk throughput, crash reports, and high-signal logs;
10. writes per-run artifacts, `benchmark-results.csv`,
   `benchmark-results.json`, and `benchmark-summary.md`.

The most important comparison metric is control-region p99 lag. Chunk
throughput is reported separately because a server can isolate hostile load well
while still being weak at chunk IO throughput.

For rigorous hotspot testing, use MCC anchors. Scenarios with `mccAnchors` place
fake players at their hot/control load points when `--use-mcc` is enabled, so
those chunks are held by real player presence instead of only plugin-scheduled
work. Chunk generation/load scenarios only anchor the control side, because
pre-placing a player at the generation target would warm the exact chunks being
measured.

## Quick Smoke

Build the current jar and plugin first:

```bash
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache
./gradlew -p tools/region-load-test-plugin build --no-configuration-cache
```

Run only the current fork:

```bash
tools/benchmark/run-benchmarks.sh \
  --suite smoke \
  --engine our-shreddedpaper \
  --skip-build
```

The summary lands under:

```text
run/benchmarks/results/<timestamp>/benchmark-summary.md
```

## Full 1.21.11 Comparison

The example config defines these `1.21.11` engines:

- `our-shreddedpaper`
- `paper`
- `folia`
- `purpur`
- `divinemc`

Official comparison jars are only downloaded when explicitly allowed:

```bash
tools/benchmark/run-benchmarks.sh \
  --suite standard \
  --allow-downloads \
  --build-current \
  --repeat 5
```

For a heavier one-hot comparison with fake players and enough samples for
trimmed means:

```bash
tools/benchmark/run-benchmarks.sh \
  --suite stressed \
  --use-mcc \
  --allow-downloads \
  --scenario one-hot-region \
  --repeat 3
```

Use filters when iterating:

```bash
tools/benchmark/run-benchmarks.sh \
  --suite smoke \
  --allow-downloads \
  --engine our-shreddedpaper,folia,paper \
  --scenario one-hot-region,chunk-generation
```

## Suites

`smoke` is short and meant to catch crashes or obvious regressions.

`stressed` is the practical comparison set for hostile hotspot claims. It uses
larger probe sample counts, longer durations, 5-second metric polling, and MCC
fake-player anchors.

`standard` is the honest comparison set. It repeats each scenario 5 times by
default and includes:

- `baseline`
- `spawn-pile`
- `one-hot-region`
- `multi-hotspot`
- `chunk-generation`
- `chunk-load`
- `boundary-torture`
- `tnt-isolation`

## Interpreting Results

Read the tables in this order:

1. `Survival` and `High signal`: crashes, watchdogs, ownership failures, and
   server log exceptions invalidate performance claims.
2. `Control p99`: the realistic isolation score. Lower is better.
3. `Load p99`: how badly the attacked region itself fell behind.
4. `Tick MSPT min`, `Tick MSPT trim5`, `Tick MSPT p95`, `Tick MSPT p99`, and
   `Tick MSPT max`: ticking-unit quality without hiding tails. `trim5` excludes
   the lowest five and highest five samples in a single run, and is only shown
   when at least 10 samples remain after trimming.
5. `Chunk/sec`: chunk load/generation throughput. This is the current area
   where our fork must prove it can catch up.
6. `Server MSPT min/p99/max` and `RSS max`: sanity checks for global pauses and
   memory growth.

## Custom Config

Copy `benchmark-config.example.json` and edit:

```bash
cp tools/benchmark/benchmark-config.example.json /tmp/bench-1.21.11.json
tools/benchmark/run-benchmarks.sh --config /tmp/bench-1.21.11.json --suite standard
```

Useful edits:

- set `java` to a pinned JDK path;
- set `heap` to match the machine class;
- add or remove engines;
- increase `standard.variables.probeSamples` and scenario durations for soak;
- set `mcc.enabled` or pass `--use-mcc` for fake-player anchored scenarios;
- change `server.viewDistance` and `server.simulationDistance`.

## Safety Notes

The runner deletes and recreates only server directories under the configured
benchmark root. The default root is `run/benchmarks`.

Do not point `--root` at a real server directory. This runner is intentionally
destructive inside its benchmark root so each scenario starts clean.
