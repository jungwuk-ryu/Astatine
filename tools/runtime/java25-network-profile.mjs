#!/usr/bin/env node
import fs from "node:fs/promises";
import { spawn } from "node:child_process";
import { pathToFileURL } from "node:url";
import path from "node:path";

const TOOL_NAME = "tools/runtime/java25-network-profile.mjs";

const PROFILES = new Set([
  "baseline",
  "compact-object-headers",
]);

const GC_FLAGS = {
  g1: [
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UseStringDeduplication",
  ],
  zgc: [
    "-XX:+UseZGC",
  ],
  shenandoah: [
    "-XX:+UseShenandoahGC",
  ],
  none: [],
};

export function parseArgs(argv) {
  const options = {
    profile: "baseline",
    java: null,
    heap: "8G",
    directMemory: null,
    gc: "g1",
    extraArgs: [],
    jar: null,
    scenario: null,
    repeat: null,
    metadata: null,
    printCommand: false,
    verify: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    switch (arg) {
      case "--profile":
        options.profile = readValue(argv, i, arg);
        i++;
        break;
      case "--java":
        options.java = readValue(argv, i, arg);
        i++;
        break;
      case "--heap":
        options.heap = readValue(argv, i, arg);
        i++;
        break;
      case "--direct-memory":
        options.directMemory = readValue(argv, i, arg);
        i++;
        break;
      case "--gc":
        options.gc = readValue(argv, i, arg);
        i++;
        break;
      case "--extra-arg":
        options.extraArgs.push(readRawValue(argv, i, arg));
        i++;
        break;
      case "--jar":
        options.jar = readValue(argv, i, arg);
        i++;
        break;
      case "--scenario":
        options.scenario = readValue(argv, i, arg);
        i++;
        break;
      case "--repeat":
        options.repeat = Number(readValue(argv, i, arg));
        i++;
        break;
      case "--metadata":
        options.metadata = readValue(argv, i, arg);
        i++;
        break;
      case "--print-command":
        options.printCommand = true;
        break;
      case "--verify":
        options.verify = true;
        break;
      case "--help":
      case "-h":
        options.help = true;
        break;
      default:
        throw new Error(`Unknown option: ${arg}`);
    }
  }

  if (!PROFILES.has(options.profile)) {
    throw new Error(`--profile must be one of: ${Array.from(PROFILES).join(", ")}`);
  }
  if (!Object.hasOwn(GC_FLAGS, options.gc)) {
    throw new Error(`--gc must be one of: ${Object.keys(GC_FLAGS).join(", ")}`);
  }
  if (options.repeat !== null && (!Number.isInteger(options.repeat) || options.repeat < 1)) {
    throw new Error("--repeat must be a positive integer");
  }
  for (const extraArg of options.extraArgs) {
    if (/^-XX:[+-]UseCompactObjectHeaders$/.test(extraArg)) {
      throw new Error("--profile owns -XX:[+-]UseCompactObjectHeaders; do not pass it through --extra-arg");
    }
  }

  return options;
}

export function resolveJavaBin(javaOverride, env = process.env) {
  if (javaOverride) {
    return javaOverride;
  }
  if (env.JAVA) {
    return env.JAVA;
  }
  if (env.JAVA_HOME) {
    return path.join(env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java");
  }
  return "java";
}

export function buildJvmArgs(options) {
  const args = [
    `-Xms${options.heap}`,
    `-Xmx${options.heap}`,
    "-Dfile.encoding=UTF-8",
    "-Duser.timezone=UTC",
    ...GC_FLAGS[options.gc],
    "-XX:+DisableExplicitGC",
    "-XX:+PerfDisableSharedMem",
  ];

  if (options.directMemory) {
    args.push(`-XX:MaxDirectMemorySize=${options.directMemory}`);
  }
  if (options.profile === "compact-object-headers") {
    args.push("-XX:+UseCompactObjectHeaders");
  }
  args.push(...options.extraArgs);
  return args;
}

export function buildServerCommand(javaBin, options) {
  const args = buildJvmArgs(options);
  if (options.jar) {
    args.push("-jar", options.jar, "nogui");
  }
  return [javaBin, ...args];
}

export function buildMetadata(options, javaBin, javaVersionText, generatedAt = new Date().toISOString()) {
  const jvmArgs = buildJvmArgs(options);
  return {
    version: 1,
    tool: TOOL_NAME,
    generatedAt,
    profile: options.profile,
    compactObjectHeaders: options.profile === "compact-object-headers" ? "enabled" : "disabled",
    java: {
      executable: javaBin,
      version: javaVersionText.trim(),
    },
    jvm: {
      heap: options.heap,
      directMemory: options.directMemory,
      gc: options.gc,
      flags: jvmArgs,
    },
    benchmark: {
      scenario: options.scenario,
      repeat: options.repeat,
    },
  };
}

export function quoteArg(arg) {
  if (/^[A-Za-z0-9_./:=+@%,~-]+$/.test(arg)) {
    return arg;
  }
  return "'" + arg.replaceAll("'", "'\\''") + "'";
}

function readValue(args, index, name) {
  const value = args[index + 1];
  if (!value || value.startsWith("--")) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

function readRawValue(args, index, name) {
  const value = args[index + 1];
  if (!value) {
    throw new Error(`${name} requires a value`);
  }
  return value;
}

function usage() {
  console.log(`Usage: node ${TOOL_NAME} [options]

Builds reproducible Java 25 network benchmark JVM profiles without changing
the server's default launch flags.

Options:
  --profile <name>       baseline or compact-object-headers. Default: baseline.
  --java <path>          Java executable. Default: $JAVA, $JAVA_HOME/bin/java, then java.
  --heap <size>          Heap for both -Xms and -Xmx. Default: 8G.
  --direct-memory <size> Optional -XX:MaxDirectMemorySize value.
  --gc <name>            g1, zgc, shenandoah, or none. Default: g1.
  --extra-arg <flag>     Extra JVM flag. Can be repeated.
  --jar <path>           Server jar to append as -jar <path> nogui.
  --scenario <name>      Benchmark scenario metadata.
  --repeat <n>           Benchmark repeat-count metadata.
  --metadata <path|- >   Write profile metadata JSON, or print to stdout with "-".
  --print-command        Print the full Java command line.
  --verify               Start the JVM with the selected flags and -version.
  --help                 Show this help.

Examples:
  node ${TOOL_NAME} --profile compact-object-headers --verify
  node ${TOOL_NAME} --profile compact-object-headers --jar build/libs/server.jar --print-command
`);
}

async function capture(command, args) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stdout = "";
    let stderr = "";
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", chunk => {
      stdout += chunk;
    });
    child.stderr.on("data", chunk => {
      stderr += chunk;
    });
    child.once("error", reject);
    child.once("exit", code => {
      if (code === 0) {
        resolve({ stdout, stderr });
        return;
      }
      const output = [stdout.trim(), stderr.trim()].filter(Boolean).join("\n");
      reject(new Error(`${command} exited with ${code}${output ? `:\n${output}` : ""}`));
    });
  });
}

async function writeMetadata(target, metadata) {
  const json = `${JSON.stringify(metadata, null, 2)}\n`;
  if (target === "-") {
    process.stdout.write(json);
    return;
  }
  await fs.mkdir(path.dirname(path.resolve(target)), { recursive: true });
  await fs.writeFile(target, json, "utf8");
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    usage();
    return;
  }

  const javaBin = resolveJavaBin(options.java);
  if (options.verify) {
    await capture(javaBin, [...buildJvmArgs(options), "-XshowSettings:vm", "-version"]);
  }

  const versionResult = await capture(javaBin, ["-version"]);
  const versionText = [versionResult.stderr, versionResult.stdout].filter(Boolean).join("").trim();
  const metadata = buildMetadata(options, javaBin, versionText);

  if (options.printCommand) {
    console.log(buildServerCommand(javaBin, options).map(quoteArg).join(" "));
  }
  if (options.metadata) {
    await writeMetadata(options.metadata, metadata);
  }
  if (!options.printCommand && !options.metadata) {
    console.log(`${metadata.profile}: ${metadata.jvm.flags.map(quoteArg).join(" ")}`);
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
