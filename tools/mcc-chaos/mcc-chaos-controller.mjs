#!/usr/bin/env node

import fs from "node:fs/promises";
import net from "node:net";
import { setTimeout as sleep } from "node:timers/promises";

function parseArgs(argv) {
  const args = {
    ports: [],
    host: "127.0.0.1",
    password: "",
    durationSec: 300,
    radius: 96,
    seed: `${Date.now()}`,
    profile: "baseline",
    intensity: 1,
    out: "",
    connectTimeoutSec: 90,
    commandTimeoutMs: 8000,
    thinkMinMs: 150,
    thinkMaxMs: 950,
    failOnCommandError: false,
    rconPort: 0,
    rconPassword: "",
    movementMode: "mcc",
    disableTerrainDuringController: false,
    verbose: false,
  };

  for (let i = 2; i < argv.length; i++) {
    const arg = argv[i];
    const readValue = () => {
      if (arg.includes("=")) {
        return arg.slice(arg.indexOf("=") + 1);
      }
      i++;
      return argv[i];
    };

    if (arg === "--verbose") {
      args.verbose = true;
    } else if (arg === "--fail-on-command-error") {
      args.failOnCommandError = true;
    } else if (arg === "--disable-terrain-during-controller") {
      args.disableTerrainDuringController = true;
    } else if (arg.startsWith("--ports")) {
      args.ports = readValue().split(",").map((p) => Number.parseInt(p.trim(), 10)).filter(Boolean);
    } else if (arg.startsWith("--host")) {
      args.host = readValue();
    } else if (arg.startsWith("--password")) {
      args.password = readValue();
    } else if (arg.startsWith("--duration-sec")) {
      args.durationSec = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--radius")) {
      args.radius = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--seed")) {
      args.seed = readValue();
    } else if (arg.startsWith("--profile")) {
      args.profile = readValue();
    } else if (arg.startsWith("--intensity")) {
      args.intensity = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--out")) {
      args.out = readValue();
    } else if (arg.startsWith("--connect-timeout-sec")) {
      args.connectTimeoutSec = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--command-timeout-ms")) {
      args.commandTimeoutMs = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--rcon-port")) {
      args.rconPort = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--rcon-password")) {
      args.rconPassword = readValue();
    } else if (arg.startsWith("--movement-mode")) {
      args.movementMode = readValue();
    } else if (arg.startsWith("--think-min-ms")) {
      args.thinkMinMs = Number.parseInt(readValue(), 10);
    } else if (arg.startsWith("--think-max-ms")) {
      args.thinkMaxMs = Number.parseInt(readValue(), 10);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (args.ports.length === 0) {
    throw new Error("At least one WebSocket port is required. Use --ports 8043,8044.");
  }
  if (!args.password) {
    throw new Error("--password is required.");
  }
  if (!["baseline", "anarchy-smp"].includes(args.profile)) {
    throw new Error("--profile must be baseline or anarchy-smp.");
  }
  if (!Number.isInteger(args.intensity) || args.intensity < 1 || args.intensity > 4) {
    throw new Error("--intensity must be an integer from 1 to 4.");
  }
  if (!["mcc", "rcon-teleport"].includes(args.movementMode)) {
    throw new Error("--movement-mode must be mcc or rcon-teleport.");
  }
  if (args.movementMode === "rcon-teleport" && (!Number.isInteger(args.rconPort) || args.rconPort <= 0 || !args.rconPassword)) {
    throw new Error("--movement-mode rcon-teleport requires --rcon-port and --rcon-password.");
  }
  return args;
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

function makeRng(seedText) {
  let seed = 2166136261;
  for (let i = 0; i < seedText.length; i++) {
    seed ^= seedText.charCodeAt(i);
    seed = Math.imul(seed, 16777619);
  }

  return () => {
    seed += 0x6D2B79F5;
    let t = seed;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function randomInt(rng, min, max) {
  return Math.floor(rng() * (max - min + 1)) + min;
}

function pickWeighted(rng, weighted) {
  const total = weighted.reduce((sum, item) => sum + item.weight, 0);
  let cursor = rng() * total;
  for (const item of weighted) {
    cursor -= item.weight;
    if (cursor <= 0) {
      return item.value;
    }
  }
  return weighted[weighted.length - 1].value;
}

function parseEventPayload(message) {
  try {
    const envelope = JSON.parse(message);
    if (typeof envelope.data === "string") {
      try {
        envelope.parsedData = JSON.parse(envelope.data);
      } catch {
        envelope.parsedData = envelope.data;
      }
    }
    return envelope;
  } catch {
    return { event: "Unparsed", data: message };
  }
}

class MccClient {
  constructor({ host, port, password, name, timeoutMs, verbose }) {
    this.host = host;
    this.port = port;
    this.password = password;
    this.name = name;
    this.timeoutMs = timeoutMs;
    this.verbose = verbose;
    this.ws = null;
    this.pending = new Map();
    this.events = [];
    this.actionLog = [];
    this.lastKnownLocation = null;
    this.lastAction = null;
    this.commandStats = {
      sent: 0,
      ok: 0,
      failed: 0,
      timedOut: 0,
    };
  }

  async connect(connectTimeoutSec) {
    const deadline = Date.now() + connectTimeoutSec * 1000;
    let lastError = null;
    while (Date.now() < deadline) {
      try {
        await this.tryConnectOnce();
        await this.command("Authenticate", [this.password], { timeoutMs: 5000 });
        await this.command("ChangeSessionId", [this.name], { timeoutMs: 5000 });
        return;
      } catch (error) {
        lastError = error;
        this.close();
        await sleep(1000);
      }
    }
    throw new Error(`Could not connect to ${this.name} ws://${this.host}:${this.port}: ${lastError?.message ?? "timeout"}`);
  }

  tryConnectOnce() {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(`ws://${this.host}:${this.port}/`);
      let settled = false;
      const timer = setTimeout(() => {
        if (!settled) {
          settled = true;
          try {
            ws.close();
          } catch {
            // ignored
          }
          reject(new Error("WebSocket open timed out"));
        }
      }, 5000);

      ws.addEventListener("open", () => {
        if (settled) {
          return;
        }
        settled = true;
        clearTimeout(timer);
        this.ws = ws;
        this.installHandlers();
        resolve();
      }, { once: true });

      ws.addEventListener("error", () => {
        if (settled) {
          return;
        }
        settled = true;
        clearTimeout(timer);
        reject(new Error("WebSocket connection failed"));
      }, { once: true });
    });
  }

  installHandlers() {
    this.ws.addEventListener("message", (event) => {
      const text = typeof event.data === "string" ? event.data : Buffer.from(event.data).toString("utf8");
      const payload = parseEventPayload(text);
      this.events.push({
        at: new Date().toISOString(),
        event: payload.event ?? "Unknown",
        data: payload.parsedData ?? payload.data ?? "",
      });
      if (this.events.length > 400) {
        this.events.shift();
      }

      if (payload.event === "OnWsCommandResponse" && payload.parsedData?.requestId) {
        const pending = this.pending.get(payload.parsedData.requestId);
        if (pending) {
          this.pending.delete(payload.parsedData.requestId);
          clearTimeout(pending.timer);
          pending.resolve(payload.parsedData);
        }
      }
    });

    this.ws.addEventListener("close", () => {
      for (const [requestId, pending] of this.pending) {
        clearTimeout(pending.timer);
        pending.reject(new Error(`WebSocket closed while waiting for ${requestId}`));
      }
      this.pending.clear();
    });
  }

  command(command, parameters = [], options = {}) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error(`${this.name} WebSocket is not open`));
    }

    const requestId = `${this.name}-${Date.now()}-${Math.floor(Math.random() * 1e9)}`;
    const timeoutMs = options.timeoutMs ?? this.timeoutMs;
    const body = JSON.stringify({ command, requestId, parameters });
    this.commandStats.sent++;

    if (this.verbose) {
      console.log(`[${this.name}] ${command} ${JSON.stringify(parameters)}`);
    }

    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(requestId);
        this.commandStats.timedOut++;
        reject(new Error(`${command} timed out after ${timeoutMs}ms`));
      }, timeoutMs);

      this.pending.set(requestId, { resolve, reject, timer });
      this.ws.send(body);
    }).then((response) => {
      if (response.success) {
        this.commandStats.ok++;
      } else {
        this.commandStats.failed++;
      }
      return response;
    }).catch((error) => {
      this.commandStats.failed++;
      throw error;
    });
  }

  close() {
    if (this.ws) {
      try {
        this.ws.close();
      } catch {
        // ignored
      }
    }
    this.ws = null;
  }
}

function parseResponseJson(response) {
  if (!response?.message || typeof response.message !== "string") {
    return null;
  }
  try {
    return JSON.parse(response.message);
  } catch {
    return null;
  }
}

function toFiniteNumber(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function normalizeLocation(location) {
  if (!location || typeof location !== "object") {
    return null;
  }

  const x = toFiniteNumber(location.x ?? location.X ?? location.posX ?? location.PosX);
  const y = toFiniteNumber(location.y ?? location.Y ?? location.posY ?? location.PosY);
  const z = toFiniteNumber(location.z ?? location.Z ?? location.posZ ?? location.PosZ);
  if (x === null || y === null || z === null) {
    return null;
  }

  const normalized = {
    x: Math.round(x * 100) / 100,
    y: Math.round(y * 100) / 100,
    z: Math.round(z * 100) / 100,
  };
  const yaw = toFiniteNumber(location.yaw ?? location.Yaw);
  const pitch = toFiniteNumber(location.pitch ?? location.Pitch);
  if (yaw !== null) {
    normalized.yaw = Math.round(yaw * 100) / 100;
  }
  if (pitch !== null) {
    normalized.pitch = Math.round(pitch * 100) / 100;
  }
  return normalized;
}

function rememberLocation(client, location, source) {
  const normalized = normalizeLocation(location);
  if (!normalized) {
    return null;
  }
  client.lastKnownLocation = {
    ...normalized,
    at: new Date().toISOString(),
    source,
  };
  return client.lastKnownLocation;
}

function incrementCounter(map, key) {
  map[key] = (map[key] ?? 0) + 1;
}

function recordAction(summary, client, action, details = {}) {
  const entry = {
    at: new Date().toISOString(),
    action,
    ...details,
    location: client.lastKnownLocation,
  };
  client.lastAction = entry;
  client.actionLog.push(entry);
  if (client.actionLog.length > 120) {
    client.actionLog.shift();
  }
  incrementCounter(summary.actionCounts, action);
  if (details.scenario) {
    incrementCounter(summary.scenarioCounts, details.scenario);
  }
  return entry;
}

function recordCommandError(summary, client, command, parameters, message) {
  summary.commandErrors.push({
    at: new Date().toISOString(),
    bot: client.name,
    command,
    parameters,
    message,
    lastKnownLocation: client.lastKnownLocation,
    lastAction: client.lastAction,
  });
}

async function bestEffort(client, command, parameters, summary, options = {}) {
  try {
    const response = await client.command(command, parameters, options);
    if (!response.success) {
      recordCommandError(summary, client, command, parameters, response.message ?? "");
    }
    return response;
  } catch (error) {
    const recoverable = /WebSocket closed|not open/i.test(error.message ?? "");
    if (recoverable && !options.noReconnectRetry) {
      try {
        client.close();
        await client.connect(20);
        summary.recoveries = (summary.recoveries ?? 0) + 1;
        const response = await client.command(command, parameters, { ...options, noReconnectRetry: true });
        if (!response.success) {
          recordCommandError(summary, client, command, parameters, response.message ?? "");
        }
        return response;
      } catch (retryError) {
        recordCommandError(summary, client, command, parameters, `${error.message}; reconnect retry failed: ${retryError.message}`);
        return null;
      }
    }

    recordCommandError(summary, client, command, parameters, error.message);
    return null;
  }
}

async function initializeClient(client, summary, args) {
  await bestEffort(client, "GetWorld", [], summary);
  if (args.disableTerrainDuringController) {
    await bestEffort(client, "SetTerrainEnabled", [false], summary, { timeoutMs: 5000 });
  }
  await bestEffort(client, "SendEntityAction", ["StopSneaking"], summary);
}

async function getLocation(client, fallback, summary = null, source = "poll", args = null) {
  const response = await client.command("GetCurrentLocation", [], { timeoutMs: 5000 });
  const parsed = parseResponseJson(response);
  const location = args
    ? stableControllerLocation(args, normalizeLocation(parsed), fallback)
    : (normalizeLocation(parsed) ?? normalizeLocation(fallback));
  if (location) {
    rememberLocation(client, location, source);
    return location;
  }
  return parsed ?? fallback;
}

async function moveToAndSettle(client, args, target, parameters, summary, source, options = {}, actionEntry = null) {
  const stableTarget = stableControllerLocation(args, target, target) ?? target;
  let response;
  if (args.movementMode === "rcon-teleport") {
    try {
      await sendRconCommand(args, `tp ${client.name} ${stableTarget.x} ${stableTarget.y} ${stableTarget.z}`, options.timeoutMs ?? 10000);
      response = { success: true, message: "rcon-teleport" };
    } catch (error) {
      recordCommandError(summary, client, "RconTeleport", [stableTarget.x, stableTarget.y, stableTarget.z], error.message);
      response = null;
    }
  } else {
    response = await bestEffort(client, "MoveToLocation", parameters, summary, options);
  }
  await sleep(args.movementMode === "rcon-teleport" ? 350 : 150);
  const actual = await getLocation(client, stableTarget, summary, `${source}-settle`, args).catch(() => null);
  if (actionEntry && actual) {
    actionEntry.locationAfterMove = actual;
  }
  return response;
}

function botRole(args, index) {
  if (args.profile !== "anarchy-smp") {
    return "baseline";
  }
  return ["raider", "brawler", "builder", "scout"][index % 4];
}

const ANARCHY_ARENAS = [
  { x: 384, y: 8, z: 0, scenario: "redstone-raid" },
  { x: -384, y: 8, z: 0, scenario: "mobfarm-cram" },
  { x: 0, y: 8, z: -384, scenario: "pvp-arena" },
  { x: 0, y: 8, z: 384, scenario: "vehicle-passenger" },
  { x: 128, y: 8, z: 0, scenario: "fluid-leaf-boundary" },
];

function anarchyBotSpawn(index) {
  const arena = ANARCHY_ARENAS[index % ANARCHY_ARENAS.length];
  return {
    x: arena.x + ((index % 3) - 1) * 4,
    y: arena.y,
    z: arena.z + 28 + Math.floor(index / 3) * 4,
    scenario: arena.scenario,
  };
}

function stableControllerLocation(args, location, fallback = null) {
  const normalized = normalizeLocation(location);
  const normalizedFallback = normalizeLocation(fallback);
  if (!normalized) {
    return normalizedFallback;
  }
  if (args?.profile === "anarchy-smp" && (normalized.y < -16 || normalized.y > 320)) {
    return normalizedFallback ?? { ...normalized, y: 8 };
  }
  return normalized;
}

function nearestAnarchyArena(home, index) {
  const homeX = toFiniteNumber(home?.x);
  const homeZ = toFiniteNumber(home?.z);
  if (homeX === null || homeZ === null) {
    return ANARCHY_ARENAS[index % ANARCHY_ARENAS.length];
  }
  return ANARCHY_ARENAS.reduce((nearest, candidate) => {
    const nearestDistance = Math.pow(nearest.x - homeX, 2) + Math.pow(nearest.z - homeZ, 2);
    const candidateDistance = Math.pow(candidate.x - homeX, 2) + Math.pow(candidate.z - homeZ, 2);
    return candidateDistance < nearestDistance ? candidate : nearest;
  }, ANARCHY_ARENAS[index % ANARCHY_ARENAS.length]);
}

function anarchyTarget(args, rng, home, index) {
  if (args.profile !== "anarchy-smp") {
    return {
      x: Math.round(home.x + randomInt(rng, -args.radius, args.radius)),
      y: home.y,
      z: Math.round(home.z + randomInt(rng, -args.radius, args.radius)),
      scenario: "free-roam",
    };
  }
  const arena = nearestAnarchyArena(home, index);
  return {
    x: arena.x + randomInt(rng, -24, 24),
    y: arena.y,
    z: arena.z + randomInt(rng, -24, 24),
    scenario: arena.scenario,
  };
}

function entityLocation(entity) {
  return normalizeLocation(entity?.location ?? entity?.Location);
}

function entityDistanceSquared(entity, location) {
  const other = entityLocation(entity);
  if (!other || !location) {
    return Number.POSITIVE_INFINITY;
  }
  const dx = other.x - location.x;
  const dy = other.y - location.y;
  const dz = other.z - location.z;
  return dx * dx + dy * dy + dz * dz;
}

function isCombatTarget(entity, includePlayers) {
  const type = `${entity?.type ?? ""}`.toLowerCase();
  if (!type) {
    return false;
  }
  if (type === "item" || type === "experienceorb" || type === "tnt" || type === "primedtnt") {
    return false;
  }
  if (type === "player") {
    return includePlayers;
  }
  return /zombie|skeleton|creeper|slime|spider|enderman|witch|piglin|armorstand|villager|minecart/.test(type);
}

async function combatAction(client, args, summary, rng, actionEntry, includePlayers = true) {
  const loc = await getLocation(client, client.lastKnownLocation, summary, "combat-poll", args).catch(() => client.lastKnownLocation);
  const response = await bestEffort(client, "GetEntities", [], summary, { timeoutMs: 7000 });
  const entities = parseResponseJson(response) ?? {};
  const candidates = Object.entries(entities)
    .filter(([, entity]) => isCombatTarget(entity, includePlayers))
    .sort(([, a], [, b]) => entityDistanceSquared(a, loc) - entityDistanceSquared(b, loc));
  actionEntry.entityCount = Object.keys(entities).length;
  actionEntry.candidateEntityCount = candidates.length;
  if (candidates.length === 0) {
    return;
  }
  const [entityId, entity] = candidates[0];
  const target = entityLocation(entity);
  actionEntry.entityId = Number.parseInt(entityId, 10);
  actionEntry.entityType = entity?.type ?? "unknown";
  actionEntry.target = target;
  if (target && rng() > 0.35) {
    await bestEffort(client, "LookAtLocation", [target.x, target.y + 1.0, target.z], summary);
  }
  if (target && entityDistanceSquared(entity, loc) > 25) {
    await moveToAndSettle(client, args, target, [target.x, target.y, target.z, true, false, 4, 1], summary, "combat", { timeoutMs: 20000 }, actionEntry);
  }
  await bestEffort(client, "SendAnimation", ["MainHand"], summary);
  await bestEffort(client, "InteractEntity", [actionEntry.entityId, "Attack", "MainHand"], summary);
}

async function vandalizeAction(client, args, summary, rng, home, actionEntry) {
  await bestEffort(client, "ChangeSlot", [randomInt(rng, 0, 8)], summary);
  const loc = await getLocation(client, home, summary, "vandalize-poll", args).catch(() => home);
  const target = {
    x: Math.floor(loc.x) + randomInt(rng, -3, 3),
    y: Math.floor(loc.y) - 1 + randomInt(rng, 0, 2),
    z: Math.floor(loc.z) + randomInt(rng, -3, 3),
  };
  actionEntry.target = target;
  if (!args.disableTerrainDuringController && rng() > 0.45) {
    await bestEffort(client, "DigBlock", [target.x, target.y, target.z, "Up"], summary);
  } else {
    await bestEffort(client, "SendPlaceBlock", [target.x, target.y, target.z, "Up", "MainHand"], summary);
  }
  if (rng() > 0.7) {
    await bestEffort(client, "UseItemInHand", [], summary);
  }
}

async function runBot(client, args, summary, index) {
  const rng = makeRng(`${args.seed}:${client.name}:${client.port}`);
  const fallback = args.profile === "anarchy-smp" ? anarchyBotSpawn(index) : { x: index * 4, y: 4, z: index * 4 };
  const role = botRole(args, index);
  incrementCounter(summary.roleCounts, role);
  let home = fallback;
  try {
    home = await getLocation(client, fallback, summary, "initial", args);
  } catch (error) {
    rememberLocation(client, fallback, "fallback-initial");
    recordCommandError(summary, client, "GetCurrentLocation", [], error.message);
  }
  if (args.profile === "anarchy-smp") {
    home = anarchyBotSpawn(index);
    rememberLocation(client, home, "controller-home");
    const arena = nearestAnarchyArena(home, index);
    recordAction(summary, client, "scenario-prime", { role, scenario: arena.scenario, target: arena });
  }

  const end = Date.now() + args.durationSec * 1000;
  while (Date.now() < end) {
    const weightedActions = [
      { value: "move", weight: 38 },
      { value: "boundary", weight: 7 },
      { value: "look", weight: 10 },
      { value: "sneak", weight: 10 },
      { value: "sprint", weight: 8 },
      { value: "animate", weight: 8 },
      { value: "use", weight: 6 },
      { value: "place", weight: 8 },
      { value: "entities", weight: 5 },
    ];
    if (!args.disableTerrainDuringController) {
      weightedActions.push({ value: "dig", weight: 4 });
    }
    if (args.profile === "anarchy-smp") {
      weightedActions.push(
        { value: "skirmish", weight: 16 + args.intensity * 3 },
        { value: "combat", weight: 14 + args.intensity * 4 },
        { value: "vandalize", weight: role === "builder" ? 18 : 9 },
        { value: "panic", weight: role === "scout" ? 12 : 6 },
        { value: "hotbar", weight: 7 },
      );
    }
    const action = pickWeighted(rng, weightedActions);

    try {
      const actionEntry = recordAction(summary, client, action, { role });
      if (action === "move") {
        const target = anarchyTarget(args, rng, home, index);
        actionEntry.scenario = target.scenario;
        actionEntry.target = target;
        incrementCounter(summary.scenarioCounts, target.scenario);
        await moveToAndSettle(client, args, target, [target.x, target.y, target.z, true, false, 6, 0], summary, "move", { timeoutMs: 30000 }, actionEntry);
      } else if (action === "boundary") {
        const nearFluidLeafFixture = Math.abs(home.x - 128) <= args.radius * 2 && Math.abs(home.z) <= args.radius * 2;
        const boundaryBase = nearFluidLeafFixture ? 128 : Math.round(home.x / 128) * 128;
        const target = {
          x: boundaryBase + (rng() > 0.5 ? -1 : 1) + randomInt(rng, -2, 2),
          y: home.y,
          z: nearFluidLeafFixture ? randomInt(rng, -28, 28) : Math.round(home.z) + randomInt(rng, -24, 24),
        };
        actionEntry.scenario = nearFluidLeafFixture ? "fluid-leaf-boundary" : "region-boundary";
        actionEntry.target = target;
        incrementCounter(summary.scenarioCounts, actionEntry.scenario);
        await moveToAndSettle(client, args, target, [target.x, target.y, target.z, true, false, 5, 0], summary, "boundary", { timeoutMs: 30000 }, actionEntry);
      } else if (action === "look") {
        const target = {
          x: home.x + randomInt(rng, -32, 32),
          y: home.y + randomInt(rng, 0, 10),
          z: home.z + randomInt(rng, -32, 32),
        };
        actionEntry.target = target;
        await bestEffort(client, "LookAtLocation", [
          target.x,
          target.y,
          target.z,
        ], summary);
      } else if (action === "sneak") {
        const on = rng() > 0.5;
        actionEntry.mode = on ? "StartSneaking" : "StopSneaking";
        await bestEffort(client, "SendEntityAction", [on ? "StartSneaking" : "StopSneaking"], summary);
        await sleep(randomInt(rng, 100, 700));
        await bestEffort(client, "SendEntityAction", [on ? "StopSneaking" : "StartSneaking"], summary);
      } else if (action === "sprint") {
        const command = rng() > 0.5 ? "StartSprinting" : "StopSprinting";
        actionEntry.mode = command;
        await bestEffort(client, "SendEntityAction", [command], summary);
      } else if (action === "animate") {
        await bestEffort(client, "SendAnimation", ["MainHand"], summary);
      } else if (action === "use") {
        await bestEffort(client, "UseItemInHand", [], summary);
      } else if (action === "place") {
        const loc = await getLocation(client, home, summary, "place-poll", args).catch(() => home);
        const target = {
          x: Math.floor(loc.x) + randomInt(rng, -2, 2),
          y: Math.floor(loc.y) - 1,
          z: Math.floor(loc.z) + randomInt(rng, -2, 2),
        };
        actionEntry.target = target;
        await bestEffort(client, "SendPlaceBlock", [
          target.x,
          target.y,
          target.z,
          "Up",
          "MainHand",
        ], summary);
      } else if (action === "dig") {
        const loc = await getLocation(client, home, summary, "dig-poll", args).catch(() => home);
        const target = {
          x: Math.floor(loc.x) + randomInt(rng, -2, 2),
          y: Math.floor(loc.y) - 1,
          z: Math.floor(loc.z) + randomInt(rng, -2, 2),
        };
        actionEntry.target = target;
        await bestEffort(client, "DigBlock", [
          target.x,
          target.y,
          target.z,
          "Up",
        ], summary);
      } else if (action === "entities") {
        const response = await bestEffort(client, "GetEntities", [], summary);
        const entities = parseResponseJson(response) ?? {};
        const candidates = Object.entries(entities).filter(([, entity]) => {
          const type = `${entity?.type ?? ""}`.toLowerCase();
          return type && type !== "player" && type !== "item";
        });
        actionEntry.entityCount = Object.keys(entities).length;
        actionEntry.candidateEntityCount = candidates.length;
        if (candidates.length > 0 && rng() > 0.55) {
          const [entityId] = candidates[randomInt(rng, 0, candidates.length - 1)];
          actionEntry.entityId = Number.parseInt(entityId, 10);
          await bestEffort(client, "InteractEntity", [actionEntry.entityId, "Attack", "MainHand"], summary);
        }
      } else if (action === "skirmish") {
        const target = anarchyTarget(args, rng, home, index + 1);
        actionEntry.scenario = target.scenario;
        actionEntry.target = target;
        incrementCounter(summary.scenarioCounts, target.scenario);
        await bestEffort(client, "SendEntityAction", ["StartSprinting"], summary);
        await moveToAndSettle(client, args, target, [target.x, target.y, target.z, true, false, 5, 0], summary, "skirmish", { timeoutMs: 30000 }, actionEntry);
        if (rng() > 0.35) {
          await combatAction(client, args, summary, rng, actionEntry, true);
        }
      } else if (action === "combat") {
        actionEntry.scenario = "pvp-arena";
        incrementCounter(summary.scenarioCounts, actionEntry.scenario);
        await combatAction(client, args, summary, rng, actionEntry, true);
      } else if (action === "vandalize") {
        actionEntry.scenario = "base-grief";
        incrementCounter(summary.scenarioCounts, actionEntry.scenario);
        await vandalizeAction(client, args, summary, rng, home, actionEntry);
      } else if (action === "panic") {
        const loc = await getLocation(client, home, summary, "panic-poll", args).catch(() => home);
        const target = {
          x: Math.round(loc.x + randomInt(rng, -24, 24)),
          y: loc.y,
          z: Math.round(loc.z + randomInt(rng, -24, 24)),
        };
        actionEntry.scenario = "panic-kite";
        actionEntry.target = target;
        incrementCounter(summary.scenarioCounts, actionEntry.scenario);
        await bestEffort(client, "SendEntityAction", ["StartSprinting"], summary);
        await bestEffort(client, "LookAtLocation", [target.x + randomInt(rng, -8, 8), target.y + 2, target.z + randomInt(rng, -8, 8)], summary);
        await moveToAndSettle(client, args, target, [target.x, target.y, target.z, true, false, 8, 0], summary, "panic", { timeoutMs: 20000 }, actionEntry);
        await bestEffort(client, "SendAnimation", ["MainHand"], summary);
        if (rng() > 0.8) {
          await bestEffort(client, "Respawn", [], summary, { timeoutMs: 5000 });
        }
      } else if (action === "hotbar") {
        const slot = randomInt(rng, 0, 8);
        actionEntry.slot = slot;
        await bestEffort(client, "ChangeSlot", [slot], summary);
      }
    } catch (error) {
      recordCommandError(summary, client, action, [], error.message);
    }

    await sleep(randomInt(rng, args.thinkMinMs, args.thinkMaxMs));
  }
}

async function main() {
  const args = parseArgs(process.argv);
  const summary = {
    startedAt: new Date().toISOString(),
    finishedAt: null,
    seed: args.seed,
    commandSeed: args.seed,
    chaosProfile: args.profile,
    chaosIntensity: args.intensity,
    durationSec: args.durationSec,
    ports: args.ports,
    recoveries: 0,
    actionCounts: {},
    scenarioCounts: {},
    roleCounts: {},
    commandErrors: [],
    bots: [],
  };

  const clients = args.ports.map((port, index) => new MccClient({
    host: args.host,
    port,
    password: args.password,
    name: `mccbot${String(index + 1).padStart(2, "0")}`,
    timeoutMs: args.commandTimeoutMs,
    verbose: args.verbose,
  }));

  try {
    await Promise.all(clients.map((client) => client.connect(args.connectTimeoutSec)));
    await Promise.all(clients.map((client) => initializeClient(client, summary, args)));
    await Promise.all(clients.map((client, index) => runBot(client, args, summary, index)));
  } finally {
    for (const client of clients) {
      summary.bots.push({
        name: client.name,
        port: client.port,
        stats: client.commandStats,
        lastKnownLocation: client.lastKnownLocation,
        lastAction: client.lastAction,
        recentActions: client.actionLog.slice(-40),
        recentEvents: client.events.slice(-80),
      });
      client.close();
    }
    summary.finishedAt = new Date().toISOString();
    if (args.out) {
      await fs.writeFile(args.out, JSON.stringify(summary, null, 2), "utf8");
    }
  }

  const hardErrors = summary.commandErrors.filter((error) => {
    const text = `${error.message ?? ""}`.toLowerCase();
    return text.includes("websocket closed") || text.includes("not open") || text.includes("timed out");
  });

  if (hardErrors.length > 0 || (args.failOnCommandError && summary.commandErrors.length > 0)) {
    const message = `MCC chaos completed with ${summary.commandErrors.length} command errors (${hardErrors.length} hard).`;
    if (args.failOnCommandError) {
      console.error(message);
      process.exitCode = 2;
    } else {
      console.warn(`${message} Server-side failure signatures decide pass/fail by default.`);
      process.exitCode = 0;
    }
  } else {
    console.log(`MCC chaos completed. bots=${clients.length} commandErrors=${summary.commandErrors.length}`);
    process.exitCode = 0;
  }
  process.exit(process.exitCode ?? 0);
}

main().catch((error) => {
  console.error(error.stack ?? error.message);
  process.exitCode = 1;
});
