#!/usr/bin/env python3
"""Exercise an unmodified vanilla client against an owned loopback server."""
import argparse
import ctypes
import ctypes.util
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import threading
import time
from urllib.parse import parse_qs, urlparse
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('template', type=Path)
parser.add_argument('jar', type=Path)
parser.add_argument('client_cache', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--transport', choices=['epoll', 'io_uring'], required=True)
parser.add_argument('--java', type=Path, required=True)
parser.add_argument('--plugin', type=Path, action='append', default=[])
parser.add_argument('--repeats', type=int, default=3)
parser.add_argument('--display', default=':131')
parser.add_argument('--server-port', type=int, default=25689)
parser.add_argument('--rcon-port', type=int, default=25690)
parser.add_argument('--hold-seconds', type=int, default=0)
parser.add_argument('--mock-session', action='store_true', help='Exercise encrypted login with a loopback session API; no real Mojang account')
args = parser.parse_args()
if not 1 <= args.repeats <= 10:
    parser.error('repeats must be 1..10')
if not 0 <= args.hold_seconds <= 3600:
    parser.error('hold-seconds must be 0..3600')
if args.server_port == args.rcon_port or not all(1024 <= p <= 65535 for p in [args.server_port, args.rcon_port]):
    parser.error('server-port and rcon-port must be distinct ports in 1024..65535')
repo = Path(__file__).resolve().parents[3]
root = args.output.resolve()
template = args.template.resolve(strict=True)
cache = args.client_cache.resolve(strict=True)
jar = args.jar.resolve(strict=True)
java = args.java.resolve(strict=True)
version = json.loads((cache / 'version.json').read_text())
client_sha1 = hashlib.sha1((cache / 'client.jar').read_bytes()).hexdigest()
if version['id'] != '1.21.11' or client_sha1 != version['downloads']['client']['sha1']:
    raise RuntimeError('Client must match the official vanilla 1.21.11 artifact')
for port in [args.server_port, args.rcon_port]:
    with socket.socket() as probe:
        probe.bind(('127.0.0.1', port))
if Path('/tmp/.X11-unix/X' + args.display.removeprefix(':')).exists():
    raise RuntimeError('Display is already in use')
root.mkdir()
server_dir = root / 'server'
server_dir.mkdir()
for name in ['world', 'world_nether', 'world_the_end', 'config']:
    shutil.copytree(template / name, server_dir / name)
for name in ['cache', 'libraries']:
    (server_dir / name).symlink_to((template / name).resolve(), target_is_directory=True)
for name in ['bukkit.yml', 'spigot.yml', 'purpur.yml', 'shreddedpaper.yml', 'eula.txt']:
    shutil.copy2(template / name, server_dir / name)
cfg = server_dir / 'shreddedpaper.yml'
text, count = re.subn(r'(?m)^(\s*prefer-io-uring-transport: )(?:true|false)$',
                     lambda m: m[1] + str(args.transport == 'io_uring').lower(), cfg.read_text())
if count != 1:
    raise RuntimeError('Expected exactly one io_uring preference')
cfg.write_text(text)
properties = dict(line.split('=', 1) for line in (template / 'server.properties').read_text().splitlines()
                  if '=' in line and not line.startswith('#'))
properties.update({'server-ip': '127.0.0.1', 'server-port': str(args.server_port), 'rcon.port': str(args.rcon_port),
                   'rcon.password': 'native-transport-local-qa', 'enable-rcon': 'true',
                   'online-mode': 'false', 'enforce-secure-profile': 'false', 'white-list': 'false',
                   'enable-query': 'false', 'management-server-enabled': 'false',
                   'use-native-transport': 'true', 'allow-flight': 'true', 'gamemode': 'creative',
                   'network-compression-threshold': '512', 'view-distance': '4', 'simulation-distance': '2',
                   'resource-pack': '', 'resource-pack-sha1': '', 'require-resource-pack': 'false'})
if args.mock_session:
    properties['online-mode'] = 'true'
(server_dir / 'server.properties').write_text(''.join(f'{k}={v}\n' for k, v in properties.items()))
plugins = server_dir / 'plugins'
plugins.mkdir()
for plugin in args.plugin:
    shutil.copy2(plugin, plugins / plugin.name)
shutil.copy2(jar, server_dir / 'server.jar')
username = 'NativeUringQA'
ident = str(uuid.UUID(bytes=hashlib.md5(('OfflinePlayer:' + username).encode()).digest(), version=3))
manifest = {'transport': args.transport, 'serverPort': args.server_port, 'rconPort': args.rcon_port,
            'jarSha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
            'vanillaClientSha1': client_sha1, 'authentication': 'loopback-session-api' if args.mock_session else 'offline',
            'realMojangAccount': False, 'attempts': [],
            'pluginHashes': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in args.plugin}}
processes = []
logs = []
session_api = None
auth_args = []
if args.mock_session:
    joined_hashes = set()
    auth_events = []

    class SessionAPI(BaseHTTPRequestHandler):
        def log_message(self, *unused):
            pass

        def respond(self, status, value=None):
            payload = b'' if value is None else json.dumps(value).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_POST(self):
            body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
            if self.path == '/session/minecraft/join':
                data = json.loads(body)
                if data.get('selectedProfile', '').replace('-', '') != ident.replace('-', ''):
                    self.respond(403)
                    return
                joined_hashes.add(data['serverId'])
                auth_events.append({'operation': 'join', 'profileMatched': True})
                self.respond(204)
            else:
                self.respond(404)

        def do_GET(self):
            parsed = urlparse(self.path)
            if parsed.path == '/session/minecraft/hasJoined':
                query = parse_qs(parsed.query)
                matched = query.get('username') == [username] and query.get('serverId', [None])[0] in joined_hashes
                auth_events.append({'operation': 'hasJoined', 'serverHashMatched': matched})
                self.respond(200 if matched else 204,
                             {'id': ident.replace('-', ''), 'name': username, 'properties': []} if matched else None)
            elif parsed.path.startswith('/session/minecraft/profile/'):
                self.respond(200, {'id': ident.replace('-', ''), 'name': username, 'properties': []})
            elif parsed.path == '/publickeys':
                self.respond(200, {'profilePropertyKeys': [], 'playerCertificateKeys': []})
            else:
                self.respond(404)

    session_api = ThreadingHTTPServer(('127.0.0.1', 0), SessionAPI)
    threading.Thread(target=session_api.serve_forever, daemon=True).start()
    origin = f'http://127.0.0.1:{session_api.server_port}'
    auth_args = [f'-Dminecraft.api.{name}.host={origin}' for name in ['session', 'services', 'profiles']]

def launch(name, command, cwd=None, env=None):
    log = (root / (name + '.log')).open('w')
    logs.append(log)
    process = subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True, start_new_session=True)
    processes.append(process)
    return process

def rcon(command):
    module = (repo / 'tools/benchmark/tracker/rcon.mjs').as_uri()
    script = ('import {sendRconCommand} from ' + json.dumps(module) + ';'
              f'process.stdout.write(await sendRconCommand({args.rcon_port},"native-transport-local-qa",process.argv[1],15000));')
    return subprocess.check_output(['node', '--input-type=module', '-e', script, command], text=True, timeout=20).strip()

def observed_transport(pid):
    names, rings = [], 0
    for task in Path(f'/proc/{pid}/task').iterdir():
        try:
            name = (task / 'comm').read_text().strip()
            if name.startswith('Netty'):
                names.append(name)
        except OSError:
            pass
    for fd in Path(f'/proc/{pid}/fd').iterdir():
        try:
            if os.readlink(fd) in ['anon_inode:[io_uring]', 'anon_inode:io_uring']:
                rings += 1
        except OSError:
            pass
    selected = ('io_uring' if rings and any(n.startswith('Netty io_uring') for n in names)
                else 'epoll' if any(n.startswith('Netty Epoll') for n in names) else 'unknown')
    return {'type': selected, 'rings': rings, 'threads': names}

def forward(seconds):
    x11 = ctypes.CDLL(ctypes.util.find_library('X11'))
    xtst = ctypes.CDLL(ctypes.util.find_library('Xtst'))
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
    x11.XKeysymToKeycode.restype = ctypes.c_uint
    x11.XFlush.argtypes = [ctypes.c_void_p]
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    xtst.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
    display = x11.XOpenDisplay(args.display.encode())
    if not display:
        raise RuntimeError('Cannot open owned display')
    key = x11.XKeysymToKeycode(display, ord('w'))
    try:
        xtst.XTestFakeKeyEvent(display, key, 1, 0)
        x11.XFlush(display)
        time.sleep(seconds)
    finally:
        xtst.XTestFakeKeyEvent(display, key, 0, 0)
        x11.XFlush(display)
        x11.XCloseDisplay(display)

server = None
try:
    display = launch('display', ['Xvfb', args.display, '-screen', '0', '1280x800x24', '-ac',
                                 '+extension', 'GLX', '+render', '-noreset'])
    server = launch('server', [str(java), '--enable-preview', '--enable-native-access=ALL-UNNAMED',
                               '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dio.netty.eventLoopThreads=4',
                               '-XX:ActiveProcessorCount=2', '-Xms512m', '-Xmx2G', *auth_args, '-jar', 'server.jar', '--nogui'], server_dir)
    manifest['serverPid'] = server.pid
    for _ in range(180):
        if server.poll() is not None:
            raise RuntimeError('Server exited during startup')
        if 'For help, type "help"' in (root / 'server.log').read_text(errors='replace'):
            break
        time.sleep(1)
    else:
        raise TimeoutError('Server startup timed out')
    manifest['observedTransport'] = observed_transport(server.pid)
    if manifest['observedTransport']['type'] != args.transport:
        raise RuntimeError('Requested transport did not activate')
    for command in ['difficulty peaceful', 'gamerule minecraft:spawn_mobs false', 'time set day', 'weather clear']:
        rcon(command)
    env = dict(os.environ, DISPLAY=args.display, LIBGL_ALWAYS_SOFTWARE='true', ALSOFT_DRIVERS='null', LP_NUM_THREADS='2')
    for attempt in range(args.repeats):
        game = root / f'game-{attempt}'
        game.mkdir()
        (game / 'options.txt').write_text('lang:en_us\nmaxFps:30\nrenderDistance:4\nsimulationDistance:5\n'
                                         'enableVsync:false\nfullscreen:false\nresourcePacks:[]\n'
                                         'soundCategory_master:0.0\npauseOnLostFocus:false\n'
                                         'onboardAccessibility:true\nskipMultiplayerWarning:true\ntutorialStep:none\n')
        client = launch(f'client-{attempt}', [str(java), '--enable-native-access=ALL-UNNAMED',
                '-Xms256m', '-Xmx1200m', '-XX:ActiveProcessorCount=2',
                '-Djava.library.path=' + str(cache / 'natives'),
                '-Dorg.lwjgl.system.SharedLibraryExtractPath=' + str(cache / 'natives'),
                '-Dminecraft.launcher.brand=EartopiaQA', '-Dminecraft.launcher.version=1',
                *auth_args,
                '-cp', (cache / 'classpath.txt').read_text().strip(), 'net.minecraft.client.main.Main',
                '--username', username, '--version', '1.21.11', '--gameDir', str(game),
                '--assetsDir', str(cache / 'assets'), '--assetIndex', version['assetIndex']['id'],
                '--uuid', ident, '--accessToken', '0', '--userType', 'legacy',
                '--width', '1280', '--height', '800', '--quickPlayMultiplayer', f'127.0.0.1:{args.server_port}'], cwd=game, env=env)
        for _ in range(120):
            if client.poll() is not None:
                raise RuntimeError('Vanilla client exited before joining')
            if username in rcon('list'):
                break
            time.sleep(1)
        else:
            raise TimeoutError('Vanilla client did not join')
        time.sleep(12)
        rcon(f'gamemode creative {username}')
        rcon(f'tp {username} 8.5 -60 8.5')
        time.sleep(3)
        before = rcon(f'data get entity {username} Pos')
        forward(2)
        time.sleep(2)
        after = rcon(f'data get entity {username} Pos')
        if before == after or not before or not after:
            raise RuntimeError('Native keyboard movement was not acknowledged')
        screenshot = root / f'native-{attempt}.png'
        subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'x11grab', '-video_size', '1280x800',
                        '-i', args.display, '-frames:v', '1', str(screenshot)], check=True, timeout=15)
        dimensions = []
        for dimension, y in [('minecraft:the_nether', 100), ('minecraft:overworld', -60)]:
            rcon(f'execute in {dimension} run tp {username} 8 {y} 8')
            time.sleep(8)
            current = rcon(f'data get entity {username} Dimension')
            if dimension not in current or username not in rcon('list'):
                raise RuntimeError('Native client lost connection during dimension transfer')
            dimensions.append(current)
        manifest['attempts'].append({'index': attempt, 'before': before, 'after': after,
                                      'dimensions': dimensions, 'screenshot': screenshot.name, 'connected': True})
        held = time.monotonic()
        while time.monotonic() - held < args.hold_seconds:
            if client.poll() is not None or username not in rcon('list'):
                raise RuntimeError('Native client lost connection during hold')
            time.sleep(max(0, min(5, args.hold_seconds - (time.monotonic() - held))))
        manifest['attempts'][-1]['heldSeconds'] = time.monotonic() - held
        print('NATIVE_LOGIN_PASS', args.transport, attempt, flush=True)
        client.terminate()
        try:
            client.wait(timeout=20)
        except subprocess.TimeoutExpired:
            client.kill()
            client.wait(timeout=5)
        for _ in range(20):
            if username not in rcon('list'):
                break
            time.sleep(1)
        else:
            raise RuntimeError('Disconnected native player was not removed')
    if args.mock_session:
        joins = [event for event in auth_events if event['operation'] == 'join']
        matched = [event for event in auth_events if event['operation'] == 'hasJoined' and event['serverHashMatched']]
        if len(joins) != args.repeats or len(matched) != args.repeats:
            raise RuntimeError('Encrypted login did not complete matching client/server session requests')
        manifest['encryptedSessionEvents'] = auth_events
    manifest['status'] = 'passed'
except BaseException as error:
    manifest['status'] = 'failed'
    manifest['error'] = repr(error)
    raise
finally:
    if server is not None and server.poll() is None:
        server.stdin.write('stop\n')
        server.stdin.flush()
        try:
            server.wait(timeout=60)
        except subprocess.TimeoutExpired:
            server.terminate()
    if server is not None:
        manifest['serverExit'] = server.poll()
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
    for log in logs:
        log.close()
    if session_api is not None:
        session_api.shutdown()
        session_api.server_close()
    (root / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
if manifest['serverExit'] != 0:
    raise RuntimeError('Owned server did not stop cleanly')
print('NATIVE_TRANSPORT_PASS', args.transport, root)
