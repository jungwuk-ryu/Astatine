#!/usr/bin/env python3
"""Compile/run the locker probe against an already extracted, isolated server artifact."""
import argparse
import hashlib
import json
from pathlib import Path
import os
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("server", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
server = args.server.resolve(strict=True)
runtime = list((server / "versions").rglob("shreddedpaper-*.jar"))
if len(runtime) != 1:
    raise SystemExit("Expected one extracted runtime jar")
classpath = os.pathsep.join(map(str, [runtime[0], *sorted((server / "libraries").rglob("*.jar"))]))
source = Path(__file__).resolve().with_name("LockProbe.java")
with tempfile.TemporaryDirectory(prefix="astatine-lock-probe-") as output:
    subprocess.run(["javac", "--enable-preview", "--release", "25", "-cp", classpath, "-d", output, str(source)], check=True)
    run = subprocess.run(["java", "--enable-preview", "-XX:ActiveProcessorCount=2", "-Xms128m", "-Xmx512m",
                          "-cp", output + os.pathsep + classpath, "LockProbe"], cwd=output, text=True,
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=120)
args.output.with_suffix(".log").write_text(run.stdout)
if run.returncode != 0 or "LOCK_PROBE_PASS" not in run.stdout:
    raise SystemExit(run.stdout)
rows = [json.loads(line.split("PROBE ", 1)[1]) for line in run.stdout.splitlines() if "PROBE {" in line]
result = {"runtime_jar": str(runtime[0]), "runtime_sha256": hashlib.sha256(runtime[0].read_bytes()).hexdigest(),
          "java": subprocess.check_output(["java", "--version"], text=True), "rows": rows,
          "limits": "Uncontended CPU/allocations and deterministic geometry; not live contention or server throughput."}
args.output.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps(result, indent=2))
