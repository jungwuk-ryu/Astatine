#!/usr/bin/env node
import fs from "node:fs/promises";
import { existsSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const defaultBaseline = path.join(repoRoot, "tools", "async-audit", "root-covered-critical-baseline.json");

const args = parseArgs(process.argv.slice(2));
const baselinePath = path.resolve(repoRoot, args.baseline ?? defaultBaseline);
const currentPath = path.join(os.tmpdir(), `async-root-covered-${process.pid}.json`);

try {
  await runScanner(currentPath);
  const current = await readCandidateSignatures(currentPath);

  if (args.writeBaseline) {
    await writeBaseline(baselinePath, current);
    console.log(`Wrote root-covered critical baseline: ${path.relative(repoRoot, baselinePath)} (${current.length} candidates)`);
    process.exit(0);
  }

  if (!existsSync(baselinePath)) {
    throw new Error(`Missing baseline ${baselinePath}. Run with --write-baseline after manually auditing root-covered critical candidates.`);
  }

  const baseline = await readCandidateSignatures(baselinePath);
  const baselineSet = new Set(baseline.map(signatureKey));
  const currentSet = new Set(current.map(signatureKey));
  const added = current.filter(candidate => !baselineSet.has(signatureKey(candidate)));
  const removed = baseline.filter(candidate => !currentSet.has(signatureKey(candidate)));

  if (added.length > 0 || removed.length > 0) {
    console.error("Root-covered critical candidate baseline changed.");
    if (added.length > 0) {
      console.error("\nAdded candidates:");
      console.error(formatCandidates(added));
    }
    if (removed.length > 0) {
      console.error("\nRemoved candidates:");
      console.error(formatCandidates(removed));
    }
    process.exit(1);
  }

  console.log(`Root-covered critical baseline unchanged (${current.length} candidates).`);
} finally {
  await fs.rm(currentPath, { force: true });
}

function parseArgs(argv) {
  const parsed = {
    baseline: defaultBaseline,
    writeBaseline: false,
  };
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === "--baseline") {
      parsed.baseline = argv[++i];
      if (!parsed.baseline) {
        throw new Error("--baseline requires a path");
      }
    } else if (arg === "--write-baseline") {
      parsed.writeBaseline = true;
    } else if (arg === "--help" || arg === "-h") {
      console.log("Usage: node tools/runtime/verify-async-root-baseline.mjs [--write-baseline] [--baseline <path>]");
      process.exit(0);
    } else {
      throw new Error(`Unknown option: ${arg}`);
    }
  }
  return parsed;
}

async function runScanner(jsonPath) {
  const result = spawnSync("node", [
    "tools/async-audit/scan-async-ownership.mjs",
    "--include-root-covered",
    "--min-severity",
    "critical",
    "--json",
    jsonPath,
  ], {
    cwd: repoRoot,
    encoding: "utf8",
  });

  if (result.status !== 0) {
    throw new Error(`Root-covered scanner failed:\n${result.stdout}\n${result.stderr}`);
  }
}

async function readCandidateSignatures(jsonPath) {
  const parsed = JSON.parse(await fs.readFile(jsonPath, "utf8"));
  const candidates = Array.isArray(parsed.candidates) ? parsed.candidates : [];
  return candidates.map(candidate => ({
    id: candidate.id,
    path: candidate.path,
    sink: candidate.sink,
    evidence: candidate.evidence,
  })).sort((a, b) => signatureKey(a).localeCompare(signatureKey(b)));
}

async function writeBaseline(targetPath, candidates) {
  const payload = {
    generatedAt: new Date().toISOString(),
    note: "Audited root-covered critical async ownership candidates. Release gates fail if this set changes without review.",
    candidates,
  };
  await fs.mkdir(path.dirname(targetPath), { recursive: true });
  await fs.writeFile(targetPath, JSON.stringify(payload, null, 2) + "\n", "utf8");
}

function signatureKey(candidate) {
  return `${candidate.id}\u0000${candidate.path}\u0000${candidate.sink}\u0000${candidate.evidence}`;
}

function formatCandidates(candidates, limit = 30) {
  const lines = candidates.slice(0, limit).map(candidate => {
    return `- ${candidate.id} ${candidate.path} [${candidate.sink}] ${candidate.evidence}`;
  });
  if (candidates.length > limit) {
    lines.push(`... ${candidates.length - limit} more`);
  }
  return lines.join("\n");
}
