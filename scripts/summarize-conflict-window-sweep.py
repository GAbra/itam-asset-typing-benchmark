#!/usr/bin/env python3
import argparse
import json
import pathlib

NOISES = ("clean", "light", "moderate", "stress", "severe")
CALIBRATION_NOISES = ("light", "moderate", "stress", "severe")


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def ratio(num, den):
    return 0.0 if not den else num / den


def strip_policy(report):
    copy = dict(report)
    copy.pop("resolutionPolicy", None)
    copy.pop("conflictPriorityWindow", None)
    return copy


def metrics(report):
    return {
        "total": report["total"],
        "exactTypeSubtypeAccuracy": report["exactTypeSubtypeAccuracy"],
        "fullAutoCount": report["fullAutoCount"],
        "fullAutoWrong": report["fullAutoWrong"],
        "fullAutoCoverage": report["fullAutoCoverage"],
        "fullAutoErrorRate": report["fullAutoErrorRate"],
        "wrongFullAutoPerTotal": report["wrongFullAutoPerTotal"],
        "subtypeAbstentionRate": report["subtypeAbstentionRate"],
    }


def delta(baseline, candidate):
    base_correct = baseline["fullAutoCount"] - baseline["fullAutoWrong"]
    cand_correct = candidate["fullAutoCount"] - candidate["fullAutoWrong"]
    removed = baseline["fullAutoCount"] - candidate["fullAutoCount"]
    captured = baseline["fullAutoWrong"] - candidate["fullAutoWrong"]
    correct_lost = base_correct - cand_correct
    return {
        "capturedWrongFullAutos": captured,
        "correctFullAutosLost": correct_lost,
        "removedFullAutos": removed,
        "abstentionPrecision": None if removed == 0 else ratio(captured, removed),
        "relativeWrongFullAutoReduction": ratio(captured, baseline["fullAutoWrong"]),
        "fullAutoCoverageDelta": candidate["fullAutoCoverage"] - baseline["fullAutoCoverage"],
        "fullAutoErrorRateDelta": candidate["fullAutoErrorRate"] - baseline["fullAutoErrorRate"],
        "wrongFullAutoPerTotalDelta": candidate["wrongFullAutoPerTotal"] - baseline["wrongFullAutoPerTotal"],
        "exactTypeSubtypeAccuracyDelta": candidate["exactTypeSubtypeAccuracy"] - baseline["exactTypeSubtypeAccuracy"],
    }


def aggregate(reports):
    total = sum(r["total"] for r in reports)
    full_auto = sum(r["fullAutoCount"] for r in reports)
    wrong_auto = sum(r["fullAutoWrong"] for r in reports)
    exact = sum(r["exactTypeSubtypeAccuracy"] * r["total"] for r in reports)
    return {
        "total": total,
        "exactTypeSubtypeAccuracy": ratio(exact, total),
        "fullAutoCount": full_auto,
        "fullAutoWrong": wrong_auto,
        "fullAutoCoverage": ratio(full_auto, total),
        "fullAutoErrorRate": ratio(wrong_auto, full_auto),
        "wrongFullAutoPerTotal": ratio(wrong_auto, total),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("--windows", nargs="+", type=int, required=True)
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    windows = list(dict.fromkeys(args.windows))
    failures = []
    cases = {}

    baseline_by_noise = {}
    candidate_by_window = {w: {} for w in windows}

    for noise in NOISES:
        case_dir = root / "accuracy" / noise
        baseline_path = case_dir / "baseline.json"
        if not baseline_path.exists():
            failures.append(f"missing baseline report for {noise}")
            continue
        baseline = load(baseline_path)
        baseline_by_noise[noise] = baseline
        if baseline.get("result") != "OK" or baseline.get("engineDivergences") != 0:
            failures.append(f"invalid baseline execution for {noise}")
        if baseline.get("conflictPriorityWindow") != 0:
            failures.append(f"baseline window is not 0 for {noise}")

        case_entry = {"baseline": metrics(baseline), "windows": {}}
        for window in windows:
            path = case_dir / f"window-{window}.json"
            if not path.exists():
                failures.append(f"missing window-{window}.json for {noise}")
                continue
            candidate = load(path)
            candidate_by_window[window][noise] = candidate
            if candidate.get("result") != "OK" or candidate.get("engineDivergences") != 0:
                failures.append(f"invalid execution for {noise}, window={window}")
            if candidate.get("conflictPriorityWindow") != window:
                failures.append(f"window mismatch for {noise}, expected={window}")
            if candidate.get("fullAutoWrong", 0) > baseline.get("fullAutoWrong", 0):
                failures.append(f"wrong FULL AUTO increased for {noise}, window={window}")
            case_entry["windows"][str(window)] = {
                "metrics": metrics(candidate),
                "deltaVsBaseline": delta(baseline, candidate),
            }
        cases[noise] = case_entry

    window_summary = {}
    eligible = []

    for window in windows:
        by_noise = candidate_by_window[window]
        if any(noise not in by_noise for noise in NOISES) or any(noise not in baseline_by_noise for noise in NOISES):
            continue

        clean_unchanged = strip_policy(by_noise["clean"]) == strip_policy(baseline_by_noise["clean"])
        base_agg = aggregate([baseline_by_noise[n] for n in CALIBRATION_NOISES])
        cand_agg = aggregate([by_noise[n] for n in CALIBRATION_NOISES])
        agg_delta = delta(base_agg, cand_agg)
        precision = agg_delta["abstentionPrecision"]

        criteria = {
            "cleanUnchanged": clean_unchanged,
            "aggregateAbstentionPrecisionAtLeast90Pct": precision is not None and precision >= 0.90,
            "stressFullAutoCoverageAtLeast90Pct": by_noise["stress"]["fullAutoCoverage"] >= 0.90,
            "severeFullAutoCoverageAtLeast85Pct": by_noise["severe"]["fullAutoCoverage"] >= 0.85,
            "wrongFullAutoNeverIncreases": all(
                by_noise[n]["fullAutoWrong"] <= baseline_by_noise[n]["fullAutoWrong"] for n in NOISES
            ),
            "engineDivergencesZero": all(by_noise[n].get("engineDivergences") == 0 for n in NOISES),
        }
        is_eligible = all(criteria.values())
        if is_eligible:
            eligible.append((cand_agg["wrongFullAutoPerTotal"], window))

        window_summary[str(window)] = {
            "eligible": is_eligible,
            "criteria": criteria,
            "aggregateNoisyCases": {
                "baseline": base_agg,
                "candidate": cand_agg,
                "delta": agg_delta,
            },
            "stress": metrics(by_noise["stress"]),
            "severe": metrics(by_noise["severe"]),
        }

    selected = min(eligible, key=lambda x: (x[0], x[1]))[1] if eligible else None
    report = {
        "result": "PASS" if not failures else "FAIL",
        "testedWindows": windows,
        "selectionRule": {
            "purpose": "Choose one conflict-priority window without source weights or per-engine tuning.",
            "requirements": [
                "clean output unchanged",
                "aggregate abstention precision across light/moderate/stress/severe >= 0.90",
                "stress FULL AUTO coverage >= 0.90",
                "severe FULL AUTO coverage >= 0.85",
                "wrong FULL AUTO count never increases",
                "engine divergences remain zero",
            ],
            "choiceAmongEligible": "lowest aggregate wrong FULL AUTO per total; tie -> smaller window",
        },
        "selectedWindow": selected,
        "cases": cases,
        "windows": window_summary,
        "failures": failures,
    }
    out = root / "sweep-summary.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"SWEEP_SUMMARY: {out}")
    if selected is not None:
        print(f"SELECTED_WINDOW: {selected}")
    else:
        print("SELECTED_WINDOW: none")


if __name__ == "__main__":
    main()
