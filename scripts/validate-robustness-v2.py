#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib


def fail(msg):
    raise SystemExit(f"FAIL: {msg}")


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot parse {path}: {exc}")


def verify_reports(root, window):
    checked = 0
    for group in ("accuracy", "distributions"):
        base = root / group
        if not base.is_dir():
            fail(f"missing {base}")
        for case in sorted(p for p in base.iterdir() if p.is_dir()):
            baseline = load(case / "baseline.json")
            conservative = load(case / "conservative.json")
            split = load(case / "split.json")
            for label, report, expected_window in (
                    ("baseline", baseline, 0),
                    ("conservative", conservative, window)):
                if report.get("result") != "OK" or report.get("engineDivergences") != 0:
                    fail(f"{label} engine divergence in {case}")
                if report.get("conflictPriorityWindow") != expected_window:
                    fail(f"{label} conflict window mismatch in {case}")
                if report.get("total") != split.get("holdoutCount") or report.get("total", 0) <= 0:
                    fail(f"{label} holdout/report count mismatch in {case}")
                for key in (
                        "typeAccuracy", "exactTypeSubtypeAccuracy", "autoCoverage", "autoErrorRate",
                        "unresolvedRate", "fullAutoCoverage", "fullAutoErrorRate",
                        "wrongFullAutoPerTotal", "subtypeAbstentionRate"):
                    value = report.get(key)
                    if not isinstance(value, (int, float)) or not 0.0 <= value <= 1.0:
                        fail(f"invalid {label} {key} in {case}: {value}")
            checked += 1
    if checked != 8:
        fail(f"expected 8 robustness cases, got {checked}")


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
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("--window", type=int, default=80)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    if not root.is_dir():
        fail(f"not a directory: {root}")
    verify_reports(root, args.window)
    summary = load(root / "robustness-summary.json")
    if args.strict and summary.get("result") != "PASS":
        fail("robustness gate failed: " + "; ".join(summary.get("failures", [])))
    verify_hashes(root)
    print(f"PASS: robustness v2 artifacts validated: {root}; gate={summary.get('result')}")


if __name__ == "__main__":
    main()
