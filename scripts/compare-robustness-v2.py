#!/usr/bin/env python3
import argparse
import json
import pathlib


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def ratio(num, den):
    return 0.0 if not den else num / den


def case_delta(baseline, conservative):
    baseline_correct_auto = baseline["fullAutoCount"] - baseline["fullAutoWrong"]
    conservative_correct_auto = conservative["fullAutoCount"] - conservative["fullAutoWrong"]
    removed_auto = baseline["fullAutoCount"] - conservative["fullAutoCount"]
    captured_wrong = baseline["fullAutoWrong"] - conservative["fullAutoWrong"]
    correct_auto_lost = baseline_correct_auto - conservative_correct_auto
    return {
        "total": baseline["total"],
        "baseline": {
            "exactTypeSubtypeAccuracy": baseline["exactTypeSubtypeAccuracy"],
            "fullAutoCount": baseline["fullAutoCount"],
            "fullAutoWrong": baseline["fullAutoWrong"],
            "fullAutoCoverage": baseline["fullAutoCoverage"],
            "fullAutoErrorRate": baseline["fullAutoErrorRate"],
            "wrongFullAutoPerTotal": baseline["wrongFullAutoPerTotal"],
            "subtypeAbstentionRate": baseline["subtypeAbstentionRate"],
        },
        "conservative": {
            "exactTypeSubtypeAccuracy": conservative["exactTypeSubtypeAccuracy"],
            "fullAutoCount": conservative["fullAutoCount"],
            "fullAutoWrong": conservative["fullAutoWrong"],
            "fullAutoCoverage": conservative["fullAutoCoverage"],
            "fullAutoErrorRate": conservative["fullAutoErrorRate"],
            "wrongFullAutoPerTotal": conservative["wrongFullAutoPerTotal"],
            "subtypeAbstentionRate": conservative["subtypeAbstentionRate"],
        },
        "delta": {
            "capturedWrongFullAutos": captured_wrong,
            "correctFullAutosLost": correct_auto_lost,
            "removedFullAutos": removed_auto,
            "abstentionPrecision": ratio(captured_wrong, removed_auto),
            "relativeWrongFullAutoReduction": ratio(captured_wrong, baseline["fullAutoWrong"]),
            "fullAutoCoverageDelta": conservative["fullAutoCoverage"] - baseline["fullAutoCoverage"],
            "fullAutoErrorRateDelta": conservative["fullAutoErrorRate"] - baseline["fullAutoErrorRate"],
            "wrongFullAutoPerTotalDelta": conservative["wrongFullAutoPerTotal"] - baseline["wrongFullAutoPerTotal"],
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("--window", type=int, default=80)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    cases = {}
    failures = []

    for group in ("accuracy", "distributions"):
        base = root / group
        if not base.is_dir():
            failures.append(f"missing group: {group}")
            continue
        for case_dir in sorted(p for p in base.iterdir() if p.is_dir()):
            baseline_path = case_dir / "baseline.json"
            conservative_path = case_dir / "conservative.json"
            if not baseline_path.exists() or not conservative_path.exists():
                failures.append(f"missing baseline/conservative report in {case_dir}")
                continue
            baseline = load(baseline_path)
            conservative = load(conservative_path)
            key = f"{group}/{case_dir.name}"

            if baseline.get("result") != "OK" or conservative.get("result") != "OK":
                failures.append(f"engine divergence in {key}")
            if baseline.get("engineDivergences") != 0 or conservative.get("engineDivergences") != 0:
                failures.append(f"non-zero engine divergence in {key}")
            if baseline.get("total") != conservative.get("total"):
                failures.append(f"baseline/conservative total mismatch in {key}")
            if baseline.get("conflictPriorityWindow") != 0:
                failures.append(f"baseline conflict window is not 0 in {key}")
            if conservative.get("conflictPriorityWindow") != args.window:
                failures.append(f"conservative conflict window mismatch in {key}")
            if conservative.get("fullAutoWrong", 0) > baseline.get("fullAutoWrong", 0):
                failures.append(f"conservative policy increased wrong full AUTO count in {key}")

            cases[key] = case_delta(baseline, conservative)

    clean = cases.get("accuracy/clean")
    if clean:
        if clean["baseline"] != clean["conservative"]:
            failures.append("clean corpus changed under conservative policy")

    for key in ("accuracy/stress", "accuracy/severe"):
        case = cases.get(key)
        if not case:
            failures.append(f"missing required gate case {key}")
            continue
        if case["baseline"]["fullAutoWrong"] > 0 and case["delta"]["capturedWrongFullAutos"] <= 0:
            failures.append(f"no wrong full AUTO decisions captured in {key}")
        if case["conservative"]["fullAutoCoverage"] < 0.70:
            failures.append(f"full AUTO coverage fell below 70% in {key}")

    report = {
        "result": "PASS" if not failures else "FAIL",
        "policy": {
            "baselineConflictPriorityWindow": 0,
            "conservativeConflictPriorityWindow": args.window,
            "seedIsolation": "use a seed different from the exploratory 20260910 corpus",
        },
        "gateCriteria": {
            "engineDivergences": 0,
            "cleanOutputUnchanged": True,
            "wrongFullAutoNeverIncreases": True,
            "stressAndSevereCaptureAtLeastOneWrongFullAuto": True,
            "stressAndSevereMinimumFullAutoCoverage": 0.70,
        },
        "cases": cases,
        "failures": failures,
    }
    out = root / "robustness-summary.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"ROBUSTNESS_SUMMARY: {out}")
    if args.strict and failures:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
