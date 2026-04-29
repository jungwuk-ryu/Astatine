# Benchmark Results - 2026-04-27

Historical note: these results are the first comparative Windows benchmark
snapshot before the later async ownership, watchdog, plugin teleport, disconnect,
and region tick overhead fixes were merged. Treat the numbers as directional
baseline evidence, not the final 1.21.11 Astatine PR benchmark.

## Run Metadata

- Benchmark root: `D:\server-benchmarks`
- Result CSV: `D:\server-benchmarks\results\benchmark-results.csv`
- Result summary: `D:\server-benchmarks\results\benchmark-summary.md`
- Per-scenario raw logs: `D:\server-benchmarks\results\<engine>\<scenario>\`
- Java: `C:\Program Files\Java\jdk-25.0.2\bin\java.exe`
- Heap: `-Xms8G -Xmx8G`
- Plugin set: `RegionLoadTest` only
- Scenarios per engine: baseline, villagers, pathfinding, chunk-generation, chunk-load, tnt-isolation

## Important Caveats

- This is a single local Windows run. It is useful for directional comparison, not final statistical proof.
- `TPS avg/min` is parsed from the best common command each engine exposes. Paper reports a 1-minute TPS window rather than the same 5-second window used by some other engines, so Paper baseline TPS is more startup-sensitive.
- `Load probe` and `Control probe` are chunk-local scheduler cadence lag from `RegionLoadTest probe`; they are a portable proxy for local responsiveness, not exact engine-internal region MSPT.
- Negative probe lag values near zero are timer noise and should be read as zero.
- The run completed 36/36 scenarios with no recorded benchmark errors.

## Fixes Made Before Final Run

The benchmark exposed a real custom-engine correctness issue before final data collection:

- Region write-lock promotion now records write ownership even when the current thread already held the underlying read lock.
- Scheduled block/fluid ticks now execute only while the current region tick actually owns the cell and holds its write lock.
- The benchmark plugin now spawns villagers/zombies on the current chunk surface instead of relying on `/fill` platform setup.
- Benchmark initialization no longer uses `/fill`, avoiding command-side cross-region block mutation during setup.

## Entity Load

Villagers are the most useful hostile-load signal in this run. The custom fork held global TPS at 20 and kept the far control probe near zero lag, while original ShreddedPaper/Divine/Paper/Purpur all showed shared degradation in the control probe because they do not isolate the load as strongly.

| Engine | TPS avg | TPS min | MSPT avg | MSPT max | Load probe avg lag | Control probe avg lag |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| custom-shreddedpaper | 20.00 | 20.00 | 1.62 | 8.50 | 0.59 | -0.00 |
| original-shreddedpaper | 19.38 | 16.90 | 14.94 | 617.40 | 1.42 | 1.42 |
| folia | 19.76 | 19.13 | 4.83 | 17.02 | 1.09 | 1.08 |
| divinemc | 19.60 | 18.00 | 5.60 | 815.00 | 0.92 | 0.92 |
| paper | 19.80 | 19.80 | 10.04 | 145.00 | 0.84 | 0.84 |
| purpur | 19.52 | 17.60 | 10.84 | 258.30 | 1.09 | 1.08 |

## Chunk Generation

The custom fork preserved TPS and the far probe, but chunk generation throughput was lower than Paper/Folia. That is consistent with the custom fork's chunk IO/QoS isolation being conservative.

| Engine | TPS avg | MSPT max | Control probe avg lag | Chunks | Chunks/sec |
| --- | ---: | ---: | ---: | ---: | ---: |
| custom-shreddedpaper | 20.00 | 56.20 | 0.13 | 676 | 31.17 |
| original-shreddedpaper | 20.00 | 15.40 | -0.01 | 676 | 36.37 |
| folia | 19.96 | 0.72 | 0.21 | 676 | 40.67 |
| divinemc | 20.00 | 45.00 | -0.09 | 676 | 35.21 |
| paper | 20.00 | 43.70 | -0.08 | 676 | 40.18 |
| purpur | 20.00 | 50.00 | -0.09 | 676 | 36.98 |

## Chunk Load

Chunk load-only throughput is currently a weakness for the custom fork. It stayed stable, but throughput was materially below Paper/Purpur/Divine.

| Engine | TPS avg | MSPT max | Control probe avg lag | Chunks | Chunks/sec |
| --- | ---: | ---: | ---: | ---: | ---: |
| custom-shreddedpaper | 20.00 | 14.70 | 0.00 | 676 | 588.19 |
| original-shreddedpaper | 20.00 | 25.40 | -0.00 | 676 | 822.56 |
| folia | 20.00 | 0.48 | 0.00 | 676 | 804.72 |
| divinemc | 20.00 | 27.20 | -0.00 | 676 | 846.07 |
| paper | 20.00 | 24.00 | -0.01 | 676 | 1073.35 |
| purpur | 20.00 | 17.40 | -0.00 | 676 | 985.09 |

## TNT Smoke

This TNT scenario was intentionally small so every engine could finish. It is a smoke test, not the same as the original thousands-of-TNT failure case.

| Engine | TPS avg | TPS min | MSPT avg | MSPT max | Load probe avg lag | Control probe avg lag |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| custom-shreddedpaper | 20.00 | 19.90 | 9.44 | 18.10 | 0.00 | -0.00 |
| original-shreddedpaper | 20.00 | 20.00 | 1.80 | 127.40 | -0.00 | -0.00 |
| folia | 19.98 | 19.93 | 0.26 | 0.88 | 0.06 | 0.06 |
| divinemc | 20.00 | 20.00 | 1.12 | 135.20 | 0.00 | 0.00 |
| paper | 20.00 | 20.00 | 0.60 | 116.80 | 0.00 | -0.00 |
| purpur | 20.00 | 20.00 | 0.90 | 120.50 | -0.00 | 0.00 |

## Full Table

| Engine | Scenario | TPS avg | TPS min | MSPT avg | MSPT max | Load probe avg lag | Control probe avg lag | Chunk success | Chunk/sec |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| custom-shreddedpaper | baseline | 20.00 | 20.00 | 1.58 | 3.00 | 0.00 | n/a | 0 | n/a |
| custom-shreddedpaper | villagers | 20.00 | 20.00 | 1.62 | 8.50 | 0.59 | -0.00 | 0 | n/a |
| custom-shreddedpaper | pathfinding | 20.00 | 20.00 | 1.62 | 4.00 | 0.00 | 0.00 | 0 | n/a |
| custom-shreddedpaper | chunk-generation | 20.00 | 20.00 | 1.70 | 56.20 | n/a | 0.13 | 676 | 31.17 |
| custom-shreddedpaper | chunk-load | 20.00 | 20.00 | 1.74 | 14.70 | n/a | 0.00 | 676 | 588.19 |
| custom-shreddedpaper | tnt-isolation | 20.00 | 19.90 | 9.44 | 18.10 | 0.00 | -0.00 | 0 | n/a |
| original-shreddedpaper | baseline | 20.00 | 20.00 | 1.75 | 4.90 | 0.00 | n/a | 0 | n/a |
| original-shreddedpaper | villagers | 19.38 | 16.90 | 14.94 | 617.40 | 1.42 | 1.42 | 0 | n/a |
| original-shreddedpaper | pathfinding | 20.00 | 20.00 | 5.18 | 23.00 | -0.00 | 0.00 | 0 | n/a |
| original-shreddedpaper | chunk-generation | 20.00 | 20.00 | 1.91 | 15.40 | n/a | -0.01 | 676 | 36.37 |
| original-shreddedpaper | chunk-load | 20.00 | 20.00 | 2.08 | 25.40 | n/a | -0.00 | 676 | 822.56 |
| original-shreddedpaper | tnt-isolation | 20.00 | 20.00 | 1.80 | 127.40 | -0.00 | -0.00 | 0 | n/a |
| folia | baseline | 20.00 | 20.00 | 0.54 | 1.17 | -0.00 | n/a | 0 | n/a |
| folia | villagers | 19.76 | 19.13 | 4.83 | 17.02 | 1.09 | 1.08 | 0 | n/a |
| folia | pathfinding | 19.95 | 19.73 | 1.76 | 5.63 | 0.25 | 0.33 | 0 | n/a |
| folia | chunk-generation | 19.96 | 19.74 | 0.34 | 0.72 | n/a | 0.21 | 676 | 40.67 |
| folia | chunk-load | 20.00 | 20.00 | 0.29 | 0.48 | n/a | 0.00 | 676 | 804.72 |
| folia | tnt-isolation | 19.98 | 19.93 | 0.26 | 0.88 | 0.06 | 0.06 | 0 | n/a |
| divinemc | baseline | 20.00 | 20.00 | 0.80 | 2.50 | -0.01 | n/a | 0 | n/a |
| divinemc | villagers | 19.60 | 18.00 | 5.60 | 815.00 | 0.92 | 0.92 | 0 | n/a |
| divinemc | pathfinding | 20.00 | 20.00 | 2.92 | 9.40 | 0.00 | -0.00 | 0 | n/a |
| divinemc | chunk-generation | 20.00 | 20.00 | 1.07 | 45.00 | n/a | -0.09 | 676 | 35.21 |
| divinemc | chunk-load | 20.00 | 20.00 | 1.23 | 27.20 | n/a | -0.00 | 676 | 846.07 |
| divinemc | tnt-isolation | 20.00 | 20.00 | 1.12 | 135.20 | 0.00 | 0.00 | 0 | n/a |
| paper | baseline | 16.58 | 14.90 | 0.65 | 2.30 | 0.00 | n/a | 0 | n/a |
| paper | villagers | 19.80 | 19.80 | 10.04 | 145.00 | 0.84 | 0.84 | 0 | n/a |
| paper | pathfinding | 20.00 | 20.00 | 3.68 | 14.50 | 0.00 | 0.00 | 0 | n/a |
| paper | chunk-generation | 20.00 | 20.00 | 0.77 | 43.70 | n/a | -0.08 | 676 | 40.18 |
| paper | chunk-load | 20.00 | 20.00 | 0.76 | 24.00 | n/a | -0.01 | 676 | 1073.35 |
| paper | tnt-isolation | 20.00 | 20.00 | 0.60 | 116.80 | 0.00 | -0.00 | 0 | n/a |
| purpur | baseline | 20.00 | 20.00 | 0.73 | 1.90 | 0.00 | n/a | 0 | n/a |
| purpur | villagers | 19.52 | 17.60 | 10.84 | 258.30 | 1.09 | 1.08 | 0 | n/a |
| purpur | pathfinding | 20.00 | 20.00 | 3.96 | 20.70 | -0.00 | 0.00 | 0 | n/a |
| purpur | chunk-generation | 20.00 | 20.00 | 0.94 | 50.00 | n/a | -0.09 | 676 | 36.98 |
| purpur | chunk-load | 20.00 | 20.00 | 0.98 | 17.40 | n/a | -0.00 | 676 | 985.09 |
| purpur | tnt-isolation | 20.00 | 20.00 | 0.90 | 120.50 | -0.00 | 0.00 | 0 | n/a |

## Immediate Follow-Up Work

- Improve custom chunk load/generation throughput without removing QoS isolation.
- Add a larger TNT stress pass that matches the original thousands-of-TNT failure case.
- Add repeated runs and median/p95 aggregation for each engine/scenario.
- Consider a no-startup-skew TPS metric for Paper by extending warmup or relying primarily on MSPT/probe for Paper comparisons.
