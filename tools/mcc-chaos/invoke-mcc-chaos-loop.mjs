#!/usr/bin/env node

import fs from "node:fs/promises";
import fsSync from "node:fs";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import process from "node:process";
import { randomBytes } from "node:crypto";
import { spawn } from "node:child_process";
import { setTimeout as sleep } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..", "..");

const DEFAULT_FAILURE_PATTERNS = [
  "Encountered an unexpected exception",
  "Exception ticking world",
  "Failed to handle packet",
  "Thread failed main thread check",
  "tried to run a task from the wrong thread",
  "Synchronous chunk load is not allowed",
  "Cannot merge non-quiescent",
  "ConcurrentModificationException",
  "IllegalStateException",
  "NullPointerException",
  "IndexOutOfBoundsException",
  "UnsupportedOperationException",
  "ClassCastException",
  "Crash report",
  "Watching Server",
  "This crash report has been saved",
  "AsyncCatcher",
  "wrong thread",
  "not owned",
  "already retired",
  "Block change is not write locked",
  "Cannot add entity off-main",
  "Cannot move an entity into off-main",
  "Cannot perform command async",
  "Chunk system crash propagated",
  "Critical region mailbox reserve exceeded",
];

const FAILURE_BUCKETS = [
  {
    name: "scheduled tick/neighbor/fluid-leaf-redstone",
    patterns: [
      "LevelTicks",
      "ScheduledTick",
      "scheduleTick",
      "NeighborUpdater",
      "BlockEventData",
      "FlowingFluid",
      "LiquidBlock",
      "LeavesBlock",
      "RedStoneWireBlock",
      "redstone",
      "fluid",
      "leaf",
      "lava",
      "water",
    ],
  },
  {
    name: "entity movement/teleport/player tick",
    patterns: [
      "ServerGamePacketListenerImpl",
      "handleMovePlayer",
      "PlayerList",
      "PlayerChunkLoader",
      "tickNonPassenger",
      "Entity.move",
      "absMoveTo",
      "teleport",
      "moved too quickly",
      "moved wrongly",
    ],
  },
  {
    name: "natural spawning/entity add/structure sync load",
    patterns: [
      "NaturalSpawner",
      "spawnForChunk",
      "MobSpawn",
      "addFreshEntity",
      "addEntity",
      "Structure",
      "ChunkGeneratorStructureState",
      "StructureManager",
      "Synchronous chunk load",
      "getChunkAt",
    ],
  },
  {
    name: "player chunk send/post-processing/stale holder broadcast",
    patterns: [
      "RegionizedPlayerChunkLoader",
      "PlayerChunkSender",
      "ChunkMap",
      "ChunkHolder",
      "NewChunkHolder",
      "stale holder",
      "post-processing",
      "broadcast",
      "tracker",
      "ClientboundLevelChunk",
      "FullChunkStatus",
    ],
  },
];

const RLT_ENTITY_TAG = "shreddedpaper_rlt";
const ANARCHY_BOT_ARENAS = [
  { x: 384, y: 8, z: 0, label: "redstone-raid" },
  { x: -384, y: 8, z: 0, label: "mobfarm-cram" },
  { x: 0, y: 8, z: -384, label: "pvp-arena" },
  { x: 0, y: 8, z: 384, label: "vehicle-passenger" },
  { x: 128, y: 8, z: 0, label: "fluid-leaf-boundary" },
];

function anarchyBotArena(index) {
  return ANARCHY_BOT_ARENAS[index % ANARCHY_BOT_ARENAS.length];
}

function anarchyBotSpawn(index) {
  const arena = anarchyBotArena(index);
  return {
    x: arena.x + ((index % 3) - 1) * 4,
    y: arena.y,
    z: arena.z + 28 + Math.floor(index / 3) * 4,
    label: arena.label,
  };
}

function parseArgs(argv) {
  const args = {
    root: "",
    javaPath: process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java",
    mccPath: "",
    botCount: 4,
    minBots: 0,
    durationSec: 300,
    maxCycles: 1,
    agentMode: "ReportOnly",
    codexCommand: "codex",
    heapGb: 6,
    serverPort: 25565,
    rconPort: 25575,
    webSocketBasePort: 8043,
    rconPassword: "codex-mcc-chaos",
    webSocketPassword: "",
    seed: "",
    chaosProfile: "anarchy-smp",
    chaosIntensity: 2,
    skipBuild: false,
    skipMccDownload: false,
    noNetwork: false,
    noStop: false,
    enableNaturalSpawns: false,
    failOnMovementWarnings: false,
    dryRun: false,
  };

  const aliases = new Map([
    ["--Root", "root"],
    ["--root", "root"],
    ["--JavaPath", "javaPath"],
    ["--java-path", "javaPath"],
    ["--MccPath", "mccPath"],
    ["--mcc-path", "mccPath"],
    ["--BotCount", "botCount"],
    ["--bot-count", "botCount"],
    ["--MinBots", "minBots"],
    ["--min-bots", "minBots"],
    ["--DurationSec", "durationSec"],
    ["--duration-sec", "durationSec"],
    ["--MaxCycles", "maxCycles"],
    ["--max-cycles", "maxCycles"],
    ["--AgentMode", "agentMode"],
    ["--agent-mode", "agentMode"],
    ["--CodexCommand", "codexCommand"],
    ["--codex-command", "codexCommand"],
    ["--HeapGb", "heapGb"],
    ["--heap-gb", "heapGb"],
    ["--ServerPort", "serverPort"],
    ["--server-port", "serverPort"],
    ["--RconPort", "rconPort"],
    ["--rcon-port", "rconPort"],
    ["--WebSocketBasePort", "webSocketBasePort"],
    ["--websocket-base-port", "webSocketBasePort"],
    ["--RconPassword", "rconPassword"],
    ["--rcon-password", "rconPassword"],
    ["--WebSocketPassword", "webSocketPassword"],
    ["--websocket-password", "webSocketPassword"],
    ["--Seed", "seed"],
    ["--seed", "seed"],
    ["--ChaosProfile", "chaosProfile"],
    ["--chaos-profile", "chaosProfile"],
    ["--ChaosIntensity", "chaosIntensity"],
    ["--chaos-intensity", "chaosIntensity"],
  ]);

  const switches = new Map([
    ["--SkipBuild", "skipBuild"],
    ["--skip-build", "skipBuild"],
    ["--SkipMccDownload", "skipMccDownload"],
    ["--skip-mcc-download", "skipMccDownload"],
    ["--Offline", "noNetwork"],
    ["--offline", "noNetwork"],
    ["--NoNetwork", "noNetwork"],
    ["--no-network", "noNetwork"],
    ["--NoStop", "noStop"],
    ["--no-stop", "noStop"],
    ["--EnableNaturalSpawns", "enableNaturalSpawns"],
    ["--enable-natural-spawns", "enableNaturalSpawns"],
    ["--FailOnMovementWarnings", "failOnMovementWarnings"],
    ["--fail-on-movement-warnings", "failOnMovementWarnings"],
    ["--dry-run", "dryRun"],
  ]);

  for (let i = 2; i < argv.length; i++) {
    const raw = argv[i];
    if (raw === "--help" || raw === "-h") {
      printHelp();
      process.exit(0);
    }
    const [flag, inlineValue] = raw.includes("=") ? raw.split(/=(.*)/s, 2) : [raw, null];
    if (switches.has(flag)) {
      args[switches.get(flag)] = true;
      continue;
    }
    if (!aliases.has(flag)) {
      throw new Error(`Unknown argument: ${raw}. Use --help.`);
    }
    const key = aliases.get(flag);
    const value = inlineValue ?? argv[++i];
    if (value === undefined) {
      throw new Error(`Missing value for ${flag}`);
    }
    if (["botCount", "minBots", "durationSec", "maxCycles", "heapGb", "serverPort", "rconPort", "webSocketBasePort", "chaosIntensity"].includes(key)) {
      args[key] = Number.parseInt(value, 10);
    } else {
      args[key] = value;
    }
  }

  if (!["ReportOnly", "Prompt", "AutoFix"].includes(args.agentMode)) {
    throw new Error("--agent-mode must be ReportOnly, Prompt, or AutoFix");
  }
  if (!Number.isInteger(args.botCount) || args.botCount < 0) {
    throw new Error("--bot-count must be zero or greater");
  }
  if (!args.minBots) {
    args.minBots = args.botCount <= 1 ? args.botCount : Math.max(1, Math.floor(args.botCount * 0.75));
  }
  if (!Number.isInteger(args.minBots) || args.minBots < 0 || args.minBots > args.botCount) {
    throw new Error("--min-bots must be an integer from 0 to --bot-count");
  }
  if (!["baseline", "anarchy-smp"].includes(args.chaosProfile)) {
    throw new Error("--chaos-profile must be baseline or anarchy-smp");
  }
  if (!Number.isInteger(args.chaosIntensity) || args.chaosIntensity < 1 || args.chaosIntensity > 4) {
    throw new Error("--chaos-intensity must be an integer from 1 to 4");
  }
  if (!args.root) {
    args.root = path.join(repoRoot, "run", "mcc-chaos");
  }
  if (!args.seed) {
    args.seed = new Date().toISOString().replace(/[-:TZ.]/g, "").slice(0, 14);
  }
  if (!args.webSocketPassword) {
    args.webSocketPassword = `mcc-chaos-${cryptoRandomHex(16)}`;
  }
  if (args.noNetwork) {
    args.skipMccDownload = true;
  }
  return args;
}

function printHelp() {
  console.log(`Usage:
  tools/mcc-chaos/Invoke-MccChaosLoop.sh [options]

Examples:
  tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 4 --duration-sec 300
  tools/mcc-chaos/Invoke-MccChaosLoop.sh --bot-count 8 --duration-sec 600 --max-cycles 5 --agent-mode AutoFix

Important options:
  --mcc-path <path>              Use an existing macOS/Linux MinecraftClient binary
  --min-bots <count>             Minimum bots that must join before chaos starts (default: floor(75%))
  --skip-build                   Reuse existing built server/plugin artifacts
  --skip-mcc-download            Fail instead of downloading MCC
  --offline / --no-network       Require cached/local MCC and WebSocketBot.cs
  --enable-natural-spawns        Enable natural spawn fixture
  --chaos-profile <name>         baseline or anarchy-smp (default: anarchy-smp)
  --chaos-intensity <1..4>       Scale SMP fixture density (default: 2)
  --fail-on-movement-warnings    Treat moved-too-quickly/wrongly as failures
  --dry-run                      Validate paths/config without starting processes
`);
}

function cryptoRandomHex(bytes) {
  return randomBytes(bytes).toString("hex");
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function writeUtf8(file, text) {
  await ensureDir(path.dirname(file));
  await fs.writeFile(file, text, "utf8");
}

function assertUnderRoot(rootPath, candidate) {
  const fullRoot = path.resolve(rootPath) + path.sep;
  const full = path.resolve(candidate);
  if (!full.startsWith(fullRoot)) {
    throw new Error(`Refusing to operate outside chaos root: ${full}`);
  }
}

function assertArtifactFresh(artifactPath, sourcePaths, label) {
  if (!fsSync.existsSync(artifactPath)) {
    throw new Error(`${label} is missing: ${artifactPath}`);
  }
  const artifactMtime = fsSync.statSync(artifactPath).mtimeMs;
  const staleSource = sourcePaths
    .filter((sourcePath) => fsSync.existsSync(sourcePath))
    .find((sourcePath) => fsSync.statSync(sourcePath).mtimeMs > artifactMtime + 1000);
  if (staleSource) {
    throw new Error(`${label} is older than ${path.relative(repoRoot, staleSource)}. Rebuild it or run without --skip-build.`);
  }
}

function quoteArg(arg) {
  return /\s/.test(arg) ? `"${arg.replaceAll('"', '\\"')}"` : arg;
}

async function runLogged(file, args, cwd, logPath, options = {}) {
  await ensureDir(path.dirname(logPath));
  const outPath = `${logPath}.out`;
  const errPath = `${logPath}.err`;
  await writeUtf8(logPath, `COMMAND: ${file} ${args.map(quoteArg).join(" ")}\n`);
  const out = fsSync.openSync(outPath, "w");
  const err = fsSync.openSync(errPath, "w");
  const child = spawn(file, args, { cwd, stdio: ["ignore", out, err], env: process.env });
  let timedOut = false;
  const timer = options.timeoutMs
    ? setTimeout(() => {
        timedOut = true;
        killProcessTree(child).catch(() => {});
      }, options.timeoutMs)
    : null;
  const exitCode = await waitForExit(child);
  if (timer) {
    clearTimeout(timer);
  }
  fsSync.closeSync(out);
  fsSync.closeSync(err);
  await fs.appendFile(logPath, await readMaybe(outPath), "utf8");
  await fs.appendFile(logPath, await readMaybe(errPath), "utf8");
  const finalExitCode = timedOut ? 124 : exitCode;
  if (finalExitCode !== 0 && !options.allowFailure) {
    const timeoutMessage = timedOut ? " after timeout" : "";
    throw new Error(`Command failed${timeoutMessage} with exit code ${finalExitCode}: ${file} ${args.join(" ")}. See ${logPath}`);
  }
  return finalExitCode;
}

function waitForExit(child) {
  return new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("exit", (code, signal) => resolve(code ?? (signal ? 128 : 1)));
  });
}

async function readMaybe(file) {
  try {
    return await fs.readFile(file, "utf8");
  } catch {
    return "";
  }
}

function makeRconPacket(id, type, payload) {
  const payloadBytes = Buffer.from(payload, "utf8");
  const length = 10 + payloadBytes.length;
  const packet = Buffer.alloc(4 + length);
  packet.writeInt32LE(length, 0);
  packet.writeInt32LE(id, 4);
  packet.writeInt32LE(type, 8);
  payloadBytes.copy(packet, 12);
  return packet;
}

function readRconPacket(socket, timeoutMs) {
  return new Promise((resolve, reject) => {
    let buffer = Buffer.alloc(0);
    const timer = setTimeout(() => cleanup(new Error("RCON read timeout")), timeoutMs);
    const onData = (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length < 4) {
        return;
      }
      const length = buffer.readInt32LE(0);
      if (buffer.length < 4 + length) {
        return;
      }
      const data = buffer.subarray(4, 4 + length);
      cleanup(null, {
        id: data.readInt32LE(0),
        type: data.readInt32LE(4),
        payload: data.subarray(8, Math.max(8, data.length - 2)).toString("utf8"),
      });
    };
    const cleanup = (error, packet = null) => {
      clearTimeout(timer);
      socket.off("data", onData);
      socket.off("error", cleanup);
      if (error instanceof Error) {
        reject(error);
      } else {
        resolve(packet);
      }
    };
    socket.on("data", onData);
    socket.once("error", cleanup);
  });
}

async function sendRconCommand(args, command, timeoutMs = 10000) {
  const socket = net.createConnection({ host: "127.0.0.1", port: args.rconPort });
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });
  try {
    socket.write(makeRconPacket(1, 3, args.rconPassword));
    const auth = await readRconPacket(socket, timeoutMs);
    if (auth.id === -1) {
      throw new Error(`RCON authentication failed on port ${args.rconPort}`);
    }
    socket.write(makeRconPacket(2, 2, command));
    const response = await readRconPacket(socket, timeoutMs);
    return response.payload;
  } finally {
    socket.destroy();
  }
}

async function testRconReady(args) {
  try {
    await sendRconCommand(args, "list", 2500);
    return true;
  } catch {
    return false;
  }
}

async function waitForServerReady(args, serverDir, child, timeoutSec = 240) {
  const deadline = Date.now() + timeoutSec * 1000;
  const latestLog = path.join(serverDir, "logs", "latest.log");
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`Server process exited before ready. ExitCode=${child.exitCode}`);
    }
    const tail = await readTail(latestLog, 120);
    if (tail.includes("Done (") && await testRconReady(args)) {
      return;
    }
    await sleep(500);
  }
  throw new Error("Timed out waiting for server readiness.");
}

async function readTail(file, maxLines) {
  const text = await readMaybe(file);
  return text.split(/\r?\n/).slice(-maxLines).join("\n");
}

async function fileOffset(file) {
  try {
    return (await fs.stat(file)).size;
  } catch {
    return 0;
  }
}

async function fileSince(file, offset) {
  try {
    const fh = await fs.open(file, "r");
    try {
      const stat = await fh.stat();
      const start = offset > stat.size ? 0 : offset;
      const buffer = Buffer.alloc(stat.size - start);
      await fh.read(buffer, 0, buffer.length, start);
      return buffer.toString("utf8");
    } finally {
      await fh.close();
    }
  } catch {
    return "";
  }
}

async function botJoinDiagnostics(botInfos, joinedNames) {
  const joined = new Set(joinedNames);
  const lines = [];
  for (const bot of botInfos) {
    if (joined.has(bot.name)) {
      continue;
    }
    const stderr = (await readMaybe(path.join(bot.dir, "stderr.log"))).trim().split(/\r?\n/).slice(-8).join(" | ");
    const consoleLog = (await readMaybe(path.join(bot.dir, "mcc-console.log"))).trim().split(/\r?\n/).slice(-8).join(" | ");
    if (stderr || consoleLog) {
      lines.push(`${bot.name}: stderr=${stderr || "(empty)"} console=${consoleLog || "(empty)"}`);
    } else {
      lines.push(`${bot.name}: no MCC stderr or console log was written`);
    }
  }
  return lines.join("; ");
}

async function waitForPlayers(args, names, processes = [], botInfos = [], timeoutSec = 140, requiredCount = null, options = {}) {
  if (names.length === 0) {
    return [];
  }
  const minBots = Math.min(names.length, requiredCount ?? (args.minBots || names.length));
  const deadline = Date.now() + timeoutSec * 1000;
  const restartCounts = new Array(processes.length).fill(0);
  const maxRestarts = options.maxRestarts ?? 0;
  let lastJoined = [];
  let lastExited = [];
  while (Date.now() < deadline) {
    const exited = processes
      .map((child, index) => ({ child, index }))
      .filter(({ child }) => isChildExited(child));
    lastExited = exited;
    if (options.restartExited && exited.length > 0) {
      let restarted = false;
      for (const { index, child } of exited) {
        const bot = botInfos[index];
        if (!bot || restartCounts[index] >= maxRestarts) {
          continue;
        }
        restartCounts[index]++;
        console.warn(`Restarting exited MCC bot ${bot.name} during join wait: exit=${describeChildExit(child)}, attempt=${restartCounts[index]}/${maxRestarts}`);
        const restartedChild = await startMccBot(bot);
        processes[index] = restartedChild;
        if (typeof options.onRestart === "function") {
          options.onRestart(index, restartedChild, bot);
        }
        restarted = true;
        await sleep(1000);
      }
      if (restarted) {
        continue;
      }
    }
    if (exited.length > names.length - minBots) {
      const namesExited = exited.map(({ index, child }) => `${names[index] ?? `bot#${index + 1}`} exit=${describeChildExit(child)}`).join(", ");
      const diagnostics = await botJoinDiagnostics(botInfos, lastJoined);
      throw new Error(`MCC process exited before enough players joined: required=${minBots}/${names.length}, joined=${lastJoined.join(",") || "(none)"}, exited=${namesExited}. Diagnostics: ${diagnostics}`);
    }
    const list = await sendRconCommand(args, "list", 5000);
    lastJoined = names.filter((name) => list.includes(name));
    if (lastJoined.length >= minBots) {
      return lastJoined;
    }
    await sleep(1000);
  }
  const namesExited = lastExited.map(({ index, child }) => `${names[index] ?? `bot#${index + 1}`} exit=${describeChildExit(child)}`).join(", ");
  const diagnostics = await botJoinDiagnostics(botInfos, lastJoined);
  throw new Error(`Timed out waiting for MCC players to join: required=${minBots}/${names.length}, joined=${lastJoined.join(",") || "(none)"}, exited=${namesExited || "(none)"}. Diagnostics: ${diagnostics}`);
}

function isChildExited(child) {
  if (!child) {
    return true;
  }
  if (child.exitCode !== null || child.signalCode !== null) {
    return true;
  }
  if (!child.pid) {
    return true;
  }
  return !isProcessAlive(child.pid);
}

function isProcessAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function describeChildExit(child) {
  if (!child) {
    return "missing-child";
  }
  if (child.exitCode !== null) {
    return `code=${child.exitCode}`;
  }
  if (child.signalCode !== null) {
    return `signal=${child.signalCode}`;
  }
  if (!child.pid || !isProcessAlive(child.pid)) {
    return `pid=${child.pid ?? "unknown"} missing`;
  }
  return `pid=${child.pid} still-running`;
}

async function invokeRconCommands(args, commands, rconLog, timeoutMs = 15000, pauseMs = 0) {
  for (const command of commands) {
    try {
      const response = await sendRconCommand(args, command, timeoutMs);
      await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
      const plain = stripMinecraftColors(response);
      if (isBadRconResponse(plain)) {
        throw new Error(`RCON command failed: ${command}\n${plain.trim()}`);
      }
    } catch (error) {
      await fs.appendFile(rconLog, `> ${command}\nERROR: ${error.message}\n\n`, "utf8");
      throw error;
    }
    if (pauseMs > 0) {
      await sleep(pauseMs);
    }
  }
}

function stripMinecraftColors(text) {
  return text.replace(/\u00a7./g, "");
}

function isBadRconResponse(text) {
  return /(^|\n)\s*(Error:|Unknown or incomplete command|Incorrect argument for command|An unexpected error occurred|No player was found|No entity was found|Player not found|That position is not loaded|Too many blocks in the specified area|Broadcast target chunk is not loaded|Broadcast target chunk unloaded|Run \/rlt chunkgen|Usage: \/rlt|RLT playercheck failed|chunks must|tasks must|radiusChunks must|regions\/strideChunks must)/i.test(text)
    || /(?:Block change is not write locked|Synchronous chunk load is not allowed)/i.test(text)
    || /(?:Broadcast scheduling failed|target chunk is not loaded|sync-?load probe .*failed|failed=[1-9]\d*|failure=[1-9]\d*|rejected=[1-9]\d*|null=[1-9]\d*)/i.test(text);
}

function parseRltStatus(text) {
  const match = /RegionLoadTest status:.*activeChunkBatches=(\d+)/i.exec(stripMinecraftColors(text));
  return match ? Number.parseInt(match[1], 10) : null;
}

function parseRegionLoadTestStatus(text) {
  const plain = stripMinecraftColors(text);
  const counters = {};
  for (const key of ["managedTasks", "managedEntities", "managedChunkTickets", "activeChunkBatches"]) {
    const match = new RegExp(`${key}=([0-9]+)`).exec(plain);
    if (!match) {
      throw new Error(`Could not parse RegionLoadTest status counter ${key}: ${plain.trim()}`);
    }
    counters[key] = Number.parseInt(match[1], 10);
  }
  return counters;
}

async function waitForRltIdle(args, rconLog, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs;
  let lastStatus = "";
  while (Date.now() < deadline) {
    const response = await sendRconCommand(args, "rlt status", 10000);
    await fs.appendFile(rconLog, `> rlt status\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response);
    if (isBadRconResponse(plain)) {
      throw new Error(`RLT status failed:\n${plain.trim()}`);
    }
    lastStatus = plain.trim();
    const activeChunkBatches = parseRltStatus(plain);
    if (activeChunkBatches === 0) {
      return;
    }
    await sleep(1000);
  }
  throw new Error(`Timed out waiting for RLT chunk batches to finish. Last status: ${lastStatus || "(none)"}`);
}

async function assertRegionLoadTestClean(args, rconLog, pluginJar) {
  if (!fsSync.existsSync(pluginJar)) {
    return;
  }
  await invokeRconCommands(args, ["rlt cleanup"], rconLog, 45000, 250);
  const deadline = Date.now() + 120000;
  let lastStatus = "";
  while (Date.now() < deadline) {
    await sleep(1000);
    const status = await sendRconCommand(args, "rlt status", 45000);
    await fs.appendFile(rconLog, `> rlt status\n${status}\n\n`, "utf8");
    const plain = stripMinecraftColors(status);
    if (isBadRconResponse(plain)) {
      throw new Error(`RLT status failed after cleanup:\n${plain.trim()}`);
    }
    lastStatus = plain.trim();
    const counters = parseRegionLoadTestStatus(plain);
    const nonZero = Object.entries(counters).filter(([, value]) => value !== 0);
    if (nonZero.length === 0) {
      await waitForNoTaggedRltEntities(args, rconLog, 30000);
      return;
    }
  }
  throw new Error(`RegionLoadTest did not clean up fully before timeout. Last status: ${lastStatus || "(none)"}`);
}

async function waitForNoTaggedRltEntities(args, rconLog, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastFound = [];
  while (Date.now() < deadline) {
    lastFound = await probeTaggedRltEntities(args, rconLog);
    if (lastFound.length === 0) {
      return;
    }
    await sleep(1000);
  }
  await assertNoTaggedRltEntities(args, rconLog, lastFound);
}

async function probeTaggedRltEntities(args, rconLog) {
  const found = [];
  for (const dimension of ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]) {
    const command = `execute in ${dimension} if entity @e[tag=${RLT_ENTITY_TAG},limit=1]`;
    const response = await sendRconCommand(args, command, 45000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    if (plain && !/test failed/i.test(plain)) {
      found.push(`${dimension}: ${plain}`);
    }
  }
  return found;
}

async function assertNoTaggedRltEntities(args, rconLog, previousFindings = []) {
  const killed = [];
  for (const dimension of ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]) {
    const command = `execute in ${dimension} run kill @e[tag=${RLT_ENTITY_TAG}]`;
    const response = await sendRconCommand(args, command, 45000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    if (/\bKilled\b/i.test(plain)) {
      killed.push(`${dimension}: ${plain}`);
    }
  }
  if (killed.length > 0) {
    throw new Error(`RegionLoadTest cleanup left tagged live entities:\n${killed.join("\n")}`);
  }
  if (previousFindings.length > 0) {
    throw new Error(`RegionLoadTest cleanup still saw tagged live entities before final kill:\n${previousFindings.join("\n")}`);
  }
}

async function assertOwnershipCountersClean(args, rconLog) {
  const status = await sendRconCommand(args, "region ownership", 45000);
  await fs.appendFile(rconLog, `> region ownership\n${status}\n\n`, "utf8");
  const plain = stripMinecraftColors(status);
  if (isBadRconResponse(plain)) {
    throw new Error(`Ownership counter command failed:\n${plain.trim()}`);
  }
  const counters = parseOwnershipCounters(plain);
  const fatalKeys = [
    "ownerHandoffRequeues",
    "ownerHandoffRejections",
    "prefetchFailures",
  ];
  const nonZero = fatalKeys
    .map((key) => [key, counters[key] ?? 0])
    .filter(([, value]) => value !== 0);
  const loadedReadIssue = loadedReadFallbackIssue(counters.loadedReadFallbacks ?? 0, plain);
  if (loadedReadIssue) {
    nonZero.unshift(["loadedReadFallbacks", counters.loadedReadFallbacks ?? 0]);
  }
  if (nonZero.length > 0) {
    const detail = loadedReadIssue ? `\n${loadedReadIssue}` : "";
    throw new Error(`Ownership guard counters are not clean: ${nonZero.map(([key, value]) => `${key}=${value}`).join(", ")}${detail}\n${plain.trim()}`);
  }
}

async function waitForPlayerLifecycleState(args, playerName, mode, rconLog, pluginJar, timeoutMs = 30000, expectedWorld = null, options = {}) {
  const deadline = Date.now() + timeoutMs;
  let lastResponse = "";
  while (Date.now() < deadline) {
    const child = typeof options.childProvider === "function" ? options.childProvider() : null;
    if (child && isChildExited(child)) {
      throw new Error(`MCC process for ${playerName} exited while waiting for lifecycle state ${mode}: ${describeChildExit(child)}`);
    }
    if (fsSync.existsSync(pluginJar)) {
      const command = expectedWorld
        ? `rlt playercheck ${playerName} ${mode} ${expectedWorld}`
        : `rlt playercheck ${playerName} ${mode}`;
      const response = await sendRconCommand(args, command, 15000);
      await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
      const plain = stripMinecraftColors(response).trim();
      lastResponse = plain;
      if (plain.includes("RLT playercheck ok:")) {
        return;
      }
    } else {
      const response = await sendRconCommand(args, "list", 15000);
      await fs.appendFile(rconLog, `> list\n${response}\n\n`, "utf8");
      const present = response.includes(playerName);
      lastResponse = stripMinecraftColors(response).trim();
      if ((mode === "present" && present) || (mode === "absent" && !present)) {
        return;
      }
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for MCC player ${playerName} to become ${mode}${expectedWorld ? ` in ${expectedWorld}` : ""}. Last response: ${lastResponse || "(none)"}`);
}

async function waitForPlayerNearPosition(args, playerName, expected, rconLog, timeoutMs = 30000, radius = 8, stableSamples = 3, options = {}) {
  const deadline = Date.now() + timeoutMs;
  let consecutive = 0;
  let lastResponse = "";
  const command = `execute positioned ${expected.x} ${expected.y} ${expected.z} if entity @a[name=${playerName},distance=..${radius}]`;
  while (Date.now() < deadline) {
    const child = typeof options.childProvider === "function" ? options.childProvider() : null;
    if (child && isChildExited(child)) {
      throw new Error(`MCC process for ${playerName} exited while waiting near ${expected.x},${expected.y},${expected.z}: ${describeChildExit(child)}`);
    }
    const response = await sendRconCommand(args, command, 15000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    lastResponse = plain;
    if (/test passed|found/i.test(plain)) {
      consecutive++;
      if (consecutive >= stableSamples) {
        return;
      }
    } else {
      consecutive = 0;
    }
    await sleep(750);
  }
  throw new Error(`Timed out waiting for ${playerName} to remain near ${expected.x},${expected.y},${expected.z} within ${radius} blocks. Last response: ${lastResponse || "(none)"}`);
}

async function stabilizeMccBotInArena(args, bot, processIndex, spawn, rconLog, mccProcesses = null, pluginJar = null, context = "arena readiness", maxAttempts = 4) {
  let lastError = null;
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      if (mccProcesses) {
        const child = mccProcesses[processIndex];
        if (!child || isChildExited(child)) {
      await restartMccBotForReconnect(args, bot, processIndex, mccProcesses, rconLog, `${context}: process was not running before attempt ${attempt}/${maxAttempts}`);
        }
      }
      await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 30000, null, {
        childProvider: mccProcesses ? () => mccProcesses[processIndex] : null,
      });
      await invokeRconCommands(args, botArenaStabilizeCommands(bot.name, spawn), rconLog, 15000, 250);
      await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 8000, null, {
        childProvider: mccProcesses ? () => mccProcesses[processIndex] : null,
      });
      await waitForPlayerNearPosition(args, bot.name, spawn, rconLog, 20000, 8, 2, {
        childProvider: mccProcesses ? () => mccProcesses[processIndex] : null,
      });
      return;
    } catch (error) {
      lastError = error;
      if (!mccProcesses || attempt >= maxAttempts) {
        break;
      }
      await fs.appendFile(rconLog, `# ${bot.name} ${context} failed (attempt ${attempt}/${maxAttempts}): ${error.message}\n\n`, "utf8");
      await restartMccBotForReconnect(args, bot, processIndex, mccProcesses, rconLog, `${context} retry: ${error.message}`);
    }
  }
  throw lastError ?? new Error(`${bot.name} ${context} failed without a captured error`);
}

async function stabilizeMccBotsInArena(args, activeBotInfos, rconLog, mccProcesses = null, pluginJar = null, context = "arena readiness", maxAttempts = 4, spawnIndexResolver = null) {
  if (args.chaosProfile !== "anarchy-smp") {
    return;
  }
  const results = await Promise.allSettled(activeBotInfos.map((bot, fallbackIndex) => {
    const processIndex = Number.isInteger(bot.index) ? bot.index : fallbackIndex;
    const spawnIndex = typeof spawnIndexResolver === "function" ? spawnIndexResolver(bot, fallbackIndex) : processIndex;
    const spawn = anarchyBotSpawn(spawnIndex);
    return stabilizeMccBotInArena(args, bot, processIndex, spawn, rconLog, mccProcesses, pluginJar, context, maxAttempts);
  }));
  const failures = results
    .map((result, index) => ({ result, bot: activeBotInfos[index] }))
    .filter(({ result }) => result.status === "rejected");
  if (failures.length > 0) {
    throw new Error(`${context} failed for ${failures.length}/${activeBotInfos.length} MCC bots:\n${failures.map(({ bot, result }) => `${bot.name}: ${result.reason?.message ?? result.reason}`).join("\n")}`);
  }
}

async function assertMccBotArenaReadiness(args, activeBotInfos, rconLog, mccProcesses = null, pluginJar = null) {
  await stabilizeMccBotsInArena(args, activeBotInfos, rconLog, mccProcesses, pluginJar, "arena readiness after reconnect churn", 4);
}

async function assertRconScenarioEvidence(args, rconLog, activeBotInfos) {
  const text = await fs.readFile(rconLog, "utf8");
  const requiredMarkers = new Set([
    "mcc-chaos teleport-hook spawn",
    "mcc-chaos teleport-hook fluid-leaf-boundary",
    "mcc-chaos teleport-hook chunkload-send",
    "mcc-chaos teleport-hook far-player-chunk-send",
    "mcc-chaos dimension-travel nether-end",
    "mcc-chaos dimension-travel return",
    "mcc-chaos real-nether-portal-enter",
    "mcc-chaos real-nether-portal-return",
    "mcc-chaos real-end-portal-enter",
    "mcc-chaos real-portal-traversal-complete",
    "mcc-chaos cold-region-teleport cold-far-player-chunk-send",
    "mcc-chaos reconnect-churn-background-load",
  ]);

  if (args.chaosProfile === "anarchy-smp") {
    requiredMarkers.add("mcc-chaos profile anarchy-smp");
    requiredMarkers.add("mcc-chaos bot-fixture");
    for (const scenario of ["redstone-raid", "mobfarm-cram", "pvp-arena", "vehicle-passenger"]) {
      requiredMarkers.add(scenario);
    }
  }
  if (args.enableNaturalSpawns) {
    requiredMarkers.add("mcc-chaos natural-spawns enabled");
  }

  for (const bot of activeBotInfos) {
    requiredMarkers.add(`mcc-chaos reconnect-churn kicked ${bot.name}`);
    requiredMarkers.add(`mcc-chaos reconnect-churn rejoined ${bot.name}`);
  }

  const missing = [...requiredMarkers].filter((marker) => !text.includes(marker));
  if (missing.length > 0) {
    throw new Error(`MCC RCON scenario evidence is incomplete. Missing markers:\n${missing.join("\n")}`);
  }
}

function assertRltRuntimeEvidence(serverLogSegment) {
  const issues = [];
  const crossQueueMatches = [...serverLogSegment.matchAll(/Cross-region queue probe finished: .*?tasks=(\d+) queued=(\d+) rejected=(\d+) executed=(\d+) failed=(\d+)/g)];
  if (crossQueueMatches.length === 0) {
    issues.push("Missing cross-region queue completion evidence");
  }
  for (const match of crossQueueMatches) {
    const [, tasksRaw, queuedRaw, rejectedRaw, executedRaw, failedRaw] = match;
    const tasks = Number(tasksRaw);
    const queued = Number(queuedRaw);
    const rejected = Number(rejectedRaw);
    const executed = Number(executedRaw);
    const failed = Number(failedRaw);
    if (queued !== tasks || executed !== tasks || rejected !== 0 || failed !== 0) {
      issues.push(`Bad cross-region queue completion: tasks=${tasks} queued=${queued} rejected=${rejected} executed=${executed} failed=${failed}`);
    }
  }

  const syncLoadMatches = [...serverLogSegment.matchAll(/Sync-load guard probe finished: .*?loadedBefore=(true|false), attempts=(\d+), guardRejections=(\d+), unexpectedSuccess=(\d+), unexpectedFailure=(\d+)/g)];
  if (syncLoadMatches.length === 0) {
    issues.push("Missing sync-load guard completion evidence");
  }
  for (const match of syncLoadMatches) {
    const [, loadedBeforeRaw, attemptsRaw, guardRejectionsRaw, unexpectedSuccessRaw, unexpectedFailureRaw] = match;
    const loadedBefore = loadedBeforeRaw === "true";
    const attempts = Number(attemptsRaw);
    const guardRejections = Number(guardRejectionsRaw);
    const unexpectedSuccess = Number(unexpectedSuccessRaw);
    const unexpectedFailure = Number(unexpectedFailureRaw);
    if (!loadedBefore && guardRejections !== attempts) {
      issues.push(`Sync-load guard did not reject every unloaded target attempt: attempts=${attempts} guardRejections=${guardRejections}`);
    }
    if (unexpectedSuccess !== 0 || unexpectedFailure !== 0) {
      issues.push(`Bad sync-load guard completion: loadedBefore=${loadedBefore} attempts=${attempts} guardRejections=${guardRejections} unexpectedSuccess=${unexpectedSuccess} unexpectedFailure=${unexpectedFailure}`);
    }
  }

  const pathMatches = [...serverLogSegment.matchAll(/pathfinding load finished: .*?requested=(\d+).*?queued=(\d+), completed=(\d+), rejected=(\d+), spawned=(\d+), skippedUnloaded=(\d+), pathStarted=(\d+), crossChunkMoves=(\d+), progressed=(\d+), arrived=(\d+), removed=(\d+)/g)];
  if (pathMatches.length === 0) {
    issues.push("Missing pathfinding movement completion evidence");
  }
  for (const match of pathMatches) {
    const [, requestedRaw, queuedRaw, completedRaw, rejectedRaw, spawnedRaw, skippedRaw, pathStartedRaw, crossChunkMovesRaw, progressedRaw, arrivedRaw, removedRaw] = match;
    const requested = Number(requestedRaw);
    const queued = Number(queuedRaw);
    const completed = Number(completedRaw);
    const rejected = Number(rejectedRaw);
    const spawned = Number(spawnedRaw);
    const skipped = Number(skippedRaw);
    const pathStarted = Number(pathStartedRaw);
    const crossChunkMoves = Number(crossChunkMovesRaw);
    const progressed = Number(progressedRaw);
    const arrived = Number(arrivedRaw);
    const removed = Number(removedRaw);
    if (queued !== requested || completed !== queued || rejected !== 0 || spawned <= 0 || skipped !== 0 || pathStarted !== spawned || crossChunkMoves <= 0 || progressed <= 0) {
      issues.push(`Bad pathfinding movement completion: requested=${requested} queued=${queued} completed=${completed} rejected=${rejected} spawned=${spawned} skippedUnloaded=${skipped} pathStarted=${pathStarted} crossChunkMoves=${crossChunkMoves} progressed=${progressed} arrived=${arrived} removed=${removed}`);
    }
  }

  const batchMatches = [...serverLogSegment.matchAll(/([^\n:]+?) batch finished: .*?total=(\d+) success=(\d+) null=(\d+) failure=(\d+)/g)];
  if (batchMatches.length === 0) {
    issues.push("Missing RLT chunk batch completion evidence");
  }
  let scenarioGenerationBatches = 0;
  let scenarioLoadBatches = 0;
  for (const match of batchMatches) {
    const [, label, totalRaw, successRaw, nullRaw, failureRaw] = match;
    const total = Number(totalRaw);
    const success = Number(successRaw);
    const nullResults = Number(nullRaw);
    const failures = Number(failureRaw);
    if (success !== total || nullResults !== 0 || failures !== 0) {
      issues.push(`Bad RLT batch completion for ${label.trim()}: total=${total} success=${success} null=${nullResults} failure=${failures}`);
    }
    if (label.includes("scenario chunk generation")) {
      scenarioGenerationBatches++;
    }
    if (label.includes("scenario chunk load-only")) {
      scenarioLoadBatches++;
    }
  }
  if (scenarioGenerationBatches < 3) {
    issues.push(`Missing scenario generation batch completions: ${scenarioGenerationBatches}/3`);
  }
  if (scenarioLoadBatches < 3) {
    issues.push(`Missing scenario load-only batch completions: ${scenarioLoadBatches}/3`);
  }

  const controlMatches = [...serverLogSegment.matchAll(/scenario control finished at chunk=.*?: samples=(\d+) periodTicks=(\d+) avgLagMs=([-\d.]+) p95LagMs=([-\d.]+) p99LagMs=([-\d.]+) maxLagMs=([-\d.]+)/g)];
  if (controlMatches.length < 2) {
    issues.push(`Missing scenario control lag completions: ${controlMatches.length}/2`);
  }
  for (const match of controlMatches) {
    const [, samplesRaw, periodTicksRaw, avgRaw, p95Raw, p99Raw, maxRaw] = match;
    const samples = Number(samplesRaw);
    const periodTicks = Number(periodTicksRaw);
    const avg = Number(avgRaw);
    const p95 = Number(p95Raw);
    const p99 = Number(p99Raw);
    const max = Number(maxRaw);
    if (samples < 2 || periodTicks < 1 || ![avg, p95, p99, max].every(Number.isFinite)) {
      issues.push(`Malformed scenario control lag completion: samples=${samplesRaw} periodTicks=${periodTicksRaw} avg=${avgRaw} p95=${p95Raw} p99=${p99Raw} max=${maxRaw}`);
    }
    if (p95 > 250 || p99 > 1000 || max > 5000) {
      issues.push(`Scenario control lag exceeded ceiling: samples=${samples} periodTicks=${periodTicks} avg=${avg.toFixed(3)} p95=${p95.toFixed(3)} p99=${p99.toFixed(3)} max=${max.toFixed(3)}`);
    }
  }

  const redstoneMatches = [...serverLogSegment.matchAll(/redstone boundary load finished: .*?pulseTasks=(\d+), completed=(\d+), rejected=(\d+), writes=(\d+), skippedUnloaded=(\d+)/g)];
  if (redstoneMatches.length === 0) {
    issues.push("Missing redstone/piston boundary completion evidence");
  }
  for (const match of redstoneMatches) {
    const [, tasksRaw, completedRaw, rejectedRaw, writesRaw, skippedRaw] = match;
    const tasks = Number(tasksRaw);
    const completed = Number(completedRaw);
    const rejected = Number(rejectedRaw);
    const writes = Number(writesRaw);
    const skipped = Number(skippedRaw);
    if (tasks <= 0 || completed !== tasks || rejected !== 0 || writes <= 0 || skipped !== 0) {
      issues.push(`Bad redstone/piston boundary completion: tasks=${tasks} completed=${completed} rejected=${rejected} writes=${writes} skippedUnloaded=${skipped}`);
    }
  }

  const vehicleMatches = [...serverLogSegment.matchAll(/vehicle\/passenger load finished: .*?requested=(\d+).*?queued=(\d+), completed=(\d+), rejected=(\d+), spawned=(\d+), skippedUnloaded=(\d+), mounted=(\d+), crossChunkMoves=(\d+), passengerRetained=(\d+), removed=(\d+)/g)];
  if (vehicleMatches.length === 0) {
    issues.push("Missing vehicle/passenger completion evidence");
  }
  for (const match of vehicleMatches) {
    const [, requestedRaw, queuedRaw, completedRaw, rejectedRaw, spawnedRaw, skippedRaw, mountedRaw, crossChunkMovesRaw, retainedRaw, removedRaw] = match;
    const requested = Number(requestedRaw);
    const queued = Number(queuedRaw);
    const completed = Number(completedRaw);
    const rejected = Number(rejectedRaw);
    const spawned = Number(spawnedRaw);
    const skipped = Number(skippedRaw);
    const mounted = Number(mountedRaw);
    const crossChunkMoves = Number(crossChunkMovesRaw);
    const passengerRetained = Number(retainedRaw);
    const removed = Number(removedRaw);
    if (queued !== requested || completed !== queued || rejected !== 0 || spawned <= 0 || skipped !== 0
      || mounted !== spawned || crossChunkMoves <= 0 || passengerRetained <= 0) {
      issues.push(`Bad vehicle/passenger completion: requested=${requested} queued=${queued} completed=${completed} rejected=${rejected} spawned=${spawned} skippedUnloaded=${skipped} mounted=${mounted} crossChunkMoves=${crossChunkMoves} passengerRetained=${passengerRetained} removed=${removed}`);
    }
  }

  if (issues.length > 0) {
    throw new Error(`MCC RLT runtime evidence is incomplete or unhealthy:\n${issues.join("\n")}`);
  }
}

function parseOwnershipCounters(text) {
  const plain = stripMinecraftColors(text);
  const counters = {};
  for (const key of ["loadedReadFallbacks", "ownerHandoffs", "ownerHandoffRequeues", "ownerHandoffRejections", "prefetchFailures"]) {
    const match = new RegExp(`${key}=([0-9]+)`).exec(plain);
    if (!match) {
      throw new Error(`Could not parse ownership counter ${key}: ${plain.trim()}`);
    }
    counters[key] = Number.parseInt(match[1], 10);
  }
  return counters;
}

function loadedReadFallbackIssue(count, plain) {
  if (count === 0) {
    return null;
  }
  const samples = parseLoadedReadFallbackSamples(plain);
  if (samples.length < count) {
    return `Only ${samples.length}/${count} loaded-read fallback samples were reported; refusing to classify hidden fallback sources.`;
  }
  const disallowed = samples.filter((sample) => !isAllowedLoadedReadFallbackSample(sample));
  if (disallowed.length > 0) {
    return `Disallowed loaded-read fallback samples:\n${disallowed.join("\n")}`;
  }
  return null;
}

function parseLoadedReadFallbackSamples(plain) {
  return plain
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => /^(block|fluid|block-entity)-/.test(line) && line.includes(" caller="));
}

function isAllowedLoadedReadFallbackSample(sample) {
  return /caller=net\.minecraft\.world\.entity\.LivingEntity#travel(?:InAir|Flying):/.test(sample);
}

async function ensureRltChunks(args, rconLog, pluginJar, anchors, radiusChunks, timeoutMs = 120000) {
  if (!fsSync.existsSync(pluginJar) || anchors.length === 0) {
    return;
  }
  const commands = anchors.map((anchor) =>
    `rlt at world ${anchor.x} ${anchor.y} ${anchor.z} chunkgen ${radiusChunks} false`
  );
  await invokeRconCommands(args, commands, rconLog, 30000, 100);
  await waitForRltIdle(args, rconLog, timeoutMs);
}

function mccRid() {
  const platform = os.platform();
  const arch = os.arch();
  if (platform === "darwin") {
    return arch === "arm64" ? "osx-arm64" : "osx-x64";
  }
  if (platform === "linux") {
    if (arch === "arm64") return "linux-arm64";
    if (arch === "arm") return "linux-arm";
    return "linux-x64";
  }
  throw new Error(`This script is for macOS/Linux. Unsupported platform: ${platform}/${arch}`);
}

async function downloadFile(url, target) {
  const response = await fetch(url, { headers: { "User-Agent": "ShreddedPaper-MccChaos" } });
  if (!response.ok) {
    throw new Error(`Download failed ${response.status}: ${url}`);
  }
  const arrayBuffer = await response.arrayBuffer();
  await writeUtf8(`${target}.download-url.txt`, `${url}\n`);
  await fs.writeFile(target, Buffer.from(arrayBuffer));
  await fs.chmod(target, 0o755);
}

async function installMcc(args, cacheDir) {
  if (args.mccPath) {
    const resolved = path.resolve(args.mccPath);
    await fs.access(resolved, fsSync.constants.X_OK);
    return resolved;
  }

  const mccDir = path.join(cacheDir, "mcc");
  await ensureDir(mccDir);
  const existing = (await fs.readdir(mccDir).catch(() => []))
    .filter((name) => name.startsWith("MinecraftClient") && !name.endsWith(".txt"))
    .map((name) => path.join(mccDir, name))
    .filter((file) => fsSync.existsSync(file))
    .sort((a, b) => fsSync.statSync(b).mtimeMs - fsSync.statSync(a).mtimeMs)[0];
  if (existing) {
    await fs.chmod(existing, 0o755).catch(() => {});
    return existing;
  }
  if (args.skipMccDownload) {
    throw new Error("MCC executable is missing and --skip-mcc-download was specified.");
  }

  const rid = mccRid();
  const release = await fetch("https://api.github.com/repos/MCCTeam/Minecraft-Console-Client/releases/latest", {
    headers: { "User-Agent": "ShreddedPaper-MccChaos" },
  }).then((response) => {
    if (!response.ok) throw new Error(`GitHub release lookup failed: ${response.status}`);
    return response.json();
  });
  const asset = release.assets.find((candidate) => candidate.name.endsWith(rid));
  if (!asset) {
    throw new Error(`Could not find MCC asset ending with ${rid} in latest release ${release.tag_name}`);
  }
  const target = path.join(mccDir, asset.name);
  console.log(`Downloading MCC ${release.tag_name}: ${asset.name}`);
  await downloadFile(asset.browser_download_url, target);
  return target;
}

async function getWebSocketBotTemplate(cacheDir) {
  const template = path.join(cacheDir, "WebSocketBot.cs");
  if (!fsSync.existsSync(template)) {
    if (globalThis.__mccChaosNoNetwork) {
      throw new Error(`Missing cached WebSocketBot.cs and --offline/--no-network was specified: ${template}`);
    }
    const url = "https://raw.githubusercontent.com/MCCTeam/Minecraft-Console-Client/master/MinecraftClient/config/ChatBots/WebSocketBot.cs";
    await downloadFile(url, template);
  }
  return fs.readFile(template, "utf8");
}

function serverProperties(args) {
  const spawnEnabled = args.enableNaturalSpawns ? "true" : "false";
  const defaultGameMode = args.chaosProfile === "anarchy-smp" ? "survival" : "creative";
  return `accepts-transfers=false
allow-flight=true
allow-nether=true
broadcast-console-to-ops=false
broadcast-rcon-to-ops=false
difficulty=normal
enable-command-block=false
enable-query=false
enable-rcon=true
enable-status=true
enforce-secure-profile=false
force-gamemode=true
function-permission-level=4
gamemode=${defaultGameMode}
generate-structures=false
hardcore=false
level-name=world
level-seed=mcc-chaos-${args.seed}
level-type=minecraft:flat
max-players=64
max-tick-time=-1
motd=ShreddedPaper MCC chaos
network-compression-threshold=256
online-mode=false
op-permission-level=4
prevent-proxy-connections=false
pvp=true
query.port=${args.serverPort}
rcon.password=${args.rconPassword}
rcon.port=${args.rconPort}
server-ip=
server-port=${args.serverPort}
simulation-distance=8
spawn-animals=${spawnEnabled}
spawn-monsters=${spawnEnabled}
spawn-npcs=${spawnEnabled}
spawn-protection=0
sync-chunk-writes=false
use-native-transport=true
view-distance=12
white-list=false
`;
}

async function prepareServerDirectory(args, cycleRunDir, serverJar, pluginJar) {
  const serverDir = path.join(cycleRunDir, "server");
  assertUnderRoot(args.rootPath, serverDir);
  await fs.rm(serverDir, { recursive: true, force: true });
  await ensureDir(path.join(serverDir, "plugins"));
  await fs.copyFile(serverJar, path.join(serverDir, "server.jar"));
  if (!fsSync.existsSync(pluginJar)) {
    throw new Error(`Region load test plugin jar is required for MCC chaos validation: ${pluginJar}`);
  }
  await fs.copyFile(pluginJar, path.join(serverDir, "plugins", "region-load-test-plugin.jar"));
  await writeUtf8(path.join(serverDir, "eula.txt"), "eula=true\n");
  await writeUtf8(path.join(serverDir, "server.properties"), serverProperties(args));
  return serverDir;
}

async function startTestServer(args, serverDir) {
  const stdout = fsSync.openSync(path.join(serverDir, "console.out.log"), "w");
  const stderr = fsSync.openSync(path.join(serverDir, "console.err.log"), "w");
  const javaArgs = [
    "--enable-preview",
    `-Xms${args.heapGb}G`,
    `-Xmx${args.heapGb}G`,
    "-Dfile.encoding=UTF-8",
    "-Duser.timezone=GMT+9",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:+UseStringDeduplication",
    "-jar",
    "server.jar",
    "nogui",
  ];
  const child = spawn(args.javaPath, javaArgs, {
    cwd: serverDir,
    stdio: ["ignore", stdout, stderr],
    detached: true,
    env: process.env,
  });
  try {
    await waitForServerReady(args, serverDir, child);
    return child;
  } catch (error) {
    await killProcessTree(child);
    throw error;
  }
}

function writeMccConfig(args, name, logFile) {
  return `[Main.General]
Account = { Login = "${name}", Password = "-" }
Server = { Host = "127.0.0.1", Port = ${args.serverPort} }
AccountType = "microsoft"
Method = "mcc"

[Main.Advanced]
Language = "en_us"
MinecraftVersion = "1.21.11"
EnableSentry = false
LoadMccTranslation = false
TerrainAndMovements = true
InventoryHandling = true
EntityHandling = true
AutoRespawn = true
ExitOnFailure = false
Timestamps = true
ResolveSrvRecords = "no"

[Logging]
DebugMessages = false
ChatMessages = true
InfoMessages = true
WarningMessages = true
ErrorMessages = true
LogToFile = true
LogFile = "${logFile.replaceAll("\\", "\\\\")}"
PrependTimestamp = true
SaveColorCodes = false

[ChatBot.ScriptScheduler]
Enabled = true

[[ChatBot.ScriptScheduler.TaskList]]
Task_Name = "load-websocket"
Trigger_On_First_Login = true
Trigger_On_Login = true
Trigger_On_Times = { Enable = false, Times = [] }
Trigger_On_Interval = { Enable = false, MinTime = 1.0, MaxTime = 1.0 }
Action = "script ChatBots/WebSocketBot.cs"
`;
}

async function writeMccBotFiles(args, cycleRunDir, mccExe, template) {
  const botInfos = [];
  for (let i = 1; i <= args.botCount; i++) {
    const name = `mccbot${String(i).padStart(2, "0")}`;
    const port = args.webSocketBasePort + i - 1;
    const botDir = path.join(cycleRunDir, name);
    await ensureDir(path.join(botDir, "ChatBots"));
    const script = template.replace(
      /MCC\.LoadBot\(new WebSocketBot\("127\.0\.0\.1",\s*8043,\s*"CHANGE_THIS_PASSWORD"(,\s*debugMode:\s*true)?\)\);/,
      `MCC.LoadBot(new WebSocketBot("127.0.0.1", ${port}, "${args.webSocketPassword}"));`,
    );
    await writeUtf8(path.join(botDir, "ChatBots", "WebSocketBot.cs"), script);
    const config = path.join(botDir, "MinecraftClient.ini");
    await writeUtf8(config, writeMccConfig(args, name, path.join(botDir, "mcc-console.log")));
    botInfos.push({ name, index: i - 1, port, dir: botDir, config, exe: mccExe });
  }
  return botInfos;
}

async function startMccBot(bot) {
  const stdoutPath = path.join(bot.dir, "stdout.log");
  const stderrPath = path.join(bot.dir, "stderr.log");
  await fs.appendFile(stdoutPath, `\n### MCC process start ${new Date().toISOString()} ###\n`, "utf8");
  await fs.appendFile(stderrPath, `\n### MCC process start ${new Date().toISOString()} ###\n`, "utf8");
  const stdout = fsSync.openSync(stdoutPath, "a");
  const stderr = fsSync.openSync(stderrPath, "a");
  return spawn(bot.exe, [bot.config], {
    cwd: bot.dir,
    stdio: ["ignore", stdout, stderr],
    detached: true,
    env: process.env,
  });
}

async function startMccBots(botInfos) {
  const children = [];
  for (const bot of botInfos) {
    children.push(await startMccBot(bot));
    await sleep(2000);
  }
  return children;
}

function arenaPlatformCommands({ x, y, z }, radius = 24, airHeight = 8) {
  const minX = x - radius;
  const maxX = x + radius;
  const minZ = z - radius;
  const maxZ = z + radius;
  return [
    ...fillVolumeCommands(minX, y - 1, minZ, maxX, y - 1, maxZ, "minecraft:stone"),
    ...fillVolumeCommands(minX, y, minZ, maxX, y + airHeight, maxZ, "minecraft:air"),
  ];
}

function fillVolumeCommands(minX, minY, minZ, maxX, maxY, maxZ, block) {
  const area = Math.max(1, (maxX - minX + 1) * (maxZ - minZ + 1));
  const maxLayers = Math.max(1, Math.floor(32768 / area));
  const commands = [];
  for (let y = minY; y <= maxY; y += maxLayers) {
    const endY = Math.min(maxY, y + maxLayers - 1);
    commands.push(`fill ${minX} ${y} ${minZ} ${maxX} ${endY} ${maxZ} ${block}`);
  }
  return commands;
}

function platformBounds({ x, z }, radius = 24) {
  return {
    minX: x - radius,
    minZ: z - radius,
    maxX: x + radius,
    maxZ: z + radius,
  };
}

async function invokeForceloadedRconCommands(args, rconLog, bounds, commands, timeoutMs = 30000, pauseMs = 200) {
  await invokeRconCommands(args, [
    `forceload add ${bounds.minX} ${bounds.minZ} ${bounds.maxX} ${bounds.maxZ}`,
  ], rconLog, 20000, 200);
  await sleep(2000);
  try {
    await invokeRconCommands(args, commands, rconLog, timeoutMs, pauseMs);
  } finally {
    await invokeRconCommands(args, [
      `forceload remove ${bounds.minX} ${bounds.minZ} ${bounds.maxX} ${bounds.maxZ}`,
    ], rconLog, 20000, 200);
  }
}

async function preloadTargetWithBot(args, botNames, target, rconLog, waitMs = 3000) {
  if (botNames.length === 0) {
    return;
  }
  const name = botNames[Math.abs(Math.round(target.x + target.z)) % botNames.length];
  await invokeRconCommands(args, [
    `tp ${name} ${target.x} ${target.y} ${target.z}`,
    `say mcc-chaos preload ${target.x} ${target.y} ${target.z}`,
  ], rconLog, 15000, 250);
  await sleep(waitMs);
}

async function invokeSetupCommands(args, botNames, rconLog) {
  const anarchyProfile = args.chaosProfile === "anarchy-smp";
  const botGameMode = anarchyProfile ? "survival" : "creative";
  const commands = [
    "difficulty hard",
    "gamerule minecraft:keep_inventory true",
    "gamerule minecraft:immediate_respawn true",
    "time set day",
    "weather clear",
    "setworldspawn 0 4 0",
    ...arenaPlatformCommands({ x: 0, y: 8, z: 0 }, 28, 8),
  ];
  for (const name of botNames) {
    commands.push(`gamemode ${botGameMode} ${name}`);
    commands.push(`effect give ${name} minecraft:saturation infinite 1 true`);
    if (anarchyProfile) {
      commands.push(`effect give ${name} minecraft:resistance infinite 4 true`);
      commands.push(`effect give ${name} minecraft:regeneration infinite 0 true`);
      commands.push(`effect give ${name} minecraft:fire_resistance infinite 0 true`);
      commands.push(`effect give ${name} minecraft:slow_falling infinite 0 true`);
      commands.push(`clear ${name}`);
      commands.push(`give ${name} minecraft:iron_sword 1`);
      commands.push(`give ${name} minecraft:bow 1`);
      commands.push(`give ${name} minecraft:arrow 128`);
      commands.push(`give ${name} minecraft:shield 1`);
      commands.push(`give ${name} minecraft:iron_helmet 1`);
      commands.push(`give ${name} minecraft:iron_chestplate 1`);
      commands.push(`give ${name} minecraft:iron_leggings 1`);
      commands.push(`give ${name} minecraft:iron_boots 1`);
      commands.push(`give ${name} minecraft:diamond_pickaxe 1`);
      commands.push(`give ${name} minecraft:stone 128`);
      commands.push(`give ${name} minecraft:oak_planks 128`);
      commands.push(`give ${name} minecraft:water_bucket 1`);
      commands.push(`give ${name} minecraft:lava_bucket 1`);
      commands.push(`give ${name} minecraft:golden_apple 16`);
      commands.push(`give ${name} minecraft:cooked_beef 64`);
    } else {
      commands.push(`give ${name} minecraft:stone 64`);
      commands.push(`give ${name} minecraft:oak_planks 64`);
      commands.push(`give ${name} minecraft:torch 64`);
    }
  }
  await invokeRconCommands(args, commands, rconLog, 15000);
}

async function invokeFluidLeafBoundaryFixture(args, rconLog) {
  await invokeRconCommands(args, [
    "fill 112 4 -32 144 4 32 minecraft:stone",
    "fill 112 5 -32 144 8 32 minecraft:air",
    "fill 126 5 -24 126 5 24 minecraft:water",
    "fill 127 5 -24 127 5 24 minecraft:water",
    "fill 128 5 -24 128 6 24 minecraft:oak_leaves[persistent=false]",
    "fill 129 5 -24 129 5 24 minecraft:redstone_wire",
    "setblock 130 5 0 minecraft:redstone_torch",
    "setblock 128 7 0 minecraft:oak_leaves[persistent=false]",
    "setblock 127 6 0 minecraft:air",
    "setblock 126 6 0 minecraft:lava",
  ], rconLog, 20000, 150);
}

async function invokeTeleportHooks(args, botNames, rconLog, pluginJar) {
  const targets = [
    { x: 0, y: 8, z: 0, label: "spawn" },
    { x: 128, y: 8, z: 0, label: "fluid-leaf-boundary" },
    { x: 2048, y: 12, z: 0, label: "chunkload-send" },
    { x: -2048, y: 12, z: 2048, label: "far-player-chunk-send" },
  ];
  await ensureRltChunks(args, rconLog, pluginJar, targets, 4);
  for (const target of targets) {
    const commands = [];
    commands.push(...arenaPlatformCommands(target, 24, 8));
    commands.push(`say mcc-chaos preload ${target.x} ${target.y} ${target.z}`);
    botNames.forEach((name, index) => {
      commands.push(`tp ${name} ${target.x} ${target.y} ${target.z + (index % 4) * 4}`);
    });
    commands.push(`say mcc-chaos teleport-hook ${target.label} ${target.x} ${target.y} ${target.z}`);
    await invokeForceloadedRconCommands(args, rconLog, platformBounds(target, 24), commands, 20000, 250);
  }
  const commands = [];
  botNames.forEach((name, index) => {
    const x = index % 2 === 0 ? 124 : 132;
    const z = -20 + (index % 11) * 4;
    commands.push(`tp ${name} ${x} 8 ${z}`);
  });
  await invokeRconCommands(args, commands, rconLog, 15000, 250);
}

async function invokeColdRegionTeleportHook(args, botNames, rconLog) {
  if (botNames.length === 0) {
    return;
  }
  const target = { x: 4096, y: 12, z: -4096, label: "cold-far-player-chunk-send" };
  const commands = [];
  commands.push(...arenaPlatformCommands(target, 24, 8));
  botNames.forEach((name, index) => {
    commands.push(`tp ${name} ${target.x} ${target.y} ${target.z + (index % 4) * 4}`);
  });
  commands.push(`say mcc-chaos cold-region-teleport ${target.label} ${target.x} ${target.y} ${target.z}`);
  await invokeForceloadedRconCommands(args, rconLog, platformBounds(target, 24), commands, 30000, 250);
}

async function invokeDimensionTravelHook(args, botNames, rconLog) {
  if (botNames.length === 0) {
    return;
  }
  const netherBot = botNames[0];
  const endBot = botNames[Math.min(1, botNames.length - 1)];
  await invokeRconCommands(args, [
    "execute in minecraft:the_nether run forceload add -16 -16 16 16",
    "execute in minecraft:the_nether run fill -8 64 -8 8 64 8 minecraft:netherrack",
    "execute in minecraft:the_nether run fill -8 65 -8 8 72 8 minecraft:air",
    "execute in minecraft:the_end run forceload add -16 -16 16 16",
    "execute in minecraft:the_end run fill -8 64 -8 8 64 8 minecraft:end_stone",
    "execute in minecraft:the_end run fill -8 65 -8 8 72 8 minecraft:air",
    `execute in minecraft:the_nether run tp ${netherBot} 0 66 0`,
    `execute in minecraft:the_end run tp ${endBot} 0 66 0`,
    "say mcc-chaos dimension-travel nether-end",
  ], rconLog, 30000, 250);
  await sleep(3000);
  await invokeRconCommands(args, [
    `execute in minecraft:overworld run tp ${netherBot} 0 8 0`,
    `execute in minecraft:overworld run tp ${endBot} 4 8 0`,
    "execute in minecraft:the_nether run forceload remove -16 -16 16 16",
    "execute in minecraft:the_end run forceload remove -16 -16 16 16",
    "say mcc-chaos dimension-travel return",
  ], rconLog, 30000, 250);
}

async function assertPlayerInDimension(args, rconLog, playerName, dimension, label, timeoutMs = 30000) {
  const expectedWorld = worldNameForDimension(dimension);
  const deadline = Date.now() + timeoutMs;
  let lastResponse = "";
  while (Date.now() < deadline) {
    const command = `rlt playercheck ${playerName} present ${expectedWorld}`;
    const response = await sendRconCommand(args, command, 30000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    lastResponse = plain;
    if (plain.includes("RLT playercheck ok:")) {
      return;
    }
    await sleep(500);
  }
  throw new Error(`Real portal traversal did not place ${playerName} in ${dimension} for ${label}. Last response: ${lastResponse || "(none)"}`);
}

function worldNameForDimension(dimension) {
  switch (dimension) {
    case "minecraft:overworld":
      return "world";
    case "minecraft:the_nether":
      return "world_nether";
    case "minecraft:the_end":
      return "world_the_end";
    default:
      throw new Error(`Unsupported dimension for player world check: ${dimension}`);
  }
}

async function invokeRealPortalTraversalHook(args, botNames, rconLog) {
  if (botNames.length === 0) {
    return;
  }
  const netherBot = botNames[0];
  const endBot = botNames[Math.min(1, botNames.length - 1)];
  await invokeRconCommands(args, [
    "execute in minecraft:the_nether run forceload add 16 16 47 47",
    "execute in minecraft:the_nether run fill 24 64 24 40 64 40 minecraft:netherrack",
    "execute in minecraft:the_nether run fill 24 65 24 40 72 40 minecraft:air",
    "execute in minecraft:the_end run forceload add 32 16 63 47",
    "execute in minecraft:the_end run fill 40 64 24 56 64 40 minecraft:end_stone",
    "execute in minecraft:the_end run fill 40 65 24 56 72 40 minecraft:air",
  ], rconLog, 30000, 250);

  await invokeForceloadedRconCommands(args, rconLog, { minX: 16, minZ: 16, maxX: 63, maxZ: 47 }, [
    ...arenaPlatformCommands({ x: 32, y: 8, z: 32 }, 20, 8),
    "fill 30 8 32 34 12 32 minecraft:obsidian",
    "fill 31 9 32 33 11 32 minecraft:nether_portal[axis=x]",
    `tp ${netherBot} 32 9 32`,
    "say mcc-chaos real-nether-portal-enter",
  ], 30000, 250);
  await sleep(9000);
  await assertPlayerInDimension(args, rconLog, netherBot, "minecraft:the_nether", "nether portal");

  await invokeRconCommands(args, [
    `execute in minecraft:overworld run tp ${netherBot} 36 8 32`,
    "say mcc-chaos real-nether-portal-return",
  ], rconLog, 30000, 250);
  await sleep(2000);

  await invokeForceloadedRconCommands(args, rconLog, { minX: 40, minZ: 16, maxX: 63, maxZ: 47 }, [
    ...arenaPlatformCommands({ x: 48, y: 8, z: 32 }, 12, 8),
    `tp ${endBot} 48 10 36`,
    "say mcc-chaos real-end-portal-cooldown",
  ], 30000, 250);
  await sleep(15000);

  await invokeForceloadedRconCommands(args, rconLog, { minX: 40, minZ: 16, maxX: 63, maxZ: 47 }, [
    "setblock 48 8 32 minecraft:end_portal",
    `tp ${endBot} 48.5 8.2 32.5`,
    "say mcc-chaos real-end-portal-enter",
  ], 30000, 250);
  await sleep(6000);
  await assertPlayerInDimension(args, rconLog, endBot, "minecraft:the_end", "end portal");

  await invokeRconCommands(args, [
    `execute in minecraft:overworld run tp ${endBot} 40 8 32`,
    "execute in minecraft:the_nether run forceload remove 16 16 47 47",
    "execute in minecraft:the_end run forceload remove 32 16 63 47",
    "say mcc-chaos real-portal-traversal-complete",
  ], rconLog, 30000, 250);
}

async function invokeNaturalSpawnHook(args, botNames, rconLog, pluginJar) {
  if (!args.enableNaturalSpawns) {
    return;
  }
  await ensureRltChunks(args, rconLog, pluginJar, [{ x: 328, y: 8, z: 328 }], 4);
  const bounds = { minX: 288, minZ: 288, maxX: 368, maxZ: 368 };
  const commands = [
    "gamerule minecraft:spawn_mobs true",
    "time set midnight",
    "fill 288 3 288 368 3 368 minecraft:grass_block",
    "fill 288 4 288 328 18 328 minecraft:air",
    "fill 329 4 288 368 18 328 minecraft:air",
    "fill 288 4 329 328 18 368 minecraft:air",
    "fill 329 4 329 368 18 368 minecraft:air",
  ];
  if (botNames.length > 0) {
    commands.push("say mcc-chaos preload 328 8 328");
    commands.push(`tp ${botNames[0]} 328 8 328`);
  }
  commands.push("say mcc-chaos natural-spawns enabled");
  await invokeForceloadedRconCommands(args, rconLog, bounds, commands, 20000, 250);
}

async function invokeHostileBackgroundLoad(args, lifeTicks, rconLog, pluginJar) {
  if (!fsSync.existsSync(pluginJar)) {
    return;
  }
  await ensureRltChunks(args, rconLog, pluginJar, [
    { x: 0, y: 8, z: 0 },
    { x: 96, y: 8, z: 0 },
    { x: -128, y: 8, z: 0 },
    { x: 0, y: 8, z: 128 },
    { x: 128, y: 8, z: 0 },
    { x: 512, y: 8, z: 0 },
    { x: 2048, y: 8, z: 0 },
    { x: 3584, y: 8, z: 0 },
    { x: 5120, y: 8, z: 0 },
    { x: 6656, y: 8, z: 0 },
    { x: 200000, y: 8, z: 0 },
  ], 8);
  await invokeRconCommands(args, [
    "forceload add -64 -64 64 64",
    "forceload add 16 -80 176 80",
    "forceload add -192 -64 -64 64",
    "forceload add 432 -80 592 80",
  ], rconLog, 30000, 250);
  await invokeRconCommands(args, [
    ...arenaPlatformCommands({ x: 96, y: 8, z: 0 }, 80, 8),
    ...arenaPlatformCommands({ x: 512, y: 8, z: 0 }, 80, 8),
  ], rconLog, 30000, 250);
  await invokeRconCommands(args, [
    `rlt at world 0 8 0 villagers 80 32 ${lifeTicks}`,
    `rlt at world 96 8 0 path 80 32 ${lifeTicks}`,
    `rlt at world -128 8 0 tracker 400 ${lifeTicks} 16`,
  ], rconLog, 30000, 500);
  await invokeForceloadedRconCommands(args, rconLog, { minX: 0, minZ: 128, maxX: 127, maxZ: 159 }, [
    `rlt at world 0 8 128 broadcast 12 96 ${lifeTicks}`,
  ], 30000, 500);
  await invokeRconCommands(args, [
    "rlt at world 0 8 0 scheduler region 128 256",
    "rlt at world 128 8 0 crossqueue 16 96 128",
    "rlt at world 0 8 0 syncload 64 4",
    "rlt at world 512 8 0 redstone 3 80 48 1",
    "rlt at world 2048 8 0 scenario load 3 96 4 false 240 1",
    "rlt at world 200000 8 0 scenario gen 3 128 4 false 240 1",
  ], rconLog, 30000, 500);
}

async function invokeAnarchySmpHooks(args, botNames, lifeTicks, rconLog, pluginJar) {
  if (args.chaosProfile !== "anarchy-smp") {
    return;
  }

  const intensity = Math.max(1, Math.min(4, args.chaosIntensity));
  const fixtureTicks = Math.max(600, lifeTicks);
  const redstoneLanes = 3 + intensity * 2;
  const redstoneLength = 32 + intensity * 16;
  const mobSpawners = 4 + intensity * 3;
  const mobsPerWave = 1 + intensity;
  const pvpLaunchers = 6 + intensity * 3;
  const projectilesPerBurst = 2 + intensity;
  const vehicleCount = 8 + intensity * 6;
  const trackerCount = 80 + intensity * 80;
  const fixtureAnchors = [
    { x: 384, y: 8, z: 0 },
    { x: -384, y: 8, z: 0 },
    { x: 0, y: 8, z: -384 },
    { x: 0, y: 8, z: 384 },
    { x: 384, y: 8, z: 128 },
    { x: -384, y: 8, z: 128 },
  ];
  await ensureRltChunks(args, rconLog, pluginJar, fixtureAnchors, 8);

  await invokeRconCommands(args, [`say mcc-chaos profile anarchy-smp intensity ${intensity}`], rconLog, 15000, 200);

  const fixtureGroups = [
    {
      preload: { x: 384, y: 8, z: 0 },
      bounds: { minX: 320, minZ: -80, maxX: 448, maxZ: 80 },
      commands: [
        ...arenaPlatformCommands({ x: 384, y: 8, z: 0 }, 80, 10),
        "fill 376 8 -64 376 8 64 minecraft:water",
        "fill 392 8 -64 392 8 64 minecraft:lava",
      ],
    },
    {
      preload: { x: -384, y: 8, z: 0 },
      bounds: { minX: -448, minZ: -80, maxX: -320, maxZ: 80 },
      commands: [
        ...arenaPlatformCommands({ x: -384, y: 8, z: 0 }, 80, 10),
        "fill -392 8 -64 -392 8 64 minecraft:water",
        "fill -376 8 -64 -376 8 64 minecraft:lava",
      ],
    },
    {
      preload: { x: 0, y: 8, z: -384 },
      bounds: { minX: -80, minZ: -448, maxX: 80, maxZ: -320 },
      commands: [
        ...arenaPlatformCommands({ x: 0, y: 8, z: -384 }, 80, 10),
      ],
    },
    {
      preload: { x: 0, y: 8, z: 384 },
      bounds: { minX: -80, minZ: 320, maxX: 80, maxZ: 448 },
      commands: [
        ...arenaPlatformCommands({ x: 0, y: 8, z: 384 }, 80, 10),
      ],
    },
  ];
  for (const fixture of fixtureGroups) {
    await invokeForceloadedRconCommands(args, rconLog, fixture.bounds, fixture.commands, 30000, 200);
    await preloadTargetWithBot(args, botNames, fixture.preload, rconLog);
  }

  const commands = [];
  if (fsSync.existsSync(pluginJar)) {
    commands.push("forceload add 320 -80 448 80");
    commands.push(`rlt at world 384 8 0 redstone ${redstoneLanes} ${fixtureTicks} ${redstoneLength} 1`);
    commands.push(`rlt at world -384 8 0 mobfarm ${mobSpawners} ${mobsPerWave} ${fixtureTicks} 20 72`);
    commands.push(`rlt at world 0 8 -384 pvp ${pvpLaunchers} ${projectilesPerBurst} ${Math.min(fixtureTicks, 1200)} 6 92 3`);
    commands.push(`rlt at world 0 8 384 vehicles ${vehicleCount} ${Math.min(fixtureTicks, 1200)} 96`);
    commands.push(`rlt at world -384 8 128 tracker ${trackerCount} ${fixtureTicks} 24`);
    commands.push(`rlt at world 0 8 -384 scheduler region ${32 + intensity * 32} ${96 * intensity}`);
  }

  botNames.forEach((name, index) => {
    const spawn = anarchyBotSpawn(index);
    commands.push(`tp ${name} ${spawn.x} ${spawn.y} ${spawn.z}`);
    commands.push(`say mcc-chaos bot-fixture ${name} ${spawn.label}`);
  });

  await invokeRconCommands(args, commands, rconLog, 45000, 200);
  if (fsSync.existsSync(pluginJar)) {
    await invokeForceloadedRconCommands(args, rconLog, { minX: 384, minZ: 128, maxX: 511, maxZ: 159 }, [
      `rlt at world 384 8 128 broadcast ${6 + intensity * 4} ${64 + intensity * 48} ${fixtureTicks}`,
    ], 45000, 200);
  }
}

function botArenaStabilizeCommands(botName, spawn) {
  const targetSelector = `@a[name=${botName},limit=1]`;
  return [
    `execute if entity ${targetSelector} run effect give ${targetSelector} minecraft:saturation infinite 1 true`,
    `execute if entity ${targetSelector} run effect give ${targetSelector} minecraft:resistance infinite 4 true`,
    `execute if entity ${targetSelector} run effect give ${targetSelector} minecraft:regeneration infinite 0 true`,
    `execute if entity ${targetSelector} run effect give ${targetSelector} minecraft:fire_resistance infinite 0 true`,
    `execute if entity ${targetSelector} run effect give ${targetSelector} minecraft:slow_falling infinite 0 true`,
    `execute if entity ${targetSelector} run tp ${targetSelector} ${spawn.x} ${spawn.y} ${spawn.z}`,
  ];
}

async function restartMccBotForReconnect(args, bot, processIndex, mccProcesses, rconLog, reason) {
  await fs.appendFile(rconLog, `# ${bot.name} restart requested during reconnect churn: ${reason}\n\n`, "utf8");
  await killProcessTree(mccProcesses[processIndex]);
  mccProcesses[processIndex] = await startMccBot(bot);
  await waitForPlayers(args, [bot.name], [mccProcesses[processIndex]], [bot], 90, 1, {
    restartExited: true,
    maxRestarts: 1,
    onRestart: (ignoredIndex, restartedChild) => {
      mccProcesses[processIndex] = restartedChild;
    },
  });
}

async function invokeReconnectChurnHook(args, activeBotInfos, mccProcesses, lifeTicks, rconLog, pluginJar) {
  if (activeBotInfos.length === 0) {
    return activeBotInfos;
  }
  const botNames = activeBotInfos.map((bot) => bot.name);
  if (fsSync.existsSync(pluginJar)) {
    await ensureRltChunks(args, rconLog, pluginJar, [
      { x: 0, y: 8, z: 0 },
      { x: 128, y: 8, z: 0 },
      { x: 384, y: 8, z: 128 },
    ], 4);
    await invokeRconCommands(args, [
      `rlt at world 0 8 0 tracker 120 ${lifeTicks} 16`,
      `rlt at world 128 8 0 crossqueue 16 64 96`,
      "say mcc-chaos reconnect-churn-background-load",
    ], rconLog, 30000, 400);
    await invokeForceloadedRconCommands(args, rconLog, { minX: 384, minZ: 128, maxX: 447, maxZ: 159 }, [
      `rlt at world 384 8 128 broadcast 8 96 ${lifeTicks}`,
    ], 30000, 400);
  }

  for (const bot of activeBotInfos) {
    const processIndex = Number.isInteger(bot.index) ? bot.index : botNames.indexOf(bot.name);
    if (processIndex < 0 || processIndex >= mccProcesses.length) {
      throw new Error(`Cannot restart MCC bot ${bot.name}: process index ${processIndex} is outside ${mccProcesses.length} tracked processes.`);
    }
    try {
      await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 30000);
    } catch (error) {
      await fs.appendFile(rconLog, `# ${bot.name} was absent before reconnect churn; restarting MCC client. Previous wait error: ${error.message}\n\n`, "utf8");
      await killProcessTree(mccProcesses[processIndex]);
      mccProcesses[processIndex] = await startMccBot(bot);
      await waitForPlayers(args, [bot.name], [mccProcesses[processIndex]], [bot], 90, 1, {
        restartExited: true,
        maxRestarts: 1,
        onRestart: (ignoredIndex, restartedChild) => {
          mccProcesses[processIndex] = restartedChild;
        },
      });
      await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 30000);
    }
    await invokeRconCommands(args, [
      `kick ${bot.name} qa-rejoin-churn`,
      `say mcc-chaos reconnect-churn kicked ${bot.name}`,
    ], rconLog, 15000, 250);
    await sleep(2500);
    await waitForPlayerLifecycleState(args, bot.name, "absent", rconLog, pluginJar, 30000);
    await killProcessTree(mccProcesses[processIndex]);
    mccProcesses[processIndex] = await startMccBot(bot);
    await waitForPlayers(args, [bot.name], [mccProcesses[processIndex]], [bot], 90, 1, {
      restartExited: true,
      maxRestarts: 1,
      onRestart: (ignoredIndex, restartedChild) => {
        mccProcesses[processIndex] = restartedChild;
      },
    });
    await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 30000);
    await assertOwnershipCountersClean(args, rconLog);
    const rejoinSpawn = anarchyBotSpawn(botNames.indexOf(bot.name));
    const rejoinCommands = botArenaStabilizeCommands(bot.name, rejoinSpawn);
    let rejoinReady = false;
    for (let attempt = 1; attempt <= 2 && !rejoinReady; attempt++) {
      await invokeRconCommands(args, rejoinCommands, rconLog, 15000, 250);
      try {
        await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 8000);
        rejoinReady = true;
      } catch (error) {
        await fs.appendFile(rconLog, `# ${bot.name} disappeared during reconnect post-join setup (attempt ${attempt}/2): ${error.message}\n\n`, "utf8");
        if (attempt >= 2) {
          throw error;
        }
        await killProcessTree(mccProcesses[processIndex]);
        mccProcesses[processIndex] = await startMccBot(bot);
        await waitForPlayers(args, [bot.name], [mccProcesses[processIndex]], [bot], 90, 1, {
          restartExited: true,
          maxRestarts: 1,
          onRestart: (ignoredIndex, restartedChild) => {
            mccProcesses[processIndex] = restartedChild;
          },
        });
        await waitForPlayerLifecycleState(args, bot.name, "present", rconLog, pluginJar, 30000);
      }
    }
    await invokeRconCommands(args, [
      `say mcc-chaos reconnect-churn rejoined ${bot.name}`,
    ], rconLog, 15000, 250);
  }

  await stabilizeMccBotsInArena(
    args,
    activeBotInfos,
    rconLog,
    mccProcesses,
    pluginJar,
    "arena stabilization after reconnect churn",
    4,
    (bot) => botNames.indexOf(bot.name),
  );

  return activeBotInfos;
}

async function invokeChaosScenarioHooks(args, botNames, lifeTicks, rconLog, pluginJar) {
  await invokeFluidLeafBoundaryFixture(args, rconLog);
  await invokeTeleportHooks(args, botNames, rconLog, pluginJar);
  await invokeDimensionTravelHook(args, botNames, rconLog);
  await invokeRealPortalTraversalHook(args, botNames, rconLog);
  await invokeColdRegionTeleportHook(args, botNames, rconLog);
  await invokeNaturalSpawnHook(args, botNames, rconLog, pluginJar);
  await invokeHostileBackgroundLoad(args, lifeTicks, rconLog, pluginJar);
  await invokeAnarchySmpHooks(args, botNames, lifeTicks, rconLog, pluginJar);
}

async function invokeController(args, botInfos, cycleResultDir) {
  const out = path.join(cycleResultDir, "mcc-chaos-summary.json");
  const exitCodeFile = path.join(cycleResultDir, "mcc-chaos-controller.exitcode");
  if (botInfos.length === 0) {
    await writeUtf8(out, JSON.stringify({
      startedAt: new Date().toISOString(),
      finishedAt: new Date().toISOString(),
      seed: args.seed,
      commandSeed: args.seed,
      durationSec: args.durationSec,
      chaosProfile: args.chaosProfile,
      chaosIntensity: args.chaosIntensity,
      ports: [],
      actionCounts: {},
      scenarioCounts: {},
      commandErrors: [],
      bots: [],
    }, null, 2));
    await writeUtf8(exitCodeFile, "0\n");
    await sleep(args.durationSec * 1000);
    return out;
  }

  const ports = botInfos.map((bot) => bot.port).join(",");
  const log = path.join(cycleResultDir, "mcc-chaos-controller.log");
  const controllerArgs = [
    path.join(scriptDir, "mcc-chaos-controller.mjs"),
    "--ports", ports,
    "--password", args.webSocketPassword,
    "--duration-sec", String(args.durationSec),
    "--seed", args.seed,
    "--profile", args.chaosProfile,
    "--intensity", String(args.chaosIntensity),
    "--rcon-port", String(args.rconPort),
    "--rcon-password", args.rconPassword,
    "--think-min-ms", args.chaosProfile === "anarchy-smp" ? "80" : "150",
    "--think-max-ms", args.chaosProfile === "anarchy-smp" ? "550" : "950",
    "--out", out,
  ];
  if (args.chaosProfile === "anarchy-smp") {
    controllerArgs.push("--movement-mode", "rcon-teleport", "--disable-terrain-during-controller");
  }
  const controllerTimeoutSec = args.durationSec + 180 + Math.max(0, botInfos.length - 1) * 10;
  const exitCode = await runLogged("node", controllerArgs, repoRoot, log, { allowFailure: true, timeoutMs: controllerTimeoutSec * 1000 });
  await writeUtf8(exitCodeFile, `${exitCode}\n`);
  return out;
}

async function listCrashReports(crashDir) {
  const names = await fs.readdir(crashDir).catch(() => []);
  return names.filter((name) => name.startsWith("crash-") && name.endsWith(".txt")).map((name) => path.join(crashDir, name));
}

function failurePatterns(args) {
  return args.failOnMovementWarnings ? [...DEFAULT_FAILURE_PATTERNS, "moved too quickly", "moved wrongly"] : DEFAULT_FAILURE_PATTERNS;
}

function expectedHarnessMovementWarning(line) {
  return /\bmccbot\d+\s+moved\s+(?:too quickly|wrongly)!/i.test(line);
}

function failurePatternMatches(args, serverLogSegment) {
  const matches = new Set();
  const patterns = failurePatterns(args);
  for (const line of serverLogSegment.split(/\r?\n/)) {
    for (const pattern of patterns) {
      if (!line.includes(pattern)) {
        continue;
      }
      if (!args.failOnMovementWarnings && (pattern === "moved too quickly" || pattern === "moved wrongly") && expectedHarnessMovementWarning(line)) {
        continue;
      }
      matches.add(pattern);
    }
  }
  return [...matches];
}

async function testRunFailed(args, serverLogSegment, newCrashReports, controllerSummaryPath, controllerExitCodePath) {
  const patternMatches = failurePatternMatches(args, serverLogSegment);
  let controllerHardErrors = [];
  try {
    const summary = JSON.parse(await fs.readFile(controllerSummaryPath, "utf8"));
    controllerHardErrors = controllerHealthIssues(args, summary).map((message) => ({ message }));
  } catch (error) {
    controllerHardErrors = [{ message: `Could not parse controller summary: ${error.message}` }];
  }
  const rawExit = (await readMaybe(controllerExitCodePath)).trim();
  const controllerExitCode = Number.parseInt(rawExit || "1", 10);
  return {
    failed: patternMatches.length > 0 || newCrashReports.length > 0 || controllerHardErrors.length > 0 || controllerExitCode !== 0,
    patternMatches,
    crashReports: newCrashReports,
    controllerHardErrors,
    controllerExitCode,
  };
}

function controllerHealthIssues(args, summary) {
  const issues = [];
  const bots = summary.bots ?? [];
  if (bots.length < args.minBots) {
    issues.push(`Controller had too few active bots: ${bots.length}/${args.minBots}`);
  }

  const totals = bots.reduce((acc, bot) => {
    const stats = bot.stats ?? {};
    acc.sent += stats.sent ?? 0;
    acc.ok += stats.ok ?? 0;
    acc.failed += stats.failed ?? 0;
    acc.timedOut += stats.timedOut ?? 0;
    return acc;
  }, { sent: 0, ok: 0, failed: 0, timedOut: 0 });
  if (bots.length > 0 && totals.ok < bots.length * 8) {
    issues.push(`Controller made too little positive progress: ok=${totals.ok}, bots=${bots.length}`);
  }
  const timeoutRatio = totals.sent === 0 ? 0 : totals.timedOut / totals.sent;
  if (totals.sent > 0 && timeoutRatio > 0.5) {
    issues.push(`Controller timeout ratio too high: timedOut=${totals.timedOut}, sent=${totals.sent}`);
  }
  const failedRatio = totals.sent === 0 ? 0 : totals.failed / totals.sent;
  if (totals.sent > 0 && failedRatio > 0.35) {
    issues.push(`Controller command failure ratio too high: failed=${totals.failed}, sent=${totals.sent}`);
  }
  const unexpectedCommandErrors = (summary.commandErrors ?? []).filter((error) => !isExpectedControllerCommandError(error));
  if (unexpectedCommandErrors.length > 0) {
    const examples = unexpectedCommandErrors.slice(0, 3).map((error) => {
      const command = error.command ?? "unknown";
      const message = `${error.message ?? ""}`.trim() || "(empty)";
      return `${command}: ${message}`;
    });
    issues.push(`Controller saw unexpected command errors: count=${unexpectedCommandErrors.length}; examples=${examples.join(" | ")}`);
  }
  for (const bot of bots) {
    if ((bot.stats?.ok ?? 0) === 0) {
      issues.push(`Controller bot made no successful command progress: ${bot.name}`);
    }
  }

  if (args.chaosProfile === "anarchy-smp") {
    const scenarios = summary.scenarioCounts ?? {};
    const requiredArenaCount = Math.min(
      Math.max(args.minBots || 0, bots.length),
      ANARCHY_BOT_ARENAS.length
    );
    const required = ANARCHY_BOT_ARENAS.slice(0, requiredArenaCount).map((arena) => arena.label);
    const missing = required.filter((scenario) => !scenarios[scenario]);
    if (missing.length > 0) {
      issues.push(`Controller missed required scenario evidence: ${missing.join(", ")}`);
    }
  }

  return issues;
}

function isExpectedControllerCommandError(error) {
  const command = `${error.command ?? ""}`;
  const message = `${error.message ?? ""}`.trim();
  if (command === "MoveToLocation" && message === "") {
    return true;
  }
  if (command === "Respawn" && message === "") {
    return true;
  }
  if (command === "InteractEntity" && message === "") {
    return true;
  }
  if (command === "GetEntities" && /Collection was modified; enumeration operation may not execute/i.test(message)) {
    return true;
  }
  if (/\btimed out after \d+ms$/i.test(message)) {
    return true;
  }
  if (command === "DigBlock" && /^Block is air$/i.test(message)) {
    return true;
  }
  return false;
}

function findBucketMatches(text) {
  const matches = [];
  for (const bucket of FAILURE_BUCKETS) {
    const pattern = bucket.patterns.find((candidate) => new RegExp(candidate, "i").test(text));
    if (pattern) {
      matches.push({ bucket: bucket.name, matchedSignature: pattern });
    }
  }
  return matches;
}

function failureContextText(args, serverLogSegment, failure) {
  if (failure.patternMatches.length === 0) {
    return "";
  }

  const patterns = failurePatterns(args);
  const lines = serverLogSegment.split(/\r?\n/);
  const included = new Set();
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    if (!patterns.some((pattern) => line.includes(pattern))) {
      continue;
    }
    for (let contextIndex = Math.max(0, index - 30); contextIndex <= Math.min(lines.length - 1, index + 60); contextIndex++) {
      included.add(contextIndex);
    }
  }

  return [...included]
    .sort((a, b) => a - b)
    .map((index) => lines[index])
    .join("\n");
}

async function formatControllerDetail(controllerSummaryPath) {
  try {
    const summary = JSON.parse(await fs.readFile(controllerSummaryPath, "utf8"));
    const lines = [
      `Command seed: ${summary.commandSeed ?? summary.seed ?? "unknown"}`,
      `Chaos profile: ${summary.chaosProfile ?? "unknown"} intensity=${summary.chaosIntensity ?? "unknown"}`,
      `Action counts: ${JSON.stringify(summary.actionCounts ?? {})}`,
      `Scenario counts: ${JSON.stringify(summary.scenarioCounts ?? {})}`,
      `Role counts: ${JSON.stringify(summary.roleCounts ?? {})}`,
    ];
    for (const bot of summary.bots ?? []) {
      lines.push(`- ${bot.name} port=${bot.port} location=${JSON.stringify(bot.lastKnownLocation ?? null)} stats=${JSON.stringify(bot.stats ?? {})}`);
    }
    return lines.join("\n");
  } catch (error) {
    return `Controller summary unavailable: ${error.message}`;
  }
}

async function newFailureReport(args, cycleResultDir, serverLogSegment, failure, controllerSummaryPath) {
  const reportPath = path.join(cycleResultDir, "failure-report.md");
  const classificationPath = path.join(cycleResultDir, "failure-classification.json");
  const crashTexts = [];
  for (const crash of failure.crashReports.slice(0, 4)) {
    crashTexts.push(`### ${path.basename(crash)}\nPath: ${crash}\n\n\`\`\`log\n${(await readMaybe(crash)).split(/\r?\n/).slice(0, 100).join("\n")}\n\`\`\``);
  }
  const bucketText = `${failureContextText(args, serverLogSegment, failure)}\n${crashTexts.join("\n")}`.trim();
  const bucketMatches = bucketText ? findBucketMatches(bucketText) : [];
  await writeUtf8(classificationPath, JSON.stringify(bucketMatches, null, 2) + "\n");
  const gitCommit = (await commandOutput("git", ["-C", repoRoot, "rev-parse", "HEAD"])).trim() || "unknown";
  const gitStatus = await commandOutput("git", ["-C", repoRoot, "status", "--short", "--branch"]);
  const gitDiffStat = await commandOutput("git", ["-C", repoRoot, "diff", "--stat"]);
  const controllerDetail = await formatControllerDetail(controllerSummaryPath);
  const tail = serverLogSegment.split(/\r?\n/).slice(-240).join("\n");

  await writeUtf8(reportPath, `# MCC Chaos Failure Report

Generated: ${new Date().toISOString()}
Commit: ${gitCommit}
Seed: ${args.seed}
Duration: ${args.durationSec}s
Bots: ${args.botCount}
Chaos profile: ${args.chaosProfile}
Chaos intensity: ${args.chaosIntensity}
Server port: ${args.serverPort}
RCON port: ${args.rconPort}
Controller summary: ${controllerSummaryPath}
Classification JSON: ${classificationPath}

## Compact Classification

${bucketMatches.length ? bucketMatches.map((match) => `- Bucket: ${match.bucket}\n  Matched signature: ${match.matchedSignature}`).join("\n") : "No bucket-specific signature matched."}

## Controller Detail

\`\`\`
${controllerDetail}
\`\`\`

## Failure Signals

Pattern matches:

\`\`\`
${failure.patternMatches.join("\n")}
\`\`\`

New crash reports:

\`\`\`
${failure.crashReports.join("\n")}
\`\`\`

Controller hard errors:

\`\`\`
${failure.controllerHardErrors.map((error) => error.message).join("\n")}
\`\`\`

Controller exit code:

\`\`\`
${failure.controllerExitCode}
\`\`\`

## Git Status

\`\`\`
${gitStatus}
\`\`\`

## Git Diff Stat

\`\`\`
${gitDiffStat}
\`\`\`

## Crash Snippets

${crashTexts.length ? crashTexts.join("\n\n") : "No new crash reports."}

## Server Log Tail

\`\`\`log
${tail}
\`\`\`
`);
  return reportPath;
}

async function commandOutput(file, args) {
  return new Promise((resolve) => {
    const child = spawn(file, args, { cwd: repoRoot, stdio: ["ignore", "pipe", "pipe"] });
    let output = "";
    child.stdout.on("data", (chunk) => output += chunk.toString());
    child.stderr.on("data", (chunk) => output += chunk.toString());
    child.on("error", () => resolve(""));
    child.on("exit", () => resolve(output));
  });
}

async function newAgentPrompt(args, cycleResultDir, failureReport) {
  const promptPath = path.join(cycleResultDir, "agent-prompt.md");
  await writeUtf8(promptPath, `You are fixing ShreddedPaper async/region runtime bugs found by the MCC chaos pipeline.

Repository: ${repoRoot}
Failure report: ${failureReport}

Please:
1. Inspect the failure report, server log excerpts, crash reports, and relevant code.
2. Identify the smallest production fix for the root cause.
3. Preserve unrelated user changes in the dirty worktree.
4. Run focused build/test verification after editing.
5. Summarize changed files and verification.

Do not modify files under ${args.rootPath} except transient logs if needed.
`);
  return promptPath;
}

async function invokeAgentFix(args, promptPath, cycleResultDir) {
  const logPath = path.join(cycleResultDir, "codex-agent.log");
  const promptText = await fs.readFile(promptPath, "utf8");
  await ensureDir(path.dirname(logPath));
  const log = fsSync.openSync(logPath, "w");
  const child = spawn(args.codexCommand, ["exec", "--dangerously-bypass-approvals-and-sandbox", "-C", repoRoot, "-"], {
    cwd: repoRoot,
    stdio: ["pipe", log, log],
  });
  child.stdin.end(promptText);
  const exitCode = await waitForExit(child);
  fsSync.closeSync(log);
  if (exitCode !== 0) {
    throw new Error(`Codex agent failed with exit code ${exitCode}. See ${logPath}`);
  }
}

async function buildArtifacts(args, cycleResultDir, serverJar, pluginJar) {
  const pluginSources = [
    path.join(repoRoot, "tools", "region-load-test-plugin", "src", "main", "java", "io", "multipaper", "regionloadtest", "RegionLoadTestCommand.java"),
    path.join(repoRoot, "tools", "region-load-test-plugin", "src", "main", "java", "io", "multipaper", "regionloadtest", "RegionLoadTestPlugin.java"),
    path.join(repoRoot, "tools", "region-load-test-plugin", "build.gradle.kts"),
  ];
  if (args.skipBuild) {
    if (!fsSync.existsSync(serverJar)) {
      throw new Error(`Missing server jar and --skip-build was specified: ${serverJar}`);
    }
    assertArtifactFresh(pluginJar, pluginSources, "Region load test plugin jar");
    return;
  }
  await runLogged(path.join(repoRoot, "gradlew"), ["applyAllPatches", "--stacktrace", "--console=plain"], repoRoot, path.join(cycleResultDir, "gradle-applyAllPatches.log"));
  await runLogged(path.join(repoRoot, "gradlew"), [":shreddedpaper-server:createMojmapPaperclipJar", "--stacktrace", "--console=plain"], repoRoot, path.join(cycleResultDir, "gradle-createMojmapPaperclipJar.log"));
  await runLogged(path.join(repoRoot, "gradlew"), ["-p", "tools/region-load-test-plugin", "jar", "--stacktrace", "--console=plain"], repoRoot, path.join(cycleResultDir, "gradle-region-load-test-plugin.log"));
  if (!fsSync.existsSync(serverJar)) {
    throw new Error(`Server jar was not produced: ${serverJar}`);
  }
  if (!fsSync.existsSync(pluginJar)) {
    throw new Error(`Region load test plugin jar was not produced: ${pluginJar}`);
  }
}

async function stopServer(args, child, serverDir) {
  if (!child || child.exitCode !== null) {
    return;
  }
  let forced = false;
  try {
    await sendRconCommand(args, "stop", 5000);
  } catch (error) {
    console.warn(`Could not send stop over RCON: ${error.message}`);
    await captureServerThreadDump(args, child, serverDir, "stop-rcon-failed");
  }
  const stopped = await waitForExitWithTimeout(child, 60000);
  if (!stopped) {
    console.warn(`Server did not stop in time; killing PID ${child.pid}.`);
    await captureServerThreadDump(args, child, serverDir, "forced-kill");
    await killProcessTree(child);
    forced = true;
  }
  await assertServerResourcesReleased(args, serverDir);
  if (forced) {
    throw new Error(`MCC server required forced kill after stop; see ${serverDir}`);
  }
}

async function captureServerThreadDump(args, child, serverDir, label) {
  if (!child || child.exitCode !== null || !child.pid) {
    return;
  }
  const javaDir = path.dirname(args.javaPath);
  let jcmd = path.join(javaDir, "jcmd");
  if (!fsSync.existsSync(jcmd)) {
    const discovered = (await commandOutput("which", ["jcmd"])).trim().split(/\r?\n/, 1)[0] ?? "";
    if (discovered) {
      jcmd = discovered;
    }
  }
  if (!fsSync.existsSync(jcmd)) {
    console.warn(`Could not capture JVM thread dump; jcmd not found next to Java at ${jcmd}`);
    return;
  }
  const safeLabel = label.replaceAll(/[^a-zA-Z0-9_.-]/g, "_");
  const dumpPath = path.join(serverDir, `jcmd-${safeLabel}.log`);
  try {
    await runLogged(jcmd, [String(child.pid), "Thread.print", "-l"], serverDir, dumpPath, {
      allowFailure: true,
      timeoutMs: 10000,
    });
    console.warn(`Captured JVM thread dump before shutdown escalation: ${dumpPath}`);
  } catch (error) {
    console.warn(`Could not capture JVM thread dump before shutdown escalation: ${error.message}`);
  }
}

async function assertServerResourcesReleased(args, serverDir) {
  await assertNoLsof(`TCP:${args.serverPort}`, ["-Pan", `-iTCP:${args.serverPort}`, "-sTCP:LISTEN"]);
  await assertNoLsof(`TCP:${args.rconPort}`, ["-Pan", `-iTCP:${args.rconPort}`, "-sTCP:LISTEN"]);
  await assertNoLsof("world/session.lock", [path.join(serverDir, "world", "session.lock")]);
}

async function assertNoLsof(label, argsForLsof) {
  const result = await commandOutput("lsof", argsForLsof);
  if (result.trim()) {
    throw new Error(`Resource still held after MCC server shutdown (${label}):\n${result}`);
  }
}

async function waitForExitWithTimeout(child, timeoutMs) {
  if (!child || child.exitCode !== null) return true;
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(false), timeoutMs);
    child.once("exit", () => {
      clearTimeout(timer);
      resolve(true);
    });
  });
}

async function killProcessTree(child) {
  if (!child || child.exitCode !== null) {
    return;
  }
  try {
    process.kill(-child.pid, "SIGTERM");
  } catch {
    try { child.kill("SIGTERM"); } catch {}
  }
  if (!await waitForExitWithTimeout(child, 5000)) {
    try {
      process.kill(-child.pid, "SIGKILL");
    } catch {
      try { child.kill("SIGKILL"); } catch {}
    }
  }
}

async function stopProcesses(children) {
  await Promise.all(children.map((child) => killProcessTree(child)));
}

async function invokeOneCycle(args, cycleNumber, mccExe, webSocketTemplate, serverJar, pluginJar) {
  const cycleName = `cycle-${String(cycleNumber).padStart(2, "0")}`;
  const cycleRunDir = path.join(args.runRoot, cycleName);
  const cycleResultDir = path.join(args.resultRoot, cycleName);
  assertUnderRoot(args.rootPath, cycleRunDir);
  assertUnderRoot(args.rootPath, cycleResultDir);
  await fs.rm(cycleRunDir, { recursive: true, force: true });
  await fs.rm(cycleResultDir, { recursive: true, force: true });
  await ensureDir(cycleRunDir);
  await ensureDir(cycleResultDir);

  await buildArtifacts(args, cycleResultDir, serverJar, pluginJar);
  const serverDir = await prepareServerDirectory(args, cycleRunDir, serverJar, pluginJar);
  const crashDir = path.join(serverDir, "crash-reports");
  const beforeCrashReports = await listCrashReports(crashDir);
  let serverProcess = null;
  let mccProcesses = [];
  let controllerSummary = "";

  try {
    try {
      serverProcess = await startTestServer(args, serverDir);
    } catch (error) {
      const serverLogSegment = `${await readMaybe(path.join(serverDir, "logs", "latest.log"))}\nStartup failure: ${error.message}\n`;
      await writeUtf8(path.join(cycleResultDir, "server-startup-failure.log"), serverLogSegment);
      const failure = {
        failed: true,
        patternMatches: ["startup-failure"],
        crashReports: (await listCrashReports(crashDir)).filter((crash) => !beforeCrashReports.includes(crash)),
        controllerHardErrors: [{ message: error.message }],
        controllerExitCode: 1,
      };
      const failureReport = await newFailureReport(args, cycleResultDir, serverLogSegment, failure, "");
      return { passed: false, cycle: cycleNumber, resultDir: cycleResultDir, failureReport, agentPrompt: "" };
    }

    const logPath = path.join(serverDir, "logs", "latest.log");
    const logOffset = 0;
    const botInfos = await writeMccBotFiles(args, cycleRunDir, mccExe, webSocketTemplate);
    mccProcesses = await startMccBots(botInfos);
    let activeBotInfos = botInfos;
    try {
      const joinedNames = await waitForPlayers(args, botInfos.map((bot) => bot.name), mccProcesses, botInfos, 140, null, { restartExited: true, maxRestarts: 2 });
      activeBotInfos = botInfos.filter((bot) => joinedNames.includes(bot.name));
      if (activeBotInfos.length < botInfos.length) {
        console.warn(`Continuing with ${activeBotInfos.length}/${botInfos.length} joined MCC bots: ${activeBotInfos.map((bot) => bot.name).join(", ")}`);
      }
    } catch (error) {
      const serverLogSegment = `${await readMaybe(path.join(serverDir, "logs", "latest.log"))}\nMCC join failure: ${error.message}\n`;
      await writeUtf8(path.join(cycleResultDir, "server-test-segment.log"), serverLogSegment);
      const failure = {
        failed: true,
        patternMatches: ["mcc-join-failure"],
        crashReports: (await listCrashReports(crashDir)).filter((crash) => !beforeCrashReports.includes(crash)),
        controllerHardErrors: [{ message: error.message }],
        controllerExitCode: 1,
      };
      const failureReport = await newFailureReport(args, cycleResultDir, serverLogSegment, failure, "");
      return { passed: false, cycle: cycleNumber, resultDir: cycleResultDir, failureReport, agentPrompt: "" };
    }

    const rconLog = path.join(cycleResultDir, "rcon.log");
    await writeUtf8(rconLog, "");
    await invokeSetupCommands(args, activeBotInfos.map((bot) => bot.name), rconLog);
    const lifeTicks = Math.max(1200, (args.durationSec + 30) * 20);
    await invokeChaosScenarioHooks(args, activeBotInfos.map((bot) => bot.name), lifeTicks, rconLog, pluginJar);
    activeBotInfos = await invokeReconnectChurnHook(args, activeBotInfos, mccProcesses, lifeTicks, rconLog, pluginJar);
    await assertMccBotArenaReadiness(args, activeBotInfos, rconLog, mccProcesses, pluginJar);
    await assertRconScenarioEvidence(args, rconLog, activeBotInfos);
    controllerSummary = await invokeController(args, activeBotInfos, cycleResultDir);
    await sleep(5000);
    await assertRegionLoadTestClean(args, rconLog, pluginJar);
    await assertOwnershipCountersClean(args, rconLog);
    const serverLogSegment = await fileSince(logPath, logOffset);
    await writeUtf8(path.join(cycleResultDir, "server-test-segment.log"), serverLogSegment);
    assertRltRuntimeEvidence(serverLogSegment);
    const newCrashReports = (await listCrashReports(crashDir)).filter((crash) => !beforeCrashReports.includes(crash));
    const controllerExitCodePath = path.join(cycleResultDir, "mcc-chaos-controller.exitcode");
    const failure = await testRunFailed(args, serverLogSegment, newCrashReports, controllerSummary, controllerExitCodePath);
    if (failure.failed) {
      const failureReport = await newFailureReport(args, cycleResultDir, serverLogSegment, failure, controllerSummary);
      return { passed: false, cycle: cycleNumber, resultDir: cycleResultDir, failureReport, agentPrompt: "" };
    }

    await writeUtf8(path.join(cycleResultDir, "pass.txt"), `MCC chaos cycle passed at ${new Date().toISOString()}.\n`);
    return { passed: true, cycle: cycleNumber, resultDir: cycleResultDir, failureReport: "", agentPrompt: "" };
  } catch (error) {
    const serverLogSegment = `${await readMaybe(path.join(serverDir, "logs", "latest.log"))}\nMCC cycle failure: ${error?.stack ?? error}\n`;
    await writeUtf8(path.join(cycleResultDir, "server-test-segment.log"), serverLogSegment);
    const failure = {
      failed: true,
      patternMatches: ["mcc-cycle-failure"],
      crashReports: (await listCrashReports(crashDir)).filter((crash) => !beforeCrashReports.includes(crash)),
      controllerHardErrors: [{ message: error?.message ?? String(error) }],
      controllerExitCode: 1,
    };
    const failureReport = await newFailureReport(args, cycleResultDir, serverLogSegment, failure, controllerSummary);
    return { passed: false, cycle: cycleNumber, resultDir: cycleResultDir, failureReport, agentPrompt: "" };
  } finally {
    await stopProcesses(mccProcesses);
    if (!args.noStop) {
      await stopServer(args, serverProcess, serverDir);
    }
  }
}

async function main() {
  const args = parseArgs(process.argv);
  args.rootPath = path.resolve(args.root);
  args.cacheDir = path.join(args.rootPath, "cache");
  args.runRoot = path.join(args.rootPath, "runs");
  args.resultRoot = path.join(args.rootPath, "results");

  const serverJar = path.join(repoRoot, "shreddedpaper-server", "build", "libs", "shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar");
  const pluginJar = path.join(repoRoot, "tools", "region-load-test-plugin", "build", "libs", "region-load-test-plugin-0.1.0-SNAPSHOT.jar");

  console.log(`MCC chaos root: ${args.rootPath}`);
  console.log(`Seed: ${args.seed}`);
  console.log(`AgentMode: ${args.agentMode}`);
  console.log(`ChaosProfile: ${args.chaosProfile}`);
  console.log(`ChaosIntensity: ${args.chaosIntensity}`);
  console.log(`Platform RID: ${mccRid()}`);
  globalThis.__mccChaosNoNetwork = args.noNetwork;

  await ensureDir(args.rootPath);
  await ensureDir(args.cacheDir);
  await ensureDir(args.runRoot);
  await ensureDir(args.resultRoot);

  if (args.dryRun) {
    console.log(JSON.stringify({
      repoRoot,
      serverJar,
      pluginJar,
      root: args.rootPath,
      cacheDir: args.cacheDir,
      runRoot: args.runRoot,
      resultRoot: args.resultRoot,
      mccRid: mccRid(),
      botCount: args.botCount,
      durationSec: args.durationSec,
      serverPort: args.serverPort,
      rconPort: args.rconPort,
      onlineMode: false,
    }, null, 2));
    return;
  }

  const mccExe = await installMcc(args, args.cacheDir);
  const webSocketTemplate = await getWebSocketBotTemplate(args.cacheDir);
  let lastResult = null;

  for (let cycle = 1; cycle <= args.maxCycles; cycle++) {
    console.log(`Starting MCC chaos cycle ${cycle} / ${args.maxCycles}`);
    lastResult = await invokeOneCycle(args, cycle, mccExe, webSocketTemplate, serverJar, pluginJar);
    if (lastResult.passed) {
      console.log(`MCC chaos passed on cycle ${cycle}. Results: ${lastResult.resultDir}`);
      process.exitCode = 0;
      return;
    }

    console.log(`MCC chaos failed on cycle ${cycle}. Failure report: ${lastResult.failureReport}`);
    if (args.agentMode === "ReportOnly") {
      console.log("AgentMode=ReportOnly, stopping after report generation.");
      process.exitCode = 1;
      return;
    }

    if (!lastResult.agentPrompt) {
      lastResult.agentPrompt = await newAgentPrompt(args, lastResult.resultDir, lastResult.failureReport);
    }
    if (args.agentMode === "Prompt") {
      console.log(`Agent prompt written: ${lastResult.agentPrompt}`);
      process.exitCode = 1;
      return;
    }
    if (cycle < args.maxCycles) {
      await invokeAgentFix(args, lastResult.agentPrompt, lastResult.resultDir);
    }
  }

  if (lastResult && !lastResult.passed) {
    console.log(`MCC chaos failed after ${args.maxCycles} cycle(s). Last report: ${lastResult.failureReport}`);
  }
  process.exitCode = 1;
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
