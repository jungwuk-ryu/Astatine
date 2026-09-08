#!/usr/bin/env node
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../../..');
if (!process.argv[2]) throw new Error('Usage: node tools/benchmark/player-contact/build.mjs <isolated-server-directory>');
const server = path.resolve(process.argv[2]);
const classes = path.join(repo, 'shreddedpaper-server/build/classes/java/main');
await fs.access(path.join(classes, 'io/multipaper/shreddedpaper/entity/PlayerTouchQuery.class'));

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
const buildRoot = path.join(repo, 'run/player-contact-plugin');
await fs.mkdir(buildRoot, {recursive: true});
const output = await fs.mkdtemp(path.join(buildRoot, 'classes-'));
function run(command, args) {
    const result = spawnSync(command, args, {stdio: 'inherit'});
    if (result.error) throw result.error;
    if (result.status !== 0) throw new Error(`${command} exited with ${result.status}`);
}
run('javac', ['--enable-preview', '--release', '25', '-cp', dependencies.join(path.delimiter), '-d', output, path.join(here, 'PlayerContactBenchmark.java')]);
await fs.copyFile(path.join(here, 'plugin.yml'), path.join(output, 'plugin.yml'));
const jar = path.join(buildRoot, 'player-contact-benchmark.jar');
run('jar', ['--create', '--file', jar, '-C', output, '.']);
console.log(jar);
