#!/usr/bin/env node

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

function findRepoRoot(start) {
  let current = path.resolve(start);
  while (true) {
    if (fs.existsSync(path.join(current, 'gradlew')) && fs.existsSync(path.join(current, 'shreddedpaper-server'))) {
      return current;
    }
    const parent = path.dirname(current);
    if (parent === current) {
      throw new Error('Could not find repository root');
    }
    current = parent;
  }
}

function parseArgs(argv) {
  const args = {
    json: 'tools/async-audit/java-call-sites.json',
    markdown: 'tools/async-audit/JAVA_CALL_SITES.md',
    noMarkdown: false,
  };

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i];
    if (arg === '--json') {
      args.json = argv[++i];
    } else if (arg === '--markdown') {
      args.markdown = argv[++i];
    } else if (arg === '--no-markdown') {
      args.noMarkdown = true;
    } else if (arg === '--help' || arg === '-h') {
      console.log(`Usage: node tools/async-audit/scan-java-call-sites.mjs [options]

Options:
  --json <path>       JSON output path
  --markdown <path>   Markdown output path
  --no-markdown       Skip markdown output
`);
      process.exit(0);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return args;
}

function run(command, args, options) {
  const result = spawnSync(command, args, {
    ...options,
    stdio: 'inherit',
  });
  if (result.error) {
    throw result.error;
  }
  if (result.status !== 0) {
    throw new Error(`${command} exited with ${result.status}`);
  }
}

const repoRoot = findRepoRoot(process.cwd());
const args = parseArgs(process.argv.slice(2));
const classesDir = path.join(repoRoot, 'run', 'async-audit', 'classes');
fs.mkdirSync(classesDir, { recursive: true });

run('javac', ['-d', classesDir, path.join(repoRoot, 'tools', 'async-audit', 'AsyncOwnershipCallScanner.java')], { cwd: repoRoot });

const javaArgs = [
  '-cp',
  classesDir,
  'AsyncOwnershipCallScanner',
  '--root',
  repoRoot,
  '--json',
  args.json,
];

if (args.noMarkdown) {
  javaArgs.push('--no-markdown');
} else {
  javaArgs.push('--markdown', args.markdown);
}

run('java', javaArgs, { cwd: repoRoot });
