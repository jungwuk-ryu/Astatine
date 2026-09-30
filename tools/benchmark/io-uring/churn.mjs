// Connect only to the disposable crowded-audit server, after its measured clients exit.
import fs from 'node:fs/promises';
import {createRequire} from 'node:module';
import {setTimeout as sleep} from 'node:timers/promises';
import {sendRconCommand} from '../tracker/rcon.mjs';

const require = createRequire(new URL('../tracker/package.json', import.meta.url));
const mc = require('minecraft-protocol');
const [output, countArg = '100'] = process.argv.slice(2);
const count = Number(countArg);
if (!output || !Number.isInteger(count) || count < 4 || count > 1000 || count % 4) {
    throw new Error('Usage: node churn.mjs <new result.json> [4..1000 connections, divisible by four]');
}
await fs.access(output).then(() => { throw new Error('Result already exists'); }, error => {
    if (error.code !== 'ENOENT') throw error;
});
const rcon = command => sendRconCommand(25686, 'local-tracker-benchmark', command, 15000);
async function emptyPopulation() {
    const start = performance.now();
    for (let second = 0; second < 20; second++) {
        const reply = await rcon('list');
        if (/There are 0 (?:of|out of)/.test(reply)) return performance.now() - start;
        await sleep(1000);
    }
    throw new Error('Disconnected players remained after 20 seconds');
}
const result = {host: '127.0.0.1', port: 25685, connections: count, cycles: [], faults: [],
    reusedProfiles: 4, slowReaderSeconds: 10,
    limitation: 'Offline loopback clients with locally paused readers; kernel backpressure and WAN behavior are not measured.'};
await emptyPopulation();
try {
    for (let cycle = 0; cycle < count / 4; cycle++) {
        const clients = [];
        try {
            for (let i = 0; i < 4; i++) {
                const client = mc.createClient({host: '127.0.0.1', port: 25685,
                    username: `churn${String(i).padStart(4, '0')}`, auth: 'offline',
                    version: '1.21.11', hideErrors: true});
                client.intentionalClose = false;
                client.on('ping', data => client.write('pong', {id: data.id}));
                client.on('error', error => {
                    if (!client.intentionalClose) result.faults.push({name: client.username, error: error.message});
                });
                client.on('end', reason => {
                    if (!client.intentionalClose) result.faults.push({name: client.username, end: reason});
                });
                client.on('kick_disconnect', data => {
                    if (!client.intentionalClose) result.faults.push({name: client.username, kick: data.reason});
                });
                client.on('position', data => {
                    client.write('teleport_confirm', {teleportId: data.teleportId});
                    client.write('position', {x: data.x, y: data.y, z: data.z,
                        flags: {onGround: true, hasHorizontalCollision: false}});
                    if (!client.ready) { client.write('player_loaded', {}); client.ready = true; }
                });
                client.on('chunk_batch_finished', () => client.write('chunk_batch_received', {chunksPerTick: 64}));
                clients.push(client);
                await sleep(100);
            }
            for (let second = 0; second < 60; second++) {
                if (result.faults.length) throw new Error('Connection churn fault');
                if (clients.every(client => client.ready && client.state === 'play')) break;
                if (second === 59) throw new Error('Churn login timeout');
                await sleep(1000);
            }
            const population = await rcon('list');
            if (!/There are 4 (?:of|out of)/.test(population)) throw new Error('Churn population mismatch');
            // Exercise a paused reader with queued entity updates, then resume or close it.
            const slow = cycle % 5 === 0;
            if (slow) {
                clients[0].socket.pause();
                clients[1].socket.pause();
                await sleep(10000);
                clients[1].socket.resume();
            } else await sleep(1000);
            if (result.faults.length) throw new Error('Unexpected churn disconnect');
            const bytes = clients.reduce((sum, client) => sum + client.socket.bytesRead, 0);
            for (let i = 0; i < clients.length; i++) {
                clients[i].intentionalClose = true;
                if (i % 2 === 0) clients[i].socket.destroy();
                else clients[i].end();
            }
            const cleanupMs = await emptyPopulation();
            result.cycles.push({cycle, connected: clients.length, slowReaders: slow ? 2 : 0,
                population, receivedBytes: bytes, cleanupMs});
            console.log(`CHURN ${cycle + 1}/${count / 4} cleanup=${cleanupMs.toFixed(0)}ms`);
        } finally {
            for (const client of clients) { client.intentionalClose = true; client.socket?.destroy(); }
        }
    }
    if (result.faults.length) throw new Error('Connection churn faults');
    result.status = 'passed';
} catch (error) {
    result.status = 'failed';
    result.error = error.stack;
    throw error;
} finally {
    await fs.writeFile(output, JSON.stringify(result, null, 2) + '\n');
}
console.log(`CHURN_PASS ${count} connections`);
