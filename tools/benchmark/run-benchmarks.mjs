#!/usr/bin/env node
import fs from "node:fs/promises";
import { createWriteStream, existsSync, statSync } from "node:fs";
import net from "node:net";
import { spawn } from "node:child_process";
import { dirname, join, resolve, relative, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { Readable } from "node:stream";
import { pipeline } from "node:stream/promises";
import { formatMatches, scanText } from "../runtime/scan-server-log.mjs";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const defaultRoot = resolve(repoRoot, "run", "benchmarks");
const defaultConfigPath = resolve(repoRoot, "tools", "benchmark", "benchmark-config.example.json");

const options = {
  config: defaultConfigPath,
  root: defaultRoot,
  suite: "smoke",
  engines: [],
  scenarios: [],
  repeat: null,
  java: null,
  heap: null,
  allowDownloads: false,
  buildCurrent: false,
  buildPlugin: true,
  skipBuild: false,
  keepRunDirs: false,
  failOnError: false,
  prepareOnly: false,
  useMcc: false,
  mccPath: null,
  skipMccDownload: false,
};

const DEFAULT_CONFIG = {
  version: 1,
  minecraftVersion: "1.21.11",
  java: "java",
  heap: "8G",
  ports: {
    serverBase: 26000,
    rconBase: 36000,
  },
  rconPassword: "codex-benchmark",
  mcc: {
    enabled: false,
    cacheDir: "run/benchmarks/mcc-cache",
    botNamePrefix: "benchbot",
    joinTimeoutSec: 140,
    startDelayMs: 2000,
    platformRadius: 24,
    platformAirHeight: 8,
  },
  server: {
    viewDistance: 12,
    simulationDistance: 7,
    seed: "shreddedpaper-benchmark-20260426",
    levelName: "world",
    maxPlayers: 400,
  },
  plugins: [
    {
      name: "RegionLoadTest",
      path: "tools/region-load-test-plugin/build/libs/region-load-test-plugin-0.1.0-SNAPSHOT.jar",
      build: ["./gradlew", "-p", "tools/region-load-test-plugin", "build", "--no-configuration-cache"],
      required: true,
    },
  ],
  engines: [
    {
      name: "our-shreddedpaper",
      jar: "shreddedpaper-server/build/libs/shreddedpaper-paperclip-1.21.11-R0.1-SNAPSHOT-mojmap.jar",
      build: ["./gradlew", ":shreddedpaper-server:createMojmapPaperclipJar", "--no-configuration-cache"],
      regionCommand: true,
    },
    {
      name: "paper",
      jar: "jars/paper-1.21.11-130.jar",
      downloadUrl: "https://fill-data.papermc.io/v1/objects/25eb85bd8415195ce4bc188e1939e0c7cef77fb51d26d4e766407ee922561097/paper-1.21.11-130.jar",
    },
    {
      name: "folia",
      jar: "jars/folia-1.21.11-14.jar",
      downloadUrl: "https://fill-data.papermc.io/v1/objects/f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39/folia-1.21.11-14.jar",
    },
    {
      name: "purpur",
      jar: "jars/purpur-1.21.11-2568.jar",
      downloadUrl: "https://api.purpurmc.org/v2/purpur/1.21.11/2568/download",
    },
    {
      name: "divinemc",
      jar: "jars/divinemc-1.21.11-24.jar",
      downloadUrl: "https://files.bxteam.org/divinemc/versions/1.21.11/24/divinemc-1.21.11-24.jar",
    },
  ],
  suites: {
    smoke: {
      repeat: 1,
      variables: {
        probeSamples: 240,
        lifeTicks: 480,
        entityCount: 40,
        heavyEntityCount: 60,
        redstoneLanes: 4,
        redstoneLength: 48,
        mobSpawners: 8,
        pvpLaunchers: 8,
        vehicles: 12,
        trackerCount: 80,
        broadcastChunks: 4,
        broadcastBlocks: 32,
        schedulerTasks: 32,
        payloadIterations: 64,
        chunkRegions: 1,
        chunkStride: 96,
        chunkRadius: 2,
        durationShort: 45,
        durationMedium: 60,
        durationLong: 90,
        pollEverySec: 3,
      },
      scenarios: [
        "baseline",
        "one-hot-region",
        "chunk-generation",
        "boundary-torture",
      ],
    },
    stressed: {
      repeat: 3,
      variables: {
        probeSamples: 1200,
        lifeTicks: 1800,
        entityCount: 240,
        heavyEntityCount: 420,
        redstoneLanes: 24,
        redstoneLength: 192,
        mobSpawners: 48,
        pvpLaunchers: 48,
        vehicles: 64,
        trackerCount: 600,
        broadcastChunks: 16,
        broadcastBlocks: 128,
        schedulerTasks: 192,
        payloadIterations: 512,
        chunkRegions: 4,
        chunkStride: 128,
        chunkRadius: 6,
        durationShort: 120,
        durationMedium: 210,
        durationLong: 300,
        pollEverySec: 5,
      },
      scenarios: [
        "one-hot-region",
        "multi-hotspot",
        "chunk-generation",
        "boundary-torture",
      ],
    },
    standard: {
      repeat: 5,
      variables: {
        probeSamples: 600,
        lifeTicks: 1200,
        entityCount: 180,
        heavyEntityCount: 300,
        redstoneLanes: 16,
        redstoneLength: 128,
        mobSpawners: 32,
        pvpLaunchers: 32,
        vehicles: 48,
        trackerCount: 400,
        broadcastChunks: 12,
        broadcastBlocks: 96,
        schedulerTasks: 128,
        payloadIterations: 256,
        chunkRegions: 4,
        chunkStride: 128,
        chunkRadius: 6,
        durationShort: 90,
        durationMedium: 150,
        durationLong: 240,
        pollEverySec: 5,
      },
      scenarios: [
        "baseline",
        "spawn-pile",
        "one-hot-region",
        "multi-hotspot",
        "chunk-load",
        "chunk-generation",
        "boundary-torture",
        "tnt-isolation",
      ],
    },
  },
  scenarios: [
    {
      name: "baseline",
      durationSec: "${durationShort}",
      mccAnchors: [
        { role: "baseline", x: 0, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 0 96 0 probe ${probeSamples} 1",
      ],
    },
    {
      name: "spawn-pile",
      durationSec: "${durationMedium}",
      mccAnchors: [
        { role: "spawn", x: 0, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 0 96 0 probe ${probeSamples} 1",
        "rlt at world 0 96 0 villagers ${heavyEntityCount} 12 ${lifeTicks}",
        "rlt at world 8 96 8 path ${entityCount} 12 ${lifeTicks}",
        "rlt at world -8 96 -8 tracker ${trackerCount} ${lifeTicks} 8",
      ],
    },
    {
      name: "one-hot-region",
      durationSec: "${durationMedium}",
      pollEverySec: "${pollEverySec}",
      mccAnchors: [
        { role: "hot", x: 0, y: 96, z: 0 },
        { role: "control", x: 8192, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 0 96 0 probe ${probeSamples} 1",
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 0 96 0 villagers ${entityCount} 32 ${lifeTicks}",
        "rlt at world 64 96 0 path ${entityCount} 48 ${lifeTicks}",
        "rlt at world -64 96 0 redstone ${redstoneLanes} ${lifeTicks} ${redstoneLength} 1",
        "rlt at world 0 96 96 mobfarm ${mobSpawners} 3 ${lifeTicks} 20 72",
        "rlt at world 0 96 -96 pvp ${pvpLaunchers} 4 ${lifeTicks} 8 96 4",
        "rlt at world 96 96 96 broadcast ${broadcastChunks} ${broadcastBlocks} ${lifeTicks}",
        "rlt at world 32 96 32 scheduler region ${schedulerTasks} ${payloadIterations}",
        "rlt at world -32 96 32 scheduler regionlocal ${schedulerTasks} ${payloadIterations}",
        "rlt at world 32 96 -32 crossqueue 16 ${schedulerTasks} ${payloadIterations}",
        "rlt at world -96 96 -96 vehicles ${vehicles} ${lifeTicks} 96",
      ],
    },
    {
      name: "multi-hotspot",
      durationSec: "${durationLong}",
      mccAnchors: [
        { role: "control", x: 8192, y: 96, z: 0 },
        { role: "villagers", x: 0, y: 96, z: 0 },
        { role: "path", x: 1024, y: 96, z: 0 },
        { role: "redstone", x: -1024, y: 96, z: 0 },
        { role: "mobfarm", x: 0, y: 96, z: 1024 },
        { role: "pvp", x: 0, y: 96, z: -1024 },
        { role: "vehicles", x: 1024, y: 96, z: 1024 },
      ],
      commands: [
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 0 96 0 villagers ${entityCount} 32 ${lifeTicks}",
        "rlt at world 1024 96 0 path ${entityCount} 48 ${lifeTicks}",
        "rlt at world -1024 96 0 redstone ${redstoneLanes} ${lifeTicks} ${redstoneLength} 1",
        "rlt at world 0 96 1024 mobfarm ${mobSpawners} 3 ${lifeTicks} 20 72",
        "rlt at world 0 96 -1024 pvp ${pvpLaunchers} 4 ${lifeTicks} 8 96 4",
        "rlt at world 1024 96 1024 vehicles ${vehicles} ${lifeTicks} 96",
      ],
    },
    {
      name: "chunk-load",
      durationSec: "${durationLong}",
      mccAnchors: [
        { role: "control", x: 8192, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 200000 96 0 scenario load ${chunkRegions} ${chunkStride} ${chunkRadius} false ${probeSamples} 1",
      ],
    },
    {
      name: "chunk-generation",
      durationSec: "${durationLong}",
      mccAnchors: [
        { role: "control", x: 8192, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 300000 96 0 scenario gen ${chunkRegions} ${chunkStride} ${chunkRadius} false ${probeSamples} 1",
      ],
    },
    {
      name: "boundary-torture",
      durationSec: "${durationMedium}",
      mccAnchors: [
        { role: "hot", x: 0, y: 96, z: 0 },
        { role: "control", x: 8192, y: 96, z: 0 },
        { role: "pvp", x: 96, y: 96, z: 0 },
        { role: "vehicles", x: -96, y: 96, z: 0 },
        { role: "crossq", x: 0, y: 96, z: 96 },
        { role: "syncld", x: 0, y: 96, z: -96 },
      ],
      commands: [
        "rlt at world 0 96 0 probe ${probeSamples} 1",
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 0 96 0 redstone ${redstoneLanes} ${lifeTicks} ${redstoneLength} 1",
        "rlt at world 96 96 0 pvp ${pvpLaunchers} 4 ${lifeTicks} 6 96 3",
        "rlt at world -96 96 0 vehicles ${vehicles} ${lifeTicks} 96",
        "rlt at world 0 96 96 crossqueue 16 64 ${payloadIterations}",
        "rlt at world 0 96 -96 syncload 64 4",
      ],
    },
    {
      name: "tnt-isolation",
      durationSec: "${durationMedium}",
      mccAnchors: [
        { role: "hot", x: 0, y: 96, z: 0 },
        { role: "control", x: 8192, y: 96, z: 0 },
      ],
      commands: [
        "rlt at world 0 96 0 probe ${probeSamples} 1",
        "rlt at world 8192 96 0 probe ${probeSamples} 1",
        "rlt at world 0 96 0 tntsingle 18 18 2 40 8",
      ],
    },
  ],
};

function usage() {
  console.log(`Usage: tools/benchmark/run-benchmarks.sh [options]
       node tools/benchmark/run-benchmarks.mjs [options]

Runs repeatable RCON/RLT benchmarks across one or more server engines.

Options:
  --config <path>        Benchmark config JSON. Default: tools/benchmark/benchmark-config.example.json.
  --root <path>          Output root. Default: run/benchmarks.
  --suite <name>         Suite in config: smoke, stressed, or standard. Default: smoke.
  --smoke                Alias for --suite smoke.
  --stressed             Alias for --suite stressed.
  --standard             Alias for --suite standard.
  --engine <name[,..]>   Filter engines. Can be repeated.
  --scenario <name[,..]> Filter scenarios. Can be repeated.
  --repeat <n>           Override suite repeat count.
  --java <path>          Java executable override.
  --heap <size>          Heap override, e.g. 8G.
  --allow-downloads      Download missing engine jars that define downloadUrl.
  --build-current        Build engines/plugins with build commands before running.
  --skip-build           Skip all build commands.
  --keep-run-dirs        Keep prepared server directories after runs.
  --use-mcc              Start MCC fake players for scenarios with mccAnchors.
  --mcc-path <path>      MCC executable override.
  --skip-mcc-download    Require cached/local MCC when --use-mcc is enabled.
  --prepare-only         Build/download/copy prerequisites, then stop.
  --fail-on-error        Exit non-zero when any run crashes or logs high-signal failures.
  --help                 Show this help.

Examples:
  tools/benchmark/run-benchmarks.sh --suite smoke --engine our-shreddedpaper
  tools/benchmark/run-benchmarks.sh --suite stressed --use-mcc --scenario one-hot-region --repeat 3
  tools/benchmark/run-benchmarks.sh --suite standard --allow-downloads --repeat 5
`);
}

function parseArgs(args) {
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    switch (arg) {
      case "--config":
        options.config = resolve(readValue(args, i, arg));
        i++;
        break;
      case "--root":
        options.root = resolve(readValue(args, i, arg));
        i++;
        break;
      case "--suite":
        options.suite = readValue(args, i, arg);
        i++;
        break;
      case "--smoke":
        options.suite = "smoke";
        break;
      case "--stressed":
        options.suite = "stressed";
        break;
      case "--standard":
        options.suite = "standard";
        break;
      case "--engine":
        options.engines.push(...splitList(readValue(args, i, arg)));
        i++;
        break;
      case "--scenario":
        options.scenarios.push(...splitList(readValue(args, i, arg)));
        i++;
        break;
      case "--repeat":
        options.repeat = Number(readValue(args, i, arg));
        i++;
        break;
      case "--java":
        options.java = readValue(args, i, arg);
        i++;
        break;
      case "--heap":
        options.heap = readValue(args, i, arg);
        i++;
        break;
      case "--allow-downloads":
        options.allowDownloads = true;
        break;
      case "--build-current":
        options.buildCurrent = true;
        break;
      case "--skip-build":
        options.skipBuild = true;
        options.buildPlugin = false;
        options.buildCurrent = false;
        break;
      case "--keep-run-dirs":
        options.keepRunDirs = true;
        break;
      case "--use-mcc":
        options.useMcc = true;
        break;
      case "--mcc-path":
        options.mccPath = readValue(args, i, arg);
        i++;
        break;
      case "--skip-mcc-download":
        options.skipMccDownload = true;
        break;
      case "--prepare-only":
        options.prepareOnly = true;
        break;
      case "--fail-on-error":
        options.failOnError = true;
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
  if (options.repeat !== null && (!Number.isInteger(options.repeat) || options.repeat < 1)) {
    throw new Error("--repeat must be a positive integer");
  }
}

function readValue(args, index, name) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

function splitList(value) {
  return value.split(",").map(part => part.trim()).filter(Boolean);
}

async function main() {
  parseArgs(process.argv.slice(2));
  const config = await loadConfig(options.config);
  const suite = config.suites?.[options.suite];
  if (!suite) {
    throw new Error(`Unknown suite '${options.suite}'. Available: ${Object.keys(config.suites ?? {}).join(", ")}`);
  }

  const root = options.root;
  const resultDir = join(root, "results", timestamp());
  const jarDir = join(root, "jars");
  const javaBin = options.java ?? config.java ?? "java";
  const heap = options.heap ?? config.heap ?? "8G";
  const rconPassword = config.rconPassword ?? "codex-benchmark";
  const repeat = options.repeat ?? suite.repeat ?? 1;
  const variables = {
    ...(suite.variables ?? {}),
    repeat,
  };

  await fs.mkdir(resultDir, { recursive: true });
  await fs.writeFile(join(resultDir, "effective-config.json"), JSON.stringify({ options, config, suite: options.suite }, null, 2), "utf8");

  if (!options.skipBuild) {
    await runBuilds(config);
  }

  const plugins = await preparePlugins(config.plugins ?? [], resultDir);
  const engines = await prepareEngines(config.engines ?? [], jarDir);
  const mccSupport = await prepareMccSupport(config, root, resultDir);
  const selectedEngines = filterByName(engines, options.engines, "engine");
  const selectedScenarios = filterByName(config.scenarios ?? [], suite.scenarios ?? [], "scenario");
  const finalScenarios = filterByName(selectedScenarios, options.scenarios, "scenario");

  if (selectedEngines.length === 0) {
    throw new Error("No runnable engines selected. Build/provide jars, or pass --allow-downloads for configured downloads.");
  }
  if (finalScenarios.length === 0) {
    throw new Error("No scenarios selected.");
  }

  console.log(`Benchmark root: ${root}`);
  console.log(`Result dir: ${resultDir}`);
  console.log(`Minecraft: ${config.minecraftVersion ?? "unknown"}`);
  console.log(`Suite: ${options.suite}; repeat=${repeat}`);
  console.log(`Engines: ${selectedEngines.map(engine => engine.name).join(", ")}`);
  console.log(`Scenarios: ${finalScenarios.map(scenario => scenario.name).join(", ")}`);

  if (options.prepareOnly) {
    console.log("prepare-only complete.");
    return;
  }

  const rows = [];
  let runIndex = 0;
  for (let repeatIndex = 1; repeatIndex <= repeat; repeatIndex++) {
    for (let engineIndex = 0; engineIndex < selectedEngines.length; engineIndex++) {
      const engine = selectedEngines[engineIndex];
      for (const scenario of finalScenarios) {
        runIndex++;
        const run = await runScenario({
          root,
          resultDir,
          javaBin,
          heap,
          rconPassword,
          config,
          engine,
          engineIndex,
          scenario,
          variables,
          repeatIndex,
          runIndex,
          plugins,
          mccSupport,
        });
        rows.push(run);
        await writeOutputs(resultDir, rows);
      }
    }
  }

  await writeOutputs(resultDir, rows);
  const failures = rows.filter(row => !row.survival || row.highSignalCount > 0 || row.metricCommandErrors > 0);
  console.log(`Benchmark complete: ${join(resultDir, "benchmark-summary.md")}`);
  if (failures.length > 0) {
    console.log(`Runs with failures/high-signal logs: ${failures.length}/${rows.length}`);
    if (options.failOnError) {
      process.exitCode = 1;
    }
  }
}

async function loadConfig(configPath) {
  if (!existsSync(configPath)) {
    return DEFAULT_CONFIG;
  }
  const userConfig = JSON.parse(await fs.readFile(configPath, "utf8"));
  return mergeConfig(DEFAULT_CONFIG, userConfig);
}

function mergeConfig(base, override) {
  if (Array.isArray(base) || Array.isArray(override)) {
    return override ?? base;
  }
  if (!isPlainObject(base) || !isPlainObject(override)) {
    return override ?? base;
  }
  const out = { ...base };
  for (const [key, value] of Object.entries(override)) {
    out[key] = mergeConfig(base[key], value);
  }
  return out;
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && Object.getPrototypeOf(value) === Object.prototype;
}

async function runBuilds(config) {
  for (const plugin of config.plugins ?? []) {
    if (plugin.build && plugin.required !== false && options.buildPlugin) {
      await runLogged(`build plugin ${plugin.name ?? plugin.path}`, plugin.build, repoRoot, null);
    }
  }
  if (options.buildCurrent) {
    for (const engine of config.engines ?? []) {
      if (engine.build) {
        await runLogged(`build engine ${engine.name}`, engine.build, repoRoot, null);
      }
    }
  }
}

async function preparePlugins(plugins, resultDir) {
  const prepared = [];
  for (const plugin of plugins) {
    const pluginPath = resolvePath(plugin.path);
    if (!existsSync(pluginPath)) {
      if (plugin.required === false) {
        continue;
      }
      throw new Error(`Missing plugin jar: ${pluginPath}. Build it or pass --build-current/without --skip-build.`);
    }
    prepared.push({ ...plugin, path: pluginPath });
  }
  await fs.writeFile(join(resultDir, "plugins.json"), JSON.stringify(prepared, null, 2), "utf8");
  return prepared;
}

async function prepareEngines(engines, jarDir) {
  const prepared = [];
  await fs.mkdir(jarDir, { recursive: true });
  for (const engine of engines) {
    let jarPath = resolvePath(engine.jar, jarDir);
    if (!existsSync(jarPath) && engine.downloadUrl && options.allowDownloads) {
      await download(engine.downloadUrl, jarPath);
    }
    if (!existsSync(jarPath)) {
      console.warn(`Skipping ${engine.name}: missing jar ${jarPath}${engine.downloadUrl ? " (pass --allow-downloads to fetch)" : ""}`);
      continue;
    }
    prepared.push({ ...engine, jarPath });
  }
  return prepared;
}

async function prepareMccSupport(config, root, resultDir) {
  const mccConfig = config.mcc ?? {};
  const enabled = options.useMcc || mccConfig.enabled === true;
  if (!enabled) {
    await fs.writeFile(join(resultDir, "mcc.json"), JSON.stringify({ enabled: false }, null, 2), "utf8");
    return null;
  }

  const cacheDir = resolvePathMaybe(mccConfig.cacheDir ?? join(root, "mcc-cache"), root);
  const executable = await installMccExecutable(cacheDir);
  const support = {
    enabled: true,
    executable,
    cacheDir,
    botNamePrefix: mccConfig.botNamePrefix ?? "benchbot",
    joinTimeoutSec: Number(mccConfig.joinTimeoutSec ?? 140),
    startDelayMs: Number(mccConfig.startDelayMs ?? 2000),
    platformRadius: Number(mccConfig.platformRadius ?? 24),
    platformAirHeight: Number(mccConfig.platformAirHeight ?? 8),
  };
  await fs.writeFile(join(resultDir, "mcc.json"), JSON.stringify(support, null, 2), "utf8");
  return support;
}

function resolvePathMaybe(pathValue, baseRoot) {
  if (!pathValue) {
    return baseRoot;
  }
  return pathValue.startsWith("/") ? pathValue : resolve(repoRoot, pathValue);
}

async function installMccExecutable(cacheDir) {
  if (options.mccPath) {
    const resolved = resolve(options.mccPath);
    await fs.access(resolved);
    await fs.chmod(resolved, 0o755).catch(() => {});
    return resolved;
  }

  const mccDir = join(cacheDir, "mcc");
  await fs.mkdir(mccDir, { recursive: true });
  const existing = (await fs.readdir(mccDir).catch(() => []))
    .filter(name => name.startsWith("MinecraftClient") && !name.endsWith(".txt"))
    .map(name => join(mccDir, name))
    .filter(file => existsSync(file))
    .sort((a, b) => statMtimeMs(b) - statMtimeMs(a))[0];
  if (existing) {
    await fs.chmod(existing, 0o755).catch(() => {});
    return existing;
  }
  if (options.skipMccDownload) {
    throw new Error(`MCC executable is missing in ${mccDir}, and --skip-mcc-download was passed.`);
  }

  const rid = mccRid();
  const release = await fetch("https://api.github.com/repos/MCCTeam/Minecraft-Console-Client/releases/latest", {
    headers: { "User-Agent": "ShreddedPaper-Benchmark" },
  }).then(response => {
    if (!response.ok) {
      throw new Error(`GitHub release lookup failed: ${response.status}`);
    }
    return response.json();
  });
  const asset = release.assets.find(candidate => candidate.name.endsWith(rid));
  if (!asset) {
    throw new Error(`Could not find MCC asset ending with ${rid} in release ${release.tag_name}`);
  }
  const target = join(mccDir, asset.name);
  console.log(`Downloading MCC ${release.tag_name}: ${asset.name}`);
  await downloadBinary(asset.browser_download_url, target);
  await fs.chmod(target, 0o755);
  return target;
}

function statMtimeMs(file) {
  try {
    return existsSync(file) ? Number(statSync(file).mtimeMs) : 0;
  } catch {
    return 0;
  }
}

function mccRid() {
  if (process.platform === "darwin") {
    return process.arch === "arm64" ? "osx-arm64" : "osx-x64";
  }
  if (process.platform === "linux") {
    if (process.arch === "arm64") {
      return "linux-arm64";
    }
    if (process.arch === "arm") {
      return "linux-arm";
    }
    return "linux-x64";
  }
  throw new Error(`MCC benchmark support is only wired for macOS/Linux. Current platform: ${process.platform}/${process.arch}`);
}

async function downloadBinary(url, outFile) {
  await fs.mkdir(dirname(outFile), { recursive: true });
  const response = await fetch(url, { headers: { "User-Agent": "ShreddedPaper-Benchmark" } });
  if (!response.ok) {
    throw new Error(`Download failed ${response.status}: ${url}`);
  }
  const file = createWriteStream(outFile);
  await pipeline(Readable.fromWeb(response.body), file);
  await fs.writeFile(`${outFile}.download-url.txt`, `${url}\n`, "utf8");
}

function resolvePath(pathValue, jarDir = null) {
  if (!pathValue) {
    throw new Error("Missing path value");
  }
  if (pathValue.startsWith("jars/") && jarDir) {
    return resolve(jarDir, pathValue.slice("jars/".length));
  }
  return resolve(repoRoot, pathValue);
}

async function download(url, outFile) {
  await fs.mkdir(dirname(outFile), { recursive: true });
  console.log(`Downloading ${url}`);
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Download failed ${response.status}: ${url}`);
  }
  const file = createWriteStream(outFile);
  await pipeline(Readable.fromWeb(response.body), file);
}

function filterByName(items, filters, label) {
  if (!filters || filters.length === 0) {
    return items;
  }
  const wanted = new Set(filters.map(name => name.toLowerCase()));
  const out = items.filter(item => wanted.has(item.name.toLowerCase()));
  const found = new Set(out.map(item => item.name.toLowerCase()));
  const missing = [...wanted].filter(name => !found.has(name));
  if (missing.length > 0) {
    console.warn(`Unknown ${label} filter(s): ${missing.join(", ")}`);
  }
  return out;
}

async function runScenario(ctx) {
  const runName = `${pad(ctx.runIndex, 4)}-${ctx.engine.name}-${ctx.scenario.name}-r${ctx.repeatIndex}`;
  const runDir = join(ctx.root, "runs", runName);
  const artifactDir = join(ctx.resultDir, ctx.engine.name, ctx.scenario.name, `repeat-${pad(ctx.repeatIndex, 2)}`);
  const serverPort = Number(ctx.config.ports?.serverBase ?? 26000) + ctx.engineIndex;
  const rconPort = Number(ctx.config.ports?.rconBase ?? 36000) + ctx.engineIndex;
  const startedAt = Date.now();
  let child = null;
  let survival = true;
  let cleanShutdown = false;
  let killed = false;
  let serverLogSegment = "";
  let highSignalMatches = [];
  let metricCommandErrors = 0;
  let tpsResponses = [];
  let msptResponses = [];
  let regionResponses = [];
  let rssSamplesMb = [];
  let mccProcesses = [];
  let mccBots = [];

  await fs.rm(runDir, { recursive: true, force: true });
  await fs.mkdir(artifactDir, { recursive: true });
  await prepareServerDir(ctx, runDir, serverPort, rconPort);

  try {
    child = await startServer(ctx, runDir, artifactDir);
    await waitForReady(runDir, rconPort, child);
    await initializeServer(ctx, rconPort, artifactDir);
    const mccSession = await startScenarioMcc(ctx, artifactDir, serverPort, rconPort);
    mccProcesses = mccSession.processes;
    mccBots = mccSession.bots;
    let logState = await readLogSegment(join(runDir, "logs", "latest.log"), 0);
    let logOffset = logState.offset;

    await sendBenchmarkCommand(rconPort, ctx.rconPassword, "rlt cleanup", join(artifactDir, "rcon.log"));
    await sleep(1000);

    const commands = scenarioCommands(ctx.scenario, ctx.variables);
    for (const command of commands) {
      await sendBenchmarkCommand(rconPort, ctx.rconPassword, command.command, join(artifactDir, "rcon.log"), command.timeoutMs);
      await sleep(command.pauseMs);
    }

    const durationSec = Number(renderValue(ctx.scenario.durationSec ?? ctx.variables.durationMedium ?? 60, ctx.variables));
    const pollEverySec = Number(renderValue(ctx.scenario.pollEverySec ?? 10, ctx.variables));
    const deadline = Date.now() + durationSec * 1000;
    await sleep(Number(ctx.scenario.initialPollDelayMs ?? 5000));
    while (Date.now() < deadline) {
      if (child.exitCode !== null) {
        survival = false;
        break;
      }
      tpsResponses.push(await sendBenchmarkCommand(rconPort, ctx.rconPassword, "tps", join(artifactDir, "rcon.log")));
      msptResponses.push(await sendBenchmarkCommand(rconPort, ctx.rconPassword, "mspt", join(artifactDir, "rcon.log")));
      if (ctx.engine.regionCommand) {
        regionResponses.push(await sendBenchmarkCommand(rconPort, ctx.rconPassword, "region top", join(artifactDir, "rcon.log")));
      }
      const rss = await readRssMb(child.pid);
      if (rss !== null) {
        rssSamplesMb.push(rss);
      }
      await sleep(pollEverySec * 1000);
    }

    const status = await sendBenchmarkCommand(rconPort, ctx.rconPassword, "rlt status", join(artifactDir, "rcon.log"));
    if (/Unknown or incomplete command|ERROR:/i.test(stripMinecraftColors(status))) {
      metricCommandErrors++;
    }
    await sendBenchmarkCommand(rconPort, ctx.rconPassword, "rlt cleanup", join(artifactDir, "rcon.log"));
    await sleep(1500);

    logState = await readLogSegment(join(runDir, "logs", "latest.log"), logOffset);
    serverLogSegment = logState.text;
    highSignalMatches = scanText(serverLogSegment, join(runDir, "logs", "latest.log"));
  } catch (error) {
    survival = false;
    await fs.writeFile(join(artifactDir, "failure.txt"), error?.stack ?? String(error), "utf8");
  } finally {
    if (child) {
      const stopResult = await stopServer(child, rconPort, ctx.rconPassword);
      cleanShutdown = stopResult.clean;
      killed = stopResult.killed;
    }
    await stopMccProcesses(mccProcesses);
    await copyRunArtifacts(runDir, artifactDir);
    if (!options.keepRunDirs) {
      await fs.rm(runDir, { recursive: true, force: true });
    }
  }

  const parsedRlt = parseRltLog(serverLogSegment);
  const tps = parseTps(tpsResponses);
  const mspt = parseMspt(msptResponses, tpsResponses);
  const tickingMspt = parseTickingUnitMspt(tpsResponses, regionResponses, mspt);
  const serverMsptAvgSamples = mspt.map(item => item.avg).filter(value => Number.isFinite(value));
  const serverMsptMaxSamples = mspt.map(item => item.max).filter(value => Number.isFinite(value));
  const loadProbes = parsedRlt.probes.filter(probe => probeKind(probe) === "load");
  const controlProbes = parsedRlt.probes.filter(probe => probeKind(probe) === "control");
  const chunkPerSecValues = parsedRlt.chunkBatches
    .map(batch => batch.chunksPerSec)
    .filter(value => Number.isFinite(value));
  const crashReports = await listFilesIfExists(join(artifactDir, "crash-reports"));
  const portClosed = !(await isTcpReachable("127.0.0.1", rconPort, 250)) && !(await isTcpReachable("127.0.0.1", serverPort, 250));
  const row = {
    run: ctx.runIndex,
    repeat: ctx.repeatIndex,
    engine: ctx.engine.name,
    scenario: ctx.scenario.name,
    survival,
    cleanShutdown,
    killed,
    portClosed,
    durationMs: Date.now() - startedAt,
    tpsAvg: average(tps),
    tpsMin: min(tps),
    tpsMax: max(tps),
    tpsSamples: tps.length,
    tpsTrim5Avg: trimmedMean(tps, 5),
    tpsP05: percentile(tps, 5),
    tpsP95: percentile(tps, 95),
    msptAvg: average(serverMsptAvgSamples),
    msptMax: max(serverMsptMaxSamples),
    serverMsptSamples: serverMsptAvgSamples.length,
    serverMsptMin: min(serverMsptAvgSamples),
    serverMsptMax: max(serverMsptAvgSamples),
    serverMsptTrim5Avg: trimmedMean(serverMsptAvgSamples, 5),
    serverMsptP95: percentile(serverMsptAvgSamples, 95),
    serverMsptP99: percentile(serverMsptAvgSamples, 99),
    tickMsptMin: min(tickingMspt.values),
    tickMsptAvg: average(tickingMspt.values),
    tickMsptMax: max(tickingMspt.values),
    tickMsptTrim5Avg: trimmedMean(tickingMspt.values, 5),
    tickMsptP95: percentile(tickingMspt.values, 95),
    tickMsptP99: percentile(tickingMspt.values, 99),
    tickMsptSamples: tickingMspt.values.length,
    tickMsptSource: tickingMspt.source,
    loadProbeAvgLagMs: average(loadProbes.map(item => item.avgLagMs)),
    loadProbeP95LagMs: max(loadProbes.map(item => item.p95LagMs)),
    loadProbeP99LagMs: max(loadProbes.map(item => item.p99LagMs)),
    loadProbeMaxLagMs: max(loadProbes.map(item => item.maxLagMs)),
    loadProbeSamples: sum(loadProbes.map(item => item.samples)),
    controlProbeAvgLagMs: average(controlProbes.map(item => item.avgLagMs)),
    controlProbeP95LagMs: max(controlProbes.map(item => item.p95LagMs)),
    controlProbeP99LagMs: max(controlProbes.map(item => item.p99LagMs)),
    controlProbeMaxLagMs: max(controlProbes.map(item => item.maxLagMs)),
    controlProbeSamples: sum(controlProbes.map(item => item.samples)),
    chunkSuccess: sum(parsedRlt.chunkBatches.map(item => item.success)),
    chunkFailure: sum(parsedRlt.chunkBatches.map(item => item.failure)),
    chunkPerSecMedian: median(chunkPerSecValues),
    chunkPerSecSum: sum(chunkPerSecValues),
    probeCount: parsedRlt.probes.length,
    chunkBatchCount: parsedRlt.chunkBatches.length,
    rssMbMax: max(rssSamplesMb),
    highSignalCount: highSignalMatches.length,
    metricCommandErrors,
    crashReports: crashReports.length,
    artifactDir,
  };
  await fs.writeFile(join(artifactDir, "parsed-run.json"), JSON.stringify({
    row,
    rlt: parsedRlt,
    tpsResponses,
    msptResponses,
    regionResponses,
    mccBots: mccBots.map(bot => ({ name: bot.name, role: bot.role, x: bot.x, y: bot.y, z: bot.z })),
    tickingMspt,
    highSignalMatches,
  }, null, 2), "utf8");
  if (highSignalMatches.length > 0) {
    await fs.writeFile(join(artifactDir, "high-signal.log"), formatMatches(highSignalMatches, 200), "utf8");
  }
  console.log(`${ctx.engine.name}/${ctx.scenario.name}/r${ctx.repeatIndex}: survival=${survival} controlP99=${formatNumber(row.controlProbeP99LagMs)} loadP99=${formatNumber(row.loadProbeP99LagMs)} tickMin=${formatNumber(row.tickMsptMin)} tickTrim5=${formatNumber(row.tickMsptTrim5Avg)} tickP99=${formatNumber(row.tickMsptP99)} tickMax=${formatNumber(row.tickMsptMax)} samples=${row.tickMsptSamples} chunk/s=${formatNumber(row.chunkPerSecMedian)} highSignal=${row.highSignalCount}`);
  return row;
}

async function prepareServerDir(ctx, runDir, serverPort, rconPort) {
  assertInside(runDir, options.root);
  await fs.mkdir(join(runDir, "plugins"), { recursive: true });
  await fs.copyFile(ctx.engine.jarPath, join(runDir, "server.jar"));
  for (const plugin of ctx.plugins) {
    await fs.copyFile(plugin.path, join(runDir, "plugins", `${plugin.name ?? "plugin"}.jar`));
  }
  await fs.writeFile(join(runDir, "eula.txt"), "eula=true\n", "utf8");
  await fs.writeFile(join(runDir, "server.properties"), serverProperties(ctx.config.server ?? {}, serverPort, rconPort, ctx.rconPassword), "utf8");
}

function serverProperties(server, serverPort, rconPort, rconPassword) {
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
force-gamemode=false
function-permission-level=4
gamemode=survival
generate-structures=false
hardcore=false
level-name=${server.levelName ?? "world"}
level-seed=${server.seed ?? "shreddedpaper-benchmark"}
max-players=${server.maxPlayers ?? 400}
max-tick-time=-1
motd=Codex benchmark
network-compression-threshold=256
online-mode=false
op-permission-level=4
prevent-proxy-connections=false
pvp=true
query.port=${serverPort}
rcon.password=${rconPassword}
rcon.port=${rconPort}
server-ip=127.0.0.1
server-port=${serverPort}
simulation-distance=${server.simulationDistance ?? 7}
spawn-animals=true
spawn-monsters=true
spawn-npcs=true
spawn-protection=0
sync-chunk-writes=false
use-native-transport=true
view-distance=${server.viewDistance ?? 12}
white-list=false
`;
}

async function startServer(ctx, runDir, artifactDir) {
  const stdoutPath = join(artifactDir, "server-stdout.log");
  const stderrPath = join(artifactDir, "server-stderr.log");
  const stdout = createWriteStream(stdoutPath, { flags: "a" });
  const stderr = createWriteStream(stderrPath, { flags: "a" });
  const args = [
    "-Xms" + ctx.heap,
    "-Xmx" + ctx.heap,
    "-Dfile.encoding=UTF-8",
    "-Duser.timezone=UTC",
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:+UseStringDeduplication",
    "-XX:+PerfDisableSharedMem",
    "-jar",
    "server.jar",
    "nogui",
  ];
  await fs.writeFile(join(artifactDir, "java-command.txt"), `${ctx.javaBin} ${args.map(quoteArg).join(" ")}\n`, "utf8");
  const child = spawn(ctx.javaBin, args, {
    cwd: runDir,
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.pipe(stdout);
  child.stderr.pipe(stderr);
  child.once("exit", (code, signal) => {
    stdout.write(`\n[process exited code=${code} signal=${signal}]\n`);
    stderr.end();
    stdout.end();
  });
  return child;
}

async function waitForReady(runDir, rconPort, child) {
  const latestLog = join(runDir, "logs", "latest.log");
  const deadline = Date.now() + 240_000;
  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`Server exited before ready. exitCode=${child.exitCode}`);
    }
    if (existsSync(latestLog)) {
      const text = await fs.readFile(latestLog, "utf8");
      if (/Done \([^)]+\)! For help, type "help"/.test(text) && await testRcon(rconPort)) {
        return;
      }
    }
    await sleep(500);
  }
  throw new Error("Timed out waiting for server readiness");
}

async function initializeServer(ctx, rconPort, artifactDir) {
  const commands = [
    "gamerule minecraft:do_daylight_cycle false",
    "gamerule minecraft:do_weather_cycle false",
    "gamerule minecraft:mob_griefing false",
    "gamerule minecraft:do_mob_spawning false",
    "time set day",
    "weather clear",
    "forceload add -64 -64 64 64",
    "forceload add 8128 -64 8256 64",
  ];
  const rconLog = join(artifactDir, "init-rcon.log");
  for (const command of commands) {
    await sendBenchmarkCommand(rconPort, ctx.rconPassword, command, rconLog);
  }
  await sleep(5000);
}

async function startScenarioMcc(ctx, artifactDir, serverPort, rconPort) {
  const anchors = scenarioMccAnchors(ctx.scenario, ctx.variables);
  if (!ctx.mccSupport || anchors.length === 0) {
    return { bots: [], processes: [] };
  }

  const botRoot = join(artifactDir, "mcc-bots");
  await fs.mkdir(botRoot, { recursive: true });
  const bots = await writeMccBotFiles(ctx, botRoot, serverPort, anchors);
  const processes = [];
  for (const bot of bots) {
    processes.push(await startMccBot(bot));
    await sleep(ctx.mccSupport.startDelayMs);
  }

  try {
    await waitForMccPlayers(rconPort, ctx.rconPassword, bots, join(artifactDir, "mcc-rcon.log"), ctx.mccSupport.joinTimeoutSec);
    await placeMccAnchors(ctx, rconPort, artifactDir, bots);
    return { bots, processes };
  } catch (error) {
    await stopMccProcesses(processes);
    throw error;
  }
}

function scenarioMccAnchors(scenario, variables) {
  return (scenario.mccAnchors ?? []).map((anchor, index) => ({
    role: anchor.role ?? `anchor-${index + 1}`,
    x: Number(renderValue(anchor.x, variables)),
    y: Number(renderValue(anchor.y, variables)),
    z: Number(renderValue(anchor.z, variables)),
  })).filter(anchor => Number.isFinite(anchor.x) && Number.isFinite(anchor.y) && Number.isFinite(anchor.z));
}

async function writeMccBotFiles(ctx, botRoot, serverPort, anchors) {
  const bots = [];
  for (let index = 0; index < anchors.length; index++) {
    const anchor = anchors[index];
    const safeRole = anchor.role.replace(/[^A-Za-z0-9_]/g, "").slice(0, 6) || "bot";
    const name = `${ctx.mccSupport.botNamePrefix}${String(index + 1).padStart(2, "0")}`.slice(0, 16);
    const dir = join(botRoot, `${String(index + 1).padStart(2, "0")}-${safeRole}-${name}`);
    await fs.mkdir(dir, { recursive: true });
    const configPath = join(dir, "MinecraftClient.ini");
    await fs.writeFile(configPath, mccConfig(name, serverPort, join(dir, "mcc-console.log")), "utf8");
    bots.push({
      ...anchor,
      name,
      dir,
      configPath,
      executable: ctx.mccSupport.executable,
    });
  }
  return bots;
}

function mccConfig(name, serverPort, logFile) {
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

async function startMccBot(bot) {
  const stdout = createWriteStream(join(bot.dir, "stdout.log"), { flags: "a" });
  const stderr = createWriteStream(join(bot.dir, "stderr.log"), { flags: "a" });
  await fs.appendFile(join(bot.dir, "stdout.log"), `\n### MCC start ${new Date().toISOString()} ###\n`, "utf8");
  const child = spawn(bot.executable, [bot.configPath], {
    cwd: bot.dir,
    detached: true,
    stdio: ["ignore", "pipe", "pipe"],
    env: process.env,
  });
  child.stdout.pipe(stdout);
  child.stderr.pipe(stderr);
  child.on("exit", (code, signal) => {
    stdout.write(`\n[process exited code=${code} signal=${signal}]\n`);
    stdout.end();
    stderr.end();
  });
  return child;
}

async function waitForMccPlayers(rconPort, rconPassword, bots, rconLog, timeoutSec) {
  const deadline = Date.now() + timeoutSec * 1000;
  const wanted = new Set(bots.map(bot => bot.name));
  while (Date.now() < deadline) {
    const response = await sendBenchmarkCommand(rconPort, rconPassword, "list", rconLog, 5000);
    const joined = bots.filter(bot => response.includes(bot.name)).map(bot => bot.name);
    if (joined.length === wanted.size) {
      return;
    }
    await sleep(1000);
  }
  throw new Error(`Timed out waiting for MCC fake players: ${[...wanted].join(", ")}`);
}

async function placeMccAnchors(ctx, rconPort, artifactDir, bots) {
  const rconLog = join(artifactDir, "mcc-rcon.log");
  for (const bot of bots) {
    const radius = ctx.mccSupport.platformRadius;
    const airHeight = ctx.mccSupport.platformAirHeight;
    const minX = Math.floor(bot.x - radius);
    const maxX = Math.floor(bot.x + radius);
    const minZ = Math.floor(bot.z - radius);
    const maxZ = Math.floor(bot.z + radius);
    const y = Math.floor(bot.y);
    await sendCheckedBenchmarkCommand(rconPort, ctx.rconPassword, `forceload add ${minX} ${minZ} ${maxX} ${maxZ}`, rconLog, 45_000);
    await sleep(2000);

    const platformCommands = [
      ...fillVolumeCommands(minX, y - 1, minZ, maxX, y - 1, maxZ, "minecraft:stone"),
      ...fillVolumeCommands(minX, y, minZ, maxX, y + airHeight, maxZ, "minecraft:air"),
    ];
    for (const command of platformCommands) {
      await sendRetriedCheckedBenchmarkCommand(rconPort, ctx.rconPassword, command, rconLog, 45_000, {
        retries: 45,
        delayMs: 1000,
        retryPattern: /position is not loaded|that position is not loaded/i,
      });
    }

    const playerCommands = [
      `gamemode creative ${bot.name}`,
      `effect give ${bot.name} minecraft:saturation infinite 1 true`,
      `effect give ${bot.name} minecraft:resistance infinite 4 true`,
      `tp ${bot.name} ${bot.x} ${bot.y} ${bot.z}`,
      `rlt playercheck ${bot.name} present world`,
    ];
    for (const command of playerCommands) {
      await sendCheckedBenchmarkCommand(rconPort, ctx.rconPassword, command, rconLog, 45_000);
    }
  }
  await sleep(3000);
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

function scenarioCommands(scenario, variables) {
  return (scenario.commands ?? []).map(entry => {
    if (typeof entry === "string") {
      return {
        command: renderTemplate(entry, variables),
        pauseMs: 500,
        timeoutMs: 45_000,
      };
    }
    return {
      command: renderTemplate(entry.command, variables),
      pauseMs: Number(entry.pauseMs ?? 500),
      timeoutMs: Number(entry.timeoutMs ?? 45_000),
    };
  });
}

async function sendBenchmarkCommand(rconPort, rconPassword, command, rconLog, timeoutMs = 15_000) {
  let response;
  try {
    response = await sendRconCommand(rconPort, rconPassword, command, timeoutMs);
  } catch (error) {
    response = `ERROR: ${error.message}`;
  }
  await fs.appendFile(rconLog, `> ${command}\n${response}\n\n`, "utf8");
  return response;
}

async function sendCheckedBenchmarkCommand(rconPort, rconPassword, command, rconLog, timeoutMs = 15_000) {
  const response = await sendBenchmarkCommand(rconPort, rconPassword, command, rconLog, timeoutMs);
  const clean = stripMinecraftColors(response);
  if (/ERROR:|Unknown or incomplete command|Incorrect argument for command|No player was found|Player not found|RLT playercheck failed|Too many blocks|That position is not loaded/i.test(clean)) {
    throw new Error(`RCON command failed: ${command}\n${clean.trim()}`);
  }
  return response;
}

async function sendRetriedCheckedBenchmarkCommand(rconPort, rconPassword, command, rconLog, timeoutMs, retryOptions = {}) {
  const retries = Number(retryOptions.retries ?? 10);
  const delayMs = Number(retryOptions.delayMs ?? 1000);
  const retryPattern = retryOptions.retryPattern ?? /position is not loaded/i;
  let lastError = null;
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      return await sendCheckedBenchmarkCommand(rconPort, rconPassword, command, rconLog, timeoutMs);
    } catch (error) {
      lastError = error;
      if (attempt >= retries || !retryPattern.test(String(error?.message ?? error))) {
        throw error;
      }
      await fs.appendFile(rconLog, `retrying after transient anchor setup failure: attempt=${attempt + 1}/${retries}, delayMs=${delayMs}\n\n`, "utf8");
      await sleep(delayMs);
    }
  }
  throw lastError;
}

async function stopServer(child, rconPort, rconPassword) {
  if (child.exitCode !== null) {
    return { clean: false, killed: false };
  }
  try {
    await sendRconCommand(rconPort, rconPassword, "stop", 5000);
  } catch {
    // The process may already be exiting.
  }
  const clean = await waitForExit(child, 90_000);
  if (clean) {
    return { clean: true, killed: false };
  }
  child.kill("SIGKILL");
  await waitForExit(child, 10_000);
  return { clean: false, killed: true };
}

async function stopMccProcesses(processes) {
  for (const child of processes) {
    await stopProcessTree(child);
  }
}

async function stopProcessTree(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) {
    return;
  }
  try {
    process.kill(-child.pid, "SIGTERM");
  } catch {
    try { child.kill("SIGTERM"); } catch {}
  }
  if (!await waitForExit(child, 5000)) {
    try {
      process.kill(-child.pid, "SIGKILL");
    } catch {
      try { child.kill("SIGKILL"); } catch {}
    }
    await waitForExit(child, 5000);
  }
}

function sendRconCommand(port, password, command, timeoutMs) {
  return new Promise((resolvePromise, reject) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    let buffer = Buffer.alloc(0);
    let stage = "auth";
    const timer = setTimeout(() => {
      socket.destroy();
      reject(new Error(`RCON timeout after ${timeoutMs}ms on ${port}`));
    }, timeoutMs);

    socket.on("connect", () => {
      socket.write(makeRconPacket(1, 3, password));
    });
    socket.on("data", (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      for (;;) {
        const packet = tryReadPacket();
        if (!packet) {
          return;
        }
        if (stage === "auth") {
          if (packet.id === -1) {
            clearTimeout(timer);
            socket.destroy();
            reject(new Error(`RCON authentication failed on ${port}`));
            return;
          }
          stage = "command";
          socket.write(makeRconPacket(2, 2, command));
        } else {
          clearTimeout(timer);
          socket.end();
          resolvePromise(packet.payload);
          return;
        }
      }
    });
    socket.on("error", (error) => {
      clearTimeout(timer);
      reject(error);
    });

    function tryReadPacket() {
      if (buffer.length < 4) {
        return null;
      }
      const length = buffer.readInt32LE(0);
      if (buffer.length < 4 + length) {
        return null;
      }
      const data = buffer.subarray(4, 4 + length);
      buffer = buffer.subarray(4 + length);
      return {
        id: data.readInt32LE(0),
        type: data.readInt32LE(4),
        payload: data.subarray(8, Math.max(8, data.length - 2)).toString("utf8"),
      };
    }
  });
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

async function testRcon(port) {
  try {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    await new Promise((resolvePromise, reject) => {
      const timer = setTimeout(() => {
        socket.destroy();
        reject(new Error("timeout"));
      }, 1500);
      socket.once("connect", () => {
        clearTimeout(timer);
        socket.end();
        resolvePromise();
      });
      socket.once("error", reject);
    });
    return true;
  } catch {
    return false;
  }
}

async function isTcpReachable(host, port, timeoutMs) {
  try {
    const socket = net.createConnection({ host, port });
    await new Promise((resolvePromise, reject) => {
      const timer = setTimeout(() => {
        socket.destroy();
        reject(new Error("timeout"));
      }, timeoutMs);
      socket.once("connect", () => {
        clearTimeout(timer);
        socket.end();
        resolvePromise();
      });
      socket.once("error", reject);
    });
    return true;
  } catch {
    return false;
  }
}

async function waitForExit(child, timeoutMs) {
  if (child.exitCode !== null) {
    return true;
  }
  return await new Promise(resolvePromise => {
    const timer = setTimeout(() => resolvePromise(false), timeoutMs);
    child.once("exit", () => {
      clearTimeout(timer);
      resolvePromise(true);
    });
  });
}

async function readLogSegment(logPath, offset) {
  if (!existsSync(logPath)) {
    return { text: "", offset };
  }
  const file = await fs.open(logPath, "r");
  try {
    const stat = await file.stat();
    const start = Math.min(offset, stat.size);
    const length = stat.size - start;
    if (length <= 0) {
      return { text: "", offset: stat.size };
    }
    const buffer = Buffer.alloc(length);
    await file.read(buffer, 0, length, start);
    return { text: buffer.toString("utf8"), offset: stat.size };
  } finally {
    await file.close();
  }
}

function parseRltLog(text) {
  const probes = [];
  const chunkBatches = [];
  const crossQueues = [];
  const syncLoads = [];
  for (const line of text.split(/\r?\n/)) {
    let match = line.match(/(?<label>.+) finished at chunk=(?<chunkX>-?\d+),(?<chunkZ>-?\d+): samples=(?<samples>\d+) periodTicks=(?<period>\d+) avgLagMs=(?<avg>-?[0-9.]+) p95LagMs=(?<p95>-?[0-9.]+)(?: p99LagMs=(?<p99>-?[0-9.]+))? maxLagMs=(?<max>-?[0-9.]+)/);
    if (match?.groups) {
      probes.push({
        label: match.groups.label.trim(),
        chunkX: Number(match.groups.chunkX),
        chunkZ: Number(match.groups.chunkZ),
        samples: Number(match.groups.samples),
        periodTicks: Number(match.groups.period),
        avgLagMs: Number(match.groups.avg),
        p95LagMs: Number(match.groups.p95),
        p99LagMs: match.groups.p99 === undefined ? Number(match.groups.p95) : Number(match.groups.p99),
        maxLagMs: Number(match.groups.max),
        raw: line,
      });
      continue;
    }
    match = line.match(/(?<label>.+) batch finished: centerChunk=(?<chunkX>-?\d+),(?<chunkZ>-?\d+) total=(?<total>\d+) success=(?<success>\d+) null=(?<null>\d+) failure=(?<failure>\d+) generate=(?<generate>true|false) urgent=(?<urgent>true|false) elapsedMs=(?<elapsed>[0-9.]+)/);
    if (match?.groups) {
      const elapsedMs = Number(match.groups.elapsed);
      const success = Number(match.groups.success);
      chunkBatches.push({
        label: match.groups.label.trim(),
        chunkX: Number(match.groups.chunkX),
        chunkZ: Number(match.groups.chunkZ),
        total: Number(match.groups.total),
        success,
        null: Number(match.groups.null),
        failure: Number(match.groups.failure),
        generate: match.groups.generate === "true",
        urgent: match.groups.urgent === "true",
        elapsedMs,
        chunksPerSec: elapsedMs > 0 ? success / (elapsedMs / 1000.0) : null,
        raw: line,
      });
      continue;
    }
    match = line.match(/Cross-region queue probe finished: .* tasks=(?<tasks>\d+) queued=(?<queued>\d+) rejected=(?<rejected>\d+) executed=(?<executed>\d+) failed=(?<failed>\d+) .* elapsedMs=(?<elapsed>[0-9.]+)/);
    if (match?.groups) {
      crossQueues.push(Object.fromEntries(Object.entries(match.groups).map(([key, value]) => [key, Number(value)])));
      continue;
    }
    match = line.match(/Sync-load guard probe finished: .* attempts=(?<attempts>\d+), guardRejections=(?<guardRejections>\d+), unexpectedSuccess=(?<unexpectedSuccess>\d+), unexpectedFailure=(?<unexpectedFailure>\d+)/);
    if (match?.groups) {
      syncLoads.push(Object.fromEntries(Object.entries(match.groups).map(([key, value]) => [key, Number(value)])));
    }
  }
  return { probes, chunkBatches, crossQueues, syncLoads };
}

function probeKind(probe) {
  const label = probe.label.toLowerCase();
  if (label.includes("control")) {
    return "control";
  }
  if (Math.abs(probe.chunkX) < 256) {
    return "load";
  }
  if (Math.abs(probe.chunkX) < 20_000) {
    return "control";
  }
  return "other";
}

function parseTps(responses) {
  const values = [];
  for (const response of responses) {
    const clean = stripMinecraftColors(response);
    let match = clean.match(/TPS from last 5s,\s*1m,\s*5m,\s*15m:\s*([0-9.]+)/i);
    if (!match) {
      match = clean.match(/TPS from last 1m,\s*5m,\s*15m:\s*([0-9.]+)/i);
    }
    if (!match) {
      match = clean.match(/Lowest Region TPS:\s*([0-9.]+)/i);
    }
    if (match) {
      values.push(Number(match[1]));
    }
  }
  return values;
}

function parseMspt(msptResponses, fallbackResponses) {
  const values = [];
  for (const response of msptResponses) {
    const clean = stripMinecraftColors(response);
    const match = clean.match(/([0-9]+(?:\.[0-9]+)?)\/([0-9]+(?:\.[0-9]+)?)\/([0-9]+(?:\.[0-9]+)?)/);
    if (match) {
      values.push({ avg: Number(match[1]), min: Number(match[2]), max: Number(match[3]) });
    }
  }
  if (values.length > 0) {
    return values;
  }
  for (const response of fallbackResponses) {
    const clean = stripMinecraftColors(response);
    const matches = [...clean.matchAll(/util at\s*([0-9]+(?:\.[0-9]+)?)\s*MSPT/gi)].map(match => Number(match[1]));
    if (matches.length > 0) {
      values.push({ avg: average(matches), min: min(matches), max: max(matches) });
    }
  }
  return values;
}

function parseTickingUnitMspt(tpsResponses, regionResponses, serverMsptValues) {
  const regionTopValues = parseOurRegionTopMspt(regionResponses);
  if (regionTopValues.length > 0) {
    return {
      source: "region-top",
      values: regionTopValues,
    };
  }

  const foliaValues = parseFoliaHealthMspt(tpsResponses);
  if (foliaValues.length > 0) {
    return {
      source: "folia-health",
      values: foliaValues,
    };
  }

  const serverValues = serverMsptValues
    .map(item => item.avg)
    .filter(value => Number.isFinite(value));
  return {
    source: serverValues.length > 0 ? "server-mspt" : "none",
    values: serverValues,
  };
}

function parseOurRegionTopMspt(regionResponses) {
  const values = [];
  for (const response of regionResponses) {
    const clean = stripMinecraftColors(response);
    for (const match of clean.matchAll(/MSPT=([0-9]+(?:\.[0-9]+)?)/g)) {
      values.push(Number(match[1]));
    }
  }
  return values;
}

function parseFoliaHealthMspt(tpsResponses) {
  const values = [];
  for (const response of tpsResponses) {
    const clean = stripMinecraftColors(response);
    for (const match of clean.matchAll(/util at\s*([0-9]+(?:\.[0-9]+)?)\s*MSPT/gi)) {
      values.push(Number(match[1]));
    }
  }
  return values;
}

function stripMinecraftColors(text) {
  return (text ?? "").replace(/§./g, "").replace(/\u001b\[[0-9;]*m/g, "");
}

function renderTemplate(text, variables) {
  return String(text).replace(/\$\{([A-Za-z0-9_.-]+)\}/g, (whole, key) => {
    if (!(key in variables)) {
      throw new Error(`Unknown benchmark variable ${key} in ${text}`);
    }
    return String(variables[key]);
  });
}

function renderValue(value, variables) {
  if (typeof value !== "string") {
    return value;
  }
  const rendered = renderTemplate(value, variables);
  return /^-?\d+(?:\.\d+)?$/.test(rendered) ? Number(rendered) : rendered;
}

async function runLogged(label, commandSpec, cwd, logPath) {
  const [commandRaw, ...argsRaw] = commandSpec;
  const command = normalizeCommand(commandRaw);
  const args = argsRaw.map(String);
  console.log(`[build] ${label}: ${command} ${args.map(quoteArg).join(" ")}`);
  await new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, {
      cwd,
      stdio: logPath ? ["ignore", "pipe", "pipe"] : "inherit",
      shell: process.platform === "win32" && command.endsWith(".bat"),
    });
    if (logPath) {
      const stream = createWriteStream(logPath, { flags: "a" });
      child.stdout.pipe(stream);
      child.stderr.pipe(stream);
      child.once("exit", () => stream.end());
    }
    child.on("error", reject);
    child.on("exit", (code, signal) => {
      if (code === 0) {
        resolvePromise();
      } else {
        reject(new Error(`${label} failed with ${signal ? `signal ${signal}` : `exit code ${code}`}`));
      }
    });
  });
}

function normalizeCommand(command) {
  if (command === "./gradlew" && process.platform === "win32") {
    return "gradlew.bat";
  }
  return command;
}

async function copyRunArtifacts(runDir, artifactDir) {
  for (const item of ["logs", "crash-reports", "console.out.log", "console.err.log", "server.properties"]) {
    const source = join(runDir, item);
    if (!existsSync(source)) {
      continue;
    }
    const target = join(artifactDir, item);
    await fs.cp(source, target, { recursive: true, force: true });
  }
}

async function listFilesIfExists(dir) {
  if (!existsSync(dir)) {
    return [];
  }
  const entries = await fs.readdir(dir, { withFileTypes: true });
  return entries.filter(entry => entry.isFile()).map(entry => entry.name);
}

async function readRssMb(pid) {
  if (!pid) {
    return null;
  }
  const command = process.platform === "win32"
    ? ["powershell.exe", "-NoProfile", "-Command", `(Get-Process -Id ${pid}).WorkingSet64`]
    : ["ps", "-o", "rss=", "-p", String(pid)];
  try {
    const output = await capture(command[0], command.slice(1), 3000);
    const value = Number(output.trim());
    if (!Number.isFinite(value)) {
      return null;
    }
    return process.platform === "win32" ? value / 1024 / 1024 : value / 1024;
  } catch {
    return null;
  }
}

function capture(command, args, timeoutMs) {
  return new Promise((resolvePromise, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    const timer = setTimeout(() => {
      child.kill("SIGKILL");
      reject(new Error(`Command timed out: ${command}`));
    }, timeoutMs);
    child.stdout.on("data", chunk => stdout += chunk);
    child.stderr.on("data", chunk => stderr += chunk);
    child.on("error", reject);
    child.on("exit", code => {
      clearTimeout(timer);
      if (code === 0) {
        resolvePromise(stdout);
      } else {
        reject(new Error(stderr || `${command} exited ${code}`));
      }
    });
  });
}

async function writeOutputs(resultDir, rows) {
  await fs.writeFile(join(resultDir, "benchmark-results.json"), JSON.stringify(rows, null, 2), "utf8");
  await fs.writeFile(join(resultDir, "benchmark-results.csv"), toCsv(rows), "utf8");
  await fs.writeFile(join(resultDir, "benchmark-summary.md"), toMarkdown(rows), "utf8");
}

function toCsv(rows) {
  const columns = [
    "run", "repeat", "engine", "scenario", "survival", "cleanShutdown", "killed", "portClosed",
    "tpsAvg", "tpsMin", "tpsMax", "tpsSamples", "tpsTrim5Avg", "tpsP05", "tpsP95",
    "msptAvg", "msptMax", "serverMsptSamples", "serverMsptMin", "serverMsptMax", "serverMsptTrim5Avg", "serverMsptP95", "serverMsptP99",
    "tickMsptMin", "tickMsptAvg", "tickMsptMax", "tickMsptTrim5Avg", "tickMsptP95", "tickMsptP99", "tickMsptSamples", "tickMsptSource",
    "loadProbeAvgLagMs", "loadProbeP95LagMs", "loadProbeP99LagMs", "loadProbeMaxLagMs", "loadProbeSamples",
    "controlProbeAvgLagMs", "controlProbeP95LagMs", "controlProbeP99LagMs", "controlProbeMaxLagMs", "controlProbeSamples",
    "chunkSuccess", "chunkFailure", "chunkPerSecMedian", "chunkPerSecSum",
    "probeCount", "chunkBatchCount", "rssMbMax", "highSignalCount", "metricCommandErrors", "crashReports", "artifactDir",
  ];
  return [
    columns.join(","),
    ...rows.map(row => columns.map(column => csvCell(row[column])).join(",")),
  ].join("\n") + "\n";
}

function csvCell(value) {
  if (value === null || value === undefined) {
    return "";
  }
  const text = String(value);
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function toMarkdown(rows) {
  const lines = [];
  lines.push(`# Benchmark Summary - ${new Date().toISOString()}`);
  lines.push("");
  lines.push(`Root: \`${options.root}\``);
  lines.push(`Suite: \`${options.suite}\``);
  lines.push("");
  lines.push("## Scenario Medians");
  lines.push("");
  lines.push("| Engine | Scenario | Runs | Survival | Control p99 med | Control max worst | Load p99 med | Tick MSPT min med | Tick MSPT trim5 med | Tick MSPT p95 med | Tick MSPT p99 med | Tick MSPT max worst | Tick samples med | Chunk/sec med | Server MSPT p99 med | RSS max | High signal |");
  lines.push("| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |");
  for (const group of groupRows(rows)) {
    lines.push(`| ${group.engine} | ${group.scenario} | ${group.rows.length} | ${group.rows.filter(row => row.survival).length}/${group.rows.length} | ${fmt(median(group.rows.map(row => row.controlProbeP99LagMs)))} | ${fmt(max(group.rows.map(row => row.controlProbeMaxLagMs)))} | ${fmt(median(group.rows.map(row => row.loadProbeP99LagMs)))} | ${fmt(median(group.rows.map(row => row.tickMsptMin)))} | ${fmt(median(group.rows.map(row => row.tickMsptTrim5Avg)))} | ${fmt(median(group.rows.map(row => row.tickMsptP95)))} | ${fmt(median(group.rows.map(row => row.tickMsptP99)))} | ${fmt(max(group.rows.map(row => row.tickMsptMax)))} | ${fmt(median(group.rows.map(row => row.tickMsptSamples)))} | ${fmt(median(group.rows.map(row => row.chunkPerSecMedian)))} | ${fmt(median(group.rows.map(row => row.serverMsptP99)))} | ${fmt(max(group.rows.map(row => row.rssMbMax)))} | ${sum(group.rows.map(row => row.highSignalCount))} |`);
  }
  lines.push("");
  lines.push("## Raw Runs");
  lines.push("");
  lines.push("| Run | Engine | Scenario | Survival | Shutdown | TPS avg | TPS min | TPS max | Tick MSPT min | Tick MSPT trim5 | Tick MSPT p95 | Tick MSPT p99 | Tick MSPT max | Samples | Server MSPT min | Server MSPT p99 | Server MSPT max | Control p99 | Load p99 | Chunk/sec | High signal | Artifacts |");
  lines.push("| ---: | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |");
  for (const row of rows) {
    lines.push(`| ${row.run} | ${row.engine} | ${row.scenario} | ${row.survival ? "yes" : "no"} | ${row.cleanShutdown ? "clean" : "dirty"} | ${fmt(row.tpsAvg)} | ${fmt(row.tpsMin)} | ${fmt(row.tpsMax)} | ${fmt(row.tickMsptMin)} | ${fmt(row.tickMsptTrim5Avg)} | ${fmt(row.tickMsptP95)} | ${fmt(row.tickMsptP99)} | ${fmt(row.tickMsptMax)} | ${row.tickMsptSamples ?? 0} | ${fmt(row.serverMsptMin)} | ${fmt(row.serverMsptP99)} | ${fmt(row.serverMsptMax)} | ${fmt(row.controlProbeP99LagMs)} | ${fmt(row.loadProbeP99LagMs)} | ${fmt(row.chunkPerSecMedian)} | ${row.highSignalCount} | \`${relative(repoRoot, row.artifactDir)}\` |`);
  }
  lines.push("");
  lines.push("Interpretation note: control p99 lag is the main isolation score. Tick MSPT is parsed from /region top for ShreddedPaper, Folia's health report for Folia, and server MSPT for single-threaded engines. trim5 excludes the lowest five and highest five samples in a run; it is n/a unless at least 10 samples remain after trimming. Min/max are intentionally shown beside p95/p99 so short lucky averages do not hide tails. Chunk/sec is intentionally shown separately because a server can be well-isolated but still weak at chunk throughput.");
  lines.push("");
  return lines.join("\n");
}

function groupRows(rows) {
  const map = new Map();
  for (const row of rows) {
    const key = `${row.engine}\0${row.scenario}`;
    if (!map.has(key)) {
      map.set(key, { engine: row.engine, scenario: row.scenario, rows: [] });
    }
    map.get(key).rows.push(row);
  }
  return [...map.values()].sort((a, b) => a.scenario.localeCompare(b.scenario) || a.engine.localeCompare(b.engine));
}

function average(values) {
  const filtered = cleanNumbers(values);
  return filtered.length === 0 ? null : sum(filtered) / filtered.length;
}

function trimmedMean(values, trimEachSide = 5, minRemaining = 10) {
  const filtered = cleanNumbers(values).sort((a, b) => a - b);
  if (filtered.length < trimEachSide * 2 + minRemaining) {
    return null;
  }
  return average(filtered.slice(trimEachSide, filtered.length - trimEachSide));
}

function percentile(values, p) {
  const filtered = cleanNumbers(values).sort((a, b) => a - b);
  if (filtered.length === 0) {
    return null;
  }
  if (filtered.length === 1) {
    return filtered[0];
  }
  const rank = (Math.max(0, Math.min(100, p)) / 100) * (filtered.length - 1);
  const lower = Math.floor(rank);
  const upper = Math.ceil(rank);
  if (lower === upper) {
    return filtered[lower];
  }
  const weight = rank - lower;
  return filtered[lower] * (1 - weight) + filtered[upper] * weight;
}

function median(values) {
  const filtered = cleanNumbers(values).sort((a, b) => a - b);
  if (filtered.length === 0) {
    return null;
  }
  const mid = Math.floor(filtered.length / 2);
  return filtered.length % 2 === 0 ? (filtered[mid - 1] + filtered[mid]) / 2 : filtered[mid];
}

function min(values) {
  const filtered = cleanNumbers(values);
  return filtered.length === 0 ? null : Math.min(...filtered);
}

function max(values) {
  const filtered = cleanNumbers(values);
  return filtered.length === 0 ? null : Math.max(...filtered);
}

function sum(values) {
  return cleanNumbers(values).reduce((acc, value) => acc + value, 0);
}

function cleanNumbers(values) {
  return values.filter(value => Number.isFinite(value));
}

function fmt(value) {
  return Number.isFinite(value) ? value.toFixed(2) : "n/a";
}

function formatNumber(value) {
  return Number.isFinite(value) ? value.toFixed(2) : "n/a";
}

function pad(value, width) {
  return String(value).padStart(width, "0");
}

function timestamp() {
  return new Date().toISOString().replaceAll(":", "").replace(/\.\d+Z$/, "Z");
}

function quoteArg(arg) {
  return /^[A-Za-z0-9_./:=+-]+$/.test(arg) ? arg : JSON.stringify(arg);
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function assertInside(target, root) {
  const rel = relative(resolve(root), resolve(target));
  if (rel === "" || (!rel.startsWith("..") && !rel.includes(`..${sep}`))) {
    return;
  }
  throw new Error(`Refusing to operate outside benchmark root: ${target}`);
}

main().catch(error => {
  console.error(error?.stack ?? String(error));
  process.exitCode = 1;
});
