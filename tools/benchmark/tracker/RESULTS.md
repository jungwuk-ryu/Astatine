# Tracker validation with 20–80 players

Measured on 2026-09-12 against `d128785`. See [README.md](README.md) for
reproduction and [TrackerBenchmark.java](TrackerBenchmark.java) for the harness.

## Change and measured effect

The normal tracker scanned candidate players, copied the tracked connection set,
and looked every connection up in the candidate set again. It now validates
existing and prospective viewers in one pass. Changes to candidates or viewer
membership retain the snapshot cleanup. Visibility, distance, sent chunks,
vertical range and broadcast eligibility are still evaluated on every tick.
Bounded tracker selection keeps its original ordering and limits.

| Players | Hidden targets per viewer | Baseline CPU / tracker tick | Candidate CPU / tracker tick | CPU reduction |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 0 | 1.126 µs | 0.746 µs | 33.8% |
| 40 | 0 | 2.196 µs | 1.457 µs | 33.6% |
| 80 | 0 | 4.704 µs | 3.044 µs | 35.3% |
| 80 | 1 | 4.826 µs | 3.261 µs | 32.4% |

These are medians of nine samples following 20 warmup samples, approximately one
million candidate visits per sample. The installed server's actual tracker runs
with real detached players, connections and loaders, applied send distance 2,
varied positions within one chunk, and the correct initial viewer set. Every
round publishes viewer counts through a volatile sink. Full identity and
membership checks run outside timing. Each version runs in its own sequential
JVM, with no concurrent local build or benchmark.

This demonstrates a reduction in tracker processing cost with at most 80
candidate players. It does not establish total server speed, player capacity,
network throughput or native client FPS. The loop can run for any tracked entity,
not only players. Its benefit depends on nearby candidates and tracked entities.

## Runtime evidence and scope

A separate 80-client movement run used real protocol connections. All 80 clients
stayed connected and received all 79 other players throughout 90 measured
seconds after a 90-second warmup. A 60-second JFR recording contained 319 region
worker execution samples; 32 included the tracker tick and 21 included its
snapshot cleanup. The recorded stacks were limited to five frames, so these
counts are incomplete inclusive attribution, not precise CPU percentages.

That recording also identified repeated region-list allocation in box ownership
checks during entity fluid processing. This is investigated separately; the
tracker figures above compare only the tracker implementation change.

Earlier 200-client socket trials on this shared host did not consistently improve
MSPT. A subsequent 90-second-warmup stationary pair used 10.7% less normalized
region-worker CPU, but neither result predicts capacity or a universal TPS gain.
Both attempted 500-client trials lost clients before sampling and provide no
valid performance comparison. Preliminary detached runs with an uninitialized
loader distance are superseded by the correctly initialized results above.

## Correctness and artifacts

- Tracker change validation: 9,033 tests, zero failures/errors, 22 skipped.
- Nine new regressions cover stable/full ticks, live visibility and range,
  departures, external updates, callback invalidation, candidate mutation,
  bounded selection, and 300 randomized changes against an independent scan.
- `applyAllPatches` and `createMojmapPaperclipJar` passed. The normalized feature
  patch reconstructs the tested Minecraft source tree
  `9fea48e76e67ea3b6197ca3ee936dc084becb08c` in a separate Git index.
- Baseline Paperclip SHA-256:
  `05342f972f3c63fce801b953c82caa3b61296de5877e675f2a0a03a72a927ca2`.
- Measured tracker-only candidate Paperclip SHA-256:
  `3f224b39d9c8176a87c5c828f653f6b6bf78cdf28d1df28695388f2ff2c590d8`.

Raw data is retained under `run/tracker-validation-20260912/`, including
`smallmicro-baseline`, `smallmicro-candidate`, `baseline-profile-80`, input
fingerprints, logs, profiles and source-tree proof. Java 25.0.2 on aarch64;
`-Xms1G -Xmx3G -XX:ActiveProcessorCount=2`; two region threads. These are
isolated local runtime tests. Production was not deployed or restarted.
