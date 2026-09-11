#!/usr/bin/env python3
import argparse
import hashlib
import json
import pathlib

NOISES = ("clean", "light", "moderate", "stress", "severe")


def fail(msg):
    raise SystemExit(f"FAIL: {msg}")


def load(path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        fail(f"cannot parse {path}: {exc}")


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
    parser.add_argument("--windows", nargs="+", type=int, required=True)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    if not root.is_dir():
        fail(f"not a directory: {root}")

    summary_path = root / "sweep-summary.json"
    if not summary_path.exists():
        fail("missing sweep-summary.json")
    summary = load(summary_path)
    if summary.get("result") != "PASS":
        fail(f"sweep summary result is {summary.get('result')}")

    expected_windows = list(dict.fromkeys(args.windows))
    if summary.get("testedWindows") != expected_windows:
        fail(f"tested window mismatch: {summary.get('testedWindows')} != {expected_windows}")

    for noise in NOISES:
        case = root / "accuracy" / noise
        baseline = load(case / "baseline.json")
        split = load(case / "split.json")
        if baseline.get("result") != "OK" or baseline.get("engineDivergences") != 0:
            fail(f"invalid baseline for {noise}")
        if baseline.get("total") != split.get("holdoutCount"):
            fail(f"holdout/report mismatch for {noise}")
        if baseline.get("conflictPriorityWindow") != 0:
            fail(f"baseline window is not 0 for {noise}")
        for window in expected_windows:
            report = load(case / f"window-{window}.json")
            if report.get("result") != "OK" or report.get("engineDivergences") != 0:
                fail(f"invalid execution for {noise}, window={window}")
            if report.get("total") != baseline.get("total"):
                fail(f"total mismatch for {noise}, window={window}")
            if report.get("conflictPriorityWindow") != window:
                fail(f"window mismatch for {noise}, window={window}")
            for key in (
                "exactTypeSubtypeAccuracy", "fullAutoCoverage", "fullAutoErrorRate",
                "wrongFullAutoPerTotal", "subtypeAbstentionRate"
            ):
                value = report.get(key)
                if not isinstance(value, (int, float)) or not 0.0 <= value <= 1.0:
                    fail(f"invalid {key} for {noise}, window={window}: {value}")

    verify_hashes(root)

    selected = summary.get("selectedWindow")
    if args.strict:
        if selected is None:
            fail("strict calibration requires one selected window")
        if selected not in expected_windows:
            fail(f"selected window not tested: {selected}")
        entry = summary.get("windows", {}).get(str(selected), {})
        if not entry.get("eligible"):
            fail(f"selected window is not eligible: {selected}")

    print(f"PASS: conflict-window sweep artifacts validated: {root}; selected={selected}")


if __name__ == "__main__":
    main()
