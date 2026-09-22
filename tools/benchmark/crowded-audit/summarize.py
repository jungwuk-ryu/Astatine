#!/usr/bin/env python3
"""Summarize JFR samples without treating sample counts as whole-server CPU time."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("recording", type=Path)
parser.add_argument("--output", type=Path, required=True)
args = parser.parse_args()
def read_events():
    with tempfile.TemporaryDirectory(prefix="astatine-jfr-export-") as output:
        subprocess.run(["javac", "-d", output, str(Path(__file__).with_name("ExportJfr.java"))], check=True)
        with subprocess.Popen(["java", "-Xmx256m", "-cp", output, "ExportJfr", str(args.recording)],
                              stdout=subprocess.PIPE, text=True) as process:
            for line in process.stdout:
                yield json.loads(line)
            if process.wait() != 0:
                raise RuntimeError("JFR export failed")

def frames(values):
    return values["frames"]

def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))] if ordered else None

def category(stack):
    joined = "\n".join(stack)
    for label, needle in [
        ("tracking_and_broadcast", "ShreddedPaperEntityTicker.processTrackQueue"),
        ("activation_scan", "ActivationRange.activateEntities"),
        ("pathfinding", ".pathfinder.PathFinder."),
        ("mob_goals_and_navigation", ".ai.goal."),
        ("mob_sensors_and_brain", ".ai.sensing."),
        ("mob_sensors_and_brain", ".ai.Brain."),
        ("entity_push_query", "LivingEntity.pushEntities"),
        ("player_tick", "ShreddedPaperPlayerTicker.tickPlayer"),
        ("entity_tick_other", "ShreddedPaperEntityTicker.tickEntity"),
        ("lock_cleanup", "ShreddedPaperRegionLocker.releaseCurrentThreadLocks"),
        ("lock_acquisition", "ShreddedPaperRegionLocker.internalTryTakeExactLockNow"),
        ("layout_merge_split", "LevelChunkRegionMap.mergeNearbyOwnersQuiescent"),
        ("layout_merge_split", "LevelChunkRegionMap.splitDisconnectedOwnerQuiescent"),
    ]:
        if needle in joined:
            return label
    return "other"

counts = Counter()
groups = defaultdict(lambda: {"samples": 0, "leaf": Counter(), "inclusive": Counter(), "categories": Counter(), "threads": Counter()})
allocations = defaultdict(Counter)
allocation_sites = defaultdict(Counter)
waits = defaultdict(lambda: {"count": 0, "total_ms": 0.0, "max_ms": 0.0, "sites_ms": Counter()})
ticks = defaultdict(list)
tick_lags = defaultdict(list)
tick_threads = defaultdict(Counter)
gc = []
scheduler = []
allocation_statistics = defaultdict(list)
for event in read_events():
    kind, values = event["type"], event["values"]
    counts[kind] += 1
    thread = values["thread"]
    stack = frames(values)
    group = ("region" if thread.startswith("AstatineRegion") else "main" if thread == "Server thread"
             else "network" if any("io.netty.channel.SingleThreadIoEventLoop.run" == frame for frame in stack) else "other")
    if kind == "jdk.ExecutionSample":
        info = groups[group]
        info["samples"] += 1
        info["threads"][thread] += 1
        info["leaf"][stack[0] if stack else "unknown"] += 1
        info["inclusive"].update(set(stack))
        info["categories"][category(stack)] += 1
    elif kind == "jdk.ObjectAllocationSample":
        allocated = values["objectClass"]["name"].replace("/", ".")
        weight = values["weight"]
        allocations[group][allocated] += weight
        allocation_sites[group][" -> ".join(stack[:8])] += weight
    elif kind in ("jdk.ThreadPark", "jdk.JavaMonitorEnter") and group in ("region", "main"):
        idle = kind == "jdk.ThreadPark" and any("DelayQueue." in entry for entry in stack)
        key = f"{group}:{'scheduler_idle' if idle else kind}"
        duration = values["durationMs"]
        item = waits[key]
        item["count"] += 1
        item["total_ms"] += duration
        item["max_ms"] = max(item["max_ms"], duration)
        item["sites_ms"][" -> ".join(stack[:9])] += duration
    elif kind.endswith("RegionTickEvent"):
        key = f'{values["world"]}:{values["regionX"]},{values["regionZ"]}'
        ticks[key].append(values["wallNanos"] / 1e6)
        tick_lags[key].append(values["scheduleLagNanos"] / 1e6)
        tick_threads[key][thread] += 1
    elif kind.endswith("RegionSchedulerEvent"):
        scheduler.append({key: value for key, value in values.items() if key not in ("frames", "thread")})
    elif kind == "jdk.GCPhasePause":
        gc.append(values["durationMs"])
    elif kind == "jdk.ThreadAllocationStatistics":
        allocation_statistics[thread].append((values["time"], values["allocated"]))

result = {"recording": str(args.recording), "event_counts": counts, "cpu_samples": {}, "allocations": {},
          "waits": {}, "region_ticks": {}, "gc": {"pauses": len(gc), "total_ms": sum(gc), "max_ms": max(gc, default=0)},
          "scheduler_samples": scheduler, "thread_allocation_deltas": {},
          "limits": ["Execution samples describe sampled Java stacks, not elapsed-time fractions or a speedup.",
                     "Inclusive stack counts overlap; categories are exclusive with documented precedence.",
                     "Allocation weights are sampled estimates; large initial weights can precede the recording window. Use thread counter deltas for rates.",
                     "Park/monitor thresholds are 1 ms; failed try-lock retries are not blocking events.",
                     "Scheduler idle is reported separately from waits on application locks.",
                     "Custom tick events must be checked against the recording's event sampling policy."]}
for group, info in groups.items():
    result["cpu_samples"][group] = {"samples": info["samples"], "categories": info["categories"],
        "threads": info["threads"], "leaf": info["leaf"].most_common(30), "inclusive": info["inclusive"].most_common(65)}
for group, types in allocations.items():
    result["allocations"][group] = {"estimated_bytes": sum(types.values()), "classes": types.most_common(20),
                                     "sites": allocation_sites[group].most_common(20)}
for key, value in waits.items():
    result["waits"][key] = {**value, "sites_ms": value["sites_ms"].most_common(10)}
for key, durations in ticks.items():
    result["region_ticks"][key] = {"events": len(durations), "mspt_p50": percentile(durations, .5),
        "mspt_p95": percentile(durations, .95), "mspt_p99": percentile(durations, .99), "mspt_max": max(durations),
        "schedule_lag_p99_ms": percentile(tick_lags[key], .99), "threads": tick_threads[key]}
for thread, records in allocation_statistics.items():
    records.sort()
    if len(records) < 2 or records[-1][0] <= records[0][0]:
        continue
    elapsed = (records[-1][0] - records[0][0]) / 1000
    delta = records[-1][1] - records[0][1]
    if delta < 0:
        raise ValueError(f"Allocation counter decreased for {thread}")
    result["thread_allocation_deltas"][thread] = {"bytes": delta, "seconds": elapsed, "bytes_per_second": delta / elapsed}
args.output.write_text(json.dumps(result, indent=2) + "\n")
print(json.dumps({"output": str(args.output), "samples": {key: value["samples"] for key, value in groups.items()},
                  "region_ticks": result["region_ticks"], "waits": {key: {k: v for k, v in value.items() if k != "sites_ms"}
                    for key, value in result["waits"].items()}}, indent=2))
