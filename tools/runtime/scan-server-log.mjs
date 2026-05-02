#!/usr/bin/env node
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const HIGH_SIGNAL_PATTERNS = [
  {
    id: "server-error",
    regex: /(?:^\[[^\n]*\/ERROR\]:|^\[[^\n]*\] \[[^\n]*\/ERROR\]:|^\[[^\n]* ERROR\]:|^\[[^\n]* ERROR\])/,
  },
  {
    id: "unexpected-exception",
    regex: /Encountered an unexpected exception|Exception ticking world|Failed to handle packet|Crash report|crash-reports\/crash-|Exception stopping the server|A single server tick took|Found one Java-level deadlock|Chunk system crash propagated/,
  },
  {
    id: "thread-ownership",
    regex: /Thread failed main thread check|tried to run a task from the wrong thread|wrong thread|not tick thread|Off-main|AsyncCatcher|not owned|already retired|Block change is not write locked|Cannot add entity off-main|Cannot move an entity into off-main|Cannot perform command async/,
  },
  {
    id: "poi-ownership",
    regex: /Accessing poi chunk off-main|poi chunk off-main|PoiManager/,
  },
  {
    id: "chunk-ticket-stall",
    regex: /ThreadedTicketLevelPropagator|ReentrantAreaLock\.lock|runDistanceManagerUpdates|RegionMailbox.*Rejected .* region task|Critical region mailbox reserve exceeded|quarantined and will not be requeued/,
  },
  {
    id: "watchdog",
    regex: /The server has not responded|The server has stopped responding|A ShreddedPaper independent region tick has stopped responding|Long-running independent region ticks:|Server thread dump|Watchdog emergency shutdown/,
  },
  {
    id: "region-watchdog-dump",
    regex: /Current Thread: ShreddedPaperRegion(?:Normal|Degraded)-|Region locks held \(regionSize=/,
  },
  {
    id: "sync-load-guard",
    regex: /Synchronous chunk load is not allowed|Cannot merge non-quiescent/,
  },
  {
    id: "fatal-runtime",
    regex: /IllegalStateException|NullPointerException|ConcurrentModificationException|IndexOutOfBoundsException|UnsupportedOperationException|ClassCastException|RejectedExecutionException|OutOfMemoryError|StackOverflowError|NoClassDefFoundError|NoSuchMethodError|DirectoryLock\$LockException|already locked \(possibly by other Minecraft instance\?\)/,
  },
  {
    id: "region-load-test-degraded",
    regex: /RegionLoadTest.*(?:failed=[1-9]\d*|failure=[1-9]\d*|rejected=[1-9]\d*|null=[1-9]\d*)|Broadcast scheduling failed|Broadcast target chunk is not loaded|Run \/rlt chunkgen|sync-?load probe .*failed|RegionLoadTest did not clean up fully/i,
  },
];

export function scanText(text, source = "<memory>") {
  const matches = [];
  const lines = text.split(/\r?\n/);

  for (let i = 0; i < lines.length; i++) {
    const line = stripAnsi(lines[i]);
    if (isIgnoredNoise(line)) {
      continue;
    }
    for (const pattern of HIGH_SIGNAL_PATTERNS) {
      pattern.regex.lastIndex = 0;
      if (pattern.regex.test(line)) {
        matches.push({
          source,
          line: i + 1,
          id: pattern.id,
          text: line,
        });
      }
    }
  }

  return matches;
}

export async function scanPath(targetPath) {
  const stat = await fs.stat(targetPath);
  if (stat.isDirectory()) {
    const files = await listLogFiles(targetPath);
    const allMatches = [];
    for (const file of files) {
      allMatches.push(...await scanPath(file));
    }
    return allMatches;
  }

  const text = await fs.readFile(targetPath, "utf8");
  return scanText(text, targetPath);
}

export function formatMatches(matches, limit = 80) {
  const shown = matches.slice(0, limit).map((match) => {
    return `${match.source}:${match.line} [${match.id}] ${match.text}`;
  });

  if (matches.length > limit) {
    shown.push(`... ${matches.length - limit} more high-signal matches omitted`);
  }

  return shown.join("\n");
}

async function listLogFiles(root) {
  const out = [];
  const entries = await fs.readdir(root, { withFileTypes: true });
  for (const entry of entries) {
    const full = path.join(root, entry.name);
    if (entry.isDirectory()) {
      out.push(...await listLogFiles(full));
    } else if (/\.(log|txt)$/.test(entry.name) || entry.name === "latest.log") {
      out.push(full);
    }
  }
  return out;
}

function stripAnsi(text) {
  return text.replace(/\u001b\[[0-9;]*m/g, "");
}

function isIgnoredNoise(line) {
  return /\/ERROR\]: No key layers in MapLike\[\{\}\]$/.test(line);
}

async function main() {
  const targets = process.argv.slice(2);
  if (targets.length === 0) {
    console.error("Usage: node tools/runtime/scan-server-log.mjs <log-or-directory>...");
    process.exit(2);
  }

  const matches = [];
  for (const target of targets) {
    matches.push(...await scanPath(path.resolve(target)));
  }

  if (matches.length > 0) {
    console.error(formatMatches(matches));
    process.exit(1);
  }

  console.log(`No high-signal server log patterns found in ${targets.length} target(s).`);
}

const isMain = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
