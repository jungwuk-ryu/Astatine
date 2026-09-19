#!/usr/bin/env python3
"""Evaluate the submitted queue against the checkout's actual Minecraft nodes.

This compiles a standalone lab. It never patches, launches, or configures a server.
Each (fork, implementation, workload) measurement runs in a fresh JVM.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import json
import os
from pathlib import Path
import platform
import queue
import re
import shutil
import statistics
import subprocess
import time

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
WORKLOADS = ["singleton_64", "same_64", "ascending_16", "descending_16",
             "mixed_256", "sparse_256", "reprioritize_128", "duplicates_128",
             "interleaved_256", "fanout_24", "clear_256"]
COLUMNS = ["fork", "cpu", "variant", "workload", "sample", "cycles",
           "elapsed_ns", "ns_per_cycle", "bytes_per_cycle", "checksum"]
SAMPLE = re.compile(r"(?:ac|ec),[a-z0-9_]+,\d+,\d+,\d+,[0-9.]+,[0-9.-]+,-?\d+")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--server-jar", type=Path, required=True)
    parser.add_argument("--api-jar", type=Path, required=True)
    parser.add_argument("--libraries", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source-root", type=Path,
                        default=REPO / "shreddedpaper-server/src/minecraft/java")
    parser.add_argument("--workloads", nargs="+", choices=WORKLOADS, default=WORKLOADS)
    parser.add_argument("--forks", type=int, default=3)
    parser.add_argument("--warmup-ms", type=int, default=3000)
    parser.add_argument("--samples", type=int, default=5)
    parser.add_argument("--sample-ms", type=int, default=300)
    parser.add_argument("--cpus", help="Comma-separated allowed CPUs; one measurement per CPU")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    if min(args.forks, args.samples, args.sample_ms) < 1 or args.warmup_ms < 0:
        parser.error("Invalid count or duration")
    allowed = sorted(os.sched_getaffinity(0))
    cpus = list(map(int, args.cpus.split(","))) if args.cpus else allowed[:1]
    if len(set(cpus)) != len(cpus) or not set(cpus).issubset(allowed):
        parser.error("CPUs must be distinct members of the process affinity set")
    out = args.output.resolve()
    if out.exists() and any(out.iterdir()):
        parser.error("Output directory must be empty to avoid mixing runs")
    out.mkdir(parents=True, exist_ok=True)
    classes = out / "classes"
    classes.mkdir()
    source_dir = out / "sources"
    source_dir.mkdir()
    sources = list((HERE / "src").rglob("*.java"))
    sources += [args.source_root / f"alternate/current/wire/{name}.java"
                for name in ("Node", "WireNode", "PriorityQueue")]
    hashes = {}
    for source in sources:
        target = source_dir / source.name
        if target.exists():
            raise RuntimeError(f"Duplicate source: {source.name}")
        shutil.copyfile(source, target)
        hashes[source.name] = digest(target)
    jars = []
    for name, source in (("server.jar", args.server_jar), ("api.jar", args.api_jar)):
        target = out / name
        shutil.copyfile(source, target)
        jars.append(target)
        hashes[name] = digest(target)
    libraries = sorted(args.libraries.resolve().rglob("*.jar"))
    if not libraries:
        raise RuntimeError("No runtime library jars found")
    classpath = os.pathsep.join(map(str, [classes, *jars, *libraries]))
    subprocess.run(["javac", "--enable-preview", "--release", "25", "-cp", classpath,
                    "-d", str(classes), *map(str, sorted(source_dir.glob("*.java")))], check=True)
    java = ["java", "--enable-preview", "-Xms256m", "-Xmx512m", "-XX:+UseG1GC",
            "-cp", classpath]
    environment = {
        "scope": "Real Minecraft Node/WireNode queue kernels; not engine time or MSPT",
        "platform": platform.platform(),
        "java": subprocess.run(["java", "-version"], capture_output=True, text=True).stderr,
        "cpuinfo": Path("/proc/cpuinfo").read_text().split("\n\n")[0],
        "cpus": cpus, "forks": args.forks, "warmup_ms": args.warmup_ms,
        "samples": args.samples, "sample_ms": args.sample_ms, "workloads": args.workloads,
        "jvm_flags": java[1:5], "source_sha256": hashes,
        "objects": "Alternating actual WireNode and Node with neighborWire; preallocated",
        "order": "Fresh JVM per workload and variant; variant order alternates by fork + workload index; paired runs use the same CPU",
        "started_at_unix": time.time(),
    }
    (out / "environment.json").write_text(json.dumps(environment, indent=2) + "\n")

    def run_jvm(name, arguments, cpu=None):
        directory = out / "jvm" / name
        directory.mkdir(parents=True)
        command = java + arguments
        if cpu is not None:
            command = ["taskset", "-c", str(cpu), *command]
        result = subprocess.run(command, cwd=directory, text=True,
                                capture_output=True, timeout=120)
        (directory / "stdout.log").write_text(result.stdout)
        (directory / "stderr.log").write_text(result.stderr)
        if result.returncode:
            raise RuntimeError(f"{name} failed; see {directory}")
        return result.stdout

    tests = run_jvm("contract", ["alternate.current.wire.QueueTest"])
    tests += run_jvm("trace-inputs", ["alternate.current.wire.QueueBenchmark", "verify"])
    (out / "validation.log").write_text(tests)
    print("PASS: actual-node contract and all 2,816 input cycles", flush=True)
    if args.verify_only:
        return
    available = queue.Queue()
    for cpu in cpus:
        available.put(cpu)

    def measure(fork, index, workload):
        cpu = available.get()
        try:
            rows = []
            variants = ["ac", "ec"] if (fork + index) % 2 == 0 else ["ec", "ac"]
            for variant in variants:
                print(f"fork={fork} cpu={cpu} {workload} {variant}", flush=True)
                stdout = run_jvm(f"{fork}-{workload}-{variant}", [
                    "alternate.current.wire.QueueBenchmark", "one", variant, workload,
                    str(args.warmup_ms), str(args.samples), str(args.sample_ms)], cpu)
                samples = list(csv.reader(SAMPLE.findall(stdout)))
                if len(samples) != args.samples:
                    raise RuntimeError(f"Unexpected sample count: {fork} {workload} {variant}")
                rows.extend([[fork, cpu, *sample] for sample in samples])
            return rows
        finally:
            available.put(cpu)

    rows = []
    with (out / "queue-raw.csv").open("w", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(COLUMNS)
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(cpus)) as workers:
            jobs = [workers.submit(measure, fork, index, workload)
                    for fork in range(args.forks) for index, workload in enumerate(args.workloads)]
            for job in concurrent.futures.as_completed(jobs):
                new_rows = job.result()
                writer.writerows(new_rows)
                file.flush()
                rows.extend(dict(zip(COLUMNS, row)) for row in new_rows)
    summary = []
    for workload in args.workloads:
        medians = {v: [statistics.median(float(r["ns_per_cycle"]) for r in rows
                                        if r["fork"] == f and r["variant"] == v
                                        and r["workload"] == workload)
                       for f in range(args.forks)] for v in ("ac", "ec")}
        ac, ec = [statistics.median(medians[v]) for v in ("ac", "ec")]
        paired = [a / e for a, e in zip(medians["ac"], medians["ec"])]
        summary.append({"workload": workload, "ac_ns": ac, "ec_ns": ec,
                        "time_change_pct": 100 * (ec / ac - 1), "speedup": ac / ec,
                        "paired_min": min(paired), "paired_max": max(paired),
                        "fork_medians": medians})
    (out / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    for result in summary:
        print(f"{result['workload']:20s} {result['ac_ns']:.1f} -> {result['ec_ns']:.1f} ns "
              f"({result['time_change_pct']:+.1f}%)")


if __name__ == "__main__":
    main()
