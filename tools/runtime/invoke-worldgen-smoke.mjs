#!/usr/bin/env node
import fs from "node:fs/promises";
import { createWriteStream, existsSync, statSync } from "node:fs";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { formatMatches, scanPath } from "./scan-server-log.mjs";

const DEFAULT_COMMANDS = [
  "list",
  "tps",
  "mspt",
  "rlt cleanup",
  "rlt at world 0 8 0 chunkgen 8 false",
  "rlt at world 96 8 0 chunkgen 8 false",
  "rlt at world -128 8 0 chunkgen 8 false",
  "rlt at world 0 8 128 chunkgen 8 false",
  "rlt at world 128 8 0 chunkgen 8 false",
  "rlt at world 3072 8 0 chunkgen 3 false",
  "rlt at world 4096 8 0 chunkgen 3 false",
  "forceload add -64 -64 64 64",
  "forceload add 16 -80 176 80",
  "forceload add -192 -64 -64 64",
  "rlt at world 0 8 0 villagers 20 24 400",
  ...arenaPlatformCommands({ x: 96, y: 8, z: 0 }, 80, 8),
  "rlt at world 96 8 0 path 20 24 400",
  "rlt at world -128 8 0 tracker 80 300 16",
  "forceload add 0 128 127 159",
  "rlt at world 0 8 128 broadcast 4 32 300",
  "forceload remove 0 128 127 159",
  "rlt at world 0 8 0 scheduler region 32 64",
  "rlt at world 128 8 0 crossqueue 16 32 64",
  "rlt at world 0 8 0 syncload 64 4",
  "rlt at world 384 8 0 chunkgen 5 false",
  "forceload add 304 -80 464 80",
  ...arenaPlatformCommands({ x: 384, y: 8, z: 0 }, 80, 8),
  "rlt at world 384 8 0 redstone 3 80 48 1",
  "rlt at world 512 8 0 chunkgen 5 false",
  "forceload add 432 -80 592 80",
  ...arenaPlatformCommands({ x: 512, y: 8, z: 0 }, 80, 8),
  "rlt at world 512 8 0 lighting 48 240 2",
  "rlt at world 2048 8 0 scenario load 2 64 3 false 120 1",
  "rlt at world 200000 8 0 scenario gen 2 96 3 false 120 1",
  "rlt status",
  "rlt cleanup",
  "forceload remove -64 -64 64 64",
  "forceload remove 16 -80 176 80",
  "forceload remove -192 -64 -64 64",
  "forceload remove 304 -80 464 80",
  "forceload remove 432 -80 592 80",
  "tps",
  "mspt",
];

const RLT_ENTITY_TAG = "shreddedpaper_rlt";

const args = parseArgs(process.argv.slice(2));
const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const serverDir = path.resolve(args.serverDir ?? path.join(repoRoot, "..", "worldgen"));
const jarPath = path.resolve(serverDir, args.jar ?? "shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar");
const javaBin = args.java ?? process.env.JAVA ?? "java";
const resultDir = path.resolve(args.resultDir ?? path.join(repoRoot, "run", "worldgen-smoke", timestamp()));
const latestLog = path.join(serverDir, "logs", "latest.log");
const crashDir = path.join(serverDir, "crash-reports");
const rconPort = Number(args.rconPort ?? 25575);
const serverPort = Number(args.serverPort ?? 25565);
const rconPassword = args.rconPassword ?? "codex-region-test";
const heap = args.heap ?? "4G";
const readyTimeoutMs = Number(args.readyTimeoutSec ?? 240) * 1000;
const postCommandWaitMs = Number(args.postCommandWaitSec ?? 20) * 1000;
const stopTimeoutMs = Number(args.stopTimeoutSec ?? 90) * 1000;
const watchdogGraceMs = Number(args.watchdogGraceMs ?? 5000);
const commands = args.commands.length > 0 ? args.commands : DEFAULT_COMMANDS;
const runPlayerLifecycle = !args.skipPlayerLifecycle;
const mccCacheDir = path.resolve(args.mccCacheDir ?? path.join(repoRoot, "run", "mcc-chaos", "cache"));

await fs.mkdir(resultDir, { recursive: true });

let child = null;
let startedByScript = false;
let exitCode = 0;
let managedServerProperties = null;

try {
  assertExists(serverDir, "server directory");
  assertExists(jarPath, "server jar");
  const crashReportsBefore = await listCrashReports(crashDir);

  const rconReady = await testRconReady();
  if (!rconReady) {
    if (args.noStart) {
      throw new Error(`Server is not reachable over RCON on port ${rconPort}, and --no-start was specified.`);
    }
    await assertPortFree("server", serverPort);
    await assertPortFree("RCON", rconPort);

    managedServerProperties = await prepareManagedServerProperties();
    child = await startServer();
    startedByScript = true;
  } else {
    throw new Error(`Server is already reachable over RCON on port ${rconPort}. Stop it first; worldgen smoke uses per-run server ownership so stale logs/counters cannot hide or invent failures.`);
  }

  await waitForReady(child);
  if (startedByScript) {
    await assertTcpReachable("server", serverPort, 5000);
    await assertTcpReachable("RCON", rconPort, 5000);
  }
  await runRconCommands(commands);
  if (runPlayerLifecycle) {
    await runMccPlayerLifecycleSmoke();
  }
  await sleep(postCommandWaitMs);
  await assertRegionLoadTestClean();
  await assertOwnershipCountersClean();
  await assertRltRuntimeEvidence();

  if (!args.noStop && startedByScript) {
    await stopServer(child);
  } else if (!startedByScript) {
    console.log("Server was already running before smoke; leaving it running.");
  }

  const matches = await scanSmokeLogs();
  if (matches.length > 0) {
    throw new Error(`High-signal log patterns found:\n${formatMatches(matches)}`);
  }

  const crashReportsAfter = await listCrashReports(crashDir);
  const newCrashReports = [...crashReportsAfter].filter((file) => !crashReportsBefore.has(file));
  if (newCrashReports.length > 0) {
    throw new Error(`New crash reports created:\n${newCrashReports.join("\n")}`);
  }

  if (startedByScript && !args.noStop) {
    await assertNoLsof(`TCP:${serverPort}`, ["-Pan", `-iTCP:${serverPort}`, "-sTCP:LISTEN"]);
    await assertNoLsof(`TCP:${rconPort}`, ["-Pan", `-iTCP:${rconPort}`, "-sTCP:LISTEN"]);
    await assertNoLsof("world/session.lock", [path.join(serverDir, "world", "session.lock")]);
  }

  const pass = `Worldgen smoke passed at ${new Date().toISOString()}.\nLog: ${latestLog}\nCommands: ${commands.length}\nPlayerLifecycle: ${runPlayerLifecycle ? "enabled" : "skipped"}\n`;
  await fs.writeFile(path.join(resultDir, "pass.txt"), pass, "utf8");
  console.log(pass.trim());
} catch (error) {
  exitCode = 1;
  const message = error?.stack ?? String(error);
  await fs.writeFile(path.join(resultDir, "fail.txt"), message, "utf8");
  console.error(message);

  if (child && child.exitCode === null) {
    await dumpThread(child.pid, "failure");
    child.kill("SIGKILL");
  }
} finally {
  if (managedServerProperties) {
    await restoreManagedServerProperties(managedServerProperties).catch((restoreError) => {
      console.warn(`Could not restore managed server.properties: ${restoreError.message}`);
    });
  }
  process.exit(exitCode);
}

async function prepareManagedServerProperties() {
  const file = path.join(serverDir, "server.properties");
  const original = await fs.readFile(file, "utf8");
  const next = upsertServerProperties(original, {
    "online-mode": "false",
    "enforce-secure-profile": "false",
  });
  if (next !== original) {
    await fs.writeFile(file, next, "utf8");
  }
  return { file, original };
}

async function restoreManagedServerProperties(snapshot) {
  await fs.writeFile(snapshot.file, snapshot.original, "utf8");
}

function upsertServerProperties(text, updates) {
  const seen = new Set();
  const lines = text.split(/\r?\n/).map((line) => {
    const match = /^([^#=][^=]*)=(.*)$/.exec(line);
    if (!match) {
      return line;
    }
    const key = match[1].trim();
    if (!Object.hasOwn(updates, key)) {
      return line;
    }
    seen.add(key);
    return `${key}=${updates[key]}`;
  });
  for (const [key, value] of Object.entries(updates)) {
    if (!seen.has(key)) {
      lines.push(`${key}=${value}`);
    }
  }
  return lines.join("\n");
}

async function startServer() {
  const stdoutPath = path.join(resultDir, "server-stdout.log");
  const stderrPath = path.join(resultDir, "server-stderr.log");
  const stdout = createWriteStream(stdoutPath, { flags: "a" });
  const stderr = createWriteStream(stderrPath, { flags: "a" });
  const javaArgs = [
    `-Dshreddedpaper.watchdog.shutdown-grace-ms=${watchdogGraceMs}`,
    `-Xms${heap}`,
    `-Xmx${heap}`,
    "-jar",
    jarPath,
    "nogui",
  ];

  console.log(`Starting ${javaBin} ${javaArgs.join(" ")}`);
  const proc = spawn(javaBin, javaArgs, {
    cwd: serverDir,
    stdio: ["ignore", "pipe", "pipe"],
  });
  proc.stdout.pipe(stdout);
  proc.stderr.pipe(stderr);
  proc.once("exit", (code, signal) => {
    stdout.write(`\n[process exited code=${code} signal=${signal}]\n`);
    stderr.end();
    stdout.end();
  });
  return proc;
}

async function waitForReady(proc) {
  const deadline = Date.now() + readyTimeoutMs;
  while (Date.now() < deadline) {
    if (proc && proc.exitCode !== null) {
      throw new Error(`Server exited before ready. ExitCode=${proc.exitCode}`);
    }

    if (existsSync(latestLog)) {
      const text = await fs.readFile(latestLog, "utf8");
      if (/Done \([^)]+\)! For help, type "help"/.test(text) && await testRconReady()) {
        return;
      }
    }
    await sleep(500);
  }

  if (proc) {
    await dumpThread(proc.pid, "ready-timeout");
  }
  throw new Error(`Timed out waiting ${readyTimeoutMs}ms for server readiness.`);
}

async function runRconCommands(commandList) {
  const rconLog = path.join(resultDir, "rcon.log");
  await fs.writeFile(rconLog, "", "utf8");
  let expectedScenarioControlCompletions = 0;
  for (const command of commandList) {
    const response = await sendRconCommand(command, 45000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response);
    if (isBadRconResponse(plain)) {
      throw new Error(`RCON command failed: ${command}\n${plain.trim()}`);
    }
    if (/^rlt\b.*\b(?:chunkgen|chunkload|scenario)\b/i.test(command)) {
      await waitForRltIdle(rconLog);
    }
    if (isScenarioCommand(command)) {
      expectedScenarioControlCompletions++;
      await waitForScenarioControlCompletions(expectedScenarioControlCompletions, 90000);
    }
    const redstoneMatch = /\bredstone\s+\d+\s+(\d+)\s+\d+\s+\d+/i.exec(command);
    if (redstoneMatch) {
      const ticks = Number(redstoneMatch[1]);
      await waitForLatestLogMatch(/redstone boundary load finished:/, Math.max(30000, ticks * 50 + 15000));
    }
    const lightingMatch = /\blighting\s+\d+\s+(\d+)\s+\d+/i.exec(command);
    if (lightingMatch) {
      const ticks = Number(lightingMatch[1]);
      await waitForLatestLogMatch(/lighting load finished:/, Math.max(30000, ticks * 50 + 15000));
    }
    await sleep(250);
  }
}

function isScenarioCommand(command) {
  return /^rlt\b.*\bscenario\s+(?:gen|chunkgen|load|chunkload)\b/i.test(command);
}

async function runMccPlayerLifecycleSmoke() {
  const rconLog = path.join(resultDir, "rcon.log");
  const botName = "sp_lifecycle";
  const botDir = path.join(resultDir, "mcc-player-lifecycle");
  await fs.rm(botDir, { recursive: true, force: true });
  await fs.mkdir(botDir, { recursive: true });
  await fs.appendFile(rconLog, "> # mcc player lifecycle smoke\n", "utf8");

  const mccExe = await installMcc();
  const config = path.join(botDir, "MinecraftClient.ini");
  await fs.writeFile(config, mccConfig(botName, path.join(botDir, "mcc-console.log")), "utf8");

  const botState = {
    child: null,
    config,
    exe: mccExe,
    cwd: botDir,
    restarts: 0,
    maxRestarts: 2,
  };

  try {
    botState.child = await startMccLifecycleBot(botState);
    await waitForLifecyclePlayerState(botName, "present", null, 90000, botState);
    await runLifecycleRconCommands([
      "forceload add 0 0 16 16",
      ...arenaPlatformCommands({ x: 0, y: 8, z: 0 }, 12, 8),
      `execute in minecraft:overworld run tp ${botName} 0.5 10 0.5`,
      `gamemode creative ${botName}`,
      `effect give ${botName} minecraft:resistance infinite 5 true`,
      "say worldgen-smoke player-lifecycle joined",
    ], rconLog, 30000, 500);
    await waitForLifecyclePlayerState(botName, "present", "world", 30000, botState);

    await runLifecycleRconCommands([
      `minecraft:kick ${botName} worldgen-smoke-reconnect`,
      "say worldgen-smoke player-lifecycle kicked",
    ], rconLog, 15000, 250);
    await waitForLifecyclePlayerState(botName, "absent", null, 30000, null);
    await stopMccLifecycleBot(botState.child);

    botState.child = await startMccLifecycleBot(botState);
    await waitForLifecyclePlayerState(botName, "present", null, 90000, botState);
    await runLifecycleRconCommands([
      `execute in minecraft:overworld run tp ${botName} 0.5 10 0.5`,
      `gamemode creative ${botName}`,
      `effect give ${botName} minecraft:resistance infinite 5 true`,
      "say worldgen-smoke player-lifecycle rejoined",
    ], rconLog, 20000, 500);

    await runLifecycleRconCommands([
      "forceload add 0 0 64 64",
      "execute in minecraft:the_nether run forceload add 0 0 16 16",
      ...arenaPlatformCommands({ x: 32, y: 8, z: 32 }, 20, 8),
      "fill 30 8 32 34 12 32 minecraft:obsidian",
      "fill 31 9 32 33 11 32 minecraft:nether_portal[axis=x]",
      "execute in minecraft:the_nether run fill 0 64 0 8 64 8 minecraft:netherrack",
      "execute in minecraft:the_nether run fill 0 65 0 8 72 8 minecraft:air",
      "execute in minecraft:the_nether run fill 2 64 4 6 68 4 minecraft:obsidian",
      "execute in minecraft:the_nether run fill 3 65 4 5 67 4 minecraft:nether_portal[axis=x]",
      `tp ${botName} 32 9 32`,
      "say worldgen-smoke player-lifecycle nether-portal-enter",
    ], rconLog, 30000, 250);
    await waitForLifecyclePlayerState(botName, "present", "world_nether", 30000, botState);

    await runLifecycleRconCommands([
      `execute in minecraft:the_nether run tp ${botName} 4 65 7`,
      "say worldgen-smoke player-lifecycle nether-portal-cooldown",
    ], rconLog, 30000, 250);
    await sleep(15000);

    await runLifecycleRconCommands([
      `execute in minecraft:the_nether run tp ${botName} 4 65 4`,
      "say worldgen-smoke player-lifecycle nether-portal-return-enter",
    ], rconLog, 30000, 250);
    await waitForLifecyclePlayerState(botName, "present", "world", 45000, botState);

    await runLifecycleRconCommands([
      "forceload add 32 16 80 64",
      ...arenaPlatformCommands({ x: 56, y: 8, z: 32 }, 12, 8),
      `tp ${botName} 56 10 36`,
      "say worldgen-smoke player-lifecycle nether-return-cooldown",
    ], rconLog, 30000, 250);
    await sleep(15000);

    await runLifecycleRconCommands([
      "execute in minecraft:the_end run forceload add -16 -16 16 16",
      "setblock 56 8 32 minecraft:end_portal",
      "execute in minecraft:the_end run fill -8 64 -8 8 64 8 minecraft:end_stone",
      "execute in minecraft:the_end run fill -8 65 -8 8 72 8 minecraft:air",
      "execute in minecraft:the_end run setblock 0 65 0 minecraft:end_portal",
      `tp ${botName} 56.5 8.2 32.5`,
      "say worldgen-smoke player-lifecycle end-portal-enter",
    ], rconLog, 30000, 250);
    await waitForLifecyclePlayerState(botName, "present", "world_the_end", 45000, botState);

    await runLifecycleRconCommands([
      `execute in minecraft:the_end run tp ${botName} 4 66 4`,
      "say worldgen-smoke player-lifecycle end-entry-cooldown",
    ], rconLog, 30000, 250);
    await sleep(15000);

    // A first-time End portal exit shows credits instead of immediately moving
    // the player back to the overworld. MCC does not drive that UI, so keep the
    // End-entry portal coverage above and use an explicit dimension handoff for
    // the return leg.
    await runLifecycleRconCommands([
      `execute in minecraft:overworld run tp ${botName} 0.5 65.2 0.5`,
      "say worldgen-smoke player-lifecycle end-return-overworld",
    ], rconLog, 30000, 250);
    await waitForLifecyclePlayerState(botName, "present", "world", 45000, botState);

    await runLifecycleRconCommands([
      "forceload remove 0 0 64 64",
      "forceload remove 32 16 80 64",
      "forceload remove 0 0 16 16",
      "execute in minecraft:the_nether run forceload remove 0 0 16 16",
      "execute in minecraft:the_end run forceload remove -16 -16 16 16",
      "say worldgen-smoke player-lifecycle complete",
    ], rconLog, 30000, 250);
  } finally {
    await sendRconCommand(`minecraft:kick ${botName} worldgen-smoke-cleanup`, 5000).catch(() => {});
    await stopMccLifecycleBot(botState.child);
  }
}

async function runLifecycleRconCommands(commandList, rconLog, timeoutMs = 15000, pauseMs = 0) {
  for (const command of commandList) {
    const response = await sendRconCommand(command, timeoutMs);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response);
    if (isBadRconResponse(plain)) {
      throw new Error(`RCON command failed: ${command}\n${plain.trim()}`);
    }
    if (pauseMs > 0) {
      await sleep(pauseMs);
    }
  }
}

async function waitForLifecyclePlayerState(playerName, mode, expectedWorld, timeoutMs, botState) {
  const rconLog = path.join(resultDir, "rcon.log");
  const deadline = Date.now() + timeoutMs;
  let lastResponse = "";
  while (Date.now() < deadline) {
    if (mode === "present" && botState && isChildExited(botState.child)) {
      if (botState.restarts >= botState.maxRestarts) {
        throw new Error(`MCC lifecycle bot exited while waiting for ${playerName} to be present: ${describeChildExit(botState.child)}`);
      }
      botState.restarts++;
      await fs.appendFile(rconLog, `> # restarting MCC lifecycle bot after ${describeChildExit(botState.child)} attempt=${botState.restarts}/${botState.maxRestarts}\n\n`, "utf8");
      botState.child = await startMccLifecycleBot(botState);
      await sleep(1000);
    }

    const command = expectedWorld
      ? `rlt playercheck ${playerName} ${mode} ${expectedWorld}`
      : `rlt playercheck ${playerName} ${mode}`;
    const response = await sendRconCommand(command, 15000);
    await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    lastResponse = plain;
    if (plain.includes("RLT playercheck ok:")) {
      return;
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for lifecycle player ${playerName} to become ${mode}${expectedWorld ? ` in ${expectedWorld}` : ""}. Last response: ${lastResponse || "(none)"}`);
}

async function startMccLifecycleBot(botState) {
  const stdout = createWriteStream(path.join(botState.cwd, "stdout.log"), { flags: "a" });
  const stderr = createWriteStream(path.join(botState.cwd, "stderr.log"), { flags: "a" });
  stdout.write(`\n### MCC lifecycle process start ${new Date().toISOString()} ###\n`);
  stderr.write(`\n### MCC lifecycle process start ${new Date().toISOString()} ###\n`);
  const proc = spawn(botState.exe, [botState.config], {
    cwd: botState.cwd,
    stdio: ["ignore", "pipe", "pipe"],
  });
  proc.stdout.pipe(stdout);
  proc.stderr.pipe(stderr);
  proc.once("exit", (code, signal) => {
    stdout.write(`\n[process exited code=${code} signal=${signal}]\n`);
    stderr.end();
    stdout.end();
  });
  return proc;
}

async function stopMccLifecycleBot(proc) {
  if (!proc || isChildExited(proc)) {
    return;
  }
  proc.kill("SIGTERM");
  if (!await waitForExit(proc, 5000)) {
    proc.kill("SIGKILL");
    await waitForExit(proc, 5000);
  }
}

async function stopServer(proc) {
  try {
    await sendRconCommand("stop", 5000);
  } catch (error) {
    console.warn(`Could not send stop over RCON: ${error.message}`);
  }

  const stopped = await waitForExit(proc, stopTimeoutMs);
  if (!stopped) {
    await dumpThread(proc.pid, "stop-timeout");
    throw new Error(`Server did not exit within ${stopTimeoutMs}ms after stop.`);
  }
}

async function testRconReady() {
  try {
    await sendRconCommand("list", 2500);
    return true;
  } catch {
    return false;
  }
}

async function scanSmokeLogs() {
  const targets = [
    latestLog,
    resultDir,
  ];
  const matches = [];
  for (const target of targets) {
    if (existsSync(target)) {
      matches.push(...await scanPath(target));
    }
  }
  return matches;
}

async function assertRegionLoadTestClean() {
  const hasRltCommand = commands.some(command => /^rlt(?:\s|$)/i.test(command));
  if (!hasRltCommand) {
    return;
  }

  const rconLog = path.join(resultDir, "rcon.log");
  await fs.appendFile(rconLog, "> rlt cleanup\n", "utf8");
  const cleanup = await sendRconCommand("rlt cleanup", 45000);
  await fs.appendFile(rconLog, `${cleanup}\n\n`, "utf8");
  const deadline = Date.now() + 120000;
  let lastStatus = "";
  while (Date.now() < deadline) {
    await sleep(1000);
    await fs.appendFile(rconLog, "> rlt status\n", "utf8");
    const status = await sendRconCommand("rlt status", 45000);
    await fs.appendFile(rconLog, `${status}\n\n`, "utf8");
    const plain = stripMinecraftColors(status);
    if (isBadRconResponse(plain)) {
      throw new Error(`RLT status failed after cleanup:\n${plain.trim()}`);
    }
    lastStatus = plain.trim();
    const counters = parseRegionLoadTestStatus(plain);
    const nonZero = Object.entries(counters).filter(([, value]) => value !== 0);
    if (nonZero.length === 0) {
      await waitForNoTaggedRltEntities(rconLog, 30000);
      return;
    }
  }
  throw new Error(`RegionLoadTest did not clean up fully before timeout. Last status: ${lastStatus || "(none)"}`);
}

async function waitForNoTaggedRltEntities(rconLog, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastFound = [];
  while (Date.now() < deadline) {
    lastFound = await probeTaggedRltEntities(rconLog);
    if (lastFound.length === 0) {
      return;
    }
    await sleep(1000);
  }
  await assertNoTaggedRltEntities(rconLog, lastFound);
}

async function probeTaggedRltEntities(rconLog) {
  const found = [];
  for (const dimension of ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]) {
    const command = `execute in ${dimension} if entity @e[tag=${RLT_ENTITY_TAG},limit=1]`;
    await fs.appendFile(rconLog, `> ${command}\n`, "utf8");
    const response = await sendRconCommand(command, 45000);
    await fs.appendFile(rconLog, `${response}\n\n`, "utf8");
    const plain = stripMinecraftColors(response).trim();
    if (plain && !/test failed/i.test(plain)) {
      found.push(`${dimension}: ${plain}`);
    }
  }
  return found;
}

async function assertNoTaggedRltEntities(rconLog, previousFindings = []) {
  const killed = [];
  for (const dimension of ["minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"]) {
    const command = `execute in ${dimension} run kill @e[tag=${RLT_ENTITY_TAG}]`;
    await fs.appendFile(rconLog, `> ${command}\n`, "utf8");
    const response = await sendRconCommand(command, 45000);
    await fs.appendFile(rconLog, `${response}\n\n`, "utf8");
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

async function assertOwnershipCountersClean() {
  const rconLog = path.join(resultDir, "rcon.log");
  await fs.appendFile(rconLog, "> region ownership\n", "utf8");
  const status = await sendRconCommand("region ownership", 45000);
  await fs.appendFile(rconLog, `${status}\n\n`, "utf8");
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

async function assertRltRuntimeEvidence() {
  const text = existsSync(latestLog) ? await fs.readFile(latestLog, "utf8") : "";
  const issues = [];
  if (commands.some(command => /^rlt\b.*\bcrossqueue\b/i.test(command))) {
    const matches = [...text.matchAll(/Cross-region queue probe finished: .*?tasks=(\d+) queued=(\d+) rejected=(\d+) executed=(\d+) failed=(\d+)/g)];
    if (matches.length === 0) {
      issues.push("Missing cross-region queue completion evidence");
    }
    for (const match of matches) {
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
  }
  if (commands.some(command => /^rlt\b.*\bsyncload\b/i.test(command))) {
    const matches = [...text.matchAll(/Sync-load guard probe finished: .*?loadedBefore=(true|false), attempts=(\d+), guardRejections=(\d+), unexpectedSuccess=(\d+), unexpectedFailure=(\d+)/g)];
    if (matches.length === 0) {
      issues.push("Missing sync-load guard completion evidence");
    }
    for (const match of matches) {
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
  }

  if (commands.some(command => /^rlt\b.*\bpath\b/i.test(command))) {
    const pathMatches = [...text.matchAll(/pathfinding load finished:[^\n]*requested=(\d+)[^\n]*queued=(\d+)[^\n]*completed=(\d+)[^\n]*rejected=(\d+)[^\n]*spawned=(\d+)[^\n]*skippedUnloaded=(\d+)[^\n]*pathStarted=(\d+)[^\n]*crossChunkMoves=(\d+)[^\n]*arrived=(\d+)[^\n]*removed=(\d+)/g)];
    if (pathMatches.length === 0) {
      issues.push("Missing pathfinding movement completion evidence");
    }
    for (const match of pathMatches) {
      const [, requestedRaw, queuedRaw, completedRaw, rejectedRaw, spawnedRaw, skippedRaw, pathStartedRaw, crossChunkMovesRaw, arrivedRaw, removedRaw] = match;
      const requested = Number(requestedRaw);
      const queued = Number(queuedRaw);
      const completed = Number(completedRaw);
      const rejected = Number(rejectedRaw);
      const spawned = Number(spawnedRaw);
      const skipped = Number(skippedRaw);
      const pathStarted = Number(pathStartedRaw);
      const crossChunkMoves = Number(crossChunkMovesRaw);
      const arrived = Number(arrivedRaw);
      const removed = Number(removedRaw);
      if (queued !== requested || completed !== queued || rejected !== 0 || spawned <= 0 || skipped !== 0 || pathStarted !== spawned || crossChunkMoves <= 0) {
        issues.push(`Bad pathfinding movement completion: requested=${requested} queued=${queued} completed=${completed} rejected=${rejected} spawned=${spawned} skippedUnloaded=${skipped} pathStarted=${pathStarted} crossChunkMoves=${crossChunkMoves} arrived=${arrived} removed=${removed}`);
      }
    }
  }

  const batchMatches = [...text.matchAll(/([^\n:]+?) batch finished: .*?total=(\d+) success=(\d+) null=(\d+) failure=(\d+)/g)];
  for (const match of batchMatches) {
    const [, label, totalRaw, successRaw, nullRaw, failureRaw] = match;
    const total = Number(totalRaw);
    const success = Number(successRaw);
    const nullResults = Number(nullRaw);
    const failures = Number(failureRaw);
    if (success !== total || nullResults !== 0 || failures !== 0) {
      issues.push(`Bad RLT batch completion for ${label.trim()}: total=${total} success=${success} null=${nullResults} failure=${failures}`);
    }
  }
  if (commands.some(command => /^rlt\b.*\bscenario\s+gen\b/i.test(command)) && !batchMatches.some(match => match[1].includes("scenario chunk generation"))) {
    issues.push("Missing scenario generation batch completion");
  }
  if (commands.some(command => /^rlt\b.*\bscenario\s+load\b/i.test(command)) && !batchMatches.some(match => match[1].includes("scenario chunk load-only"))) {
    issues.push("Missing scenario load-only batch completion");
  }
  const controlMatches = [...text.matchAll(/scenario control finished at chunk=.*?: samples=(\d+) periodTicks=(\d+) avgLagMs=([-\d.]+) p95LagMs=([-\d.]+) p99LagMs=([-\d.]+) maxLagMs=([-\d.]+)/g)];
  const expectedControlCompletions = commands.filter(command => /^rlt\b.*\bscenario\s+(?:gen|chunkgen|load|chunkload)\b/i.test(command)).length;
  if (expectedControlCompletions > 0 && controlMatches.length < expectedControlCompletions) {
    issues.push(`Missing scenario control lag completions: ${controlMatches.length}/${expectedControlCompletions}`);
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
  if (commands.some(command => /^rlt\b.*\bredstone\b/i.test(command))) {
    const redstoneMatches = [...text.matchAll(/redstone boundary load finished: .*?pulseTasks=(\d+), completed=(\d+), rejected=(\d+), writes=(\d+), skippedUnloaded=(\d+)/g)];
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
      if (tasks <= 0 || completed !== tasks || rejected !== 0 || writes <= 0) {
        issues.push(`Bad redstone/piston boundary completion: tasks=${tasks} completed=${completed} rejected=${rejected} writes=${writes} skippedUnloaded=${skipped}`);
      }
    }
  }
  if (commands.some(command => /^rlt\b.*\blighting\b/i.test(command))) {
    const lightingMatches = [...text.matchAll(/lighting load finished: .*?queued=(\d+), rejected=(\d+), writes=(\d+), lightReads=(\d+), brightSamples=(\d+), darkSamples=(\d+), skippedUnloaded=(\d+)/g)];
    if (lightingMatches.length === 0) {
      issues.push("Missing lighting completion evidence");
    }
    for (const match of lightingMatches) {
      const [, queuedRaw, rejectedRaw, writesRaw, readsRaw, brightSamplesRaw, darkSamplesRaw, skippedRaw] = match;
      const queued = Number(queuedRaw);
      const rejected = Number(rejectedRaw);
      const writes = Number(writesRaw);
      const reads = Number(readsRaw);
      const brightSamples = Number(brightSamplesRaw);
      const darkSamples = Number(darkSamplesRaw);
      const skipped = Number(skippedRaw);
      if (queued <= 0 || rejected !== 0 || writes <= 0 || reads <= 0 || brightSamples <= 0 || darkSamples <= 0 || skipped !== 0) {
        issues.push(`Bad lighting completion: queued=${queued} rejected=${rejected} writes=${writes} lightReads=${reads} brightSamples=${brightSamples} darkSamples=${darkSamples} skippedUnloaded=${skipped}`);
      }
    }
  }
  if (issues.length > 0) {
    throw new Error(`Worldgen RLT runtime evidence is incomplete or unhealthy:\n${issues.join("\n")}`);
  }
}

function parseRegionLoadTestStatus(status) {
  const counters = {};
  for (const key of ["managedTasks", "managedEntities", "managedChunkTickets", "activeChunkBatches"]) {
    const match = new RegExp(`${key}=([0-9]+)`).exec(status);
    if (!match) {
      throw new Error(`Could not parse RegionLoadTest status counter ${key}: ${status.trim()}`);
    }
    counters[key] = Number(match[1]);
  }
  return counters;
}

function parseOwnershipCounters(status) {
  const counters = {};
  for (const key of ["loadedReadFallbacks", "ownerHandoffs", "ownerHandoffRequeues", "ownerHandoffRejections", "prefetchFailures"]) {
    const match = new RegExp(`${key}=([0-9]+)`).exec(status);
    if (!match) {
      throw new Error(`Could not parse ownership counter ${key}: ${status.trim()}`);
    }
    counters[key] = Number(match[1]);
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

async function waitForRltIdle(rconLog, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs;
  let lastStatus = "";
  while (Date.now() < deadline) {
    await fs.appendFile(rconLog, "> rlt status\n", "utf8");
    const status = await sendRconCommand("rlt status", 45000);
    await fs.appendFile(rconLog, `${status}\n\n`, "utf8");
    const plain = stripMinecraftColors(status);
    if (isBadRconResponse(plain)) {
      throw new Error(`RLT status failed:\n${plain.trim()}`);
    }
    lastStatus = plain.trim();
    const counters = parseRegionLoadTestStatus(plain);
    if (counters.activeChunkBatches === 0) {
      return;
    }
    await sleep(1000);
  }
  throw new Error(`Timed out waiting for RLT chunk batches to finish. Last status: ${lastStatus || "(none)"}`);
}

async function waitForLatestLogMatch(regex, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (existsSync(latestLog)) {
      const text = await fs.readFile(latestLog, "utf8");
      if (regex.test(text)) {
        return;
      }
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for latest.log evidence: ${regex}`);
}

async function waitForScenarioControlCompletions(expectedCount, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastCount = 0;
  while (Date.now() < deadline) {
    if (existsSync(latestLog)) {
      const text = await fs.readFile(latestLog, "utf8");
      lastCount = [...text.matchAll(/scenario control finished at chunk=.*?: samples=\d+ periodTicks=\d+ avgLagMs=[-\d.]+ p95LagMs=[-\d.]+ p99LagMs=[-\d.]+ maxLagMs=[-\d.]+/g)].length;
      if (lastCount >= expectedCount) {
        return;
      }
    }
    await sleep(500);
  }
  throw new Error(`Timed out waiting for scenario control lag completion ${lastCount}/${expectedCount}`);
}

function isBadRconResponse(text) {
  return /(^|\n)\s*(Error:|Unknown or incomplete command|Incorrect argument for command|An unexpected error occurred|No player was found|No entity was found|Player not found|That position is not loaded|Too many blocks in the specified area|Broadcast target chunk is not loaded|Broadcast target chunk unloaded|Run \/rlt chunkgen|Usage: \/rlt|RLT playercheck failed|chunks must|tasks must|radiusChunks must|regions\/strideChunks must)/i.test(text)
    || /(?:Block change is not write locked|Synchronous chunk load is not allowed)/i.test(text)
    || /(?:Broadcast scheduling failed|target chunk is not loaded|sync-?load probe .*failed|failed=[1-9]\d*|failure=[1-9]\d*|rejected=[1-9]\d*|null=[1-9]\d*)/i.test(text);
}

async function assertPortFree(label, port) {
  if (await canConnectTcp(port, 750)) {
    throw new Error(`Refusing to start managed smoke: ${label} port ${port} is already accepting TCP connections.`);
  }
}

async function assertTcpReachable(label, port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await canConnectTcp(port, 500)) {
      return;
    }
    await sleep(250);
  }
  throw new Error(`Managed smoke server did not open ${label} port ${port} within ${timeoutMs}ms.`);
}

function canConnectTcp(port, timeoutMs) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    const done = (value) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(value);
    };
    socket.setTimeout(timeoutMs);
    socket.once("connect", () => done(true));
    socket.once("error", () => done(false));
    socket.once("timeout", () => done(false));
  });
}

async function sendRconCommand(command, timeoutMs) {
  const socket = net.createConnection({ host: "127.0.0.1", port: rconPort });
  socket.setTimeout(timeoutMs);
  try {
    await new Promise((resolve, reject) => {
      socket.once("connect", resolve);
      socket.once("error", reject);
      socket.once("timeout", () => reject(new Error(`RCON timeout on port ${rconPort}`)));
    });

    socket.write(makeRconPacket(1, 3, rconPassword));
    const auth = await readRconPacket(socket, timeoutMs);
    if (auth.id === -1) {
      throw new Error(`RCON authentication failed on port ${rconPort}`);
    }

    socket.write(makeRconPacket(2, 2, command));
    const response = await readRconPacket(socket, timeoutMs);
    return response.payload;
  } finally {
    socket.destroy();
  }
}

function makeRconPacket(id, type, payload) {
  const payloadBytes = Buffer.from(payload, "utf8");
  const packet = Buffer.alloc(14 + payloadBytes.length);
  packet.writeInt32LE(10 + payloadBytes.length, 0);
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

async function dumpThread(pid, reason) {
  const javaDir = path.dirname(javaBin);
  const candidates = [
    path.join(javaDir, "jcmd"),
    "jcmd",
  ];

  for (const candidate of candidates) {
    const result = spawnSync(candidate, [String(pid), "Thread.print", "-l"], { encoding: "utf8" });
    if (result.status === 0 || result.stdout || result.stderr) {
      await fs.writeFile(path.join(resultDir, `jcmd-${reason}.txt`), `${result.stdout}\n${result.stderr}`, "utf8");
      return;
    }
  }
}

async function assertNoLsof(label, argsForLsof) {
  const result = spawnSync("lsof", argsForLsof, { encoding: "utf8" });
  if (result.error) {
    throw new Error(`Could not verify resource release with lsof (${label}): ${result.error.message}`);
  }
  if (result.stdout.trim()) {
    throw new Error(`Resource still held after shutdown (${label}):\n${result.stdout}`);
  }
  if (result.status !== 0 && result.status !== 1) {
    throw new Error(`lsof failed while checking ${label} (status=${result.status}):\n${result.stderr || "(no stderr)"}`);
  }
}

async function listCrashReports(dir) {
  const out = new Set();
  if (!existsSync(dir)) {
    return out;
  }
  const entries = await fs.readdir(dir);
  for (const entry of entries) {
    if (/^crash-.*\.txt$/.test(entry)) {
      out.add(path.join(dir, entry));
    }
  }
  return out;
}

function waitForExit(proc, timeoutMs) {
  if (!proc || proc.exitCode !== null) {
    return Promise.resolve(true);
  }
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      proc.off("exit", onExit);
      resolve(false);
    }, timeoutMs);
    const onExit = () => {
      clearTimeout(timer);
      resolve(true);
    };
    proc.once("exit", onExit);
  });
}

function isChildExited(proc) {
  if (!proc) {
    return true;
  }
  if (proc.exitCode !== null || proc.signalCode !== null) {
    return true;
  }
  if (!proc.pid) {
    return true;
  }
  return !isProcessAlive(proc.pid);
}

function isProcessAlive(pid) {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
}

function describeChildExit(proc) {
  if (!proc) {
    return "missing-child";
  }
  if (proc.exitCode !== null) {
    return `code=${proc.exitCode}`;
  }
  if (proc.signalCode !== null) {
    return `signal=${proc.signalCode}`;
  }
  if (!proc.pid || !isProcessAlive(proc.pid)) {
    return `pid=${proc.pid ?? "unknown"} missing`;
  }
  return `pid=${proc.pid} still-running`;
}

function arenaPlatformCommands({ x, y, z }, radius = 24, airHeight = 8) {
  return [
    ...fillVolumeCommands(x - radius, y - 1, z - radius, x + radius, y - 1, z + radius, "minecraft:stone"),
    ...fillVolumeCommands(x - radius, y, z - radius, x + radius, y + airHeight, z + radius, "minecraft:air"),
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

async function installMcc() {
  if (args.mccPath) {
    const resolved = path.resolve(args.mccPath);
    await fs.access(resolved);
    return resolved;
  }

  const mccDir = path.join(mccCacheDir, "mcc");
  await fs.mkdir(mccDir, { recursive: true });
  const existing = (await fs.readdir(mccDir).catch(() => []))
    .filter((name) => name.startsWith("MinecraftClient") && !name.endsWith(".txt"))
    .map((name) => path.join(mccDir, name))
    .filter((file) => existsSync(file))
    .sort((a, b) => statSync(b).mtimeMs - statSync(a).mtimeMs)[0];
  if (existing) {
    await fs.chmod(existing, 0o755).catch(() => {});
    return existing;
  }

  if (args.requireCachedMcc) {
    throw new Error(`MCC binary is not cached in ${mccDir}. Pass --mcc-path <path> or warm the cache before release gating.`);
  }

  const rid = mccRid();
  const release = await fetch("https://api.github.com/repos/MCCTeam/Minecraft-Console-Client/releases/latest", {
    headers: { "User-Agent": "ShreddedPaper-WorldgenSmoke" },
  }).then((response) => {
    if (!response.ok) throw new Error(`GitHub release lookup failed: ${response.status}`);
    return response.json();
  });
  const asset = release.assets.find((candidate) => candidate.name.endsWith(rid));
  if (!asset) {
    throw new Error(`Could not find MCC asset ending with ${rid} in latest release ${release.tag_name}`);
  }
  const target = path.join(mccDir, asset.name);
  const response = await fetch(asset.browser_download_url, { headers: { "User-Agent": "ShreddedPaper-WorldgenSmoke" } });
  if (!response.ok) {
    throw new Error(`Download failed ${response.status}: ${asset.browser_download_url}`);
  }
  await fs.writeFile(target, Buffer.from(await response.arrayBuffer()));
  await fs.writeFile(`${target}.download-url.txt`, `${asset.browser_download_url}\n`, "utf8");
  await fs.chmod(target, 0o755);
  return target;
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
  throw new Error(`Unsupported MCC smoke platform: ${platform}/${arch}`);
}

function mccConfig(name, logFile) {
  return `[Main.General]
Account = { Login = "${name}", Password = "-" }
Server = { Host = "127.0.0.1", Port = ${serverPort} }
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
`;
}

function parseArgs(argv) {
  const parsed = {
    commands: [],
    noStart: false,
    noStop: false,
    attachExisting: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    const next = () => {
      if (i + 1 >= argv.length) {
        throw new Error(`Missing value for ${arg}`);
      }
      return argv[++i];
    };

    switch (arg) {
      case "--server-dir":
        parsed.serverDir = next();
        break;
      case "--jar":
        parsed.jar = next();
        break;
      case "--java":
        parsed.java = next();
        break;
      case "--rcon-port":
        parsed.rconPort = next();
        break;
      case "--server-port":
        parsed.serverPort = next();
        break;
      case "--rcon-password":
        parsed.rconPassword = next();
        break;
      case "--heap":
        parsed.heap = next();
        break;
      case "--ready-timeout-sec":
        parsed.readyTimeoutSec = next();
        break;
      case "--post-command-wait-sec":
        parsed.postCommandWaitSec = next();
        break;
      case "--stop-timeout-sec":
        parsed.stopTimeoutSec = next();
        break;
      case "--watchdog-grace-ms":
        parsed.watchdogGraceMs = next();
        break;
      case "--result-dir":
        parsed.resultDir = next();
        break;
      case "--mcc-path":
        parsed.mccPath = next();
        break;
      case "--mcc-cache-dir":
        parsed.mccCacheDir = next();
        break;
      case "--require-cached-mcc":
        parsed.requireCachedMcc = true;
        break;
      case "--command":
        parsed.commands.push(next());
        break;
      case "--skip-player-lifecycle":
        parsed.skipPlayerLifecycle = true;
        break;
      case "--no-start":
        parsed.noStart = true;
        break;
      case "--no-stop":
        parsed.noStop = true;
        break;
      case "--attach-existing":
        parsed.attachExisting = true;
        break;
      case "--allow-live-probes":
        parsed.allowLiveProbes = true;
        break;
      case "--help":
        printHelpAndExit();
        break;
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return parsed;
}

function printHelpAndExit() {
  console.log(`Usage: node tools/runtime/invoke-worldgen-smoke.mjs [options]

Options:
  --server-dir <path>          Server directory. Default: ../worldgen from repo root.
  --jar <path-or-name>         Jar path. Relative paths resolve inside server-dir.
  --java <path>                Java binary.
  --rcon-port <port>           Default: 25575.
  --server-port <port>         Default: 25565.
  --rcon-password <password>   Default: codex-region-test.
  --heap <size>                Default: 4G.
  --mcc-path <path>            Existing Minecraft Console Client binary.
  --mcc-cache-dir <path>       MCC cache directory. Default: run/mcc-chaos/cache.
  --command <command>          Override default commands; may be repeated.
  --skip-player-lifecycle      Skip the MCC real-player join/reconnect/portal lifecycle smoke.
  --require-cached-mcc         Fail instead of downloading MCC when the cache is cold.
  --no-start                   Legacy option; release-grade smoke now owns its server process.
  --no-stop                    Leave a server started by this script running.
  --attach-existing            Legacy option; existing-server attach is rejected to avoid stale evidence.
  --allow-live-probes          Legacy option retained for old invocations.
`);
  process.exit(0);
}

function assertExists(target, label) {
  if (!existsSync(target)) {
    throw new Error(`Missing ${label}: ${target}`);
  }
}

function stripMinecraftColors(text) {
  return text.replace(/\u00a7./g, "");
}

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
