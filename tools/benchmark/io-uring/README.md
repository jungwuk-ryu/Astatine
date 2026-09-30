# io_uring release validation

Use a disposable flat-world template and the same immutable Paperclip JAR for
both transports. `ASSESSMENT.md` records the initial adoption review. Never use
production worlds as benchmark input or install validation plugins on Earth.

`../crowded-audit/run.mjs` accepts `AUDIT_TRANSPORT=epoll|io_uring`. It requires
native transport, changes exactly one preference, and verifies the actual Linux
threads and io_uring descriptors after startup. It records Netty/region thread
user and system CPU deltas, RSS, descriptor counts and host load. Thread deltas
exclude new/ended threads and interrupt CPU; they are not whole-host CPU costs.

Example paired workload (run sequentially, alternate order, repeat three pairs):

```bash
AUDIT_JAVA=/absolute/path/to/java25 \
AUDIT_TRANSPORT=io_uring AUDIT_PLAYERS=40 AUDIT_COMPRESSION=512 \
AUDIT_CRAMMING=24 AUDIT_COLLISION_LIMIT=2 AUDIT_REGION_THREADS=7 \
AUDIT_JVM_ARGS='["-XX:ActiveProcessorCount=7","-Dio.netty.eventLoopThreads=4"]' \
node tools/benchmark/crowded-audit/run.mjs \
  /absolute/path/to/disposable-template /absolute/path/to/candidate.jar \
  /absolute/path/to/new-run 240
```

Accept only runs with the full requested population, mutual player visibility,
continuing keepalives, both cow-count checks, complete sampling and a clean
server exit. Compare busiest-region tick p95/p99, Netty CPU, total JVM CPU and
allocation rates. Shared-host noise and JIT effects limit inference. A failed
run is a failure, never a speedup.

After all servers stop, export each `players.jfr` and `mixed.jfr` with
`../crowded-audit/summarize.py`. `compare.py <evidence-root> --output <new.json>`
expects `perf-1-epoll`, `perf-2-io_uring`, `perf-3-io_uring`, `perf-4-epoll`,
`perf-5-epoll`, `perf-6-io_uring` and their `*-summary.json` files. It rejects
incomplete runs, differing artifacts/configuration and stalled keepalive shards.
It also reports Netty CPU per received MiB so less transmitted work is visible.

For a soak, set `AUDIT_SAMPLE_SECONDS=1800` and `AUDIT_CLIENT_MODE=stationary`.
`AUDIT_PLUGIN_JARS` is a JSON array of absolute plugin JAR paths; binaries are
copied without production configuration. Stationary clients answer Ping/Pong
and send tick-end packets, but do not implement vanilla physics or the complete
anti-cheat protocol. A failed synthetic Grim soak remains a failed test; use
the official client for separate anti-cheat compatibility evidence. The 240
cows retain normal AI. `AUDIT_CHURN_CONNECTIONS=100` follows the soak with four-client
reconnect cycles, graceful/abrupt closes and ten-second paused readers. Each
cycle must return the server population to zero within twenty seconds.
The same four profiles reconnect repeatedly, and each cycle checks exactly four
online players before closing them.
The churn script connects only to the fixed loopback audit port (25685).
`AUDIT_LATE_PROFILE_MINUTES='[10,20,28]'` adds 60-second JFR captures during a
30-minute soak. Each capture checks the server-side player and cow populations.

The compressed packet-hook smoke also supports transport selection and Grim:

```bash
AUDIT_JAVA=/absolute/path/to/java25 \
python3 tools/benchmark/crowded-audit/network-smoke.py \
  /absolute/path/to/disposable-template /absolute/path/to/candidate.jar \
  /absolute/path/to/packetevents.jar /absolute/path/to/ProtocolLib.jar \
  /absolute/path/to/new-smoke --transport io_uring --grim /absolute/path/to/grim.jar
```

The native-client runner requires Xvfb, ffmpeg, X11/XTest, Java 25 and an existing
official vanilla 1.21.11 cache containing `version.json`, `client.jar`,
`classpath.txt` (absolute entries), `assets` and `natives`. It verifies the client
SHA-1 against its official manifest, owns its display/server/client processes,
uses keyboard movement, captures the screen and verifies a Nether round trip.

```bash
python3 tools/benchmark/io-uring/native-login.py \
  /absolute/path/to/disposable-template /absolute/path/to/candidate.jar \
  /absolute/path/to/vanilla-cache /absolute/path/to/new-native-run \
  --transport io_uring --java /absolute/path/to/java25 --repeats 3 \
  --plugin /absolute/path/to/packetevents.jar \
  --plugin /absolute/path/to/ProtocolLib.jar --plugin /absolute/path/to/grim.jar
```

Add `--mock-session` to exercise the vanilla RSA/AES encrypted login path using
a loopback session API. Both server and client must submit the same session
hash. This is an authentication fixture, **not a real Mojang account test**.
It never changes the production authentication server, whitelist or security
settings. Empty fixture public keys/profile services can produce client-side
profile/certificate warnings; retain them in raw evidence.
Add `--hold-seconds 1800` for a 30-minute native connection check. The runner
polls the actual server player list every five seconds, then requires the player
to disappear after closing the client and the server to stop normally.

The native runner defaults to ports 25689/25690 and display `:131`. Separate
checks can use `--server-port`, `--rcon-port` and `--display`; every listener still
binds to loopback. It refuses occupied ports/displays and existing output directories. All evidence
directories are new, and process cleanup is limited to children it launched.
