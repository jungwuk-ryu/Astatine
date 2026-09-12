#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../../..');
if (!process.argv[2]) throw new Error('Usage: node tools/benchmark/tracker/build.mjs <isolated-server-directory>');
const server = path.resolve(process.argv[2]);
const classes = path.join(repo, 'shreddedpaper-server/build/classes/java/main');
await fs.access(path.join(classes, 'io/multipaper/shreddedpaper/tracking/EntityTrackerUpdateScratch.class'));

async function jars(directory) {
    const result = [];
    for (const entry of await fs.readdir(directory, {withFileTypes: true})) {
        const file = path.join(directory, entry.name);
        if (entry.isDirectory()) result.push(...await jars(file));
        else if (entry.isFile() && entry.name.endsWith('.jar')) result.push(file);
    }
    return result;
}
const dependencies = [classes, ...await jars(path.join(repo, 'shreddedpaper-api/build/libs')), ...await jars(path.join(server, 'libraries'))];
const buildRoot = path.join(repo, 'run/tracker-plugin');
await fs.mkdir(buildRoot, {recursive: true});
const output = await fs.mkdtemp(path.join(buildRoot, 'classes-'));
function run(command, args) {
    const result = spawnSync(command, args, {stdio: 'inherit'});
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} exited with ${result.status}`);
}
run('javac', ['--enable-preview', '--release', '25', '-cp', dependencies.join(path.delimiter), '-d', output, path.join(here, 'TrackerBenchmark.java')]);
await fs.copyFile(path.join(here, 'plugin.yml'), path.join(output, 'plugin.yml'));
const manifest = path.join(output, 'benchmark-manifest.mf');
await fs.writeFile(manifest, 'Manifest-Version: 1.0\npaperweight-mappings-namespace: mojang\n\n');
const jar = path.join(buildRoot, 'tracker-benchmark.jar');
run('jar', ['--create', '--file', jar, '--manifest', manifest, '-C', output, '.']);
console.log(jar);
