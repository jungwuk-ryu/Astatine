#!/usr/bin/env node
import fs from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawn } from "node:child_process";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");

const options = {
  skipBuild: false,
  skipMcc: false,
  skipWatchdog: false,
  skipWorldgen: false,
  isolatedWorldgen: false,
  java: "/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home/bin/java",
  worldgenDir: "/Users/jungwuk/Documents/works/worldgen",
  worldgenServerPort: "25565",
  worldgenRconPort: "25575",
  isolatedWorldgenDir: resolve(repoRoot, "run", "worldgen-smoke-isolated", "server"),
  serverJar: resolve(repoRoot, "shreddedpaper-server", "build", "libs", "shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"),
  regionLoadTestJar: resolve(repoRoot, "tools", "region-load-test-plugin", "build", "libs", "region-load-test-plugin-0.1.0-SNAPSHOT.jar"),
  heap: "4G",
  botCount: "7",
  minBots: "7",
  mccDurationSec: "180",
  chaosProfile: "anarchy-smp",
  chaosIntensity: "2",
  serverPort: "25566",
  rconPort: "25576",
  websocketBasePort: "8060",
};

function usage() {
  console.log(`Usage: node tools/runtime/invoke-async-release-gates.mjs [options]

Runs the async ownership release gates in a fixed order.

Options:
  --skip-build                 Skip Gradle patch/compile/jar gates.
  --skip-mcc                   Skip MCC chaos and MCC log scan.
  --skip-watchdog              Skip watchdog emergency shutdown smokes.
  --skip-worldgen              Skip managed worldgen smoke.
  --isolated-worldgen          Prepare and use an isolated smoke server under run/.
  --isolated-worldgen-dir <p>  Isolated smoke server directory.
  --server-jar <path>          Server jar copied into isolated worldgen.
  --region-load-test-jar <p>   RLT plugin jar copied into isolated worldgen.
  --java <path>                Java executable for worldgen smoke.
  --worldgen-dir <path>        worldgen server directory.
  --worldgen-server-port <p>   Managed worldgen server port. Default: 25565.
  --worldgen-rcon-port <p>     Managed worldgen RCON port. Default: 25575.
  --heap <size>                Heap for managed worldgen smoke. Default: 4G.
  --bot-count <n>              MCC chaos bot count. Default: 7.
  --min-bots <n>               Minimum MCC bots. Default: 7.
  --mcc-duration-sec <n>       MCC chaos duration. Default: 180.
  --chaos-profile <name>       MCC profile: baseline or anarchy-smp. Default: anarchy-smp.
  --chaos-intensity <1..4>     MCC SMP fixture density. Default: 2.
  --server-port <port>         MCC server port. Default: 25566.
  --rcon-port <port>           MCC RCON port. Default: 25576.
  --websocket-base-port <port> MCC websocket base port. Default: 8060.
  --help                       Show this help.

The worldgen smoke intentionally fails if RCON is already reachable so stale
logs and counters cannot hide or invent release-gate failures.`);
}

function readValue(args, index, name) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

function parseArgs(args) {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    switch (arg) {
      case "--skip-build":
        options.skipBuild = true;
        break;
      case "--skip-mcc":
        options.skipMcc = true;
        break;
      case "--skip-watchdog":
        options.skipWatchdog = true;
        break;
      case "--skip-worldgen":
        options.skipWorldgen = true;
        break;
      case "--isolated-worldgen":
        options.isolatedWorldgen = true;
        options.worldgenDir = options.isolatedWorldgenDir;
        options.worldgenServerPort = "25567";
        options.worldgenRconPort = "25577";
        break;
      case "--isolated-worldgen-dir":
        options.isolatedWorldgenDir = resolve(readValue(args, i, arg));
        options.worldgenDir = options.isolatedWorldgenDir;
        i++;
        break;
      case "--server-jar":
        options.serverJar = resolve(readValue(args, i, arg));
        i++;
        break;
      case "--region-load-test-jar":
        options.regionLoadTestJar = resolve(readValue(args, i, arg));
        i++;
        break;
      case "--java":
        options.java = readValue(args, i, arg);
        i++;
        break;
      case "--worldgen-dir":
        options.worldgenDir = readValue(args, i, arg);
        i++;
        break;
      case "--worldgen-server-port":
        options.worldgenServerPort = readValue(args, i, arg);
        i++;
        break;
      case "--worldgen-rcon-port":
        options.worldgenRconPort = readValue(args, i, arg);
        i++;
        break;
      case "--heap":
        options.heap = readValue(args, i, arg);
        i++;
        break;
      case "--bot-count":
        options.botCount = readValue(args, i, arg);
        i++;
        break;
      case "--min-bots":
        options.minBots = readValue(args, i, arg);
        i++;
        break;
      case "--mcc-duration-sec":
        options.mccDurationSec = readValue(args, i, arg);
        i++;
        break;
      case "--chaos-profile":
        options.chaosProfile = readValue(args, i, arg);
        i++;
        break;
      case "--chaos-intensity":
        options.chaosIntensity = readValue(args, i, arg);
        i++;
        break;
      case "--server-port":
        options.serverPort = readValue(args, i, arg);
        i++;
        break;
      case "--rcon-port":
        options.rconPort = readValue(args, i, arg);
        i++;
        break;
      case "--websocket-base-port":
        options.websocketBasePort = readValue(args, i, arg);
        i++;
        break;
      case "--help":
      case "-h":
        usage();
        process.exit(0);
        break;
      default:
        throw new Error(`Unknown option: ${arg}`);
    }
  }
}

function quoteArg(arg) {
  if (/^[A-Za-z0-9_./:=+-]+$/.test(arg)) {
    return arg;
  }
  return JSON.stringify(arg);
}

function run(label, command, args) {
  console.log("");
  console.log(`[gate] ${label}`);
  console.log(`$ ${[command, ...args].map(quoteArg).join(" ")}`);

  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, {
      cwd: repoRoot,
      stdio: "inherit",
    });
    child.on("error", reject);
    child.on("exit", (code, signal) => {
      if (code === 0) {
        resolvePromise();
        return;
      }
      reject(new Error(`${label} failed with ${signal ? `signal ${signal}` : `exit code ${code}`}`));
    });
  });
}

async function scanAllMccChaosLogs() {
  const resultsRoot = join(repoRoot, "run", "mcc-chaos", "results");
  const entries = await fs.readdir(resultsRoot, { withFileTypes: true }).catch(() => []);
  const cycleDirs = entries
    .filter((entry) => entry.isDirectory() && /^cycle-\d+$/.test(entry.name))
    .map((entry) => join(resultsRoot, entry.name))
    .sort();
  if (cycleDirs.length === 0) {
    throw new Error(`No MCC chaos cycle result directories found under ${resultsRoot}`);
  }
  await run("scan all MCC chaos logs", "node", [
    "tools/runtime/scan-server-log.mjs",
    ...cycleDirs,
  ]);
}

async function main() {
  parseArgs(process.argv.slice(2));
  const skipped = [];

  if (!options.skipBuild) {
    await run("apply patches", "./gradlew", ["applyAllPatches", "--no-configuration-cache"]);
    await run("compile server", "./gradlew", [
      ":shreddedpaper-server:compileJava",
      "--rerun-tasks",
      "--no-configuration-cache",
    ]);
    await run("build mojmap paperclip jar", "./gradlew", [
      ":shreddedpaper-server:createMojmapPaperclipJar",
      "--rerun-tasks",
      "--no-configuration-cache",
    ]);
    await run("build region load test plugin", "./gradlew", [
      "-p",
      "tools/region-load-test-plugin",
      "build",
      "--no-configuration-cache",
    ]);
  } else {
    skipped.push("build");
    console.log("[gate] build gates skipped by --skip-build");
  }

  await run("async ownership scanner", "node", [
    "tools/async-audit/scan-async-ownership.mjs",
    "--write-todo",
    "--fail-on-critical",
  ]);
  await run("root-covered async baseline", "node", [
    "tools/runtime/verify-async-root-baseline.mjs",
  ]);
  await run("runtime tool syntax", "node", ["--check", "tools/runtime/scan-server-log.mjs"]);
  await run("worldgen smoke syntax", "node", ["--check", "tools/runtime/invoke-worldgen-smoke.mjs"]);
  await run("watchdog smoke syntax", "node", ["--check", "tools/runtime/invoke-watchdog-smoke.mjs"]);
  await run("async release runner syntax", "node", ["--check", "tools/runtime/invoke-async-release-gates.mjs"]);
  await run("MCC chaos runner syntax", "node", ["--check", "tools/mcc-chaos/invoke-mcc-chaos-loop.mjs"]);
  await run("MCC chaos controller syntax", "node", ["--check", "tools/mcc-chaos/mcc-chaos-controller.mjs"]);

  if (!options.skipMcc) {
    await run("MCC chaos", "tools/mcc-chaos/Invoke-MccChaosLoop.sh", [
      "--bot-count",
      options.botCount,
      "--min-bots",
      options.minBots,
      "--duration-sec",
      options.mccDurationSec,
      "--chaos-profile",
      options.chaosProfile,
      "--chaos-intensity",
      options.chaosIntensity,
      "--server-port",
      options.serverPort,
      "--rcon-port",
      options.rconPort,
      "--websocket-base-port",
      options.websocketBasePort,
      "--enable-natural-spawns",
      "--skip-build",
      "--agent-mode",
      "ReportOnly",
    ]);
    await scanAllMccChaosLogs();
  } else {
    skipped.push("mcc");
    console.log("[gate] MCC chaos skipped by --skip-mcc");
  }

  if (!options.skipWatchdog) {
    await run("watchdog emergency shutdown smoke (global scheduler)", "node", [
      "tools/runtime/invoke-watchdog-smoke.mjs",
      "--mode",
      "global",
      "--root",
      "run/watchdog-smoke-global",
      "--server-port",
      "25568",
      "--rcon-port",
      "25578",
      "--java",
      options.java,
      "--server-jar",
      options.serverJar,
      "--region-load-test-jar",
      options.regionLoadTestJar,
    ]);
    await run("watchdog emergency shutdown smoke (region scheduler)", "node", [
      "tools/runtime/invoke-watchdog-smoke.mjs",
      "--mode",
      "region",
      "--root",
      "run/watchdog-smoke-region",
      "--server-port",
      "25569",
      "--rcon-port",
      "25579",
      "--java",
      options.java,
      "--server-jar",
      options.serverJar,
      "--region-load-test-jar",
      options.regionLoadTestJar,
    ]);
  } else {
    skipped.push("watchdog");
    console.log("[gate] watchdog smokes skipped by --skip-watchdog");
  }

  if (!options.skipWorldgen) {
    if (options.isolatedWorldgen) {
      await prepareIsolatedWorldgen();
    }
    await run("managed worldgen smoke", "node", [
      "tools/runtime/invoke-worldgen-smoke.mjs",
      "--server-dir",
      options.worldgenDir,
      "--java",
      options.java,
      "--heap",
      options.heap,
      "--server-port",
      options.worldgenServerPort,
      "--rcon-port",
      options.worldgenRconPort,
      "--require-cached-mcc",
    ]);
  } else {
    skipped.push("worldgen");
    console.log("[gate] managed worldgen smoke skipped by --skip-worldgen");
  }

  console.log("");
  if (skipped.length > 0) {
    console.log(`[gate] partial async gate set passed; skipped=${skipped.join(",")} so this is not a release-ready claim.`);
  } else {
    console.log("[gate] async release gates passed");
  }
}

async function prepareIsolatedWorldgen() {
  assertExists(options.serverJar, "server jar");
  assertExists(options.regionLoadTestJar, "region load test plugin jar");
  await fs.rm(options.worldgenDir, { recursive: true, force: true });
  await fs.mkdir(join(options.worldgenDir, "plugins"), { recursive: true });
  await fs.copyFile(options.serverJar, join(options.worldgenDir, "shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar"));
  await fs.copyFile(options.regionLoadTestJar, join(options.worldgenDir, "plugins", "region-load-test-plugin.jar"));
  await fs.writeFile(join(options.worldgenDir, "eula.txt"), "eula=true\n", "utf8");
  await fs.writeFile(join(options.worldgenDir, "server.properties"), isolatedServerProperties(), "utf8");
}

function isolatedServerProperties() {
  return `allow-flight=true
enable-command-block=false
enable-query=false
enable-rcon=true
enforce-secure-profile=false
force-gamemode=false
gamemode=creative
generate-structures=true
level-name=world
max-players=20
motd=ShreddedPaper isolated async smoke
online-mode=false
prevent-proxy-connections=false
rcon.password=codex-region-test
rcon.port=${options.worldgenRconPort}
server-ip=127.0.0.1
server-port=${options.worldgenServerPort}
simulation-distance=8
spawn-protection=0
view-distance=8
`;
}

function assertExists(target, label) {
  if (!existsSync(target)) {
    throw new Error(`Missing ${label}: ${target}`);
  }
}

main().catch(error => {
  console.error(`Error: ${error.message}`);
  process.exitCode = 1;
});
