# Realistic Workload v2 — research branch

**English** · [Русский](REALISTIC_WORKLOAD_V2_RU.md) · [Experiment protocol](EXPERIMENT_PROTOCOL_V2.md)

Work is isolated from `main` on branch `research/realistic-workload-v2`. The original controlled microbenchmark v1 remains unchanged as a reproducible historical baseline.

## Why v2 exists

v1 measures the execution cost of identical rule semantics through custom HashMap+BitSet, CEL and DMN/KIE, but its synthetic workload is too close to the rules themselves. v2 therefore separates latent ground truth, raw source observations, and normalization; introduces negative/conflicting scenarios; and validates engine correctness independently from performance.

`RealisticWorkloadGenerator` does not read canonical rules, `FeatureExtractor`, or any typing engine. Ground truth is written to a separate JSONL sidecar. AD/Nmap/KSC/Zabbix/SIEM observations remain source-shaped until `ObservationNormalizer` converts them into `AssetTypingContext`.

## Implemented research controls

- independent `REFERENCE_LINEAR` that does not use the BitSet candidate index, CEL, KIE, or `MatchResolver`;
- differential/property gates across BitSet/CEL/DMN/reference, including edge cases and scaled rulesets;
- source-specific parsers for checked-in fixtures: AD JSON, KSC JSON/typed `pChunk`, Nmap XML, Zabbix JSON-RPC, and CEF;
- `SourceSchemaRegistry` plus field-shape validation, including the numeric KSC IPv4 field;
- correlated latent hostname/IP with controlled missing/stale/rename/OS/KSC/Nmap/service-account/software-inventory noise;
- five named noise regimes: `clean`, `light`, `moderate`, `stress`, `severe`;
- four explicit profile-distribution scenarios: `balanced`, `device-heavy`, `identity-heavy`, `software-heavy`;
- deterministic 80/20 holdout assigned by an `assetId` hash without reading type/subtype labels;
- accuracy reports with type/exact accuracy, auto coverage/error, unresolved rate, status distribution, profile accuracy, and confusion matrix;
- test coverage for all `NOT_CLASSIFIED`, `AUTO_TYPE_ONLY`, `AUTO`, `TYPE_CONFLICT`, and `SUBTYPE_CONFLICT` outcomes;
- controlled ruleset scaling at `14 / 50 / 100 / 500` rules;
- application benchmark with separate `END_TO_END` and `ENGINE_ONLY` modes and counterbalanced engine order;
- a separate forked JMH track to cross-check application-level timing;
- a frozen experiment runner with environment/provenance capture, SHA-256 manifests, and automatic artifact validation.

Noise rates and profile distributions are **sensitivity-analysis scenarios, not claims about production prevalence**. Until an externally labelled corpus exists, they test whether conclusions are robust to changes in input quality and population composition.

## Quick generator run

```bash
mvn clean package
java -cp target/itam-asset-typing-benchmark-1.0.0.jar \
  ru.itam.typing.realistic.RealisticWorkloadCli all \
  --count 10000 --seed 20260910 \
  --noise stress --distribution balanced \
  --raw data/generated/realistic-v2-raw.jsonl \
  --truth data/generated/realistic-v2-truth.jsonl \
  --out data/generated/realistic-v2-normalized.jsonl
```

Use the [experiment protocol](EXPERIMENT_PROTOCOL_V2.md) and `scripts/run-research-v2-docker.sh` for a full reproducible study.

## Current research gate

| Area | Branch readiness | Remaining requirement for the strongest claim |
|---|---:|---|
| CEL runtime authenticity | 10/10 | real `dev.cel` runtime is already used |
| KIE/DMN runtime authenticity | 10/10 | real Apache KIE DMN runtime is already used |
| Custom BitSet correctness | 9.5/10 | independent reference + differential/property tests; confirm in full run |
| Source-field realism | ~9/10 | parsers/fixtures/schema gates exist; expand only from verifiable source/vendor data |
| Cross-source relationships | ~9/10 | shared latent identity + controlled breakage; external corpus remains strongest evidence |
| Data diversity | ~9/10 | noise/distribution/status/ruleset matrices implemented; inspect measured reports |
| Errors and conflicts | ~9/10 | all outcome classes covered across multiple noise regimes |
| Ground-truth independence | 9.5/10 | separate truth + rule-independent generation + label-independent holdout |
| Performance methodology | 9.5/10 | engine-only/end-to-end + counterbalanced application benchmark + forked JMH |
| Production accuracy | not rated yet | requires an independently labelled real-world or anonymized production-like corpus |

These numbers are methodology-readiness estimates, not experiment outcomes. After the user runs the study, they must be supported by actual report artifacts rather than by code existence alone.

## Irreducible external limitation

A synthetic harness can be strict, diverse, and reproducible, but it cannot prove the production accuracy of a specific ITAM estate. That requires an external labelled corpus. Final reporting should therefore separate **engine semantic correctness**, **robustness under controlled source-quality scenarios**, and **performance/scaling**. Production accuracy is reported only when independent labels are available.
