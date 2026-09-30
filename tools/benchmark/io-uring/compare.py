#!/usr/bin/env python3
"""Compare accepted transport pairs; consumes completed manifests and JFR summaries."""
import argparse
import datetime
import json
from pathlib import Path
import statistics

parser = argparse.ArgumentParser()
parser.add_argument('root', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
order = ['epoll', 'io_uring', 'io_uring', 'epoll', 'epoll', 'io_uring']
rows = []
artifact = None
common_hashes = None

def instant(value):
    return datetime.datetime.fromisoformat(value.replace('Z', '+00:00')).timestamp()

for index, transport in enumerate(order, 1):
    directory = args.root / f'perf-{index}-{transport}'
    manifest = json.loads((directory / 'manifest.json').read_text())
    clients = json.loads((directory / 'clients.json').read_text())
    if (manifest.get('error') or manifest['serverExit']['code'] != 0
            or manifest['observedTransport']['type'] != transport
            or manifest['acceptance'] != {'samples': 190, 'faults': 0, 'allClientsSeeEveryOtherPlayer': True}
            or manifest['mobCheckBefore'].strip() != 'Test passed. Count: 240'
            or manifest['mobCheckAfter'].strip() != 'Test passed. Count: 240'
            or clients['faults'] or not clients['allClientsSeeEveryOtherPlayer']):
        raise ValueError(f'Unaccepted run: {directory}')
    if artifact is None:
        artifact = manifest['jarSha256']
        common_hashes = {k: v for k, v in manifest['configHashes'].items() if k != 'shreddedpaper.yml'}
    if manifest['jarSha256'] != artifact or common_hashes != {
            k: v for k, v in manifest['configHashes'].items() if k != 'shreddedpaper.yml'}:
        raise ValueError('Artifact or non-transport configuration differs')
    yaml = (directory / 'shreddedpaper.yml').read_text()
    normalized = yaml.replace('prefer-io-uring-transport: true', 'prefer-io-uring-transport: false')
    if rows and normalized != (args.root / rows[0]['name'] / 'shreddedpaper.yml').read_text():
        raise ValueError('Configuration differs beyond the io_uring preference')
    first, last = clients['samples'][0]['clientWorkers'], clients['samples'][-1]['clientWorkers']
    if any(b['keepAlivesReceived'] <= a['keepAlivesReceived'] for a, b in zip(first, last)):
        raise ValueError('No continuing keepalives in a client shard')
    row = {'name': directory.name, 'transport': transport, 'phases': {},
           'maxClientDelayMs': max(w['eventLoopDelayMaxMs'] for s in clients['samples'] for w in s['clientWorkers']),
           'maxKeepaliveAgeMs': max(w['oldestKeepAliveMs'] for s in clients['samples'] for w in s['clientWorkers'])}
    for phase in manifest['phases']:
        summary = json.loads((directory / (phase['name'] + '-summary.json')).read_text())
        cpu = phase['threadCpu']
        netty = cpu['groups']['netty']
        auxiliary = cpu['groups'].get('io_uring_workers', {'userMs': 0, 'systemMs': 0})
        samples = [s for s in clients['samples'] if instant(phase['start']) <= instant(s['at']) <= instant(phase['end'])]
        if len(samples) < 30:
            raise ValueError('Too few client samples in phase')
        received = lambda s: sum(w['receivedBytes'] for w in s['clientWorkers'])
        byte_delta = received(samples[-1]) - received(samples[0])
        sample_ms = (instant(samples[-1]['at']) - instant(samples[0]['at'])) * 1000
        if byte_delta <= 0:
            raise ValueError('No received traffic in phase')
        cores = (netty['userMs'] + netty['systemMs'] + auxiliary['userMs'] + auxiliary['systemMs']) / cpu['elapsedMs']
        mib_per_second = byte_delta / (sample_ms / 1000) / (1024 ** 2)
        allocation = summary['thread_allocation_deltas']
        active = {k: v for k, v in summary['region_ticks'].items()
                  if k.startswith('world:') and v['events'] >= phase['seconds'] * 10}
        if len(active) != 1:
            raise ValueError(f'Expected one connected active overworld owner: {list(active)}')
        region_id, region = next(iter(active.items()))
        row['phases'][phase['name']] = {
            'nettyCpuCores': cores, 'nettyUserCores': netty['userMs'] / cpu['elapsedMs'],
            'nettySystemCores': netty['systemMs'] / cpu['elapsedMs'],
            'auxiliaryIoUringCores': (auxiliary['userMs'] + auxiliary['systemMs']) / cpu['elapsedMs'],
            'clientReceivedMiBps': mib_per_second,
            'nettyCpuMsPerReceivedMiB': cores * 1000 / mib_per_second,
            'regionId': region_id, 'region': region,
            'jvmCpuFraction': summary['cpu_load']['jvm_mean_fraction'],
            'hostCpuFraction': summary['cpu_load']['machine_mean_fraction'],
            'regionAllocationMiBps': sum(v['bytes_per_second'] for k, v in allocation.items() if k.startswith('AstatineRegion')) / (1024 ** 2),
            'gcPauseMs': summary['gc']['total_ms'],
        }
    rows.append(row)

pairs = []
for start in range(0, len(rows), 2):
    pair = {r['transport']: r for r in rows[start:start + 2]}
    e, u = pair['epoll'], pair['io_uring']
    changes = {}
    for phase in ['players', 'mixed']:
        a, b = e['phases'][phase], u['phases'][phase]
        changes[phase] = {key: 100 * (b[key] / a[key] - 1) for key in
                         ['nettyCpuCores', 'nettyCpuMsPerReceivedMiB', 'clientReceivedMiBps', 'jvmCpuFraction', 'regionAllocationMiBps']}
        changes[phase].update({key: 100 * (b['region'][key] / a['region'][key] - 1)
                              for key in ['mspt_p50', 'mspt_p95', 'mspt_p99']})
    pairs.append({'epoll': e['name'], 'io_uring': u['name'], 'ioUringChangePercent': changes})

aggregates = {}
for transport in ['epoll', 'io_uring']:
    aggregates[transport] = {}
    for phase in ['players', 'mixed']:
        values = [r['phases'][phase] for r in rows if r['transport'] == transport]
        aggregates[transport][phase] = {key: {'mean': statistics.mean(v[key] for v in values),
            'min': min(v[key] for v in values), 'max': max(v[key] for v in values)} for key in
            ['nettyCpuCores', 'nettyCpuMsPerReceivedMiB', 'clientReceivedMiBps', 'jvmCpuFraction', 'regionAllocationMiBps']}
        aggregates[transport][phase]['region'] = {key: {'median': statistics.median(v['region'][key] for v in values),
            'min': min(v['region'][key] for v in values), 'max': max(v['region'][key] for v in values)}
            for key in ['mspt_p50', 'mspt_p95', 'mspt_p99']}

result = {'candidateSha256': artifact, 'acceptedRuns': len(rows), 'rows': rows, 'pairs': pairs, 'aggregates': aggregates,
          'limits': ['Three sequential pairs on a shared host; no statistical significance or production capacity claim.',
                     'Networking CPU uses task user/system counters, excludes exited/new threads and IRQ CPU.',
                     'Bytes are client received compressed TCP bytes; CPU and throughput windows differ by boundary polling.',
                     'JFR CPU is normalized JVM load, not Java sample percentages.',
                     'Crowded performance runs exclude plugins and authenticated/encrypted sessions. Separate smoke/native checks do not establish full plugin soak acceptance.']}
args.output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps({'acceptedRuns': len(rows), 'aggregates': aggregates}, indent=2))
