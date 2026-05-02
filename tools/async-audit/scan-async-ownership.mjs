#!/usr/bin/env node

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const START_DIRS = [
  'shreddedpaper-server/src/minecraft/java',
  'shreddedpaper-server/src/main/java',
  'shreddedpaper-server/minecraft-patches/sources',
  'shreddedpaper-server/paper-patches/files',
  'tools/region-load-test-plugin/src/main/java',
  'tools/mcc-chaos',
];

const EXTENSIONS = new Set(['.java', '.patch', '.kt', '.kts', '.mjs', '.ps1', '.sh']);

const IGNORED_PATH_PARTS = [
  'shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ownership/',
];

const STATUS_ORDER = ['todo', 'investigating', 'fixing', 'blocked', 'done', 'false-positive'];

const DEFAULT_INTENT = 'unclassified';

const ROOT_COVERED_SINKS = new Set([
  'blockstate-read',
  'fluidstate-read',
  'block-entity-read',
  'poi-manager-access',
  'schedule-tick',
]);

const GUARDED_SYNC_ROOTS = [
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java',
    sinks: new Set(['bukkit-chunk-load', 'getchunk-load-true', 'set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/LevelReader.java',
    sinks: new Set(['sync-load-call', 'getchunk-load-true']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerChunkCache.java',
    sinks: new Set(['sync-load-call']),
  },
  {
    pathIncludes: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/level/ServerChunkCache.java.patch',
    sinks: new Set(['sync-load-call']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkTaskScheduler.java',
    sinks: new Set(['sync-load-call', 'getchunk-load-true']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/level/ChunkSystemLevelReader.java',
    sinks: new Set(['sync-load-call']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/ServerLevel.java',
    sinks: new Set(['sync-load-call']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/ChunkGenerator.java',
    sinks: new Set(['sync-load-call']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/dimension/end/EndDragonFight.java',
    sinks: new Set(['bukkit-chunk-load']),
  },
  {
    pathIncludes: 'shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch',
    sinks: new Set(['bukkit-chunk-load', 'getchunk-load-true']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/CraftWorld.java',
    sinks: new Set(['bukkit-chunk-load', 'getchunk-load-true']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/CraftChunk.java',
    sinks: new Set(['bukkit-chunk-load']),
  },
  {
    pathIncludes: 'shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftChunk.java.patch',
    sinks: new Set(['bukkit-chunk-load']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/generator/CraftChunkData.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/generator/CustomChunkGenerator.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/item/ItemStack.java.patch',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/item/ItemStack.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/server/level/WorldGenRegion.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/ImposterProtoChunk.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/LevelChunk.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/chunk/storage/SerializableChunkData.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/block/piston/PistonBaseBlock.java',
    sinks: new Set(['set-block-entity']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/block/CraftBlock.java',
    sinks: new Set(['bukkit-block-write']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftItemStack.java',
    sinks: new Set(['bukkit-block-write']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/legacy/CraftEvil.java',
    sinks: new Set(['bukkit-block-write']),
  },
  {
    pathIncludes: 'paper-server/src/main/java/org/bukkit/craftbukkit/legacy/MaterialRerouting.java',
    sinks: new Set(['bukkit-block-write']),
  },
  {
    pathIncludes: 'tools/region-load-test-plugin/src/main/java/io/multipaper/regionloadtest/RegionLoadTestCommand.java',
    sinks: new Set(['bukkit-block-write']),
  },
];

const SINK_FAMILIES = new Map([
  ['blockstate-read', 'loaded-only read'],
  ['fluidstate-read', 'loaded-only read'],
  ['block-entity-read', 'loaded-only read'],
  ['bukkit-chunk-load', 'sync chunk load'],
  ['getchunk-load-true', 'sync chunk load'],
  ['sync-load-call', 'sync chunk load'],
  ['getchunk-load', 'sync chunk load'],
  ['bukkit-block-write', 'owner-required mutation'],
  ['set-block-entity', 'owner-required mutation'],
  ['poi-manager-access', 'POI read/write ownership'],
  ['schedule-tick', 'scheduled tick dispatch'],
  ['scheduled-tick-dispatch', 'scheduled tick dispatch'],
  ['stale-owner-cleanup', 'owner epoch / stale work'],
  ['stale-owner-send', 'owner epoch / stale work'],
  ['stale-holder-broadcast', 'owner epoch / stale work'],
  ['ownership-gate', 'owner epoch / stale work'],
  ['plugin-scheduling-backpressure', 'backpressure / retry'],
  ['async-backpressure-stale-work', 'backpressure / retry'],
]);

function familyForSink(sink) {
  return SINK_FAMILIES.get(sink) ?? 'uncategorized';
}

function withAuditMetadata(candidate) {
  return {
    ...candidate,
    family: candidate.family ?? familyForSink(candidate.sink),
    intent: candidate.intent ?? DEFAULT_INTENT,
  };
}

const TRACKED_FIXES = [
  {
    id: 'AO-FIX-001',
    title: 'Scheduled ticks hand off to the owning region',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/LevelTicksRegionProxy.java',
    evidence: 'Scheduled ticks that target another isolation cell are queued for the owner instead of mutating from the current worker.',
    verification: './gradlew applyAllPatches; MCC chaos advanced past the original LeavesBlock.scheduleTick crash.',
  },
  {
    id: 'AO-FIX-002',
    title: 'Fluid spread defers cross-owner writes',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/material/FlowingFluid.java.patch',
    evidence: 'Cross-region fluid placement checks loaded state and hands work to the owner region.',
    verification: './gradlew applyAllPatches; fluid/leaf fixture runs in MCC chaos.',
  },
  {
    id: 'AO-FIX-003',
    title: 'Neighbor updates skip unloaded neighbor chunks',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/redstone/CollectingNeighborUpdater.java.patch',
    evidence: 'CollectingNeighborUpdater and NeighborUpdater use getBlockStateIfLoaded before executing neighbor callbacks.',
    verification: './gradlew applyAllPatches; fixed RLT redstone sync-load false blocker.',
  },
  {
    id: 'AO-FIX-004',
    title: 'Redstone signal reads avoid sync-loading unloaded neighbors',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/SignalGetter.java.patch',
    evidence: 'SignalGetter direct/control/normal signal reads return zero when the neighbor block is not loaded.',
    verification: './gradlew applyAllPatches passed after patch addition.',
  },
  {
    id: 'AO-FIX-005',
    title: 'Entity cached in-block state no longer sync-loads on player removal tick',
    category: 'entity movement/teleport/player tick',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch',
    evidence: 'Entity#getInBlockState uses getBlockStateIfLoaded and falls back to AIR.',
    verification: './gradlew applyAllPatches passed after patch addition.',
  },
  {
    id: 'AO-FIX-006',
    title: 'RegionLoadTest fixtures skip unloaded chunks',
    category: 'natural spawning/entity add/structure sync load',
    location: 'tools/region-load-test-plugin/src/main/java/io/multipaper/regionloadtest/RegionLoadTestCommand.java',
    evidence: 'RLT redstone/mobfarm/pvp/vehicle fixtures guard Bukkit block writes and entity spawns with loaded-chunk checks.',
    verification: './gradlew -p tools/region-load-test-plugin build passes.',
  },
  {
    id: 'AO-FIX-007',
    title: 'Retired player schedulers are not ticked from region workers',
    category: 'entity movement/teleport/player tick',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/MinecraftServer.java.patch',
    evidence: 'Manual scheduler ticking checks TickThread ownership and scheduler retirement state.',
    verification: './gradlew :shreddedpaper-server:createMojmapPaperclipJar passed.',
  },
  {
    id: 'AO-FIX-008',
    title: 'Redstone wire propagation avoids unloaded neighbor sync loads',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/level/block/RedStoneWireBlock.java.patch',
    evidence: 'RedStoneWireBlock and RedstoneWireEvaluator paths use getBlockStateIfLoaded or AIR fallback for boundary neighbor reads.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.',
  },
  {
    id: 'AO-FIX-009',
    title: 'Player movement block factors avoid sync-loading unloaded chunks',
    category: 'entity movement/teleport/player tick',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch',
    evidence: 'Entity jump/speed factor lookups use getBlockStateIfLoaded and fall back to the vanilla-neutral factor 1.0F.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.',
  },
  {
    id: 'AO-FIX-010',
    title: 'Inside-block and piston/diode probes avoid sync-loading boundaries',
    category: 'entity movement/teleport/player tick',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/Entity.java.patch',
    evidence: 'Entity inside-block checks, diode inputs, and piston structure resolution use loaded-only state probes at region edges.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.',
  },
  {
    id: 'AO-FIX-011',
    title: 'Lighting updates avoid ticket/region lock inversion from owner workers',
    category: 'player chunk send/post-processing/stale holder broadcast',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/level/ThreadedLevelLightEngine.java.patch',
    evidence: 'Owned ShreddedPaper region workers queue lighting work without synchronously taking chunk work tickets while already holding region locks.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:createMojmapPaperclipJar --stacktrace.',
  },
  {
    id: 'AO-FIX-012',
    title: 'Root Level reads and mutations enforce loaded-only or owner-required semantics',
    category: 'uncategorized sync-load risk',
    location: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/Level.java',
    evidence: 'Level#getBlockState/#getFluidState/#getBlockEntity use loaded-only fallbacks on region workers; owner-required block and block-entity writes defer or fail without sync-loading.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-013',
    title: 'Bukkit chunk APIs reject region-worker sync loads',
    category: 'natural spawning/entity add/structure sync load',
    location: 'shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/CraftWorld.java.patch',
    evidence: 'CraftWorld#getChunkAt/#loadChunk/#addPluginChunkTicket now validate loaded ownership on ShreddedPaper tick threads before any load=true chunk path.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-014',
    title: 'Fluid and scheduled tick dispatch revalidate target ownership/readiness',
    category: 'scheduled tick/neighbor/fluid-leaf-redstone',
    location: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/level/material/FlowingFluid.java; shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChunkTicker.java',
    evidence: 'FlowingFluid gates by target ownership instead of thread role, and scheduled tick dispatch skips cells without an owned loaded chunk.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-015',
    title: 'Entity destination chunk touch is loaded-only for independent workers',
    category: 'entity movement/teleport/player tick',
    location: 'shreddedpaper-server/src/minecraft/java/net/minecraft/world/entity/Entity.java',
    evidence: 'Independent-region entity destination probing uses getChunkIfLoaded and falls back instead of forcing a chunk load from a worker.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-016',
    title: 'Player chunk loader cleanup and sends revalidate current ownership',
    category: 'player chunk send/post-processing/stale holder broadcast',
    location: 'shreddedpaper-server/src/minecraft/java/ca/spottedleaf/moonrise/patches/chunk_system/player/RegionizedPlayerChunkLoader.java',
    evidence: 'Stale world-change cleanup checks the current loader before mutating old state, and sendChunk revalidates owner thread, loaded chunk, neighbours, and holder immediately before packet send.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-017',
    title: 'Chunk holder dirty broadcasts requeue to the current owner',
    category: 'player chunk send/post-processing/stale holder broadcast',
    location: 'shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/ShreddedPaperChangesBroadcaster.java',
    evidence: 'Mismatched-owner holders are queued back to the holder region with TRACKER_BROADCAST instead of being silently skipped.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-018',
    title: 'Plugin region scheduler uses bounded retry under mailbox pressure',
    category: 'player chunk send/post-processing/stale holder broadcast',
    location: 'shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/region/ShreddedPaperRegionSchedulerApiImpl.java',
    evidence: 'PLUGIN mailbox rejection now schedules a bounded CRITICAL_SYSTEM retry with backoff before cancellation.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-019',
    title: 'Chunk executor retries revalidate current owner before admission',
    category: 'player chunk send/post-processing/stale holder broadcast',
    location: 'shreddedpaper-server/src/main/java/io/multipaper/shreddedpaper/threading/region/RegionChunkExecutorLimiter.java',
    evidence: 'Deferred, backlog, and backpressure retry paths refresh their RegionChunkIoTracker when the target cell owner changes before admitting work.',
    verification: './gradlew :shreddedpaper-server:compileJava --stacktrace.',
  },
  {
    id: 'AO-FIX-020',
    title: 'POI access uses loaded-only reads and owner-only ticket mutation',
    category: 'villager AI/POI ownership',
    location: 'shreddedpaper-server/minecraft-patches/sources/net/minecraft/world/entity/ai/village/poi/PoiManager.java.patch',
    evidence: 'PoiManager#get accepts region read ownership for loaded-only scans, getOrLoad falls back to loaded-only from non-owner workers, and take/release mutate tickets only from the POI owner.',
    verification: './gradlew :shreddedpaper-server:compileJava.',
  },
  {
    id: 'AO-FIX-021',
    title: 'Region unloads no longer wait on global ticket propagation while holding region locks',
    category: 'chunk ticket/region lock ordering',
    location: 'shreddedpaper-server/minecraft-patches/sources/ca/spottedleaf/moonrise/patches/chunk_system/scheduling/ChunkHolderManager.java.patch',
    evidence: 'Independent region unload processing skips the global ticket update drain before local unload batches to avoid ReentrantAreaLock waits under held region locks.',
    verification: './gradlew :shreddedpaper-server:compileJava.',
  },
  {
    id: 'AO-FIX-022',
    title: 'Unsupported plugin player inventory mutations hand off to the player owner',
    category: 'Bukkit plugin compatibility/player ownership',
    location: 'shreddedpaper-server/paper-patches/files/src/main/java/org/bukkit/craftbukkit/inventory/CraftInventoryPlayer.java.patch',
    evidence: 'CraftInventoryPlayer mutators schedule unsupported sync Bukkit scheduler/event inventory writes onto the player entity scheduler instead of mutating from the server thread or disabling TickThread guards.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:compileJava.',
  },
  {
    id: 'AO-FIX-023',
    title: 'Watchdog shutdown escalation emits a final dump and forces JVM exit',
    category: 'watchdog/shutdown containment',
    location: 'shreddedpaper-server/paper-patches/files/src/main/java/org/spigotmc/WatchdogThread.java.patch',
    evidence: 'Fatal watchdog handling no longer blocks inside server.close() forever; it starts shutdown work separately, waits a bounded grace period, emits a final full dump, then exits with a halt fallback.',
    verification: './gradlew applyAllPatches; ./gradlew :shreddedpaper-server:compileJava; normal worldgen RCON stop releases ports/session.lock.',
  },
  {
    id: 'AO-FIX-024',
    title: 'Release smoke gates scan high-signal async and shutdown failures',
    category: 'validation/release gate',
    location: 'tools/runtime/scan-server-log.mjs; tools/runtime/invoke-worldgen-smoke.mjs; tools/runtime/invoke-async-release-gates.mjs; tools/runtime/verify-async-root-baseline.mjs',
    evidence: 'Worldgen smoke now runs RLT villager/path/broadcast/scheduler/crossqueue/syncload/chunk load/generation probes, scans latest.log plus the result directory for async ownership and watchdog failures, requires two explicit flags before probing an already-running server, and can be driven by a single async release gate runner that prepares an isolated smoke server.',
    verification: 'node --check tools/runtime/invoke-worldgen-smoke.mjs; node --check tools/runtime/scan-server-log.mjs; node --check tools/runtime/invoke-async-release-gates.mjs; node --check tools/runtime/verify-async-root-baseline.mjs; node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen; isolated worldgen smoke passed on 25567/25577; live worldgen attach refusal verified.',
  },
  {
    id: 'AO-FIX-025',
    title: 'Subagent review gaps hardened into fail-closed release gates',
    category: 'validation/release gate',
    location: 'tools/runtime/invoke-async-release-gates.mjs; tools/runtime/invoke-worldgen-smoke.mjs; tools/runtime/verify-async-root-baseline.mjs; tools/mcc-chaos/invoke-mcc-chaos-loop.mjs; shreddedpaper-server/minecraft-patches/sources/net/minecraft/server/MinecraftServer.java.patch; shreddedpaper-server/paper-patches/files/src/main/java/org/spigotmc/WatchdogThread.java.patch',
    evidence: 'Release runner now fails on active critical scanner findings, checks a root-covered critical baseline, builds RLT plugin artifacts, labels skipped runs as non-release, prepares isolated worldgen state itself, scans jcmd/result artifacts, asserts RLT cleanup counters, makes MCC RCON setup failures hard failures, includes join/setup log windows, checks MCC shutdown resources, marks MinecraftServer.hasFullyShutdown at successful shutdown completion, and dumps ShreddedPaper region worker threads in watchdog output.',
    verification: './gradlew applyAllPatches --no-configuration-cache; node tools/runtime/verify-async-root-baseline.mjs; node tools/runtime/invoke-async-release-gates.mjs --skip-build --skip-mcc --isolated-worldgen.',
  },
];

const MANUAL_CANDIDATES = [
];

const SINKS = [
  {
    name: 'sync-load-call',
    severity: 'critical',
    regex: /\bsyncLoad(?:NonFull)?\s*\(/,
    why: 'Direct sync chunk loading is forbidden from independent region workers.',
    fix: 'Replace with async chunk loading and resume on the owning region, or use already-loaded chunk access when absence is acceptable.',
  },
  {
    name: 'getchunk-load-true',
    severity: 'critical',
    regex: /\bgetChunk(?:At)?\s*\([^;\n]*,\s*true\b/,
    why: 'load=true can enter ServerChunkCache.syncLoad when the target chunk is absent.',
    fix: 'Use getChunkIfLoadedImmediately/getChunk(..., false), prefetch asynchronously, or hand off to the owning region.',
  },
  {
    name: 'bukkit-chunk-load',
    severity: 'critical',
    regex: /\b(?:getChunkAt|loadChunk)\s*\(/,
    why: 'Bukkit chunk APIs commonly synchronously load chunks unless guarded.',
    fix: 'Guard with isChunkLoaded or route through async chunk loading before Bukkit access.',
  },
  {
    name: 'blockstate-read',
    severity: 'high',
    regex: /\b(?:this\.level\(\)|this\.level|level|serverLevel|world|worldIn|levelAccessor|context\.getLevel\(\)|entity\.level\(\)|player\.level\(\))\.getBlockState\s*\(/,
    why: 'Block state reads can synchronously load chunks when the target is outside the owned/loaded region.',
    fix: 'Use getBlockStateIfLoaded when unloaded chunks are tolerable, or explicitly hand off/prefetch before reading.',
  },
  {
    name: 'fluidstate-read',
    severity: 'high',
    regex: /\b(?:this\.level\(\)|this\.level|level|serverLevel|world|worldIn|levelAccessor|context\.getLevel\(\)|entity\.level\(\)|player\.level\(\))\.getFluidState\s*\(/,
    why: 'Fluid reads can synchronously load chunks on cross-boundary block logic.',
    fix: 'Use getFluidIfLoaded or derive fluid state from a loaded BlockState.',
  },
  {
    name: 'bukkit-block-write',
    severity: 'high',
    regex: /\.setType\s*\(/,
    why: 'Bukkit block writes may touch unloaded chunks or trigger off-owner physics.',
    fix: 'Guard chunk loaded state, disable unsafe physics for fixtures, or dispatch the write to the owning region.',
  },
  {
    name: 'set-block-entity',
    severity: 'high',
    regex: /\.setBlockEntity\s*\(/,
    why: 'Block entity writes can require target chunk ownership and should not force load from packet/region workers.',
    fix: 'Ensure the target chunk is loaded and owned, or resume on the owning region before mutation.',
  },
  {
    name: 'poi-manager-access',
    severity: 'high',
    regex: /\bgetPoiManager\s*\(\)\s*\.\s*(?:get|getOrLoad|getOrCreate|getInRange|getInSquare|getInChunk|find|findClosest|findClosestWithType|findAll|findAllWithType|take|release|exists|getType|ensureLoadedAndValid)\s*\(/,
    why: 'POI scans and ticket mutations can cross region boundaries during villager AI and may touch unloaded POI chunks or mutate occupancy off-owner.',
    fix: 'Use loaded-only POI reads from read-owned regions, and hand off or skip POI ticket mutations unless the target POI region is owned.',
  },
  {
    name: 'schedule-tick',
    severity: 'medium',
    regex: /\.scheduleTick\s*\(/,
    why: 'Scheduling a tick for another isolation cell can violate tick ownership.',
    fix: 'Use the scheduled tick owner handoff path for cross-cell future ticks.',
  },
];

function parseArgs(argv) {
  const args = {
    writeTodo: false,
    todoPath: 'tools/async-audit/ASYNC_OWNERSHIP_TODO.md',
    jsonPath: 'tools/async-audit/async-ownership-candidates.json',
    astJsonPath: 'tools/async-audit/java-call-sites.json',
    minSeverity: 'medium',
    failOnCritical: false,
    includeRootCovered: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--write-todo') {
      args.writeTodo = true;
    } else if (arg === '--todo') {
      args.todoPath = argv[++i];
    } else if (arg === '--json') {
      args.jsonPath = argv[++i];
    } else if (arg === '--ast-json') {
      args.astJsonPath = argv[++i];
    } else if (arg === '--min-severity') {
      args.minSeverity = argv[++i] ?? args.minSeverity;
    } else if (arg === '--fail-on-critical') {
      args.failOnCritical = true;
    } else if (arg === '--include-root-covered') {
      args.includeRootCovered = true;
    } else if (arg === '--help' || arg === '-h') {
      printHelp();
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return args;
}

function printHelp() {
  console.log(`Usage: node tools/async-audit/scan-async-ownership.mjs [options]

Options:
  --write-todo              Write/update tools/async-audit/ASYNC_OWNERSHIP_TODO.md
  --todo <path>             Override TODO output path
  --json <path>             Override JSON candidate output path
  --ast-json <path>         Merge candidates from JDK AST call-site scanner when present
  --min-severity <level>    medium, high, critical (default: medium)
  --fail-on-critical        Exit non-zero when active critical candidates remain
  --include-root-covered    Include sinks already covered by root safety fixes
`);
}

function findRepoRoot(start) {
  let current = path.resolve(start);
  while (true) {
    if (fs.existsSync(path.join(current, 'gradlew')) && fs.existsSync(path.join(current, 'shreddedpaper-server'))) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      throw new Error('Could not find repository root');
    }
    current = parent;
  }
}

function walkFiles(root, relDir, out) {
  const absDir = path.join(root, relDir);
  if (!fs.existsSync(absDir)) {
    return;
  }

  for (const entry of fs.readdirSync(absDir, { withFileTypes: true })) {
    if (entry.name === 'build' || entry.name === '.gradle' || entry.name === 'run') {
      continue;
    }
    const rel = path.join(relDir, entry.name);
    if (IGNORED_PATH_PARTS.some(ignored => rel.includes(ignored))) {
      continue;
    }
    if (entry.isDirectory()) {
      walkFiles(root, rel, out);
    } else if (EXTENSIONS.has(path.extname(entry.name))) {
      out.push(rel);
    }
  }
}

function stripPatchPrefix(line, isPatch) {
  if (!isPatch) {
    return line;
  }
  if (line.startsWith('-')) {
    return null;
  }
  if (line.startsWith('+') || line.startsWith(' ')) {
    return line.slice(1);
  }
  return line;
}

function isIgnoredLine(line, relPath) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) {
    return true;
  }
  if (trimmed.includes('getBlockStateIfLoaded') || trimmed.includes('getFluidIfLoaded') || trimmed.includes('getChunkIfLoadedImmediately')) {
    return true;
  }
  if (trimmed.includes('getFluidState().')) {
    return true;
  }
  if (trimmed.match(/\b(?:public|private|protected)\s+(?:final\s+)?(?:@Nullable\s+)?BlockState\s+getBlockState\s*\(/)) {
    return true;
  }
  if (relPath.includes('/gametest/')) {
    return true;
  }
  return false;
}

function findContext(lines, index, isPatch) {
  for (let i = index; i >= 0 && i >= index - 90; i--) {
    const raw = stripPatchPrefix(lines[i], isPatch);
    if (raw == null) {
      continue;
    }
    const trimmed = raw.trim();
    if (trimmed.startsWith('class ') || trimmed.includes(' class ') || trimmed.includes(' interface ') || trimmed.includes(' record ')) {
      return trimmed.replace(/\s+/g, ' ');
    }
    if (
      /(?:public|private|protected|static|default|final|synchronized)\s+/.test(trimmed)
      && /\w[\w$<>[\], ?.@]*\s+\w[\w$]*\s*\([^;]*\)\s*(?:throws [^{]+)?\{?$/.test(trimmed)
    ) {
      return trimmed.replace(/\s+/g, ' ');
    }
  }
  return '(unknown context)';
}

function categoryFor(relPath, context, line) {
  const probe = `${relPath}\n${context}\n${line}`;
  if (/redstone|SignalGetter|NeighborUpdater|ScheduledTick|LevelTicks|FlowingFluid|LeavesBlock|Piston|RedStone|wire/i.test(probe)) {
    return 'scheduled tick/neighbor/fluid-leaf-redstone';
  }
  if (/Entity|LivingEntity|ServerPlayer|PlayerList|ServerGamePacketListenerImpl|Packet|teleport|disconnect|vehicle|movement/i.test(probe)) {
    return 'entity movement/teleport/player tick';
  }
  if (/NaturalSpawner|SpawnUtil|WorldGen|Structure|ChunkGenerator|SummonCommand|region-load-test|mobfarm|spawn/i.test(probe)) {
    return 'natural spawning/entity add/structure sync load';
  }
  if (/PlayerChunk|ChunkHolder|ChunkMap|TrackedEntity|broadcast|RegionizedPlayerChunkLoader|ChunkSender|holder/i.test(probe)) {
    return 'player chunk send/post-processing/stale holder broadcast';
  }
  return 'uncategorized sync-load risk';
}

function boostSeverity(base, relPath, category) {
  if (base === 'critical') {
    return base;
  }
  if (
    category !== 'uncategorized sync-load risk'
    || /server\/network|server\/level|world\/entity|world\/level\/redstone|world\/level\/block/i.test(relPath)
  ) {
    return base === 'medium' ? 'high' : base;
  }
  return base;
}

function severityRank(severity) {
  return { medium: 1, high: 2, critical: 3 }[severity] ?? 0;
}

function isRootCoveredSink(sink, includeRootCovered) {
  return !includeRootCovered && ROOT_COVERED_SINKS.has(sink);
}

function isGuardedSyncRoot(relPath, sink, includeRootCovered) {
  if (includeRootCovered) {
    return false;
  }
  return GUARDED_SYNC_ROOTS.some(root => relPath.includes(root.pathIncludes) && root.sinks.has(sink));
}

function makeId(relPath, context, sink, line) {
  const hash = crypto
    .createHash('sha1')
    .update(`${relPath}\n${context}\n${sink.name}\n${line.trim().replace(/\s+/g, ' ')}`)
    .digest('hex')
    .slice(0, 8)
    .toUpperCase();
  return `AO-${hash}`;
}

function scan(repoRoot, minSeverity, includeRootCovered) {
  const files = [];
  for (const dir of START_DIRS) {
    walkFiles(repoRoot, dir, files);
  }

  const candidates = [];
  const seen = new Set();

  for (const relPath of files.sort()) {
    const absPath = path.join(repoRoot, relPath);
    const isPatch = relPath.endsWith('.patch');
    const lines = fs.readFileSync(absPath, 'utf8').split(/\r?\n/);

    for (let i = 0; i < lines.length; i++) {
      const line = stripPatchPrefix(lines[i], isPatch);
      if (line == null || isIgnoredLine(line, relPath)) {
        continue;
      }

      for (const sink of SINKS) {
        if (isRootCoveredSink(sink.name, includeRootCovered) || isGuardedSyncRoot(relPath, sink.name, includeRootCovered)) {
          continue;
        }
        if (!sink.regex.test(line)) {
          continue;
        }
        const context = findContext(lines, i, isPatch);
        const category = categoryFor(relPath, context, line);
        const severity = boostSeverity(sink.severity, relPath, category);
        if (severityRank(severity) < severityRank(minSeverity)) {
          continue;
        }
        const id = makeId(relPath, context, sink, line);
        const dedupeKey = `${id}:${i + 1}`;
        if (seen.has(dedupeKey)) {
          continue;
        }
        seen.add(dedupeKey);
        candidates.push({
          id,
          title: `${sink.name} in ${path.basename(relPath)}`,
          severity,
          category,
          family: familyForSink(sink.name),
          intent: DEFAULT_INTENT,
          sink: sink.name,
          path: relPath,
          line: i + 1,
          context,
          evidence: line.trim().replace(/\s+/g, ' '),
          why: sink.why,
          fix: sink.fix,
        });
      }
    }
  }

  const merged = [...MANUAL_CANDIDATES, ...candidates];
  const byId = new Map();
  for (const candidate of merged) {
    if (!byId.has(candidate.id)) {
      byId.set(candidate.id, withAuditMetadata(candidate));
    }
  }
  const allCandidates = [...byId.values()];

  allCandidates.sort((a, b) => {
    const severity = severityRank(b.severity) - severityRank(a.severity);
    if (severity !== 0) {
      return severity;
    }
    const category = a.category.localeCompare(b.category);
    if (category !== 0) {
      return category;
    }
    const file = a.path.localeCompare(b.path);
    return file !== 0 ? file : a.line - b.line;
  });

  return allCandidates;
}

function loadAstCandidates(repoRoot, astJsonPath, minSeverity, includeRootCovered) {
  const absPath = path.resolve(repoRoot, astJsonPath);
  if (!fs.existsSync(absPath)) {
    return [];
  }
  const parsed = JSON.parse(fs.readFileSync(absPath, 'utf8'));
  if (!Array.isArray(parsed.candidates)) {
    return [];
  }
  return parsed.candidates
    .filter(candidate => severityRank(candidate.severity) >= severityRank(minSeverity))
    .filter(candidate => !isRootCoveredSink(candidate.sink, includeRootCovered))
    .filter(candidate => !isGuardedSyncRoot(candidate.path, candidate.sink, includeRootCovered))
    .map(candidate => ({
      id: candidate.id,
      title: candidate.title,
      severity: candidate.severity,
      category: candidate.category,
      family: familyForSink(candidate.sink),
      intent: DEFAULT_INTENT,
      sink: candidate.sink,
      path: candidate.path,
      line: candidate.line,
      context: candidate.context,
      evidence: candidate.evidence,
      why: `${candidate.why} Source: JDK AST MethodInvocationTree scanner.`,
      fix: candidate.fix,
    }));
}

function parseExistingTodo(absTodoPath) {
  const statuses = new Map();
  if (!fs.existsSync(absTodoPath)) {
    return statuses;
  }

  const text = fs.readFileSync(absTodoPath, 'utf8');
  const regex = /<!--\s*async-audit\s+id=([A-Z0-9-]+)\s+status=([a-z-]+)\s+owner=([^>]*?)\s*-->/g;
  let match;
  while ((match = regex.exec(text)) != null) {
    statuses.set(match[1], {
      status: match[2],
      owner: match[3].trim() || 'unassigned',
    });
  }
  return statuses;
}

function formatCandidate(candidate, existing) {
  const state = existing.get(candidate.id) ?? { status: 'todo', owner: 'unassigned' };
  return `### ${candidate.id} - ${candidate.title}
<!-- async-audit id=${candidate.id} status=${state.status} owner=${state.owner} -->
- Status: ${state.status}
- Owner: ${state.owner}
- Severity: ${candidate.severity}
- Category: ${candidate.category}
- Family: ${candidate.family}
- Intent: ${candidate.intent}
- Sink: ${candidate.sink}
- Location: \`${candidate.path}:${candidate.line}\`
- Context: \`${candidate.context}\`
- Evidence: \`${candidate.evidence}\`
- Why risky: ${candidate.why}
- Suggested fix: ${candidate.fix}
- Verification: run targeted unit/build checks, then MCC chaos for the matching bucket.
`;
}

function formatTrackedFix(fix, existing) {
  const state = existing.get(fix.id) ?? { status: 'done', owner: 'codex' };
  return `### ${fix.id} - ${fix.title}
<!-- async-audit id=${fix.id} status=${state.status} owner=${state.owner} -->
- Status: ${state.status}
- Owner: ${state.owner}
- Category: ${fix.category}
- Location: \`${fix.location}\`
- Evidence: ${fix.evidence}
- Verification: ${fix.verification}
`;
}

function countBy(items, keyFn) {
  const counts = new Map();
  for (const item of items) {
    const key = keyFn(item);
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return counts;
}

function mergeCandidates(primaryCandidates, extraCandidates) {
  const byLocation = new Set(primaryCandidates.map(candidate => `${candidate.path}:${candidate.line}:${candidate.sink}`));
  const byId = new Map();
  for (const candidate of primaryCandidates) {
    byId.set(candidate.id, withAuditMetadata(candidate));
  }
  for (const candidate of extraCandidates) {
    const locationKey = `${candidate.path}:${candidate.line}:${candidate.sink}`;
    if (byLocation.has(locationKey) || byId.has(candidate.id)) {
      continue;
    }
    byLocation.add(locationKey);
    byId.set(candidate.id, withAuditMetadata(candidate));
  }
  const merged = [...byId.values()];
  merged.sort((a, b) => {
    const severity = severityRank(b.severity) - severityRank(a.severity);
    if (severity !== 0) {
      return severity;
    }
    const category = a.category.localeCompare(b.category);
    if (category !== 0) {
      return category;
    }
    const file = a.path.localeCompare(b.path);
    return file !== 0 ? file : a.line - b.line;
  });
  return merged;
}

function renderTodo(repoRoot, candidates, existing) {
  const generatedAt = new Date().toISOString();
  const activeCounts = countBy(candidates, c => c.category);
  const familyCounts = countBy(candidates, c => c.family);
  const severityCounts = countBy(candidates, c => c.severity);

  const lines = [];
  lines.push('# Async Ownership TODO');
  lines.push('');
  lines.push('This is the shared backlog for independent region ticking hazards. Keep statuses in the metadata comment and the visible Status line in sync when assigning or closing work.');
  lines.push('');
  lines.push(`Generated: ${generatedAt}`);
  lines.push(`Scanner: \`node tools/async-audit/scan-async-ownership.mjs --write-todo\``);
  lines.push('');
  lines.push('Status values: `todo`, `investigating`, `fixing`, `blocked`, `done`, `false-positive`.');
  lines.push('');
  lines.push('## Summary');
  lines.push('');
  lines.push(`- Active candidates: ${candidates.length}`);
  lines.push(`- Tracked completed fixes: ${TRACKED_FIXES.length}`);
  lines.push('');
  lines.push('| Severity | Count |');
  lines.push('| --- | ---: |');
  for (const severity of ['critical', 'high', 'medium']) {
    lines.push(`| ${severity} | ${severityCounts.get(severity) ?? 0} |`);
  }
  lines.push('');
  lines.push('| Family | Count |');
  lines.push('| --- | ---: |');
  for (const [family, count] of [...familyCounts.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    lines.push(`| ${family} | ${count} |`);
  }
  lines.push('');
  lines.push('| Category | Count |');
  lines.push('| --- | ---: |');
  for (const [category, count] of [...activeCounts.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    lines.push(`| ${category} | ${count} |`);
  }
  lines.push('');
  lines.push('## Assignment Rules');
  lines.push('');
  lines.push('- A worker owns one category or one narrow file set at a time.');
  lines.push('- Mark `status=fixing owner=<name>` before editing.');
  lines.push('- Mark `done` only after source patch, generated source, build, and targeted chaos evidence exist.');
  lines.push('- Mark `false-positive` only with a short reason in the item body.');
  lines.push('- Do not silence `TickThread`/sync-load guards to close an item.');
  lines.push('- Do not treat `Active candidates: 0` as release-ready by itself; runtime gates below must also pass.');
  lines.push('');
  lines.push('## Release Verification Gates');
  lines.push('');
  lines.push('Run these gates before claiming async ownership work is release-ready:');
  lines.push('');
  lines.push('Single-command runner: `node tools/runtime/invoke-async-release-gates.mjs`.');
  lines.push('When the live `worldgen` server is busy, use the isolated server variant: `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`.');
  lines.push('Runs with `--skip-build`, `--skip-mcc`, or `--skip-worldgen` are partial smoke checks only; they must not be used as release-ready evidence.');
  lines.push('');
  lines.push('Expanded gate list:');
  lines.push('');
  lines.push('1. `./gradlew applyAllPatches --no-configuration-cache`');
  lines.push('2. `./gradlew :shreddedpaper-server:compileJava --rerun-tasks --no-configuration-cache`');
  lines.push('3. `./gradlew :shreddedpaper-server:createMojmapPaperclipJar --rerun-tasks --no-configuration-cache`');
  lines.push('4. `./gradlew -p tools/region-load-test-plugin build --no-configuration-cache`');
  lines.push('5. `node tools/async-audit/scan-async-ownership.mjs --write-todo --fail-on-critical`');
  lines.push('6. `node tools/runtime/verify-async-root-baseline.mjs`');
  lines.push('7. `tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --min-bots 4 --duration-sec 180 --server-port 25566 --rcon-port 25576 --websocket-base-port 8060 --skip-build --agent-mode ReportOnly`');
  lines.push('8. `node tools/runtime/scan-server-log.mjs run/mcc-chaos/results/cycle-01`');
  lines.push('9. Run managed worldgen smoke. Preferred when `worldgen` ports are idle: `node tools/runtime/invoke-worldgen-smoke.mjs --server-dir /Users/jungwuk/Documents/works/worldgen --java /opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java --heap 4G`.');
  lines.push('10. If the live `worldgen` server is already running, use an isolated smoke server on free ports: `node tools/runtime/invoke-async-release-gates.mjs --isolated-worldgen`.');
  lines.push('11. After any managed smoke, verify no Java process, RCON/server port, or `world/session.lock` remains unless the server was intentionally left running.');
  lines.push('');
  lines.push('The worldgen smoke intentionally fails if RCON is already reachable unless both `--attach-existing` and `--allow-live-probes` are passed, so accidental destructive RLT probes require two explicit opt-ins.');
  lines.push('');
  lines.push('## Completed Fixes');
  lines.push('');
  for (const fix of TRACKED_FIXES) {
    lines.push(formatTrackedFix(fix, existing));
  }
  lines.push('## Active Candidates');
  lines.push('');
  for (const candidate of candidates) {
    lines.push(formatCandidate(candidate, existing));
  }
  return `${lines.join('\n')}\n`;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const repoRoot = findRepoRoot(process.cwd());
  const regexCandidates = scan(repoRoot, args.minSeverity, args.includeRootCovered);
  const astCandidates = loadAstCandidates(repoRoot, args.astJsonPath, args.minSeverity, args.includeRootCovered);
  const candidates = mergeCandidates(regexCandidates, astCandidates);
  const jsonPath = path.resolve(repoRoot, args.jsonPath);
  fs.mkdirSync(path.dirname(jsonPath), { recursive: true });
  fs.writeFileSync(jsonPath, `${JSON.stringify({ generatedAt: new Date().toISOString(), candidates }, null, 2)}\n`);

  if (args.writeTodo) {
    const todoPath = path.resolve(repoRoot, args.todoPath);
    fs.mkdirSync(path.dirname(todoPath), { recursive: true });
    const existing = parseExistingTodo(todoPath);
    fs.writeFileSync(todoPath, renderTodo(repoRoot, candidates, existing));
  }

  const bySeverity = countBy(candidates, c => c.severity);
  const byFamily = countBy(candidates, c => c.family);
  console.log(`Async ownership candidates: ${candidates.length}`);
  console.log(`critical=${bySeverity.get('critical') ?? 0} high=${bySeverity.get('high') ?? 0} medium=${bySeverity.get('medium') ?? 0}`);
  console.log('Families:');
  for (const [family, count] of [...byFamily.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
    console.log(`  ${family}=${count}`);
  }
  if (astCandidates.length > 0) {
    console.log(`Merged JDK AST candidates: ${astCandidates.length}`);
  }
  console.log(`JSON: ${path.relative(repoRoot, jsonPath)}`);
  if (args.writeTodo) {
    console.log(`TODO: ${args.todoPath}`);
  }
  if (args.failOnCritical && (bySeverity.get('critical') ?? 0) > 0) {
    process.exitCode = 1;
  }
}

main();
