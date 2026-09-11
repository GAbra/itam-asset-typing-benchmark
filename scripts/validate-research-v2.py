#!/usr/bin/env python3
import hashlib
import json
import pathlib
import sys


def fail(msg):
    raise SystemExit(f"FAIL: {msg}")


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot parse {path}: {exc}")


def verify_accuracy(root):
    checked = 0
    for base in [root / "accuracy", root / "distributions"]:
        if not base.exists():
            fail(f"missing {base}")
        for case in sorted(p for p in base.iterdir() if p.is_dir()):
            report = load(case / "accuracy.json")
            split = load(case / "split.json")
            if report.get("result") != "OK":
                fail(f"engine divergence in {case}")
            if report.get("engineDivergences") != 0:
                fail(f"non-zero engine divergences in {case}")
            total = report.get("total", 0)
            if total <= 0 or total != split.get("holdoutCount"):
                fail(f"holdout/report count mismatch in {case}")
            for key in ["typeAccuracy", "exactTypeSubtypeAccuracy", "autoCoverage", "autoErrorRate", "unresolvedRate"]:
                value = report.get(key)
                if not isinstance(value, (int, float)) or not 0.0 <= value <= 1.0:
                    fail(f"invalid {key} in {case}: {value}")
            checked += 1
    if checked != 8:
        fail(f"expected 8 accuracy sensitivity cases, got {checked}")


def verify_benchmarks(root):
    perf = root / "performance"
    for rules in (14, 50, 100, 500):
        report = load(perf / f"benchmark-{rules}.json")
        if report.get("result") != "OK" or report.get("enabledRules") != rules:
            fail(f"invalid benchmark-{rules}.json")
        summaries = report.get("summary", [])
        keys = {(x.get("mode"), x.get("engine")) for x in summaries}
        expected = {(m, e) for m in ("END_TO_END", "ENGINE_ONLY") for e in ("HASHMAP_BITSET", "CEL", "DMN_KIE")}
        if keys != expected:
            fail(f"missing benchmark summaries for {rules} rules: {expected - keys}")
        if any((x.get("medianAssetsPerSecond") or 0) <= 0 or (x.get("medianNsPerAsset") or 0) <= 0 for x in summaries):
            fail(f"non-positive benchmark metric for {rules} rules")

    jmh = perf / "jmh.json"
    protocol = (root / "provenance" / "protocol.env").read_text(encoding="utf-8")
    run_jmh = "runJmh=1" in protocol
    if run_jmh:
        data = load(jmh)
        if not isinstance(data, list) or len(data) < 18:
            fail(f"JMH report should contain at least 18 benchmark/parameter results, got {len(data) if isinstance(data, list) else 'non-list'}")
        names = {row.get("benchmark", "") for row in data}
        for suffix in ("bitsetEndToEnd", "celEndToEnd", "dmnEndToEnd", "bitsetEngineOnly", "celEngineOnly", "dmnEngineOnly"):
            if not any(name.endswith(suffix) for name in names):
                fail(f"JMH missing {suffix}")


def verify_hashes(root):
    checksum_file = root / "SHA256SUMS.txt"
    if not checksum_file.exists():
        fail("missing SHA256SUMS.txt")
    for line in checksum_file.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        expected, name = line.split(None, 1)
        path = pathlib.Path(name.strip())
        if not path.is_absolute():
            path = pathlib.Path.cwd() / path
        if not path.exists():
            fail(f"hashed file missing: {path}")
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != expected:
            fail(f"SHA-256 mismatch: {path}")


def main():
    if len(sys.argv) != 2:
        fail("usage: validate-research-v2.py <results-dir>")
    root = pathlib.Path(sys.argv[1]).resolve()
    if not root.is_dir():
        fail(f"not a directory: {root}")
    verify_accuracy(root)
    verify_benchmarks(root)
    verify_hashes(root)
    print(f"PASS: realistic workload v2 experiment artifacts validated: {root}")


if __name__ == "__main__":
    main()
