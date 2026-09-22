// Shard packet decoding so the load generator does not serialize 80 clients on one JS loop.
import {Worker, isMainThread, parentPort, workerData} from 'node:worker_threads';
import {createRequire} from 'node:module';
import {monitorEventLoopDelay} from 'node:perf_hooks';
import {setTimeout as sleep} from 'node:timers/promises';
import fs from 'node:fs/promises';
import {sendRconCommand} from '../tracker/rcon.mjs';

if (!isMainThread) {
    const require = createRequire(new URL('../tracker/package.json', import.meta.url));
    const mc = require('minecraft-protocol');
    const playerType = require('minecraft-data')('1.21.11').entitiesByName.player.id;
    const {offset, count, total} = workerData;
    const clients = [], faults = [];
    const delay = monitorEventLoopDelay({resolution: 20});
    delay.enable();
    let closing = false, moving = false, phase = 0;
    const movement = setInterval(() => {
        phase += 0.04;
        for (let i = 0; i < clients.length; ++i) {
            const client = clients[i];
            if (!client.ready || client.state !== 'play' || !client.position) continue;
            if (moving) client.position = {x: 8.5 + 2 * Math.sin(phase + (offset + i) * .2), y: -60,
                z: 8.5 + 2 * Math.cos(phase + (offset + i) * .2)};
            client.write('position', {...client.position, flags: {onGround: true, hasHorizontalCollision: false}});
        }
    }, 100);
    parentPort.on('message', async message => {
        if (message.action === 'move') moving = true;
        if (message.action === 'reset-delay') delay.reset();
        if (message.action === 'status') {
            parentPort.postMessage({id: message.id, status: {
                offset, count: clients.length, ready: clients.filter(c => c.ready && c.state === 'play').length,
                completeVisibility: clients.every(c => c.visiblePlayers.size === total - 1),
                faults: [...faults], eventLoopDelayMaxMs: delay.max / 1e6,
            }});
        }
        if (message.action === 'close') {
            closing = true;
            clearInterval(movement);
            delay.disable();
            for (const client of clients) client.end();
            await sleep(1000);
            parentPort.close();
        }
    });
    try {
        for (let i = 0; i < count; ++i) {
            const client = mc.createClient({host: '127.0.0.1', port: 25685,
                username: `density${String(offset + i).padStart(4, '0')}`, auth: 'offline',
                version: '1.21.11', hideErrors: true, checkTimeoutInterval: 120000,
                clientSettings: {viewDistance: 2}});
            client.visiblePlayers = new Set();
            client.on('error', error => { if (!closing) faults.push({name: client.username, error: error.message}); });
            client.on('end', reason => { if (!closing) faults.push({name: client.username, end: reason}); });
            client.on('kick_disconnect', data => { if (!closing) faults.push({name: client.username, kick: data.reason}); });
            client.on('spawn_entity', data => { if (data.type === playerType) client.visiblePlayers.add(data.entityId); });
            client.on('entity_destroy', data => { for (const id of data.entityIds) client.visiblePlayers.delete(id); });
            client.on('position', data => {
                client.position = Object.fromEntries(['x', 'y', 'z'].map(axis =>
                    [axis, data[axis] + (data.flags[axis] ? (client.position?.[axis] ?? 0) : 0)]));
                client.write('teleport_confirm', {teleportId: data.teleportId});
                client.write('position', {...client.position, flags: {onGround: true, hasHorizontalCollision: false}});
                if (!client.ready) { client.write('player_loaded', {}); client.ready = true; }
            });
            client.on('chunk_batch_finished', () => client.write('chunk_batch_received', {chunksPerTick: 64}));
            clients.push(client);
            await sleep(400); // Four workers preserve the original overall login rate.
        }
    } catch (error) {
        faults.push({error: error.stack});
    }
} else {
    const count = Number(process.argv[2]), duration = Number(process.argv[3]), output = process.argv[4];
    if (count !== 80 || !Number.isInteger(duration) || duration < 140 || duration > 300 || !output) {
        throw new Error('Usage: node clients.mjs 80 <140..300 seconds> <output.json>');
    }
    const rcon = command => sendRconCommand(25686, 'local-tracker-benchmark', command, 15000);
    const workers = [], samples = [], faults = [];
    const pending = new Map();
    let nextId = 0, closing = false;
    const status = worker => new Promise((resolve, reject) => {
        const id = nextId++;
        const timer = setTimeout(() => { pending.delete(id); reject(new Error('Load worker status timeout')); }, 10000);
        pending.set(id, message => { clearTimeout(timer); pending.delete(id); resolve(message.status); });
        worker.postMessage({action: 'status', id});
    });
    async function validate() {
        const states = await Promise.all(workers.map(status));
        for (const state of states) {
            faults.push(...state.faults);
            if (state.ready !== 20 || !state.completeVisibility || state.faults.length) {
                throw new Error(`Incomplete load population: ${JSON.stringify(state)}`);
            }
        }
        if (faults.length) throw new Error(JSON.stringify(faults));
        return states;
    }
    const result = {count, duration, mode: 'moving', warmupSeconds: 90, clientWorkers: 4, faults, samples};
    try {
        for (const command of ['team add density', 'team modify density collisionRule never', 'difficulty peaceful',
            'gamerule minecraft:spawn_mobs false']) await rcon(command);
        for (let shard = 0; shard < 4; ++shard) {
            const worker = new Worker(new URL(import.meta.url), {workerData: {offset: shard * 20, count: 20, total: count}});
            worker.on('message', message => pending.get(message.id)?.(message));
            worker.on('error', error => faults.push({worker: shard, error: error.stack}));
            worker.on('exit', code => { if (!closing) faults.push({worker: shard, unexpectedExit: code}); });
            workers.push(worker);
        }
        let ready = false;
        for (let second = 0; second < 180; ++second) {
            await sleep(1000);
            const states = await Promise.all(workers.map(status));
            if (states.some(s => s.faults.length) || faults.length) throw new Error('Client failed during login');
            if (states.every(s => s.ready === 20)) { ready = true; break; }
        }
        if (!ready) throw new Error('Login timeout');
        await rcon('team join density @a');
        await rcon('tp @a 8 -60 8');
        for (const worker of workers) worker.postMessage({action: 'move'});
        console.log('Warming 90 seconds: 80 clients across four workers, moving');
        await sleep(90000);
        await validate();
        if (!(await rcon('list')).startsWith('There are 80 of')) throw new Error('Server population mismatch');
        for (const worker of workers) worker.postMessage({action: 'reset-delay'});
        console.log('Sampling; all clients received every other player.');
        for (let second = 0; second < duration; ++second) {
            const states = await validate();
            const regions = await rcon('region top 5');
            const mspt = await rcon('mspt');
            samples.push({at: new Date().toISOString(), regions, mspt, clientWorkers: states});
            if (second % 10 === 0) console.log(JSON.stringify({second, faults: faults.length,
                maxClientLoopDelayMs: Math.max(...states.map(s => s.eventLoopDelayMaxMs))}));
            await sleep(1000);
        }
        await validate();
        result.allClientsSeeEveryOtherPlayer = true;
        result.ownership = await rcon('region ownership');
    } catch (error) {
        result.error = error.stack;
        throw error;
    } finally {
        await fs.writeFile(output, JSON.stringify(result, null, 2));
        closing = true;
        await Promise.all(workers.map(worker => new Promise(resolve => {
            if (worker.threadId === -1) { resolve(); return; }
            const timer = setTimeout(() => worker.terminate().finally(resolve), 5000);
            worker.once('exit', () => { clearTimeout(timer); resolve(); });
            worker.postMessage({action: 'close'});
        })));
    }
}
