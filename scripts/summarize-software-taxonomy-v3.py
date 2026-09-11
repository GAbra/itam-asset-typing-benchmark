#!/usr/bin/env python3
import argparse
import json
import pathlib

CASES = ["clean", "light", "stress", "severe"]


def load(path: pathlib.Path):
    return json.loads(path.read_text(encoding="utf-8"))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("root")
    parser.add_argument("--window", type=int, default=80)
    parser.add_argument("--strict", action="store_true")
    args = parser.parse_args()

    root = pathlib.Path(args.root).resolve()
    failures = []
    cases = {}
    truth_hash = None

    for name in CASES:
        case_dir = root / "cases" / name
        accuracy_path = case_dir / "accuracy.json"
        generation_path = case_dir / "generation.json"
        split_path = case_dir / "split.json"
        if not accuracy_path.exists() or not generation_path.exists() or not split_path.exists():
            failures.append(f"missing reports for {name}")
            continue

        accuracy = load(accuracy_path)
        generation = load(generation_path)
        split = load(split_path)
        if accuracy.get("result") != "OK" or accuracy.get("engineDivergences") != 0:
            failures.append(f"engine divergence or failed accuracy report in {name}")
        if accuracy.get("conflictPriorityWindow") != args.window:
            failures.append(f"conflict window mismatch in {name}")

        current_truth_hash = split.get("holdoutTruthSha256")
        if truth_hash is None:
            truth_hash = current_truth_hash
        elif current_truth_hash != truth_hash:
            failures.append(f"paired holdout truth changed in {name}")

        cases[name] = {
            "total": accuracy.get("total"),
            "typeAccuracy": accuracy.get("typeAccuracy"),
            "exactTypeSubtypeAccuracy": accuracy.get("exactTypeSubtypeAccuracy"),
            "fullAutoCoverage": accuracy.get("fullAutoCoverage"),
            "fullAutoErrorRate": accuracy.get("fullAutoErrorRate"),
            "wrongFullAutoPerTotal": accuracy.get("wrongFullAutoPerTotal"),
            "subtypeAbstentionRate": accuracy.get("subtypeAbstentionRate"),
            "statusCounts": accuracy.get("statusCounts", {}),
            "categoryAccuracy": accuracy.get("profileExactAccuracy", {}),
            "categoryCounts": accuracy.get("profileCounts", {}),
            "catalogProducts": generation.get("catalogProducts"),
            "domesticSelections": generation.get("domesticSelections"),
            "noiseEvents": generation.get("noiseEventCounts", {}),
            "holdoutTruthSha256": current_truth_hash,
        }

    clean = cases.get("clean")
    stress = cases.get("stress")
    severe = cases.get("severe")

    if clean:
        if clean["typeAccuracy"] != 1.0:
            failures.append("clean SOFTWARE type accuracy is not 1.0")
        if clean["exactTypeSubtypeAccuracy"] < 0.99:
            failures.append("clean exact subtype accuracy is below 0.99")
        if clean["fullAutoErrorRate"] > 0.01:
            failures.append("clean FULL AUTO error rate exceeds 0.01")
        if len(clean["categoryCounts"]) < 14:
            failures.append("clean workload exercises fewer than 14 software categories")

    # These stricter gates were frozen before the 20260917 confirmation run.
    # The previous 20260916 run showed that every wrong confident AUTO under
    # stress/severe came from COMPONENT_AGENT being promoted to a parent family.
    if stress:
        if stress["fullAutoErrorRate"] > 0.001:
            failures.append("stress FULL AUTO error rate exceeds 0.001")
        if stress["fullAutoCoverage"] < 0.95:
            failures.append("stress FULL AUTO coverage is below 0.95")
        if stress["categoryAccuracy"].get("COMPONENT_AGENT", 0.0) < 0.93:
            failures.append("stress COMPONENT_AGENT exact accuracy is below 0.93")

    if severe:
        if severe["fullAutoErrorRate"] > 0.002:
            failures.append("severe FULL AUTO error rate exceeds 0.002")
        if severe["fullAutoCoverage"] < 0.88:
            failures.append("severe FULL AUTO coverage is below 0.88")
        if severe["categoryAccuracy"].get("COMPONENT_AGENT", 0.0) < 0.84:
            failures.append("severe COMPONENT_AGENT exact accuracy is below 0.84")

    report = {
        "result": "PASS" if not failures else "FAIL",
        "purpose": "Evaluate a multi-category RU-oriented enterprise software taxonomy using name, publisher, family, package, path, executable/service and platform evidence. Catalog composition is coverage-oriented, not a market-share estimate.",
        "conflictPriorityWindow": args.window,
        "pairedHoldoutTruthSha256": truth_hash,
        "gateCriteria": {
            "engineDivergences": 0,
            "pairedTruthUnchanged": True,
            "cleanExactSubtypeAccuracyMin": 0.99,
            "cleanFullAutoErrorRateMax": 0.01,
            "stressFullAutoErrorRateMax": 0.001,
            "stressFullAutoCoverageMin": 0.95,
            "stressComponentAgentExactAccuracyMin": 0.93,
            "severeFullAutoErrorRateMax": 0.002,
            "severeFullAutoCoverageMin": 0.88,
            "severeComponentAgentExactAccuracyMin": 0.84,
        },
        "cases": cases,
        "failures": failures,
    }
    out = root / "software-taxonomy-summary.json"
    out.write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"SOFTWARE_TAXONOMY_SUMMARY: {out}")
    if args.strict and failures:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
