# Box ownership checks and low-player validation

Measured on 2026-09-12 against `d128785`. Reproduction is documented in
[README.md](README.md). Tracker-only results are in [RESULTS.md](RESULTS.md).

## Bottleneck and change

An 80-client movement profile showed repeated allocation through
`Entity.shreddedpaper$deferFluidPushingIfNeeded` → `ShreddedPaper.isSync` →
`regionsForBox`. Every query constructed a list, backing array, region positions
and a returned array before testing ownership. This cost also occurs for other
entities, independently of total connected-player count.

The query now checks packed cell keys directly and returns at the first unowned
cell. The common current-owner path allocates nothing. Cells outside that owner
still require explicit write access; read-only isolation locks do not suffice.
Shutdown access is preserved. Scheduling arrays are constructed only when work
must be deferred, and filled directly without a temporary list.

## Installed-code benchmark

| Boxes in batch | Baseline CPU / query | Candidate CPU / query | CPU reduction | Allocated bytes / query |
| ---: | ---: | ---: | ---: | ---: |
| 20 | 44.155 ns | 30.065 ns | 31.9% | 96 → 0 |
| 40 | 44.438 ns | 29.880 ns | 32.8% | 96 → 0 |
| 80 | 43.786 ns | 29.549 ns | 32.5% | 96 → 0 |

These are medians of nine samples after 20 warmup samples, one million queries
per sample. The installed implementation runs on a real region worker. Inputs
are varied, preconstructed entity-sized boxes inside its owner. Every result is
checked and each round publishes a volatile sink. ThreadMXBean records actual
thread CPU time and allocation. Baseline and candidate use sequential JVMs and
the same benchmark plugin; no other local benchmark or compiler runs alongside.

The batch size describes query inputs, not connected players. These results
isolate the ownership check, excluding construction of the input bounding boxes,
other entity processing, packet work and plugins. They show a repeatable local
CPU/allocation reduction, not a corresponding percentage gain in total MSPT.
JFR allocation weights are not used to claim an allocation rate: the recording's
first sample has an outsized weight that prevents a reliable rate estimate.

## Validation

- Full server suite: 9,043 tests, zero failures/errors, 22 skipped.
- Ten new regressions cover inclusive boundaries, negative coordinates, every
  cell in merged owners (including holes), world separation, explicit writes,
  read-only locks and scoped promotion, mixed owner/lock access, huge unowned
  queries, shutdown access, inline execution and complete scheduling targets.
- Server compilation and `createMojmapPaperclipJar` passed.
- The measured combined candidate Paperclip SHA-256 is
  `a38196bedb5a5147c695942751c91ddca25b818b9d03af82ff1251e17e9761ac`.
  Its executed `ShreddedPaper.class` SHA-256 is
  `565f2ac60f70d08f79d487a34d2c597797b0157d859ebf9a88087b4e32e74a67`.
  Its tracker class matches the separately measured tracker-only candidate.

Raw plugin results, input fingerprints, full-suite totals, logs and runtime class
fingerprints are in `run/tracker-validation-20260912/`. All runtime trials use
isolated local servers. No production deployment or restart is included.
