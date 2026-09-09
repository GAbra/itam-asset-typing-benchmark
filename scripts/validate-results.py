"""Validate the archived reports without third-party dependencies."""
import json
import math
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]
ENGINES = {"HASHMAP_BITSET", "CEL", "DMN_KIE"}
SIZES = (10_000, 100_000, 500_000, 1_000_000)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def close(actual, expected, label):
    require(math.isfinite(actual) and math.isclose(actual, expected, rel_tol=1e-9), label)


def validate():
    for count in SIZES:
        base = ROOT / "benchmark-results"
        verify = json.loads((base / f"verify-{count}.json").read_text(encoding="utf-8"))
        require(verify["result"] == "PASS" and verify["checked"] == count, f"Verification count: {count}")
        require(verify["engineMismatches"] == verify["groundTruthMismatches"] == 0, "Mismatches")
        hashes = [verify[key] for key in ("hashBitset", "hashCel", "hashDmn")]
        require(len(set(hashes)) == 1 and len(hashes[0]) == 64, "Verification hashes")
        int(hashes[0], 16)
        report = json.loads((base / f"benchmark-{count}.json").read_text(encoding="utf-8"))
        require(report["result"] == "OK", "Benchmark status")
        require(report["warmupIterations"] == 2 and report["measuredRuns"] == 5, "Protocol")
        require(report["batchSize"] == (2000 if count == 10000 else 5000 if count == 100000 else 10000), "Batch")
        require(len(report["runs"]) == 15 and len(report["summary"]) == 3, "Report shape")
        require({s["engine"] for s in report["summary"]} == ENGINES, "Summary engines")
        for index in range(1, 6):
            rows = [r for r in report["runs"] if r["run"] == index]
            require(len(rows) == 3 and {r["engine"] for r in rows} == ENGINES, "Run engines")
            require(len({r["checksum"] for r in rows}) == 1, "Within-run checksum agreement")
            for row in rows:
                require(row["count"] == count and row["elapsedNs"] > 0, "Run count/time")
                close(row["assetsPerSecond"], count * 1e9 / row["elapsedNs"], "Throughput formula")
                close(row["nsPerAsset"], row["elapsedNs"] / count, "Time formula")
        for summary in report["summary"]:
            rows = [r for r in report["runs"] if r["engine"] == summary["engine"]]
            rates = [r["assetsPerSecond"] for r in rows]
            close(summary["medianAssetsPerSecond"], median(rates), "Median throughput")
            close(summary["medianNsPerAsset"], median(r["nsPerAsset"] for r in rows), "Median time")
            close(summary["minAssetsPerSecond"], min(rates), "Minimum throughput")
            close(summary["maxAssetsPerSecond"], max(rates), "Maximum throughput")
        print(f"PASS: {count:,} assets, verification and benchmark arithmetic")


if __name__ == "__main__":
    validate()
