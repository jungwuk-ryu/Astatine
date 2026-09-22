# Crowded-area performance audit

This is a profiling and prioritization harness, not an optimized server build.
It profiles the selected Paperclip artifact with 80 moving loopback clients,
then adds 80 invulnerable, persistent cows with normal AI in a glass enclosure.
An optional final argument selects 16–240 cows. Accepted runs used 80 and 240.
Earlier 240-cow attempts with Minecraft network JFR diagnostics enabled timed out;
those attempts are excluded. The accepted runs disabled those diagnostics.
Player team collisions are disabled by the client harness;
cow collisions remain enabled. Cramming damage and natural spawning are disabled.

Use a disposable flat-world template prepared as described in
[the tracker harness](../tracker/README.md). It must have cached `libraries` and
`cache`, generated configs, and the three world directories. The measured
template used two region workers, independent ticking, eight-chunk region cells,
tracker limit 500 and full-update frequency 20. Do not use a production world.

The output directory must not already exist. The runner binds only to loopback,
requires ports 25685/25686 to be free, installs no plugins, copies the selected
jar, starts its own process, and stops that process in `finally`. Cached jars are
shared through symlinks; world/config files are copied. The pinned client
dependencies from `../tracker/package.json` must be installed first.

```bash
# Append 240 to this command for the heavier accepted load.
node tools/benchmark/crowded-audit/run.mjs \
  /absolute/path/to/isolated-flat-template \
  shreddedpaper-server/build/libs/astatine-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar \
  /absolute/path/to/new-audit-run

python3 tools/benchmark/crowded-audit/summarize.py \
  /absolute/path/to/new-audit-run/players.jfr \
  --output /absolute/path/to/new-audit-run/players-summary.json
python3 tools/benchmark/crowded-audit/summarize.py \
  /absolute/path/to/new-audit-run/mixed.jfr \
  --output /absolute/path/to/new-audit-run/mixed-summary.json

# Run the probe after the server has stopped, without concurrent profiling/builds.
python3 tools/benchmark/crowded-audit/lock-probe.py \
  /absolute/path/to/new-audit-run \
  --output /absolute/path/to/new-audit-run/lock-probe.json
```

Java 25 is required. The server uses 1–3 GiB heap and
`-XX:ActiveProcessorCount=2`; this sizes JVM pools and is not a CPU affinity or
CPU quota. After login the clients warm up for 90 seconds. The first JFR lasts
45 seconds. All requested summons must complete, then the mixed load warms up for
30 seconds before a 60-second recording. Final acceptance requires all 80
clients to remain connected, see every other player, and finish 190 one-second
polls. Both mixed-phase population checks must return the requested entity count.
Four client worker threads split packet decoding, and each reports its event
loop delay along with connection/visibility checks.

`manifest.json` records the artifact hash, JVM flags, config hashes, source HEAD
and pre-existing server source diff, phase boundaries, setup completion, and
process exit. The artifact is not rebuilt automatically: verify its provenance
and relevant class fingerprints before attributing it to source HEAD.

The streaming JFR exporter retains execution samples, sampled allocation sites,
per-thread allocation counter deltas, region tick/scheduler events, GC pauses,
and region/main thread parks/monitor contention. It separates timer waits in
`DelayQueue` from application waits. Failed `tryLock` retries are not Java park
events and require separate scheduler instrumentation to quantify. Custom tick
events in the audited implementation are emitted on every completed tick;
scheduler events are sampled every 256 dequeues.

Execution sample percentages are not precise CPU-time percentages. Allocation
sample weights can include large initial weights; use periodic thread counter
deltas for allocation rates. The category matcher has explicit first-match
precedence; inclusive stack counts overlap and must not be added together.
All four recordings in the two accepted runs disable Minecraft's per-packet and network
summary events using `+minecraft.*#enabled=false` settings. The `+` is required
when adding custom events absent from the base profile configuration. A JFR
configuration warning aborts the run. The earlier exploratory profiles used
network summary events and include their overhead; they are kept separately.

`LockProbe` loads the real extracted runtime classes. It verifies halo overlap
with two threads, then measures uncontended acquisition/release and empty-thread
cleanup with foreign locks present. It reports nine CPU-time/allocation samples
after 20,000 warmup operations. It also calls the real empty block-effect
collector to quantify its allocation without game state changes. These are
method-level probes, not a server capacity or contention benchmark. The recorded
audit ran this command three times in fresh JVMs. Preserve raw samples; early
JIT effects and the shared host limit timing precision.

The audit does not cover online authentication, encryption, WAN delay,
compression-enabled traffic, plugin hooks, combat, hostile mob targeting,
villager brains, or native client frame rates. The before/after phases change
the workload, not the implementation; their difference is not an optimization
speedup. A preliminary interrupted run is excluded from the final result.

See [REPORT.md](REPORT.md) for the measured findings and implementation order,
and [RESULTS.json](RESULTS.json) for counters, hashes, acceptance, and probe samples.
