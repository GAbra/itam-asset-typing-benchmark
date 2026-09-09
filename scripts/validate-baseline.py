"""Validate the documented baseline independently of machine-specific speeds."""
import json
import math
from pathlib import Path
from statistics import median

base = Path(__file__).resolve().parents[1] / 'benchmark-results' / 'baseline-v2'
load = lambda path: json.loads(path.read_text(encoding='utf-8-sig'))
env = load(base / 'environment.json')
assert env['completedAtUtc'] and len(env['sourceCommit']) == 40
assert len(env['jarSha256']) == 64 and env['imageId'].startswith('sha256:')
assert env['containerCpuQuota'] == 4 and env['containerMemoryBytes'] == 4 * 1024**3
for count in (100000, 500000, 1000000):
    v = load(base / f'verify-{count}.json')
    b = load(base / f'benchmark-{count}.json')
    g = load(base / f'generation-{count}.json')
    assert v['result'] == 'PASS' and v['checked'] == v['groundTruthChecked'] == count
    assert v['engineMismatches'] == v['groundTruthMismatches'] == 0
    assert v['hashBitset'] == v['hashCel'] == v['hashDmn']
    assert v['provenance'] == b['provenance']
    assert b['result'] == 'OK' and b['measuredRuns'] == 5 and b['warmupIterations'] == 2
    assert b['batchSize'] == (5000 if count == 100000 else 10000)
    assert b['warmupRecordsPerIteration'] == 5000
    assert b['environment']['maxHeapBytes'] == 2 * 1024**3
    assert b['environment']['availableProcessors'] == 4
    assert g['count'] == count and g['seed'] == 20260909 and sum(g['distribution'].values()) == count
    assert len(b['runs']) == 15
    for s in b['summary']:
        rows = [r for r in b['runs'] if r['engine'] == s['engine']]
        assert [r['run'] for r in rows] == [1, 2, 3, 4, 5]
        for r in rows:
            assert r['count'] == count and r['elapsedNs'] > 0
            assert math.isclose(r['assetsPerSecond'], count * 1e9 / r['elapsedNs'])
            assert math.isclose(r['nsPerAsset'], r['elapsedNs'] / count)
        assert math.isclose(s['medianAssetsPerSecond'], median(r['assetsPerSecond'] for r in rows))
        assert math.isclose(s['medianNsPerAsset'], median(r['nsPerAsset'] for r in rows))
    print(f'PASS: baseline v2, {count:,} assets')
