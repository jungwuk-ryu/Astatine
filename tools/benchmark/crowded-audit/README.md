# Crowded-area performance audit

This harness profiles selected server artifacts and validates crowded-area changes.
By default it profiles the selected Paperclip artifact with 80 moving loopback clients,
then adds 80 invulnerable, persistent cows with normal AI in a glass enclosure.
An optional final argument selects 16–240 cows. Accepted runs used 80 and 240.
Earlier 240-cow attempts with Minecraft network JFR diagnostics enabled timed out;
those attempts are excluded. The accepted runs disabled those diagnostics.
Player team collisions are disabled by the client harness;
cow collisions remain enabled. Cramming damage is disabled by default; explicit
release scenarios can retain the production rule. Natural spawning stays disabled.

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
30 seconds before a 60-second recording. Final acceptance requires all selected
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

The original diagnosis does not cover online authentication, encryption, WAN delay,
compression-enabled traffic, plugin hooks, combat, hostile mob targeting,
villager brains, or native client frame rates. Separate release checks below cover
compressed traffic and selected plugin hooks. The original before/after phases change
the workload, not the implementation; their difference is not an optimization
speedup. A preliminary interrupted run is excluded from the final result.

See [REPORT.md](REPORT.md) for the measured findings and implementation order,
and [RESULTS.json](RESULTS.json) for counters, hashes, acceptance, and probe samples.

## Comparing a release

Use the same fresh template, JVM and workload for each baseline/candidate run.
Never run a benchmark alongside a build, another benchmark, or the method probes.
`AUDIT_JAVA` chooses a Java executable; `AUDIT_JVM_ARGS` is a JSON array of explicit
-D/-X/-XX flags (passed as arguments, never evaluated by a shell).
`AUDIT_PLAYERS=40` chooses 40 clients (default 80, four equal client workers).
`AUDIT_COMPRESSION=128` enables compression for a separate compatibility run.
`AUDIT_CRAMMING=24`, `AUDIT_COLLISION_LIMIT=2`, and `AUDIT_REGION_THREADS=7`
select explicit paired scenarios. Apply the same settings to both artifacts.
Defaults retain the original audit workload (cramming 0, template collision/worker limits).

Set `AUDIT_JVM_ARGS='["-Dastatine.packet-batch-diagnostics=true"]'` for the
optimized artifact to record one periodic batch summary per second. The summary
reports actual drain tasks, packets per task, and mean/maximum task scheduling
delay. Sampled pending-task and pending-packet counts are approximate snapshots.
Task scheduling delay is not the age of the oldest packet or network RTT. `-Dastatine.disable-packet-batching=true` retains
ordinary sends for a diagnostic comparison. Both flags default to false.
RegionLockWaitEvent records one summary per contended acquisition, preserving the
first recorded deadline, retry count, total wait and observed blocker across backoffs.
Retirement/layout changes/shutdown close incomplete waits; the blocker is a
best-effort snapshot, never an ownership authorization.

The plugin smoke runner uses copies of the selected PacketEvents and ProtocolLib
JARs, a fresh loopback server with compression threshold 128, four synthetic
clients, and a validation-only plugin. The plugin sends the same rotation packet
objects to all four recipients; PacketEvents changes their yaw independently.
Clients check every sentinel packet's recipient-specific yaw and sequence, echo
plugin messages, and confirm a round trip between two dimensions. The server
reports echo RTT. This proves compatibility of those exercised paths, not every
production plugin callback or native-client rendering. Never install the smoke
plugin on production. The selected plugins are copied without production config.

```bash
python3 tools/benchmark/crowded-audit/network-smoke.py \
  /absolute/path/to/isolated-flat-template \
  /absolute/path/to/candidate-paperclip.jar \
  /absolute/path/to/packetevents-spigot.jar \
  /absolute/path/to/ProtocolLib.jar \
  /absolute/path/to/new-network-smoke-output
```

See RELEASE.md for implementation choices, repeated before/after results and
rollout evidence. REPORT.md preserves the original diagnosis and its limits.
