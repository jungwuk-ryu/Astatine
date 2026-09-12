import {createRequire} from 'node:module';
import {setTimeout as sleep} from 'node:timers/promises';
import fs from 'node:fs/promises';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {sendRconCommand} from './rcon.mjs';

const require = createRequire(import.meta.url);
const mc = require('minecraft-protocol');
const playerType = require('minecraft-data')('1.21.11').entitiesByName.player.id;
const count = Number(process.argv[2] ?? 200);
const duration = Number(process.argv[3] ?? 60);
const output = process.argv[4];
const mode = process.argv[5] ?? 'stationary';
if (!output || !Number.isInteger(count) || count < 2 || count > 500 || !Number.isInteger(duration) || duration < 10 || duration > 600 || !['stationary', 'moving'].includes(mode)) {
    throw new Error('Usage: node clients.mjs <2..500 clients> <10..600 seconds> <output.json> [stationary|moving]');
}
const clients = [], faults = [], samples = [];
const rcon = command => sendRconCommand(25686, 'local-tracker-benchmark', command, 120000);
let closing = false, movement;
let serverPid;
const warmupSeconds = 90;
const clockHz = process.platform === 'linux' ? Number(execFileSync('getconf', ['CLK_TCK'], {encoding: 'utf8'}).trim()) : null;
if (process.platform === 'linux') {
    const serverDirectory = path.dirname(path.resolve(output));
    for (const pid of await fs.readdir('/proc')) {
        if (!/^\d+$/.test(pid)) continue;
        try {
            if (await fs.readlink(`/proc/${pid}/cwd`) === serverDirectory && (await fs.readFile(`/proc/${pid}/comm`, 'utf8')).trim() === 'java') serverPid = pid;
        } catch {}
    }
}

async function regionCpuTicks() {
    if (!serverPid) return null;
    let ticks = 0;
    for (const tid of await fs.readdir(`/proc/${serverPid}/task`)) {
        const stat = await fs.readFile(`/proc/${serverPid}/task/${tid}/stat`, 'utf8').catch(() => null);
        if (!stat || !stat.slice(stat.indexOf('(') + 1, stat.lastIndexOf(')')).startsWith('AstatineRegion')) continue;
        const fields = stat.slice(stat.lastIndexOf(')') + 2).split(/\s+/);
        ticks += Number(fields[11]) + Number(fields[12]);
    }
    return ticks;
}

function assertPopulation() {
    if (faults.length) throw new Error(JSON.stringify(faults.slice(0, 5)));
    if (clients.length !== count || clients.some(client => !client.ready || client.state !== 'play')) throw new Error('Incomplete client population');
    for (const client of clients) {
        if (client.visiblePlayers.size !== count - 1) throw new Error(`${client.username}: expected ${count - 1} visible players, received ${client.visiblePlayers.size}`);
    }
}

try {
    for (const command of ['team add density', 'team modify density collisionRule never', 'difficulty peaceful', 'gamerule minecraft:spawn_mobs false']) await rcon(command);
    for (let i = 0; i < count; ++i) {
        const client = mc.createClient({host: '127.0.0.1', port: 25685, username: `density${String(i).padStart(4, '0')}`, auth: 'offline', version: '1.21.11', hideErrors: true, checkTimeoutInterval: 120000, clientSettings: {viewDistance: 2}});
        client.visiblePlayers = new Set();
        client.on('error', error => { if (!closing) faults.push({name: client.username, error: error.message}); });
        client.on('end', reason => { if (!closing) faults.push({name: client.username, end: reason}); });
        client.on('kick_disconnect', data => { if (!closing) faults.push({name: client.username, kick: data.reason}); });
        client.on('spawn_entity', data => { if (data.type === playerType) client.visiblePlayers.add(data.entityId); });
        client.on('entity_destroy', data => { for (const id of data.entityIds) client.visiblePlayers.delete(id); });
        client.on('position', data => {
            client.position = Object.fromEntries(['x', 'y', 'z'].map(axis => [axis, data[axis] + (data.flags[axis] ? (client.position?.[axis] ?? 0) : 0)]));
            client.write('teleport_confirm', {teleportId: data.teleportId});
            client.write('position', {...client.position, flags: {onGround: true, hasHorizontalCollision: false}});
            if (!client.ready) { client.write('player_loaded', {}); client.ready = true; }
        });
        client.on('chunk_batch_finished', () => client.write('chunk_batch_received', {chunksPerTick: 64}));
        clients.push(client);
        await sleep(100);
        if (i % 20 === 19) await rcon('team join density @a');
        if (faults.length) throw new Error(JSON.stringify(faults.slice(0, 5)));
    }
    const deadline = Date.now() + 180000;
    while (clients.some(client => !client.ready) && Date.now() < deadline) await sleep(1000);
    await rcon('team join density @a');
    await rcon('tp @a 8 -60 8');
    let phase = 0;
    movement = setInterval(() => {
        phase += 0.04;
        for (let i = 0; i < clients.length; ++i) {
            const client = clients[i];
            if (!client.ready || client.state !== 'play') continue;
            if (mode === 'moving') client.position = {x: 8.5 + 2 * Math.sin(phase + i * 0.2), y: -60, z: 8.5 + 2 * Math.cos(phase + i * 0.2)};
            client.write('position', {...client.position, flags: {onGround: true, hasHorizontalCollision: false}});
        }
    }, mode === 'moving' ? 100 : 1000);
    console.log(`Warming ${warmupSeconds} seconds: ${count} clients, ${mode}`);
    await sleep(warmupSeconds * 1000);
    assertPopulation();
    const population = await rcon('list');
    if (!population.startsWith(`There are ${count} of`)) throw new Error('Incomplete server population');
    console.log('Sampling; all clients received every other player.');
    const cpuStart = await regionCpuTicks();
    const sampleStart = performance.now();
    for (let second = 0; second < duration; ++second) {
        assertPopulation();
        const regions = await rcon('region top 5');
        const mspt = await rcon('mspt');
        samples.push({at: new Date().toISOString(), regions, mspt});
        if (second % 10 === 0) console.log(JSON.stringify({second, faults: faults.length, regions}));
        await sleep(1000);
    }
    const sampleWallSeconds = (performance.now() - sampleStart) / 1000;
    const cpuEnd = await regionCpuTicks();
    const regionCpuSeconds = cpuStart === null ? null : (cpuEnd - cpuStart) / clockHz;
    assertPopulation();
    const ownership = await rcon('region ownership');
    await fs.writeFile(output, JSON.stringify({count, duration, mode, warmupSeconds, faults, allClientsSeeEveryOtherPlayer: true, ownership, serverPid, clockHz, sampleWallSeconds, regionCpuSeconds, samples}, null, 2));
} catch (error) {
    await fs.writeFile(output, JSON.stringify({count, duration, mode, faults, error: error.message, samples}, null, 2));
    throw error;
} finally {
    closing = true;
    clearInterval(movement);
    for (const client of clients) client.end();
}
