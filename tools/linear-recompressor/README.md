# Linear v3 recompressor

Offline, bounded-memory verifier and recompressor for ShreddedPaper Linear
region files. A fixed worker pool can process multiple files concurrently;
each worker still handles one file and one bucket at a time. The source is
replaced only after the temporary file has been forced to disk, reopened, and
verified against the source's decompressed bucket digests.

## Build

```bash
./gradlew :linear-recompressor:installDist
tools/linear-recompressor/build/install/linear-recompressor/bin/linear-recompressor help
```

## Modes

```bash
# Read-only integrity scan
linear-recompressor verify /srv/minecraft/world

# Produce and verify level-9 temporary files, report exact sizes, then delete them
linear-recompressor estimate --level 9 /srv/minecraft/world

# Offline rewrite. Every loaded world's session.lock must be listed.
linear-recompressor apply --level 9 \
  --threads 6 \
  --label world-regions \
  --checkpoint /srv/minecraft/linear-level9.checkpoint.tsv \
  --launcher-state /srv/minecraft/.runtime/launcher-state.json \
  --lock-file /srv/minecraft/world/session.lock \
  --lock-file /srv/minecraft/world_nether/session.lock \
  --lock-file /srv/minecraft/world_the_end/session.lock \
  /srv/minecraft/world /srv/minecraft/world_nether /srv/minecraft/world_the_end
```

`apply` refuses to start without at least one held session lock. For isolated
copies and tests only, `--assume-offline` bypasses that requirement. It never
falls back from an atomic move. A crash can leave a
`*.linear.recompress-l<N>.tmp`; the next run verifies and reuses a valid one or
rebuilds an invalid one. A verified candidate that is equal to or larger than
the source is deleted without replacing the source. Both replacements and
unchanged "not smaller" decisions are stored in the append-only checkpoint,
which is forced after every completed file. The checkpoint records level,
size, modification time, file key, path, and outcome, so a changed file is
processed again while an unchanged file resumes safely. Existing six-field
checkpoints remain readable.

Legacy Linear versions 1 and 2 are read using the same layout accepted by the
deployed ShreddedPaper reader and are converted to bucketed version 3. Version
3 inputs are recompressed without changing their logical bucket contents.
Read-only `verify` reports stale v3 existence bitmaps; `apply` reconstructs the
bitmap from the validated bucket entries and verifies the repaired output.

Useful options are `--threads N` (default: 1), `--label TEXT`, `--max-files N`,
`--continue-on-error`, `--min-free-gib N` (default: 5),
`--min-age-seconds N`, `--progress-every N` (default: 100), and `--verbose`.
Progress output reports the planned file total, processed percentage, file
rate, ETA, applied/kept/checkpoint/deferred/error counts, and actual saved
MiB. Per-file success lines are disabled unless `--verbose` is supplied, so a
long batch cannot block on terminal output. Linear versions 1, 2, and 3 are
accepted; unknown versions are rejected rather than guessed.

For a live cold-file pass, `--min-age-seconds 600` defers files written in the
last ten minutes. A later coordinated pass must process those deferred files;
the age gate alone is not a replacement for stopping saves on hot regions.
