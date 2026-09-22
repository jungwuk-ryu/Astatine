#!/usr/bin/env python3
"""Compression + actual PacketEvents/ProtocolLib + recipient-specific encode and echo checks."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('template', type=Path)
parser.add_argument('server_jar', type=Path)
parser.add_argument('packetevents', type=Path)
parser.add_argument('protocollib', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
with socket.socket() as probe:
    probe.bind(('127.0.0.1', 25687))
source = Path(__file__).resolve().parent
repo = source.parents[2]
work = args.output.resolve()
work.mkdir() # Never overwrite evidence or start in an existing server directory.
template = args.template.resolve(strict=True)
for name in ['world', 'world_nether', 'world_the_end', 'config']:
    shutil.copytree(template / name, work / name)
for name in ['libraries', 'cache']:
    (work / name).symlink_to(template / name, target_is_directory=True)
for name in ['eula.txt', 'bukkit.yml', 'spigot.yml', 'purpur.yml', 'shreddedpaper.yml']:
    shutil.copyfile(template / name, work / name)
properties = dict(line.split('=', 1) for line in (template / 'server.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
properties.update({'server-ip': '127.0.0.1', 'server-port': '25687', 'enable-rcon': 'false', 'enable-query': 'false',
    'online-mode': 'false', 'allow-flight': 'true', 'management-server-enabled': 'false',
    'network-compression-threshold': '128', 'view-distance': '2', 'simulation-distance': '2'})
(work / 'server.properties').write_text(''.join(f'{key}={value}\n' for key, value in properties.items()))
plugins = work / 'plugins'; plugins.mkdir()
for path in [args.packetevents, args.protocollib]: shutil.copyfile(path, plugins / path.name)
classes = work / 'smoke-classes'; classes.mkdir()
classpath = os.pathsep.join(map(str, [repo / 'shreddedpaper-server/build/classes/java/main',
    repo / 'shreddedpaper-api/build/classes/java/main', args.packetevents.resolve(), *sorted((template / 'libraries').rglob('*.jar'))]))
subprocess.run(['javac', '--release', '25', '--enable-preview', '-cp', classpath, '-d', str(classes), str(source / 'NetworkPluginSmoke.java')], check=True)
(classes / 'plugin.yml').write_text("name: NetworkPluginSmoke\nversion: '1.0'\nmain: io.multipaper.audit.NetworkPluginSmoke\napi-version: '1.21'\nfolia-supported: true\ndepend: [packetevents]\n")
subprocess.run(['jar', '--create', '--file', str(plugins / 'network-plugin-smoke.jar'), '-C', str(classes), '.'], check=True)
shutil.copyfile(args.server_jar, work / 'server.jar')
java = os.environ.get('AUDIT_JAVA', 'java')
command = [java, '--enable-preview', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-Dastatine.networkPluginSmoke=true',
    '-Dastatine.packet-batch-diagnostics=true', '-XX:ActiveProcessorCount=2', '-Xms512m', '-Xmx2G', '-jar', 'server.jar', '--nogui']
manifest = {'artifacts': {str(path): hashlib.sha256(path.read_bytes()).hexdigest() for path in [args.server_jar, args.packetevents, args.protocollib]}, 'command': command}
with (work / 'console.log').open('w') as log:
    server = subprocess.Popen(command, cwd=work, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True)
    try:
        for _ in range(180):
            if server.poll() is not None: raise RuntimeError('Server exited during startup')
            if 'For help, type "help"' in (work / 'console.log').read_text(errors='replace'): break
            time.sleep(1)
        else: raise TimeoutError('Server startup timed out')
        with (work / 'clients.log').open('w') as clients_log:
            result = subprocess.run(['node', str(source / 'network-smoke-clients.mjs'), str(work / 'clients.json')],
                cwd=repo, stdout=clients_log, stderr=subprocess.STDOUT, timeout=90)
            manifest['client_exit'] = result.returncode
    finally:
        if server.poll() is None:
            server.stdin.write('stop\n'); server.stdin.flush()
            try: server.wait(timeout=90)
            except subprocess.TimeoutExpired: server.terminate(); server.wait(timeout=15)
        manifest['server_exit'] = server.returncode
        (work / 'manifest.json').write_text(json.dumps(manifest, indent=2))
text = (work / 'console.log').read_text(errors='replace')
for line in text.splitlines():
    if 'NETWORK_PLUGIN_SMOKE_' in line: print(line)
if manifest.get('client_exit') != 0 or manifest['server_exit'] != 0 or 'NETWORK_PLUGIN_SMOKE_PASS' not in text or 'NETWORK_PLUGIN_SMOKE_FAIL' in text:
    raise SystemExit(f'Network smoke failed; inspect {work}')
print('NETWORK_SMOKE_PASS', work)
