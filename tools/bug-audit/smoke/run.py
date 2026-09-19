#!/usr/bin/env python3
"""Build the smoke plugin and run it in a new temporary, loopback-only server."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser()
parser.add_argument("--server-jar", type=Path, required=True)
parser.add_argument("--libraries", type=Path, required=True, help="An existing 1.21.11 server libraries directory")
args = parser.parse_args()
source = Path(__file__).resolve().parent
repo = source.parents[2]
input_jar = args.server_jar.resolve(strict=True)
libraries = args.libraries.resolve(strict=True)
work = Path(tempfile.mkdtemp(prefix="astatine-bug-smoke-"))
server_jar = work / "server.jar"
shutil.copyfile(input_jar, server_jar)
classes = work / "plugin-classes"
classes.mkdir()
plugins = work / "plugins"
plugins.mkdir()
classpath = os.pathsep.join(map(str, [repo / "shreddedpaper-api/build/classes/java/main", *sorted(libraries.rglob("*.jar"))]))
subprocess.run(["javac", "--release", "25", "--enable-preview", "-classpath", classpath,
                "-d", str(classes), str(source / "WorldClockSmoke.java")], check=True)
shutil.copy2(source / "plugin.yml", classes)
subprocess.run(["jar", "--create", "--file", str(plugins / "world-clock-smoke.jar"), "-C", str(classes), "."], check=True)
shutil.copytree(libraries, work / "libraries")
(work / "eula.txt").write_text("eula=true\n")
(work / "server.properties").write_text("\n".join([
    "server-ip=127.0.0.1", "server-port=0", "online-mode=false", "enable-query=false", "enable-rcon=false",
    "level-type=minecraft:flat", "view-distance=2", "simulation-distance=2", "max-players=1", "spawn-protection=0",
    'generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","structure_overrides":[]}',
    "initial-enabled-packs=vanilla,paper", "generate-structures=false", "motd=Astatine disposable bug smoke", "",
]))
(work / "shreddedpaper.yml").write_text("multithreading:\n  thread-count: 2\n  independent-region-ticking: true\n")
print(f"SMOKE_WORKDIR={work}", flush=True)
with server_jar.open("rb") as jar_input:
    print(f"SMOKE_JAR_SHA256={hashlib.file_digest(jar_input, 'sha256').hexdigest()}", flush=True)
log_path = work / "console.log"
with log_path.open("w") as log:
    process = subprocess.Popen(["java", "--enable-preview", "-Dastatine.bugAuditSmoke=true", "-XX:ActiveProcessorCount=2", "-Xms512m", "-Xmx1536m",
                                "-jar", str(server_jar), "--nogui"], cwd=work, stdin=subprocess.PIPE,
                               stdout=log, stderr=subprocess.STDOUT, text=True)
    try:
        deadline = time.monotonic() + 300
        while process.poll() is None and time.monotonic() < deadline:
            time.sleep(1)
        if process.poll() is None:
            process.stdin.write("stop\n")
            process.stdin.flush()
            process.wait(timeout=60)
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        process.stdin.close()
text = log_path.read_text(errors="replace")
for line in text.splitlines():
    if "AUDIT_" in line or "Done (" in line or "Closing Server" in line:
        print(line)
if process.returncode != 0 or "AUDIT_SMOKE_PASS" not in text or "AUDIT_SMOKE_FAIL" in text:
    raise SystemExit(f"Smoke failed (exit={process.returncode}); inspect {log_path}")
print("SMOKE_PROCESS_EXIT=0")
