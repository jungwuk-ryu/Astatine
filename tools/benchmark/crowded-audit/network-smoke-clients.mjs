import {createRequire} from 'node:module';
import {setTimeout as sleep} from 'node:timers/promises';
import fs from 'node:fs/promises';
const require = createRequire(new URL('../tracker/package.json', import.meta.url));
const mc = require('minecraft-protocol');
const clients = [], faults = [];
let closing = false;
for (let i = 0; i < 4; i++) {
    const client = mc.createClient({host: '127.0.0.1', port: 25687, username: `batch${i}`, auth: 'offline', version: '1.21.11'});
    client.count = 0; client.echoes = 0; client.dimensionChanges = 0; client.expectedSequence = 0;
    client.on('error', error => { if (!closing) faults.push(error.message); });
    client.on('end', reason => { if (!closing) faults.push(String(reason)); });
    client.on('kick_disconnect', data => { if (!closing) faults.push(JSON.stringify(data)); });
    client.on('position', data => {
        client.position = Object.fromEntries(['x', 'y', 'z'].map(axis => [axis, data[axis] + (data.flags[axis] ? (client.position?.[axis] ?? 0) : 0)]));
        client.write('teleport_confirm', {teleportId: data.teleportId});
        client.write('position', {...client.position, flags: {onGround: true, hasHorizontalCollision: false}});
        client.write('player_loaded', {});
        if (!client.registered) {
            client.registered = true;
            client.write('custom_payload', {channel: 'minecraft:register', data: Buffer.from('astatine:perf-probe')});
        }
    });
    client.on('chunk_batch_finished', () => client.write('chunk_batch_received', {chunksPerTick: 64}));
    client.on('respawn', () => client.dimensionChanges++);
    client.on('entity_look', data => {
        if (data.entityId < 4_000_000 || data.entityId >= 4_000_064) return;
        if (data.yaw !== i * 32 || data.entityId - 4_000_000 !== client.expectedSequence) {
            if (faults.length < 20) faults.push(`recipient=${i} yaw=${data.yaw} id=${data.entityId} expected=${client.expectedSequence}`);
        }
        client.expectedSequence = (client.expectedSequence + 1) % 64;
        client.count++;
    });
    client.on('custom_payload', data => {
        if (data.channel === 'astatine:perf-probe') {
            client.echoes++;
            client.write('custom_payload', data);
        }
    });
    clients.push(client);
    await sleep(250);
}
try {
    await sleep(35000);
    if (clients.some(client => client.count < 5000 || client.echoes < 100)) faults.push('Insufficient packet/echo samples');
    if (clients[3].dimensionChanges < 2) faults.push('Dimension round trip did not complete');
    const result = {faults, clients: clients.map((client, i) => ({recipient: i, packets: client.count, echoes: client.echoes, dimensionChanges: client.dimensionChanges}))};
    await fs.writeFile(process.argv[2], JSON.stringify(result, null, 2));
    if (faults.length) throw new Error(JSON.stringify(faults));
    console.log('NETWORK_CLIENT_SMOKE_PASS', JSON.stringify(result));
} finally {
    closing = true;
    for (const client of clients) client.end();
    await sleep(1000);
}
