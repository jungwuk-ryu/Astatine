#!/usr/bin/env node
import fs from "node:fs/promises";
import fsSync from "node:fs";
import net from "node:net";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const options = {
  root: path.join(repoRoot, "run", "watchdog-smoke"),
  java: process.env.JAVA_HOME ? path.join(process.env.JAVA_HOME, "bin", "java") : "java",
  serverJar: path.join(repoRoot, "shreddedpaper-server", "build", "libs", "shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"),
  pluginJar: path.join(repoRoot, "tools", "region-load-test-plugin", "build", "libs", "region-load-test-plugin-0.1.0-SNAPSHOT.jar"),
  serverPort: 25568,
  rconPort: 25578,
  rconPassword: "codex-watchdog-smoke",
  heap: "2G",
  maxTickTimeMs: 3000,
  timeoutTimeSec: 10,
  stallMs: 25000,
  shutdownGraceMs: 5000,
  mode: "global",
};

parseArgs(process.argv.slice(2));

const serverDir = path.join(path.resolve(options.root), "server");
const latestLog = path.join(serverDir, "logs", "latest.log");

let child = null;
try {
  assertExists(options.serverJar, "server jar");
  assertExists(options.pluginJar, "region load test plugin jar");
  await assertPortFree("server", options.serverPort);
  await assertPortFree("RCON", options.rconPort);
  await prepareServerDir();
  child = await startServer();
  await waitForReady(child);

  const stallCommand = watchdogStallCommand();
  const response = await sendRconCommand(stallCommand, 5000);
  await fs.writeFile(path.join(options.root, "watchdog-trigger-rcon.log"), `> ${stallCommand}\n${response}\n`, "utf8");
  if (!/Queued watchdog stall/i.test(response)) {
    throw new Error(`Watchdog stall command did not queue cleanly:\n${response}`);
  }

  const exited = await waitForExitWithTimeout(child, Math.max(60000, options.stallMs + options.shutdownGraceMs + 30000));
  if (!exited) {
    await dumpThread(child.pid, "watchdog-timeout");
    await killProcessTree(child);
    throw new Error(`Watchdog smoke server did not exit after the deliberate ${options.mode} ${options.stallMs}ms stall.`);
  }

  const log = await readMaybe(latestLog);
  if (!/The server has (?:not responded|stopped responding)|Server thread dump/i.test(log)) {
    throw new Error("Watchdog smoke did not emit the expected watchdog thread-dump marker.");
  }
  if (!/Watchdog emergency shutdown|Watchdog Server Shutdown|Stopping server/i.test(log)) {
    throw new Error("Watchdog smoke did not exercise the emergency shutdown path.");
  }
  if (options.mode === "region") {
    if (!/A ShreddedPaper independent region tick has stopped responding/i.test(log)) {
      throw new Error("Region watchdog smoke did not emit the ShreddedPaper independent-region timeout banner.");
    }
    if (!/Long-running independent region ticks:/i.test(log)) {
      throw new Error("Region watchdog smoke did not dump long-running independent region tick metadata.");
    }
    if (!/ShreddedPaperRegion(?:Normal|Degraded)-/i.test(log)) {
      throw new Error("Region watchdog smoke did not include a ShreddedPaper region worker thread dump.");
    }
  }

  await assertPortFree("server", options.serverPort);
  await assertPortFree("RCON", options.rconPort);
  console.log(`[watchdog-smoke] passed mode=${options.mode} root=${options.root}`);
} catch (error) {
  if (child && child.exitCode === null) {
    await killProcessTree(child);
  }
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
}

function parseArgs(args) {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    const next = () => {
      const value = args[++i];
      if (!value || value.startsWith("--")) {
        throw new Error(`${arg} requires a value`);
      }
      return value;
    };
    switch (arg) {
      case "--root":
        options.root = path.resolve(next());
        break;
      case "--java":
        options.java = next();
        break;
      case "--server-jar":
        options.serverJar = path.resolve(next());
        break;
      case "--region-load-test-jar":
        options.pluginJar = path.resolve(next());
        break;
      case "--server-port":
        options.serverPort = Number.parseInt(next(), 10);
        break;
      case "--rcon-port":
        options.rconPort = Number.parseInt(next(), 10);
        break;
      case "--rcon-password":
        options.rconPassword = next();
        break;
      case "--heap":
        options.heap = next();
        break;
      case "--max-tick-time-ms":
        options.maxTickTimeMs = Number.parseInt(next(), 10);
        break;
      case "--timeout-time-sec":
        options.timeoutTimeSec = Number.parseInt(next(), 10);
        break;
      case "--stall-ms":
        options.stallMs = Number.parseInt(next(), 10);
        break;
      case "--shutdown-grace-ms":
        options.shutdownGraceMs = Number.parseInt(next(), 10);
        break;
      case "--mode":
        options.mode = next();
        if (!["global", "region"].includes(options.mode)) {
          throw new Error("--mode must be global or region");
        }
        break;
      case "--help":
      case "-h":
        console.log("Usage: node tools/runtime/invoke-watchdog-smoke.mjs [--mode global|region] [--server-port n] [--rcon-port n] [--stall-ms n]");
        process.exit(0);
      default:
        throw new Error(`Unknown option: ${arg}`);
    }
  }
}

function watchdogStallCommand() {
  if (options.mode === "region") {
    return `rlt at world 0 8 0 watchdogstall region ${options.stallMs}`;
  }
  return `rlt watchdogstall global ${options.stallMs}`;
}

async function prepareServerDir() {
  await fs.rm(serverDir, { recursive: true, force: true });
  await fs.mkdir(path.join(serverDir, "plugins"), { recursive: true });
  await fs.copyFile(options.serverJar, path.join(serverDir, "server.jar"));
  await fs.copyFile(options.pluginJar, path.join(serverDir, "plugins", "region-load-test-plugin.jar"));
  await fs.writeFile(path.join(serverDir, "eula.txt"), "eula=true\n", "utf8");
  await fs.writeFile(path.join(serverDir, "server.properties"), serverProperties(), "utf8");
  await fs.writeFile(path.join(serverDir, "spigot.yml"), spigotConfig(), "utf8");
}

function serverProperties() {
  return `allow-flight=true
allow-nether=true
enable-command-block=false
enable-query=false
enable-rcon=true
enforce-secure-profile=false
force-gamemode=false
gamemode=creative
generate-structures=false
level-name=world
level-type=minecraft:flat
max-players=8
max-tick-time=${options.maxTickTimeMs}
motd=ShreddedPaper watchdog smoke
online-mode=false
prevent-proxy-connections=false
rcon.password=${options.rconPassword}
rcon.port=${options.rconPort}
server-ip=127.0.0.1
server-port=${options.serverPort}
simulation-distance=4
spawn-protection=0
view-distance=4
`;
}

function spigotConfig() {
  return `config-version: 12
settings:
  restart-on-crash: false
  restart-script: ./start.sh
  timeout-time: ${options.timeoutTimeSec}
`;
}

async function startServer() {
  const stdout = fsSync.openSync(path.join(serverDir, "console.out.log"), "w");
  const stderr = fsSync.openSync(path.join(serverDir, "console.err.log"), "w");
  return spawn(options.java, [
    "--enable-preview",
    `-Xms${options.heap}`,
    `-Xmx${options.heap}`,
    `-Dshreddedpaper.watchdog.shutdown-grace-ms=${options.shutdownGraceMs}`,
    "-jar",
    "server.jar",
    "nogui",
  ], {
    cwd: serverDir,
    stdio: ["ignore", stdout, stderr],
    detached: true,
    env: process.env,
  });
}

async function waitForReady(proc) {
  const deadline = Date.now() + 180000;
  while (Date.now() < deadline) {
    if (proc.exitCode !== null) {
      throw new Error(`Server exited before ready with code ${proc.exitCode}`);
    }
    const log = await readMaybe(latestLog);
    if (log.includes("Done (") && await testRconReady()) {
      return;
    }
    await sleep(500);
  }
  throw new Error("Timed out waiting for watchdog smoke server readiness.");
}

async function testRconReady() {
  try {
    await sendRconCommand("list", 2500);
    return true;
  } catch {
    return false;
  }
}

async function sendRconCommand(command, timeoutMs) {
  const socket = net.createConnection({ host: "127.0.0.1", port: options.rconPort });
  await new Promise((resolve, reject) => {
    socket.once("connect", resolve);
    socket.once("error", reject);
  });
  try {
    socket.write(makeRconPacket(1, 3, options.rconPassword));
    const auth = await readRconPacket(socket, timeoutMs);
    if (auth.id === -1) {
      throw new Error("RCON authentication failed.");
    }
    socket.write(makeRconPacket(2, 2, command));
    return (await readRconPacket(socket, timeoutMs)).payload;
  } finally {
    socket.destroy();
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
    const timer = setTimeout(() => cleanup(new Error(`Timed out reading RCON packet after ${timeoutMs}ms`)), timeoutMs);
    const onData = (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length < 4) return;
      const length = buffer.readInt32LE(0);
      if (buffer.length < 4 + length) return;
      const data = buffer.subarray(4, 4 + length);
      cleanup(null, {
        id: data.readInt32LE(0),
        type: data.readInt32LE(4),
        payload: data.subarray(8, Math.max(8, data.length - 2)).toString("utf8"),
      });
    };
    const onError = (error) => cleanup(error);
    const cleanup = (error, packet) => {
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

async function assertPortFree(label, port) {
  if (await canConnectTcp(port, 750)) {
    throw new Error(`${label} port ${port} is still accepting TCP connections.`);
  }
}

function canConnectTcp(port, timeoutMs) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    const timer = setTimeout(() => {
      socket.destroy();
      resolve(false);
    }, timeoutMs);
    socket.once("connect", () => {
      clearTimeout(timer);
      socket.destroy();
      resolve(true);
    });
    socket.once("error", () => {
      clearTimeout(timer);
      resolve(false);
    });
  });
}

function waitForExitWithTimeout(proc, timeoutMs) {
  if (!proc || proc.exitCode !== null) return Promise.resolve(true);
  return new Promise((resolve) => {
    const timer = setTimeout(() => resolve(false), timeoutMs);
    proc.once("exit", () => {
      clearTimeout(timer);
      resolve(true);
    });
  });
}

async function killProcessTree(proc) {
  if (!proc || proc.exitCode !== null) return;
  try {
    process.kill(-proc.pid, "SIGTERM");
  } catch {
    try { proc.kill("SIGTERM"); } catch {}
  }
  if (!await waitForExitWithTimeout(proc, 5000)) {
    try {
      process.kill(-proc.pid, "SIGKILL");
    } catch {
      try { proc.kill("SIGKILL"); } catch {}
    }
  }
}

async function dumpThread(pid, label) {
  if (!pid) return;
  const javaDir = path.dirname(options.java);
  const jcmd = path.join(javaDir, "jcmd");
  if (!fsSync.existsSync(jcmd)) return;
  const out = fsSync.openSync(path.join(options.root, `jcmd-${label}.log`), "w");
  const proc = spawn(jcmd, [String(pid), "Thread.print", "-l"], { stdio: ["ignore", out, out] });
  await waitForExitWithTimeout(proc, 10000);
  fsSync.closeSync(out);
}

function assertExists(file, label) {
  if (!fsSync.existsSync(file)) {
    throw new Error(`Missing ${label}: ${file}`);
  }
}

async function readMaybe(file) {
  try {
    return await fs.readFile(file, "utf8");
  } catch {
    return "";
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
