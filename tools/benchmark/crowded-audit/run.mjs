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
const players = Number(process.env.AUDIT_PLAYERS ?? 80);
if (!Number.isInteger(players) || players < 4 || players > 80 || players % 4) throw new Error('AUDIT_PLAYERS must be 4..80 and divisible by four');
const sampleSeconds = Number(process.env.AUDIT_SAMPLE_SECONDS ?? 190);
if (!Number.isInteger(sampleSeconds) || sampleSeconds < 190 || sampleSeconds > 3600) throw new Error('AUDIT_SAMPLE_SECONDS must be 190..3600');
const clientMode = process.env.AUDIT_CLIENT_MODE ?? 'moving';
if (!['moving', 'stationary'].includes(clientMode)) throw new Error('AUDIT_CLIENT_MODE must be moving or stationary');
const lateProfileMinutes = JSON.parse(process.env.AUDIT_LATE_PROFILE_MINUTES ?? '[]');
if (!Array.isArray(lateProfileMinutes) || !lateProfileMinutes.every((minute, index) =>
    Number.isInteger(minute) && minute >= 4 && minute * 60 + 62 <= sampleSeconds &&
    (index === 0 || minute > lateProfileMinutes[index - 1] + 1))) throw new Error('Invalid AUDIT_LATE_PROFILE_MINUTES');
const transport = process.env.AUDIT_TRANSPORT;
if (transport !== undefined && !['epoll', 'io_uring'].includes(transport)) throw new Error('AUDIT_TRANSPORT must be epoll or io_uring');
const cramming = Number(process.env.AUDIT_CRAMMING ?? 0);
if (!Number.isInteger(cramming) || cramming < 0 || cramming > 1024) throw new Error('Invalid cramming rule');
const template = await fs.realpath(templateArg);
const jar = await fs.realpath(jarArg);
const dir = path.resolve(outputArg);
const java = process.env.AUDIT_JAVA ? await fs.realpath(process.env.AUDIT_JAVA)
    : execFileSync('which', ['java'], {encoding: 'utf8'}).trim();
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
const pluginArgs = JSON.parse(process.env.AUDIT_PLUGIN_JARS ?? '[]');
if (!Array.isArray(pluginArgs) || !pluginArgs.every(file => typeof file === 'string' && path.isAbsolute(file) && file.endsWith('.jar'))) {
    throw new Error('AUDIT_PLUGIN_JARS must be a JSON array of absolute JAR paths');
}
const pluginHashes = {};
for (const file of pluginArgs) {
    const name = path.basename(file);
    if (Object.hasOwn(pluginHashes, name)) throw new Error(`Duplicate plugin name: ${name}`);
    await fs.copyFile(file, path.join(dir, 'plugins', name));
    pluginHashes[name] = sha(await fs.readFile(file));
}
await fs.copyFile(jar, path.join(dir, 'server.jar'));
if (transport !== undefined) {
    const file = path.join(dir, 'shreddedpaper.yml'), yaml = await fs.readFile(file, 'utf8');
    const expression = /^(\s*prefer-io-uring-transport: )(?:true|false)$/gm;
    if ([...yaml.matchAll(expression)].length !== 1) throw new Error('Expected exactly one io_uring preference');
    await fs.writeFile(file, yaml.replace(expression, (match, prefix) => `${prefix}${transport === 'io_uring'}`));
}
for (const [environment, filename, key, max] of [
    ['AUDIT_COLLISION_LIMIT', 'config/paper-world-defaults.yml', 'max-entity-collisions', 64],
    ['AUDIT_REGION_THREADS', 'shreddedpaper.yml', 'thread-count', 16],
]) {
    if (process.env[environment] === undefined) continue;
    const value = Number(process.env[environment]);
    if (!Number.isInteger(value) || value < 1 || value > max) throw new Error(`Invalid ${environment}`);
    const file = path.join(dir, filename), yaml = await fs.readFile(file, 'utf8');
    const expression = new RegExp(`^(\\s*${key}: )-?\\d+$`, 'gm');
    if ([...yaml.matchAll(expression)].length !== 1) throw new Error(`Expected exactly one ${key} setting`);
    await fs.writeFile(file, yaml.replace(expression, (match, prefix) => `${prefix}${value}`));
}
let properties = await fs.readFile(path.join(dir, 'server.properties'), 'utf8');
for (const [key, value] of Object.entries({
    'server-ip': '127.0.0.1', 'server-port': '25685', 'rcon.port': '25686',
    'rcon.password': 'local-tracker-benchmark', 'enable-rcon': 'true',
    'online-mode': 'false', 'enable-query': 'false', 'management-server-enabled': 'false',
    'max-players': '128', 'view-distance': '2', 'simulation-distance': '2',
    ...(transport === undefined ? {} : {'use-native-transport': 'true'}),
})) {
    properties = properties.split('\n').filter(line => !line.startsWith(`${key}=`)).join('\n');
    properties += `\n${key}=${value}\n`;
}
await fs.writeFile(path.join(dir, 'server.properties'), properties);
const extraJvmArgs = JSON.parse(process.env.AUDIT_JVM_ARGS ?? '[]');
if (!Array.isArray(extraJvmArgs) || !extraJvmArgs.every(arg => typeof arg === 'string' && /^-(?:D|X|XX:)/.test(arg))) {
    throw new Error('AUDIT_JVM_ARGS must be a JSON array of JVM -D/-X/-XX options');
}
if (process.env.AUDIT_COMPRESSION !== undefined) {
    const threshold = Number(process.env.AUDIT_COMPRESSION);
    if (!Number.isInteger(threshold) || threshold < -1 || threshold > 1048576) throw new Error('Invalid compression threshold');
    properties = properties.split('\n').filter(line => !line.startsWith('network-compression-threshold=')).join('\n');
    await fs.writeFile(path.join(dir, 'server.properties'), `${properties}\nnetwork-compression-threshold=${threshold}\n`);
}
const args = ['--enable-preview', '-Dterminal.jline=false', '-Dterminal.ansi=false',
    '-Xms1G', '-Xmx3G', '-XX:ActiveProcessorCount=2', ...extraJvmArgs, '-jar', 'server.jar', '--nogui'];
const manifest = {
    createdAt: new Date().toISOString(), sourceHead: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: repo, encoding: 'utf8'}).trim(),
    preexistingSourceDiff: execFileSync('git', ['diff', '--', 'shreddedpaper-server'], {cwd: repo, encoding: 'utf8'}),
    jarSha256: sha(await fs.readFile(jar)), java, args, javaVersion: execFileSync(java, ['--version'], {encoding: 'utf8'}),
    template, host: {arch: os.arch(), cpus: os.cpus().length, freeMemory: os.freemem(), load: os.loadavg()},
    players, mobs, cramming, mobType: 'cow', sampleSeconds, clientMode, pluginHashes, lateProfileMinutes,
    requestedTransport: transport ?? 'template-default', phases: [], configHashes: {}, resourceSamples: [],
};
for (const name of [...configFiles, 'config/paper-global.yml', 'config/paper-world-defaults.yml']) {
    manifest.configHashes[name] = sha(await fs.readFile(path.join(dir, name)));
}
let manifestWrites = Promise.resolve();
const saveManifest = () => {
    const content = JSON.stringify(manifest, null, 2);
    manifestWrites = manifestWrites.then(async () => {
        await fs.writeFile(path.join(dir, 'manifest.json.tmp'), content);
        await fs.rename(path.join(dir, 'manifest.json.tmp'), path.join(dir, 'manifest.json'));
    });
    return manifestWrites;
};
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
let resourceTimer;
let resourceSampling = false;
try {
    await awaitLog(server, path.join(dir, 'console.log'), 'For help, type "help"', 180);
    if (transport !== undefined) {
        const taskDir = `/proc/${server.child.pid}/task`, fdDir = `/proc/${server.child.pid}/fd`;
        const names = await Promise.all((await fs.readdir(taskDir)).map(async id => {
            try { return (await fs.readFile(`${taskDir}/${id}/comm`, 'utf8')).trim(); } catch { return ''; }
        }));
        const descriptors = await Promise.all((await fs.readdir(fdDir)).map(async id => {
            try { return await fs.readlink(`${fdDir}/${id}`); } catch { return ''; }
        }));
        const rings = descriptors.filter(name => /^anon_inode:(?:\[io_uring\]|io_uring)$/.test(name)).length;
        const observed = names.some(name => name.startsWith('Netty io_uring')) && rings > 0 ? 'io_uring'
            : names.some(name => name.startsWith('Netty Epoll')) ? 'epoll' : 'unknown';
        manifest.observedTransport = {type: observed, rings, threads: names.filter(name => name.startsWith('Netty'))};
        await saveManifest();
        if (observed !== transport) throw new Error(`Requested ${transport}, observed ${observed}`);
    }
    console.log(`READY ${dir} pid=${server.child.pid}`);
    async function sampleResources() {
        if (resourceSampling) return;
        resourceSampling = true;
        try {
            const status = await fs.readFile(`/proc/${server.child.pid}/status`, 'utf8');
            const value = key => Number(status.match(new RegExp(`^${key}:\\s+(\\d+)`, 'm'))?.[1] ?? 0);
            manifest.resourceSamples.push({at: new Date().toISOString(), rssKiB: value('VmRSS'),
                rssAnonKiB: value('RssAnon'), threads: value('Threads'),
                descriptors: (await fs.readdir(`/proc/${server.child.pid}/fd`)).length, hostLoad: os.loadavg()});
            await saveManifest();
        } catch (error) { manifest.resourceSampleError = error.message; }
        finally { resourceSampling = false; }
    }
    await sampleResources();
    resourceTimer = setInterval(sampleResources, 30000);
    clients = launch('node', [path.join(import.meta.dirname, 'clients.mjs'), String(players), String(sampleSeconds),
        path.join(dir, 'clients.json'), clientMode], repo, path.join(dir, 'clients.log'));
    await awaitLog(clients, path.join(dir, 'clients.log'), 'Sampling;', 240);
    const samplingStart = performance.now();
    async function profile(name, seconds) {
        const phase = {name, start: new Date().toISOString(), seconds, packetDiagnosticsDisabled: true, before: await rcon('region dump')};
        const clockTicks = Number(execFileSync('getconf', ['CLK_TCK'], {encoding: 'utf8'}));
        async function cpuSnapshot() {
            const threads = {};
            for (const id of await fs.readdir(`/proc/${server.child.pid}/task`)) {
                try {
                    const stat = await fs.readFile(`/proc/${server.child.pid}/task/${id}/stat`, 'utf8');
                    const end = stat.lastIndexOf(')'), fields = stat.slice(end + 2).split(' ');
                    threads[id] = {name: stat.slice(stat.indexOf('(') + 1, end), user: Number(fields[11]), system: Number(fields[12])};
                } catch { /* Threads can end between the directory and stat reads. */ }
            }
            return {at: performance.now(), threads};
        }
        const cpuBefore = await cpuSnapshot();
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
        const cpuAfter = await cpuSnapshot(), groups = {};
        for (const [id, current] of Object.entries(cpuAfter.threads)) {
            const previous = cpuBefore.threads[id];
            if (!previous) continue;
            const group = current.name.startsWith('Netty') ? 'netty'
                : current.name.startsWith('iou-wrk') ? 'io_uring_workers'
                : current.name.startsWith('AstatineRegion') ? 'region' : 'other';
            const value = groups[group] ??= {userMs: 0, systemMs: 0, threads: 0};
            value.userMs += (current.user - previous.user) * 1000 / clockTicks;
            value.systemMs += (current.system - previous.system) * 1000 / clockTicks;
            value.threads++;
        }
        phase.threadCpu = {elapsedMs: cpuAfter.at - cpuBefore.at, clockTicks, groups,
            limitation: 'Delta for threads present at both boundaries; excludes exited/new threads and IRQ CPU.'};
        phase.end = new Date().toISOString();
        phase.after = await rcon('region dump');
        phase.bytes = (await fs.stat(output)).size;
        manifest.phases.push(phase);
        await saveManifest();
    }
    await profile('players', 45);
    const commands = [
        `gamerule minecraft:max_entity_cramming ${cramming}`,
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
    for (const minute of lateProfileMinutes) {
        const remaining = minute * 60000 - (performance.now() - samplingStart);
        if (remaining > 0) await Promise.race([sleep(remaining), clients.done.then(result => {
            throw new Error(`Clients exited before late profile: ${JSON.stringify(result)}`);
        })]);
        if (!(await rcon('list')).startsWith(`There are ${players} of`)) throw new Error('Late profile population mismatch');
        if ((await rcon('execute if entity @e[tag=crowded_audit]')).trim() !== `Test passed. Count: ${mobs}`) throw new Error('Late profile mob mismatch');
        await profile(`steady-${minute}`, 60);
    }
    const result = await clients.done;
    if (result.code !== 0) throw new Error(`Client harness failed: ${JSON.stringify(result)}`);
    const acceptance = JSON.parse(await fs.readFile(path.join(dir, 'clients.json'), 'utf8'));
    if (acceptance.faults.length || !acceptance.allClientsSeeEveryOtherPlayer || acceptance.samples.length !== sampleSeconds) {
        throw new Error('Invalid client acceptance');
    }
    manifest.acceptance = {samples: acceptance.samples.length, faults: acceptance.faults.length,
        allClientsSeeEveryOtherPlayer: acceptance.allClientsSeeEveryOtherPlayer};
    if (process.env.AUDIT_CHURN_CONNECTIONS !== undefined) {
        const count = Number(process.env.AUDIT_CHURN_CONNECTIONS);
        if (!Number.isInteger(count) || count < 4 || count > 1000 || count % 4) throw new Error('Invalid AUDIT_CHURN_CONNECTIONS');
        const churn = launch('node', [path.join(repo, 'tools/benchmark/io-uring/churn.mjs'),
            path.join(dir, 'churn.json'), String(count)], repo, path.join(dir, 'churn.log'));
        clients = churn; // Preserve owned-process cleanup if the follow-up fails.
        const churnExit = await churn.done;
        if (churnExit.code !== 0) throw new Error(`Churn harness failed: ${JSON.stringify(churnExit)}`);
        manifest.churn = JSON.parse(await fs.readFile(path.join(dir, 'churn.json'), 'utf8'));
        await sampleResources();
    }
} catch (error) {
    manifest.error = error.stack;
    throw error;
} finally {
    clearInterval(resourceTimer);
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
