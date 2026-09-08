# Crowded player region hot paths

See [RESULTS.md](RESULTS.md) for measured results and their limits.

`Player.aiStep` performs an entity query for pickups and contact damage every
tick. The original query visits and materializes all overlapping players, even
though `ServerPlayer` inherits an empty `Entity.playerTouch`. A crowd of N
players in a section therefore causes N scans over those N players.

The contact query uses a section index that excludes exactly `ServerPlayer`.
Every other class remains eligible, including custom player subclasses. The
normal bounding-box, spectator, exclusion, iteration-order and platform-hook
behavior stays in place. The index follows the existing entity add/remove,
section movement and chunk merge lifecycle, and is allocated only while there
are candidates. Ordinary entity queries and collision handling use their
original collections.

The regression suite compares indexed results with an independently filtered
full scan, including randomized membership changes, movement, merging,
pickups, contact damage entities and custom players. It also checks that the
omitted vanilla method still has an empty bytecode body.

```bash
./gradlew applyAllPatches --no-configuration-cache
./gradlew :shreddedpaper-server:test --no-configuration-cache
./gradlew :shreddedpaper-server:createMojmapPaperclipJar --no-configuration-cache
```

Run those commands sequentially: patch application must finish before the
generated source is compiled or tested.

## Query benchmark

`PlayerContactBenchmark` is an optional plugin for an **isolated local test
server**. It constructs real server entities without adding them to the world
or connecting sockets. It compares the original full section query with the
indexed query in the same JVM, checks contact membership and order, warms both
paths, alternates measurement order and writes nine raw samples plus medians.
Each sample contains 2,000 queries. Allocation uses the current thread's JVM
allocation counter. Both paths publish every returned list to the same volatile
sink so empty queries cannot disappear through loop folding or escape analysis.
These measurements include that publication overhead and describe the contact query, not complete
player ticks, network load, TPS or whole-server MSPT.

The fork also resolves ordinary visibility before the UUID-based self exception
in `CraftPlayer.canSee`. Empty visibility maps need no lookup, and visible
targets need no additional UUID equality check even when overrides exist.
Hidden-by-default entities, self visibility and per-plugin overrides retain
their original truth table. There is no cross-tick visibility cache.

Runs with zero items additionally compare the original visibility formula
against the public `CraftPlayer.canSee` method, using the same real players.
They measure both empty maps and one visibility override per viewer. The old
formula uses map references obtained once before timing; no reflective calls
or mocks execute inside either timed loop.

After building this fork, start a disposable server once to populate its
`libraries` directory, then stop it. Build the benchmark plugin:

```bash
node tools/benchmark/player-contact/build.mjs /absolute/path/to/isolated-server
```

Copy `run/player-contact-plugin/player-contact-benchmark.jar` into that test
server's `plugins` directory, start the server and run one command at a time:

```text
contactbench 500 0
contactbench 1000 0
contactbench 500 16
contactbench 1000 16
```

Wait for `BENCH_DONE` on zero-item runs, or `CONTACT_BENCH` on runs with items,
before starting the next run. Results are written under
`plugins/PlayerContactBenchmark/contacts-<players>-<items>.json` and
`visibility-<players>-<overrides>.json`. Construction
and timing intentionally occupy a region worker; run this with no online users
and no simultaneous compiler or other benchmark. The plugin is not intended
for production installation.
