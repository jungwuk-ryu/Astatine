# Linear v3 recompressor

Offline, bounded-memory verifier and recompressor for ShreddedPaper Linear v3
region files. It processes one file and one bucket at a time. The source is
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
rebuilds an invalid one. The append-only checkpoint is forced after every
successful replacement.

Useful options are `--max-files N`, `--continue-on-error`, `--min-free-gib N`
(default: 5), `--min-age-seconds N`, and `--progress-every N` (default: 100). Only version 3 is
accepted; older Linear versions are rejected rather than silently converted.

For a live cold-file pass, `--min-age-seconds 600` defers files written in the
last ten minutes. A later coordinated pass must process those deferred files;
the age gate alone is not a replacement for stopping saves on hot regions.
