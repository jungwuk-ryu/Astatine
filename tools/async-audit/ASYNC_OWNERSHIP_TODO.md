# Async Ownership TODO

This is the shared backlog for independent region ticking hazards. Keep statuses in the metadata comment and the visible Status line in sync when assigning or closing work.

Generated: 2026-04-28T12:59:52.980Z
Scanner: `node tools/async-audit/scan-async-ownership.mjs --write-todo`

Status values: `todo`, `investigating`, `fixing`, `blocked`, `done`, `false-positive`.

## Summary

- Active candidates: 0
- Tracked completed fixes: 28

| Severity | Count |
| --- | ---: |
| critical | 0 |
| high | 0 |
| medium | 0 |

| Family | Count |
| --- | ---: |

| Category | Count |
| --- | ---: |

## Assignment Rules

- A worker owns one category or one narrow file set at a time.
- Mark `status=fixing owner=<name>` before editing.
- Mark `done` only after source patch, generated source, build, and targeted chaos evidence exist.
- Mark `false-positive` only with a short reason in the item body.
- Do not silence `TickThread`/sync-load guards to close an item.
- Do not treat `Active candidates: 0` as release-ready by itself; runtime gates below must also pass.

## Release Verification Gates

Run these gates before claiming async ownership work is release-ready:

Single-command runner: `node tools/runtime/invoke-async-release-gates.mjs`.
When the live `worldgen` server is busy, use the isolated server variant: `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`.
Runs with `--skip-build`, `--skip-mcc`, or `--skip-worldgen` are partial smoke checks only; they must not be used as release-ready evidence.

Expanded gate list:

1. `./gradlew applyAllPatches --no-configuration-cache`
2. `./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache`
3. `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks --no-configuration-cache`
4. `./gradlew -p tools/region-load-test-plugin build --no-configuration-cache`
5. `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`
6. `node tools/runtime/verify-async-root-baseline.mjs`
7. `tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --min-bots 4 --duration-sec 180 --server-port 25566 --rcon-port 25576 --websocket-base-port 8060 --skip-build --agent-mode ReportOnly`
8. `node tools/runtime/scan-server-log.mjs run/mcc-chaos/results/cycle-01`
9. Run managed worldgen smoke. Preferred when `worldgen` ports are idle: `node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/worldgen --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G`.
10. If the live `worldgen` server is already running, use an isolated smoke server on free ports: `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`.
11. After any managed smoke, verify no Java process, RCON/server port, or `world/session.lock` remains unless the server was intentionally left running.

The worldgen smoke intentionally fails if RCON is already reachable unless both `--attach-existing` and `--allow-live-probes` are passed, so accidental destructive RLT probes require two explicit opt-ins.

## Completed Fixes

### AO-FIX-001 - Scheduled ticks hand off to the owning region
<!-- async-audit id=AO-FIX-001 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelTicksRegionProxy.java`
- Evidence: Scheduled ticks that target another isolation cell are queued for the owner instead of mutating from the current worker.
- Verification: ./gradlew applyAllPatches; MCC chaos advanced past the original LeavesBlock.scheduleTick crash.

### AO-FIX-002 - Fluid spread defers cross-owner writes
<!-- async-audit id=AO-FIX-002 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/material/FlowingFluid.java.patch`
- Evidence: Cross-region fluid placement checks loaded state and hands work to the owner region.
- Verification: ./gradlew applyAllPatches; fluid/leaf fixture runs in MCC chaos.

### AO-FIX-003 - Neighbor updates skip unloaded neighbor chunks
<!-- async-audit id=AO-FIX-003 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/redstone/CollectingNeighborUpdater.java.patch`
- Evidence: CollectingNeighborUpdater and NeighborUpdater use getBlockStateIfLoaded before executing neighbor callbacks.
- Verification: ./gradlew applyAllPatches; fixed RLT redstone sync-load false blocker.

### AO-FIX-004 - Redstone signal reads avoid sync-loading unloaded neighbors
<!-- async-audit id=AO-FIX-004 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/SignalGetter.java.patch`
- Evidence: SignalGetter direct/control/normal signal reads return zero when the neighbor block is not loaded.
- Verification: ./gradlew applyAllPatches passed after patch addition.

### AO-FIX-005 - Entity cached in-block state no longer sync-loads on player removal tick
<!-- async-audit id=AO-FIX-005 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch`
- Evidence: Entity#getInBlockState uses getBlockStateIfLoaded and falls back to AIR.
- Verification: ./gradlew applyAllPatches passed after patch addition.

### AO-FIX-006 - RegionLoadTest fixtures skip unloaded chunks
<!-- async-audit id=AO-FIX-006 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: natural spawning/entity add/structure sync load
- Location: `tools/region-load-test-plugin/src/main/java/io/multipaper/regionloadtest/RegionLoadTestCommand.java`
- Evidence: RLT redstone/mobfarm/pvp/vehicle fixtures guard Bukkit block writes and entity spawns with loaded-chunk checks.
- Verification: ./gradlew -p tools/region-load-test-plugin build passes.

### AO-FIX-007 - Retired player schedulers are not ticked from region workers
<!-- async-audit id=AO-FIX-007 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/MinecraftServer.java.patch`
- Evidence: Manual scheduler ticking checks TickThread ownership and scheduler retirement state.
- Verification: ./gradlew :shreddedpaper-server:createMojmapPaperclipJar passed.

### AO-FIX-008 - Redstone wire propagation avoids unloaded neighbor sync loads
<!-- async-audit id=AO-FIX-008 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/block/RedStoneWireBlock.java.patch`
- Evidence: RedStoneWireBlock and RedstoneWireEvaluator paths use getBlockStateIfLoaded or AIR fallback for boundary neighbor reads.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.

### AO-FIX-009 - Player movement block factors avoid sync-loading unloaded chunks
<!-- async-audit id=AO-FIX-009 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch`
- Evidence: Entity jump/speed factor lookups use getBlockStateIfLoaded and fall back to the vanilla-neutral factor 1.0F.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.

### AO-FIX-010 - Inside-block and piston/diode probes avoid sync-loading boundaries
<!-- async-audit id=AO-FIX-010 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch`
- Evidence: Entity inside-block checks, diode inputs, and piston structure resolution use loaded-only state probes at region edges.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.

### AO-FIX-011 - Lighting updates avoid ticket/region lock inversion from owner workers
<!-- async-audit id=AO-FIX-011 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: player chunk send/post-processing/stale holder broadcast
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/level/ThreadedLevelLightEngine.java.patch`
- Evidence: Owned ShreddedPaper region workers queue lighting work without synchronously taking chunk work tickets while already holding region locks.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.

### AO-FIX-012 - Root Level reads and mutations enforce loaded-only or owner-required semantics
<!-- async-audit id=AO-FIX-012 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: uncategorized sync-load risk
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java`
- Evidence: Level#getBlockState/#getFluidState/#getBlockEntity use loaded-only fallbacks on region workers; owner-required block and block-entity writes defer or fail without sync-loading.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-013 - Bukkit chunk APIs reject region-worker sync loads
<!-- async-audit id=AO-FIX-013 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: natural spawning/entity add/structure sync load
- Location: `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch`
- Evidence: CraftWorld#getChunkAt/#loadChunk/#addPluginChunkTicket now validate loaded ownership on ShreddedPaper tick threads before any load=true chunk path.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-014 - Fluid and scheduled tick dispatch revalidate target ownership/readiness
<!-- async-audit id=AO-FIX-014 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: scheduled tick/neighbor/fluid-leaf-redstone
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java; shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java`
- Evidence: FlowingFluid gates by target ownership instead of thread role, and scheduled tick dispatch skips cells without an owned loaded chunk.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-015 - Entity destination chunk touch is loaded-only for independent workers
<!-- async-audit id=AO-FIX-015 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java`
- Evidence: Independent-region entity destination probing uses getChunkIfLoaded and falls back instead of forcing a chunk load from a worker.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-016 - Player chunk loader cleanup and sends revalidate current ownership
<!-- async-audit id=AO-FIX-016 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: player chunk send/post-processing/stale holder broadcast
- Location: `shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/player/RegionizedPlayerChunkLoader.java`
- Evidence: Stale world-change cleanup checks the current loader before mutating old state, and sendChunk revalidates owner thread, loaded chunk, neighbours, and holder immediately before packet send.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-017 - Chunk holder dirty broadcasts requeue to the current owner
<!-- async-audit id=AO-FIX-017 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: player chunk send/post-processing/stale holder broadcast
- Location: `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChangesBroadcaster.java`
- Evidence: Mismatched-owner holders are queued back to the holder region with TRACKER_BROADCAST instead of being silently skipped.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-018 - Plugin region scheduler uses bounded retry under mailbox pressure
<!-- async-audit id=AO-FIX-018 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: player chunk send/post-processing/stale holder broadcast
- Location: `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java`
- Evidence: PLUGIN mailbox rejection now schedules a bounded CRITICAL_SYSTEM retry with backoff before cancellation.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-019 - Chunk executor retries revalidate current owner before admission
<!-- async-audit id=AO-FIX-019 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: player chunk send/post-processing/stale holder broadcast
- Location: `shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkExecutorLimiter.java`
- Evidence: Deferred, backlog, and backpressure retry paths refresh their RegionChunkIoTracker when the target cell owner changes before admitting work.
- Verification: ./gradlew :shreddedpaper-server:compileJava --stacktrace.

### AO-FIX-020 - POI access uses loaded-only reads and owner-only ticket mutation
<!-- async-audit id=AO-FIX-020 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: villager AI/POI ownership
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/ai/village/poi/PoiManager.java.patch`
- Evidence: PoiManager#get accepts region read ownership for loaded-only scans, getOrLoad falls back to loaded-only from non-owner workers, and take/release mutate tickets only from the POI owner.
- Verification: ./gradlew :shreddedpaper-server:compileJava.

### AO-FIX-021 - Region unloads no longer wait on global ticket propagation while holding region locks
<!-- async-audit id=AO-FIX-021 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: chunk ticket/region lock ordering
- Location: `shreddedpaper-server/minecraft-patches/sources/ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkHolderManager.java.patch`
- Evidence: Independent region unload processing skips the global ticket update drain before local unload batches to avoid ReentrantAreaLock waits under held region locks.
- Verification: ./gradlew :shreddedpaper-server:compileJava.

### AO-FIX-022 - Unsupported plugin player inventory mutations hand off to the player owner
<!-- async-audit id=AO-FIX-022 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: Bukkit plugin compatibility/player ownership
- Location: `shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventoryPlayer.java.patch`
- Evidence: CraftInventoryPlayer mutators schedule unsupported sync Bukkit scheduler/event inventory writes onto the player entity scheduler instead of mutating from the server thread or disabling TickThread guards.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:compileJava.

### AO-FIX-023 - Watchdog shutdown escalation emits a final dump and forces JVM exit
<!-- async-audit id=AO-FIX-023 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: watchdog/shutdown containment
- Location: `shreddedpaper-server/paper-patches/files/src/main/java/org/spigotmc/WatchdogThread.java.patch`
- Evidence: Fatal watchdog handling no longer blocks inside server.close() forever; it starts shutdown work separately, waits a bounded grace period, emits a final full dump, then exits with a halt fallback.
- Verification: ./gradlew applyAllPatches; ./gradlew :shreddedpaper-server:compileJava; normal worldgen RCON stop releases ports/session.lock.

### AO-FIX-024 - Release smoke gates scan high-signal async and shutdown failures
<!-- async-audit id=AO-FIX-024 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: validation/release gate
- Location: `tools/runtime/scan-server-log.mjs; tools/runtime/invoke-worldgen-smoke.mjs; tools/runtime/invoke-async-release-gates.mjs; tools/runtime/verify-async-root-baseline.mjs`
- Evidence: Worldgen smoke now runs RLT villager/path/broadcast/scheduler/crossqueue/syncload/chunk load/generation probes, scans latest.log plus the result directory for async ownership and watchdog failures, requires two explicit flags before probing an already-running server, and can be driven by a single async release gate runner that prepares an isolated smoke server.
- Verification: node --check tools/runtime/invoke-worldgen-smoke.mjs; node --check tools/runtime/scan-server-log.mjs; node --check tools/runtime/invoke-async-release-gates.mjs; node --check tools/runtime/verify-async-root-baseline.mjs; node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen; isolated worldgen smoke passed on 25567/25577; live worldgen attach refusal verified.

### AO-FIX-025 - Subagent review gaps hardened into fail-closed release gates
<!-- async-audit id=AO-FIX-025 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: validation/release gate
- Location: `tools/runtime/invoke-async-release-gates.mjs; tools/runtime/invoke-worldgen-smoke.mjs; tools/runtime/verify-async-root-baseline.mjs; tools/mcc-chaos/invoke-mcc-chaos-loop.mjs; shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/MinecraftServer.java.patch; shreddedpaper-server/paper-patches/files/src/main/java/org/spigotmc/WatchdogThread.java.patch`
- Evidence: Release runner now fails on active critical scanner findings, checks a root-covered critical baseline, builds RLT plugin artifacts, labels skipped runs as non-release, prepares isolated worldgen state itself, scans jcmd/result artifacts, asserts RLT cleanup counters, makes MCC RCON setup failures hard failures, includes join/setup log windows, checks MCC shutdown resources, marks MinecraftServer.hasFullyShutdown at successful shutdown completion, and dumps ShreddedPaper region worker threads in watchdog output.
- Verification: ./gradlew applyAllPatches --no-configuration-cache; node tools/runtime/verify-async-root-baseline.mjs; node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen.

### AO-FIX-026 - Player interaction and spawn probes avoid region-worker sync loads
<!-- async-audit id=AO-FIX-026 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: entity movement/teleport/player tick
- Location: `shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/network/ServerGamePacketListenerImpl.java.patch; shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/Level.java.patch; shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/SpawnPlacementTypes.java.patch`
- Evidence: Player item/block interaction ray traces and fast clip paths use owner handoff or loaded-only chunk access on ShreddedPaper workers, and natural spawn placement probes use loaded-only block-state reads instead of forcing sync loads.
- Verification: ./gradlew applyAllPatches --no-configuration-cache; ./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks --no-configuration-cache; Gate38 MCC targeted regression; full async release gate.

### AO-FIX-027 - Starlight live lighting tasks run under the owning region lock
<!-- async-audit id=AO-FIX-027 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: lighting/chunk lifecycle ownership
- Location: `shreddedpaper-server/minecraft-patches/sources/ca/spottedleaf/moonrise/patches/starlight/light/StarLightInterface.java.patch`
- Evidence: Live block/section/edge lighting tasks defer until the relevant ShreddedPaper region lock is available, preventing palette/container reads from racing region-owned block mutations while avoiding broad locking for chunk-generation startup lighting.
- Verification: ./gradlew applyAllPatches --no-configuration-cache; ./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache; targeted MCC lightfix smoke with 4 bots and natural spawns; full async release gate.

### AO-FIX-028 - Worldgen smoke validates player lifecycle and pathfinding evidence robustly
<!-- async-audit id=AO-FIX-028 status=done owner=codex -->
- Status: done
- Owner: codex
- Category: validation/release gate
- Location: `tools/runtime/invoke-worldgen-smoke.mjs`
- Evidence: The managed worldgen smoke explicitly teleports back to the overworld after End lifecycle coverage so first-time credits UI cannot stall MCC automation, and pathfinding completion evidence tolerates additional RLT counters such as `progressed` while still requiring queued/completed/spawned/pathStarted/crossChunkMoves health.
- Verification: node --check tools/runtime/invoke-worldgen-smoke.mjs; node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/ShreddedPaper/run/worldgen-smoke-isolated/server --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G --server-port 25567 --rcon-port 25577 --require-cached-mcc; full async release gate.

## Active Candidates
