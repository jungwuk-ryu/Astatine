#!/usr/bin/env node

import fs from "node:fs/promises";
import net from "node:net";
import path from "node:path";
import process from "node:process";
import { setTimeout as sleep } from "node:timers/promises";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..", "..");

const watchedPatterns = [
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
  "AsyncCatcher",
  "wrong thread",
  "not owned",
  "already retired",
  "Block change is not write locked",
  "Cannot add entity off-main",
  "Cannot move an entity into off-main",
  "Chunk system crash propagated",
  "fastClip",
  "handleUseItem",
  "SpawnPlacementTypes",
  "Starlight",
];

function parseArgs(argv) {
  const args = {
    root: path.join(repoRoot, "run", "agent-validation-mcc"),
    serverDir: "",
    serverPort: 25640,
    rconPort: 25641,
    websocketBasePort: 8460,
    rconPassword: "codex-agent-validation",
    durationSec: 90,
    botCount: 0,
    dryRun: false,
  };

  const values = new Map([
    ["--root", "root"],
    ["--server-dir", "serverDir"],
    ["--server-port", "serverPort"],
    ["--rcon-port", "rconPort"],
    ["--websocket-base-port", "websocketBasePort"],
    ["--rcon-password", "rconPassword"],
    ["--duration-sec", "durationSec"],
    ["--bot-count", "botCount"],
  ]);
  const switches = new Map([["--dry-run", "dryRun"]]);

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
    if (!values.has(flag)) {
      throw new Error(`Unknown argument: ${raw}`);
    }
    const key = values.get(flag);
    const value = inlineValue ?? argv[++i];
    if (value === undefined) {
      throw new Error(`Missing value for ${flag}`);
    }
    if (["serverPort", "rconPort", "websocketBasePort", "durationSec", "botCount"].includes(key)) {
      args[key] = Number.parseInt(value, 10);
    } else {
      args[key] = value;
    }
  }

  if (!args.serverDir) {
    args.serverDir = path.join(args.root, "server");
  }
  for (const key of ["serverPort", "rconPort", "websocketBasePort", "durationSec", "botCount"]) {
    if (!Number.isInteger(args[key]) || args[key] < 0) {
      throw new Error(`--${key} must be a non-negative integer`);
    }
  }
  return args;
}

function printHelp() {
  console.log(`Usage:
  node tools/mcc-chaos/agent-validation-mcc.mjs [options]

Defaults are scoped for this validation lane:
  --root run/agent-validation-mcc
  --server-port 25640
  --rcon-port 25641
  --websocket-base-port 8460

The runner attaches to an already-started local server with RegionLoadTest
installed, drives scanner-blind RLT fixtures over RCON, optionally records MCC
WebSocket port reachability, and writes logs under the configured root.`);
}

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

function rconPacket(id, type, payload) {
  const body = Buffer.from(payload, "utf8");
  const length = 10 + body.length;
  const packet = Buffer.alloc(4 + length);
  packet.writeInt32LE(length, 0);
  packet.writeInt32LE(id, 4);
  packet.writeInt32LE(type, 8);
  body.copy(packet, 12);
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
    const onError = (error) => cleanup(error);
    const cleanup = (error, packet = null) => {
      clearTimeout(timer);
      socket.off("data", onData);
      socket.off("error", onError);
      if (error) reject(error);
      else resolve(packet);
    };
    socket.on("data", onData);
    socket.once("error", onError);
  });
}

async function sendRcon(args, command, timeoutMs = 15000) {
  const socket = net.createConnection({ host: "127.0.0.1", port: args.rconPort });
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });
  try {
    socket.write(rconPacket(1, 3, args.rconPassword));
    const auth = await readRconPacket(socket, timeoutMs);
    if (auth.id === -1) {
      throw new Error(`RCON authentication failed on port ${args.rconPort}`);
    }
    socket.write(rconPacket(2, 2, command));
    return (await readRconPacket(socket, timeoutMs)).payload;
  } finally {
    socket.destroy();
  }
}

function stripColors(text) {
  return text.replace(/\u00a7./g, "");
}

function commandFailed(response) {
  return /Unknown or incomplete command|Incorrect argument for command|An unexpected error occurred|Usage: \/rlt|RLT playercheck failed|No player was found|That position is not loaded/i.test(stripColors(response));
}

function validationCommands(args) {
  const seconds = Math.max(20, args.durationSec);
  const fixtureTicks = Math.max(80, Math.min(600, seconds * 20));
  return [
    "rlt cleanup",
    "difficulty hard",
    "gamerule doMobSpawning true",
    "gamerule keepInventory true",
    "gamerule doImmediateRespawn true",
    "time set midnight",
    "weather clear",
    "rlt at world 0 80 0 chunkgen 4 false",
    "rlt at world 96 80 0 chunkgen 4 false",
    "rlt at world 192 80 0 chunkgen 4 false",
    "rlt at world 320 80 0 chunkgen 4 false",
    "forceload add -16 -16 352 16",
    "fill -16 79 -16 352 79 16 minecraft:stone",
    "fill -16 80 -16 112 84 16 minecraft:air",
    "fill 113 80 -16 240 84 16 minecraft:air",
    "fill 241 80 -16 352 84 16 minecraft:air",
    "fill 120 80 -16 136 80 16 minecraft:water",
    "fill 137 80 -16 152 80 16 minecraft:lava",
    "say agent-validation-mcc fixture player-use-fastclip spawnplacement starlight reconnect portal-respawn",
    "rlt at world 0 80 0 syncload 64 4",
    `rlt at world 96 80 0 path 24 48 ${fixtureTicks}`,
    `rlt at world 128 80 0 boundary 4 ${Math.min(fixtureTicks, 240)} 48 1`,
    `rlt at world 192 80 0 lighting 48 ${Math.min(fixtureTicks, 240)} 2`,
    `rlt at world 256 80 0 tracker 96 ${Math.min(fixtureTicks, 240)} 16`,
    `rlt at world 320 80 0 broadcast 4 48 ${Math.min(fixtureTicks, 240)}`,
    "rlt status",
  ];
}

async function runCommands(args, commands, rconLog) {
  for (const command of commands) {
    const response = await sendRcon(args, command, 30000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    if (commandFailed(response)) {
      throw new Error(`RCON command failed: ${command}\n${stripColors(response).trim()}`);
    }
    await sleep(250);
  }
}

async function assertCleanup(args, rconLog) {
  const cleanup = await sendRcon(args, "rlt cleanup", 45000);
  await fs.appendFile(rconLog, `> rlt cleanup\n${cleanup}\n\n`, "utf8");
  await sleep(8000);
  const killed = [];
  for (const dimension of ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]) {
    const command = `execute in ${dimension} run kill @e[tag=shreddedpaper_rlt]`;
    const response = await sendRcon(args, command, 45000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    if (/\bKilled\b/i.test(stripColors(response))) {
      killed.push(`${dimension}: ${stripColors(response).trim()}`);
    }
  }
  if (killed.length > 0) {
    throw new Error(`RegionLoadTest cleanup left tagged entities:\n${killed.join("\n")}`);
  }
}

async function waitForEvidence(args, latestLog, wanted, timeoutMs, startOffset = 0) {
  const deadline = Date.now() + timeoutMs;
  let text = "";
  while (Date.now() < deadline) {
    text = await readRequiredLogSince(latestLog, startOffset);
    const missing = wanted.filter((pattern) => !pattern.test(text));
    if (missing.length === 0) {
      return text;
    }
    await sleep(1000);
  }
  const missing = wanted.filter((pattern) => !pattern.test(text)).map(String);
  throw new Error(`Timed out waiting for validation evidence in ${latestLog}:\n${missing.join("\n")}`);
}

async function readRequiredLog(file) {
  try {
    return await fs.readFile(file, "utf8");
  } catch (error) {
    throw new Error(`Expected server log is missing or unreadable: ${file}: ${error.message}`);
  }
}

async function readRequiredLogSince(file, startOffset) {
  const text = await readRequiredLog(file);
  return text.slice(Math.min(Math.max(0, startOffset), text.length));
}

async function fileSize(file) {
  try {
    return (await fs.stat(file)).size;
  } catch {
    return 0;
  }
}

async function probeWebSockets(args) {
  const probes = [];
  for (let index = 0; index < args.botCount; index++) {
    const port = args.websocketBasePort + index;
    probes.push(new Promise((resolve) => {
      const socket = net.createConnection({ host: "127.0.0.1", port });
      const timer = setTimeout(() => {
        socket.destroy();
        resolve({ port, reachable: false });
      }, 1500);
      socket.once("connect", () => {
        clearTimeout(timer);
        socket.destroy();
        resolve({ port, reachable: true });
      });
      socket.once("error", () => {
        clearTimeout(timer);
        resolve({ port, reachable: false });
      });
    }));
  }
  return Promise.all(probes);
}

function assertWebSocketReachability(args, webSockets) {
  if (args.botCount === 0) {
    return;
  }
  const reachable = webSockets.filter((probe) => probe.reachable);
  if (reachable.length !== args.botCount) {
    const missing = webSockets
      .filter((probe) => !probe.reachable)
      .map((probe) => probe.port)
      .join(", ");
    throw new Error(`Expected ${args.botCount} MCC WebSocket ports, reachable=${reachable.length}; missing ports: ${missing}`);
  }
}

function parseCounters(line) {
  const counters = {};
  for (const match of line.matchAll(/\b([A-Za-z][A-Za-z0-9]*)=(\d+)/g)) {
    counters[match[1]] = Number(match[2]);
  }
  return counters;
}

function findEvidenceLine(text, pattern, label) {
  const match = text.match(pattern);
  if (!match) {
    throw new Error(`Missing validation evidence: ${label}`);
  }
  return match[0];
}

function assertCounter(counters, key, predicate, label) {
  const value = counters[key];
  if (!Number.isFinite(value) || !predicate(value)) {
    throw new Error(`Bad validation evidence for ${label}: ${key}=${value}`);
  }
}

function assertValidationEvidence(logText, rconText) {
  const evidence = {};

  evidence.syncLoad = parseCounters(findEvidenceLine(logText, /Sync-load guard probe finished:[^\n]*/, "sync-load guard"));
  assertCounter(evidence.syncLoad, "attempts", (value) => value > 0, "sync-load guard");
  assertCounter(evidence.syncLoad, "unexpectedSuccess", (value) => value === 0, "sync-load guard");
  assertCounter(evidence.syncLoad, "unexpectedFailure", (value) => value === 0, "sync-load guard");

  evidence.pathfinding = parseCounters(findEvidenceLine(logText, /pathfinding load finished:[^\n]*/, "pathfinding"));
  assertCounter(evidence.pathfinding, "requested", (value) => value > 0, "pathfinding");
  assertCounter(evidence.pathfinding, "queued", (value) => value === evidence.pathfinding.requested, "pathfinding");
  assertCounter(evidence.pathfinding, "completed", (value) => value === evidence.pathfinding.queued, "pathfinding");
  assertCounter(evidence.pathfinding, "rejected", (value) => value === 0, "pathfinding");
  assertCounter(evidence.pathfinding, "spawned", (value) => value > 0, "pathfinding");
  assertCounter(evidence.pathfinding, "skippedUnloaded", (value) => value === 0, "pathfinding");
  assertCounter(evidence.pathfinding, "pathStarted", (value) => value === evidence.pathfinding.spawned, "pathfinding");

  evidence.redstone = parseCounters(findEvidenceLine(logText, /redstone boundary load finished:[^\n]*/, "redstone boundary"));
  assertCounter(evidence.redstone, "pulseTasks", (value) => value > 0, "redstone boundary");
  assertCounter(evidence.redstone, "completed", (value) => value === evidence.redstone.pulseTasks, "redstone boundary");
  assertCounter(evidence.redstone, "rejected", (value) => value === 0, "redstone boundary");
  assertCounter(evidence.redstone, "writes", (value) => value > 0, "redstone boundary");
  assertCounter(evidence.redstone, "skippedUnloaded", (value) => value === 0, "redstone boundary");

  evidence.lighting = parseCounters(findEvidenceLine(logText, /lighting load finished:[^\n]*/, "lighting"));
  assertCounter(evidence.lighting, "queued", (value) => value > 0, "lighting");
  assertCounter(evidence.lighting, "rejected", (value) => value === 0, "lighting");
  assertCounter(evidence.lighting, "writes", (value) => value > 0, "lighting");
  assertCounter(evidence.lighting, "lightReads", (value) => value > 0, "lighting");
  assertCounter(evidence.lighting, "brightSamples", (value) => value > 0, "lighting");
  assertCounter(evidence.lighting, "darkSamples", (value) => value > 0, "lighting");
  assertCounter(evidence.lighting, "skippedUnloaded", (value) => value === 0, "lighting");

  evidence.tracker = parseCounters(findEvidenceLine(rconText, /Queued tracker flood:[^\n]*/, "tracker flood"));
  assertCounter(evidence.tracker, "queued", (value) => value > 0, "tracker flood");
  assertCounter(evidence.tracker, "rejected", (value) => value === 0, "tracker flood");

  evidence.broadcast = parseCounters(findEvidenceLine(rconText, /Queued broadcast flood:[^\n]*/, "broadcast flood"));
  assertCounter(evidence.broadcast, "queuedTasks", (value) => value > 0, "broadcast flood");

  return evidence;
}

function scanFailures(logText) {
  return watchedPatterns.filter((pattern) => logText.includes(pattern));
}

async function main() {
  const args = parseArgs(process.argv);
  const root = path.resolve(args.root);
  const resultDir = path.join(root, "results", new Date().toISOString().replaceAll(/[:.]/g, "-"));
  const rconLog = path.join(resultDir, "rcon.log");
  const summaryPath = path.join(resultDir, "summary.json");
  const latestLog = path.join(path.resolve(args.serverDir), "logs", "latest.log");
  const commands = validationCommands(args);

  await ensureDir(resultDir);
  if (args.dryRun) {
    await fs.writeFile(summaryPath, JSON.stringify({ args, commands, latestLog }, null, 2), "utf8");
    console.log(`Dry run written: ${summaryPath}`);
    return;
  }

  await sendRcon(args, "list", 5000);
  await readRequiredLog(latestLog);
  const logStartOffset = await fileSize(latestLog);
  const webSockets = await probeWebSockets(args);
  assertWebSocketReachability(args, webSockets);
  await runCommands(args, commands, rconLog);
  await sleep(Math.max(5000, Math.min(30000, args.durationSec * 1000)));

  const logText = await waitForEvidence(args, latestLog, [
    /Sync-load guard probe finished:/,
    /pathfinding load finished:/,
    /boundary load finished:/,
    /lighting load finished:/,
  ], 120000, logStartOffset);
  const rconText = await fs.readFile(rconLog, "utf8");
  const evidence = assertValidationEvidence(logText, rconText);
  await runCommands(args, ["forceload remove -16 -16 352 16"], rconLog);
  await assertCleanup(args, rconLog);
  const failures = scanFailures(logText);
  const summary = {
    finishedAt: new Date().toISOString(),
    root,
    serverDir: path.resolve(args.serverDir),
    serverPort: args.serverPort,
    rconPort: args.rconPort,
    websocketBasePort: args.websocketBasePort,
    botCount: args.botCount,
    webSockets,
    evidence,
    commandsRun: commands.length,
    watchedPatternMatches: failures,
  };
  await fs.writeFile(summaryPath, JSON.stringify(summary, null, 2), "utf8");
  if (failures.length > 0) {
    throw new Error(`Validation log matched watched failure patterns: ${failures.join(", ")}`);
  }
  console.log(`Agent validation MCC pass complete: ${summaryPath}`);
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
