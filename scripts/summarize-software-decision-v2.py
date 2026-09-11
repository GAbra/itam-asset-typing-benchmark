#!/usr/bin/env python3
import argparse
import json
import pathlib

CASES = ["control", "software-light", "software-stress", "software-severe", "combined-stress"]


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def ratio(num, den):
    return 0.0 if not den else num / den


def compact(report):
    return {
        "total": report.get("total", 0),
        "typeAccuracy": report.get("typeAccuracy", 0.0),
        "exactTypeSubtypeAccuracy": report.get("exactTypeSubtypeAccuracy", 0.0),
        "fullAutoCount": report.get("fullAutoCount", 0),
        "fullAutoWrong": report.get("fullAutoWrong", 0),
        "fullAutoCoverage": report.get("fullAutoCoverage", 0.0),
        "fullAutoErrorRate": report.get("fullAutoErrorRate", 0.0),
        "wrongFullAutoPerTotal": report.get("wrongFullAutoPerTotal", 0.0),
        "subtypeAbstentionRate": report.get("subtypeAbstentionRate", 0.0),
        "statusCounts": report.get("statusCounts", {}),
        "securitySoftwareExactAccuracy": report.get("profileExactAccuracy", {}).get("SECURITY_SOFTWARE", 0.0),
        "applicationSoftwareExactAccuracy": report.get("profileExactAccuracy", {}).get("APPLICATION_SOFTWARE", 0.0),
    }


def delta(baseline, candidate):
    baseline_correct_auto = baseline["fullAutoCount"] - baseline["fullAutoWrong"]
    candidate_correct_auto = candidate["fullAutoCount"] - candidate["fullAutoWrong"]
    removed_auto = baseline["fullAutoCount"] - candidate["fullAutoCount"]
    captured_wrong = baseline["fullAutoWrong"] - candidate["fullAutoWrong"]
    correct_auto_lost = baseline_correct_auto - candidate_correct_auto
    return {
        "capturedWrongFullAutos": captured_wrong,
        "correctFullAutosLost": correct_auto_lost,
        "removedFullAutos": removed_auto,
        "abstentionPrecision": ratio(captured_wrong, removed_auto),
        "relativeWrongFullAutoReduction": ratio(captured_wrong, baseline["fullAutoWrong"]),
        "fullAutoCoverageDelta": candidate["fullAutoCoverage"] - baseline["fullAutoCoverage"],
        "fullAutoErrorRateDelta": candidate["fullAutoErrorRate"] - baseline["fullAutoErrorRate"],
        "exactTypeSubtypeAccuracyDelta": candidate["exactTypeSubtypeAccuracy"] - baseline["exactTypeSubtypeAccuracy"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("--window", type=int, default=80)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    failures = []
    cases = {}

    for name in CASES:
        case_dir = root / "cases" / name
        baseline_path = case_dir / "baseline.json"
        candidate_path = case_dir / "candidate.json"
        injection_path = case_dir / "injection.json"
        if not baseline_path.exists() or not candidate_path.exists() or not injection_path.exists():
            failures.append(f"missing baseline/candidate/injection report for {name}")
            continue

        baseline_raw = load(baseline_path)
        candidate_raw = load(candidate_path)
        injection = load(injection_path)

        for label, report in (("baseline", baseline_raw), ("candidate", candidate_raw)):
            if report.get("result") != "OK" or report.get("engineDivergences") != 0:
                failures.append(f"engine divergence in {name}/{label}")
            if report.get("conflictPriorityWindow") != args.window:
                failures.append(f"conflict window mismatch in {name}/{label}")

        if baseline_raw.get("total") != candidate_raw.get("total"):
            failures.append(f"baseline/candidate total mismatch in {name}")

        baseline = compact(baseline_raw)
        candidate = compact(candidate_raw)
        cases[name] = {
            "baseline": baseline,
            "candidate": candidate,
            "delta": delta(baseline, candidate),
            "injectionEvents": injection.get("eventCounts", {}),
            "ambiguityProfile": injection.get("profile", {}),
        }

    control = cases.get("control")
    if control:
        candidate = control["candidate"]
        if candidate["typeAccuracy"] != 1.0 or candidate["exactTypeSubtypeAccuracy"] != 1.0:
            failures.append("candidate changed clean software decisions")
        if candidate["fullAutoCoverage"] != 1.0 or candidate["fullAutoWrong"] != 0:
            failures.append("candidate is not fully automatic and correct on control corpus")

    for name, case in cases.items():
        if case["candidate"]["fullAutoWrong"] > case["baseline"]["fullAutoWrong"]:
            failures.append(f"candidate increased wrong FULL AUTO count in {name}")
        if case["candidate"]["typeAccuracy"] != 1.0:
            failures.append(f"candidate lost SOFTWARE type accuracy in {name}")

    for name in ("software-stress", "software-severe", "combined-stress"):
        case = cases.get(name)
        if not case:
            failures.append(f"missing required case {name}")
            continue
        if case["baseline"]["fullAutoWrong"] > 0 and case["delta"]["capturedWrongFullAutos"] <= 0:
            failures.append(f"candidate captured no wrong FULL AUTO decisions in {name}")

    report = {
        "result": "PASS" if not failures else "FAIL",
        "purpose": "Compare the fixed canonical software rules with a conservative candidate on a new deterministic software-only corpus.",
        "decisionPrinciple": "Automatic subtype requires explicit evidence; ambiguous software remains SOFTWARE with no subtype rather than becoming a confident wrong subtype.",
        "conflictPriorityWindow": args.window,
        "cases": cases,
        "failures": failures,
    }
    out = root / "software-decision-summary.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"SOFTWARE_DECISION_SUMMARY: {out}")
    if args.strict and failures:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
