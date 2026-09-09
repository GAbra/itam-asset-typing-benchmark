# Benchmark results

**English** · [Русский](README_RU.md)

This directory contains two intentionally separate experiment sets.

## 1. Archived original baseline

The JSON files directly under `benchmark-results/` are the original 10K / 100K / 500K / 1M experiment imported in initial commit `e16e158`. They are retained unchanged.

Verification reports contain zero engine mismatches and zero generator-label mismatches. Performance reports contain five measured passes per dataset size.

The original CPU, RAM, host OS, exact JVM, container limits and exact binary/image provenance were not captured. Their absolute speeds are therefore historical observations, not the primary reproducible performance result.

Use:

```sh
python scripts/validate-results.py
```

to validate counts, within-run checksums, summary arithmetic and verification consistency. This validates the committed files internally; it does not recreate the old measurement environment.

## 2. Controlled baseline v2

`baseline-v2/` is the current reference performance baseline. It was created to close the provenance gap of the archived experiment.

It records:

- exact Docker image ID and JAR SHA-256;
- host CPU/RAM/OS and Docker/WSL metadata;
- container CPU and memory limits;
- Java/JVM/GC settings;
- seed, ruleset version, rule SHA-256 and dataset SHA-256;
- exact commands, warmup, measured-pass count and batch size;
- generation metadata and verification reports.

Environment summary: Ryzen 9 7950X host, Docker Desktop/WSL2, container limited to 4 CPUs and 4 GiB RAM, Eclipse Adoptium Java 21.0.12, `-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4`, seed `20260909`, two warmups and five measured passes.

| Assets | HashMap + BitSet | CEL | DMN / KIE |
|--:|--:|--:|--:|
| 100,000 | 337,868 assets/s | 102,782 | 48,071 |
| 500,000 | 338,754 | 102,850 | 49,285 |
| 1,000,000 | 334,237 | 104,800 | 49,150 |

All three baseline-v2 verification reports are `PASS` with zero engine and label mismatches.

Validate the full controlled set with:

```sh
python scripts/validate-baseline.py
```

Expected output:

```text
PASS: baseline v2, 100,000 assets
PASS: baseline v2, 500,000 assets
PASS: baseline v2, 1,000,000 assets
```

## Why both are kept

The second experiment does not replace the first one and must not be described as an optimization before/after comparison. The original environment is unknown, so absolute differences between the two result sets cannot be attributed to code changes.

The archived set preserves history. Baseline v2 provides the reproducible reference with explicit executable and environment provenance.

See [methodology](../docs/METHODOLOGY.md) for measurement boundaries, correctness semantics and validity limitations.

Local scripts write ad-hoc runs to ignored `results/local/`. New research experiments should use a separately named directory with their own environment record and exact commands.
