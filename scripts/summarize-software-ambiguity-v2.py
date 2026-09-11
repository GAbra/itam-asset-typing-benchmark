#!/usr/bin/env python3
import argparse
import json
import pathlib

CASES = ["control", "software-light", "software-stress", "software-severe", "combined-stress"]


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def profile_accuracy(report, name):
    return report.get("profileExactAccuracy", {}).get(name, 0.0)


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
        accuracy_path = case_dir / "accuracy.json"
        injection_path = case_dir / "injection.json"
        if not accuracy_path.exists() or not injection_path.exists():
            failures.append(f"missing accuracy/injection report for {name}")
            continue
        accuracy = load(accuracy_path)
        injection = load(injection_path)
        if accuracy.get("result") != "OK" or accuracy.get("engineDivergences") != 0:
            failures.append(f"engine divergence in {name}")
        if accuracy.get("conflictPriorityWindow") != args.window:
            failures.append(f"conflict window mismatch in {name}")
        cases[name] = {
            "total": accuracy.get("total"),
            "exactTypeSubtypeAccuracy": accuracy.get("exactTypeSubtypeAccuracy"),
            "fullAutoCoverage": accuracy.get("fullAutoCoverage"),
            "fullAutoErrorRate": accuracy.get("fullAutoErrorRate"),
            "wrongFullAutoPerTotal": accuracy.get("wrongFullAutoPerTotal"),
            "securitySoftwareExactAccuracy": profile_accuracy(accuracy, "SECURITY_SOFTWARE"),
            "applicationSoftwareExactAccuracy": profile_accuracy(accuracy, "APPLICATION_SOFTWARE"),
            "statusCounts": accuracy.get("statusCounts", {}),
            "injectionEvents": injection.get("eventCounts", {}),
            "ambiguityProfile": injection.get("profile", {}),
        }

    control = cases.get("control")
    if control:
        if control["exactTypeSubtypeAccuracy"] != 1.0:
            failures.append("control exact accuracy is not 1.0")
        if control["securitySoftwareExactAccuracy"] != 1.0:
            failures.append("control security-software accuracy is not 1.0")
        if control["applicationSoftwareExactAccuracy"] != 1.0:
            failures.append("control application-software accuracy is not 1.0")

    for name in ("software-stress", "software-severe"):
        case = cases.get(name)
        if not case:
            continue
        events = case["injectionEvents"]
        for event in ("NEUTRAL_SECURITY_RECORD", "SECURITY_COMPONENT_RECORD",
                      "DECEPTIVE_APPLICATION_RECORD", "MISSING_SOFTWARE_IDENTITY"):
            if events.get(event, 0) <= 0:
                failures.append(f"{name} did not exercise {event}")
        if control and case["exactTypeSubtypeAccuracy"] >= control["exactTypeSubtypeAccuracy"]:
            failures.append(f"{name} did not expose any software decision-quality degradation")
        if case["securitySoftwareExactAccuracy"] >= 1.0:
            failures.append(f"{name} did not expose security-software ambiguity")
        if case["applicationSoftwareExactAccuracy"] >= 1.0:
            failures.append(f"{name} did not expose application-software false-positive ambiguity")

    report = {
        "result": "PASS" if not failures else "FAIL",
        "purpose": "Measure the current fixed rules against controlled KSC software-inventory ambiguity; no rule tuning is performed in this stage.",
        "conflictPriorityWindow": args.window,
        "cases": cases,
        "failures": failures,
    }
    out = root / "software-summary.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"SOFTWARE_SUMMARY: {out}")
    if args.strict and failures:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
