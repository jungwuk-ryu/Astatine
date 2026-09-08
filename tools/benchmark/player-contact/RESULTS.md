# Crowded-region performance validation

Measured on 2026-09-08 against baseline commit `fd358e5`, using this commit's
contact index and visibility changes. Reproduction instructions and the isolated
query benchmark are in [README.md](README.md).

## Changes and correctness boundaries

Every nearby player previously participated in every player's pickup/contact
query, despite ordinary `ServerPlayer.playerTouch` being empty. The section
index removes this redundant player-to-player scan; all other entity classes,
including custom player subclasses, remain eligible. Actual contacts still use
the existing geometry, spectator filter, iteration order and platform hooks.
Physical collision rules, tracking limits and update frequency are unchanged.

`CraftPlayer.canSee` now resolves ordinary visibility before the self exception.
An empty override map requires no lookup; an otherwise visible target requires
no UUID equality check. Hidden defaults, explicit hide/show overrides and self
visibility retain the original truth table, reading current state on every call.
There is no cross-tick visibility cache.

The new contact index stores additional references for non-player entities in
each occupied chunk. It is allocated lazily and released when empty. Queries
with many non-player contacts must still inspect those contacts.

## Final results

The target was at least a 90% reduction in contact-query time at 500 and 1,000
players. All four contact cases exceeded that target, including the mixed
population with 16 items whose contact results must remain unchanged.

| Players | Items | Original query (µs) | Indexed query (µs) | Time reduction | Bytes/query, original → indexed |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 500 | 0 | 13.847 | 0.073 | 99.47% | 6,760 → 24 |
| 1,000 | 0 | 54.108 | 0.008 | 99.99% | 15,024 → 24 |
| 500 | 16 | 19.511 | 0.253 | 98.70% | 6,760 → 264 |
| 1,000 | 16 | 60.701 | 0.273 | 99.55% | 15,024 → 264 |

Allocation fell by 96.09–99.84%. The sub-microsecond empty-path times are
sensitive to JIT compilation and sink overhead; they are not a prediction of
whole-player tick time. The retained-item cases also demonstrate that skipping
no-op players does not depend on returning an empty list.

| Players | Overrides per viewer | Original visibility (ns/check) | Updated visibility (ns/check) | Time reduction |
| ---: | ---: | ---: | ---: | ---: |
| 500 | 0 | 19.884 | 11.481 | 42.26% |
| 500 | 1 | 25.440 | 22.477 | 11.65% |
| 1,000 | 0 | 30.763 | 17.385 | 43.49% |
| 1,000 | 1 | 41.164 | 34.289 | 16.70% |

The final socket test used **200 connected clients** at the same location.
Both variants completed all 60 samples with zero connection faults.

| Version | Median reported region MSPT (5s window) | Observed window range |
| --- | ---: | ---: |
| Baseline `fd358e5` | 13.035 ms | 12.31–19.78 ms |
| Final candidate | 10.025 ms | 9.51–14.00 ms |

This is a **23.09% lower median** in the observed local workload. Both servers
ran Java 25.0.2 on aarch64 with `-Xms1G -Xmx3G -XX:ActiveProcessorCount=2`,
two configured region threads, no plugins, view/simulation distance 2, tracker
limit 500 and full-update frequency 20. The vanish API remained enabled.
The creative clients stood at `(8.5, -60, 8.5)` in a flat world with spawning
disabled and team collisions disabled. Server commands verified all 200 clients
within one block and one attached region. Warmup lasted 45 seconds before 60
samples spaced one second apart. Seven server configuration files matched after
normalization. No compilation ran during either final trial.

Both final socket logs contained zero errors, watchdog reports or timeouts.
Ownership fallback, handoff rejection and prefetch failure counters were zero.

## Measurement boundaries

The query benchmark uses real, normally constructed entities in a detached
section inside a local server JVM. It does not connect 500 or 1,000 clients.
It checks result membership/order, warms both paths, alternates measurement
order and records nine samples. Contact samples contain 2,000 queries, each
publishing its returned list to a volatile sink; allocation comes from the
current thread's JVM counter. Visibility samples use all player pairs and
500,000 or 1,000,000 checks. Reflection is outside the timed loops.

The host is shared with unrelated workloads. The socket test is an illustrative
before/after observation, not a dedicated-host capacity guarantee. Its region
MSPT samples are overlapping five-second moving averages, not individual tick
latencies or p99 tick measurements. Global main-thread `/mspt` is not used as a
substitute for region processing time.

Earlier 500-client connection attempts timed out and are excluded. A 200-client
run overlapping compilation and a failed gated run are also excluded. Initial
visibility code and a preliminary benchmark that allowed empty-query escape
analysis were revised; those measurements are not final results.

## Validation

- `applyAllPatches --no-configuration-cache` passed. The normalized feature patch
  also reproduced the tested Minecraft source tree exactly in a temporary index.
- Full server tests: 9,024 discovered, zero failures/errors, 22 skipped.
- Ten focused regressions cover the empty vanilla contact method, excluded
  player scans, contact membership/order, live geometry/spectators, removals,
  section movement/merging, randomized membership and visibility truth tables.
- `createMojmapPaperclipJar --no-configuration-cache` and the optional benchmark
  plugin build passed.

Raw logs, JSON samples, config comparisons, class fingerprints and the loopback
client harness are retained in the ignored `run/player-density/` directory.
The benchmark source and this report are committed; test worlds, plugins,
client dependencies and generated JARs are not committed. All runtime tests
use disposable local servers; no production deployment is part of this change.
