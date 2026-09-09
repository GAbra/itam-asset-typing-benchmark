# Project state

**English** · [Русский](PROJECT_STATE_RU.md)

Implemented:

- three execution engines from one canonical ruleset;
- shared feature extraction and result resolution;
- deterministic synthetic data with expected type/subtype labels;
- differential and generator-label verification;
- illustrative raw-source samples;
- batched benchmark and report generation;
- Docker, PowerShell and shell entry points;
- CI correctness/packaging checks;
- archived original benchmark results retained unchanged;
- controlled baseline v2 with explicit runtime/environment provenance through 1M assets;
- English/Russian documentation entry points.

The archived baseline covers 10K, 100K, 500K and 1M assets with zero reported mismatches, but its original hardware/runtime provenance is incomplete.

The controlled baseline v2 covers 100K, 500K and 1M assets with zero engine and generator-label mismatches and records exact image/JAR fingerprints, host/container metadata, JVM settings, input/rule hashes and exact commands.

Read [methodology](METHODOLOGY.md) before interpreting absolute performance or comparing the two result sets. They are separate experiments, not a before/after optimization series.

The Maven project version is `1.0.0`. The repository contains CI logic for a versioned GitHub Release and GHCR image, but publication depends on creating the matching `v1.0.0` tag.

For current build status use [GitHub Actions](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml). See the [README roadmap](../README.md#research-roadmap) for unimplemented experiments.
