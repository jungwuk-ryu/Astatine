// Profile the current artifact in a disposable server. Never point the template at production.
import fs from 'node:fs/promises';
import {openSync, closeSync} from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import os from 'node:os';
import crypto from 'node:crypto';
import {spawn, execFileSync} from 'node:child_process';
import {setTimeout as sleep} from 'node:timers/promises';
import {sendRconCommand} from '../tracker/rcon.mjs';

const repo = path.resolve(import.meta.dirname, '../../..');
const [templateArg, jarArg, outputArg] = process.argv.slice(2);
if (!outputArg) throw new Error('Usage: node run.mjs <isolated flat-world template> <paperclip.jar> <new output directory> [16..240 cows, default 80]');
const mobs = Number(process.argv[5] ?? 80);
if (!Number.isInteger(mobs) || mobs < 16 || mobs > 240) throw new Error('Cow count must be 16..240');
const template = await fs.realpath(templateArg);
const jar = await fs.realpath(jarArg);
const dir = path.resolve(outputArg);
const java = execFileSync('which', ['java'], {encoding: 'utf8'}).trim();
const jcmd = path.join(path.dirname(java), 'jcmd');
const sha = data => crypto.createHash('sha256').update(data).digest('hex');
const rcon = command => sendRconCommand(25686, 'local-tracker-benchmark', command, 15000);
for (const port of [25685, 25686]) {
    await new Promise((resolve, reject) => {
        const socket = net.createServer();
        socket.once('error', reject);
        socket.listen(port, '127.0.0.1', () => socket.close(resolve));
    });
}
await fs.mkdir(dir); // Fail instead of overwriting previous evidence.
const configFiles = ['bukkit.yml', 'spigot.yml', 'purpur.yml', 'shreddedpaper.yml', 'server.properties', 'eula.txt'];
for (const file of configFiles) await fs.copyFile(path.join(template, file), path.join(dir, file));
for (const name of ['config', 'world', 'world_nether', 'world_the_end']) {
    await fs.cp(path.join(template, name), path.join(dir, name), {recursive: true});
}
for (const name of ['libraries', 'cache']) await fs.symlink(path.join(template, name), path.join(dir, name));
await fs.mkdir(path.join(dir, 'plugins'));
await fs.copyFile(jar, path.join(dir, 'server.jar'));
let properties = await fs.readFile(path.join(dir, 'server.properties'), 'utf8');
for (const [key, value] of Object.entries({
    'server-ip': '127.0.0.1', 'server-port': '25685', 'rcon.port': '25686',
    'rcon.password': 'local-tracker-benchmark', 'enable-rcon': 'true',
    'online-mode': 'false', 'enable-query': 'false', 'management-server-enabled': 'false',
    'max-players': '128', 'view-distance': '2', 'simulation-distance': '2',
})) {
    properties = properties.split('\n').filter(line => !line.startsWith(`${key}=`)).join('\n');
    properties += `\n${key}=${value}\n`;
}
await fs.writeFile(path.join(dir, 'server.properties'), properties);
const args = ['--enable-preview', '-Dterminal.jline=false', '-Dterminal.ansi=false',
    '-Xms1G', '-Xmx3G', '-XX:ActiveProcessorCount=2', '-jar', 'server.jar', '--nogui'];
const manifest = {
    createdAt: new Date().toISOString(), sourceHead: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repo, encoding: 'utf8'}).trim(),
    preexistingSourceDiff: execFileSync('git', ['diff', '--', 'shreddedpaper-server'], {cwd: repo, encoding: 'utf8'}),
    jarSha256: sha(await fs.readFile(jar)), java, args, javaVersion: execFileSync(java, ['--version'], {encoding: 'utf8'}),
    template, host: {arch: os.arch(), cpus: os.cpus().length, freeMemory: os.freemem(), load: os.loadavg()},
    players: 80, mobs, mobType: 'cow', phases: [], configHashes: {},
};
for (const name of [...configFiles, 'config/paper-global.yml', 'config/paper-world-defaults.yml']) {
    manifest.configHashes[name] = sha(await fs.readFile(path.join(dir, name)));
}
const saveManifest = () => fs.writeFile(path.join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2));
await saveManifest();

function launch(command, args, cwd, log) {
    const fd = openSync(log, 'w');
    const child = spawn(command, args, {cwd, stdio: ['pipe', fd, fd]});
    closeSync(fd);
    const done = new Promise((resolve, reject) => {
        child.once('error', reject);
        child.once('exit', (code, signal) => resolve({code, signal}));
    });
    return {child, done};
}
async function awaitLog(proc, file, marker, seconds) {
    for (let i = 0; i < seconds; ++i) {
        if (proc.child.exitCode !== null) throw new Error(`Process exited waiting for ${marker}`);
        if ((await fs.readFile(file, 'utf8')).includes(marker)) return;
        await sleep(1000);
    }
    throw new Error(`Timeout waiting for ${marker}`);
}
const server = launch(java, args, dir, path.join(dir, 'console.log'));
manifest.pid = server.child.pid;
let clients;
try {
    await awaitLog(server, path.join(dir, 'console.log'), 'For help, type "help"', 180);
    console.log(`READY ${dir} pid=${server.child.pid}`);
    clients = launch('node', [path.join(import.meta.dirname, 'clients.mjs'), '80', '190',
        path.join(dir, 'clients.json'), 'moving'], repo, path.join(dir, 'clients.log'));
    await awaitLog(clients, path.join(dir, 'clients.log'), 'Sampling;', 240);
    async function profile(name, seconds) {
        const phase = {name, start: new Date().toISOString(), seconds, packetDiagnosticsDisabled: true, before: await rcon('region dump')};
        const output = path.join(dir, `${name}.jfr`);
        phase.jfrStart = execFileSync(jcmd, [String(server.child.pid), 'JFR.start', `name=${name}`,
            'settings=profile', `duration=${seconds}s`, `filename=${output}`,
            'jdk.ExecutionSample#period=5ms', 'jdk.ThreadPark#threshold=1ms', 'jdk.JavaMonitorEnter#threshold=1ms',
            '+minecraft.NetworkSummary#enabled=false',
            '+minecraft.PacketSent#enabled=false', '+minecraft.PacketReceived#enabled=false'],
            {encoding: 'utf8', timeout: 15000});
        console.log(`PROFILE ${name} ${seconds}s`);
        if (phase.jfrStart.includes('Warning!')) throw new Error(`JFR configuration rejected: ${phase.jfrStart}`);
        await sleep((seconds + 2) * 1000);
        phase.end = new Date().toISOString();
        phase.after = await rcon('region dump');
        phase.bytes = (await fs.stat(output)).size;
        manifest.phases.push(phase);
        await saveManifest();
    }
    await profile('players', 45);
    const commands = [
        'gamerule minecraft:max_entity_cramming 0',
        'fill 0 -60 0 24 -57 0 minecraft:glass', 'fill 0 -60 24 24 -57 24 minecraft:glass',
        'fill 0 -60 0 0 -57 24 minecraft:glass', 'fill 24 -60 0 24 -57 24 minecraft:glass',
    ];
    manifest.setupReplies = [];
    for (const command of commands) manifest.setupReplies.push({command, reply: await rcon(command)});
    let summoned = 0;
    for (let i = 0; i < mobs; ++i) {
        const x = 4.5 + (i % 16), z = 4.5 + Math.floor(i / 16);
        const reply = await rcon(`summon minecraft:cow ${x} -60 ${z} {Tags:["crowded_audit"],PersistenceRequired:1b,Invulnerable:1b}`);
        // The command acknowledges scheduling before owner-thread execution; verify completion below.
        if (reply && !reply.includes('Summoned')) throw new Error(`Summon failed: ${reply}`);
        summoned++;
    }
    for (let retry = 0; retry < 60; ++retry) {
        const log = await fs.readFile(path.join(dir, 'console.log'), 'utf8');
        manifest.summoned = (log.match(/\[Rcon: Summoned new Cow\]/g) ?? []).length;
        if (manifest.summoned === summoned) break;
        await sleep(1000);
    }
    if (manifest.summoned !== summoned) throw new Error(`Only ${manifest.summoned}/${summoned} summons completed`);
    console.log(`MOBS ${summoned}; warming 30s`);
    await sleep(30000);
    manifest.mobCheckBefore = await rcon('execute if entity @e[tag=crowded_audit]');
    if (manifest.mobCheckBefore.trim() !== `Test passed. Count: ${mobs}`) throw new Error(`Wrong mob population: ${manifest.mobCheckBefore}`);
    await profile('mixed', 60);
    manifest.mobCheckAfter = await rcon('execute if entity @e[tag=crowded_audit]');
    if (manifest.mobCheckAfter.trim() !== `Test passed. Count: ${mobs}`) throw new Error(`Wrong final mob population: ${manifest.mobCheckAfter}`);
    const result = await clients.done;
    if (result.code !== 0) throw new Error(`Client harness failed: ${JSON.stringify(result)}`);
    const acceptance = JSON.parse(await fs.readFile(path.join(dir, 'clients.json'), 'utf8'));
    if (acceptance.faults.length || !acceptance.allClientsSeeEveryOtherPlayer || acceptance.samples.length !== 190) {
        throw new Error('Invalid client acceptance');
    }
    manifest.acceptance = {samples: acceptance.samples.length, faults: acceptance.faults.length,
        allClientsSeeEveryOtherPlayer: acceptance.allClientsSeeEveryOtherPlayer};
} catch (error) {
    manifest.error = error.stack;
    throw error;
} finally {
    if (clients && clients.child.exitCode === null) {
        clients.child.kill('SIGTERM');
        await clients.done;
    }
    if (server.child.exitCode === null) {
        server.child.stdin.write('stop\n');
        const timeout = setTimeout(() => server.child.kill('SIGTERM'), 60000);
        const killTimeout = setTimeout(() => server.child.kill('SIGKILL'), 75000);
        try { manifest.serverExit = await server.done; }
        finally { clearTimeout(timeout); clearTimeout(killTimeout); }
    } else manifest.serverExit = await server.done;
    await saveManifest();
}
if (manifest.serverExit.code !== 0) throw new Error(`Server exit: ${JSON.stringify(manifest.serverExit)}`);
console.log(`DONE ${dir}`);
