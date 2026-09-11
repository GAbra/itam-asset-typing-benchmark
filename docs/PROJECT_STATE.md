# Project state

**English** · [Русский](PROJECT_STATE_RU.md)

## Stable baseline

Implemented and preserved:

- three execution engines from one canonical ruleset;
- shared feature extraction and result resolution;
- deterministic synthetic data with expected type/subtype labels;
- differential and generator-label verification;
- illustrative raw-source samples;
- batched benchmark and report generation;
- Docker, PowerShell and shell entry points;
- CI correctness/packaging checks;
- archived original benchmark results retained unchanged;
- controlled baseline v2 with explicit runtime/environment provenance through 1M assets.

The archived baseline covers 10K, 100K, 500K and 1M assets with zero reported mismatches, but its original hardware/runtime provenance is incomplete.

The controlled baseline v2 covers 100K, 500K and 1M assets with zero engine and generator-label mismatches and records image/JAR fingerprints, host/container metadata, JVM settings, input/rule hashes and exact commands.

## Research track

`research/realistic-workload-v2` is complete at the synthetic-prototype level. It adds:

- source-shaped AD/Nmap/KSC/Zabbix/SIEM observations;
- independently stored ground truth and deterministic holdout;
- missing/stale/conflicting evidence scenarios;
- independent reference evaluation;
- rule-count scaling plus END_TO_END, ENGINE_ONLY and JMH measurements;
- conservative conflict handling with calibrated `conflictPriorityWindow=80`;
- fresh-seed robustness confirmation;
- Software Taxonomy v3 with 16 software subtypes and a 74-product/family RU-oriented coverage catalog;
- final fresh-seed Software Taxonomy v3 confirmation with all frozen gates passing.

Further synthetic tuning is not required for the current prototype. The next meaningful validation step is an independently labelled real or properly anonymized production-like corpus.

See [Final research summary](RESEARCH_SUMMARY.md).

## Scope

The repository demonstrates reproducibility, engine agreement and controlled decision behavior. Synthetic accuracy numbers must not be presented as production accuracy.

The Maven project version remains `1.0.0`. Release/GHCR publication still depends on an explicit release/tag decision.

For current build status use [GitHub Actions](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml). See the [README roadmap](../README.md#research-roadmap) for remaining external-validation work.
