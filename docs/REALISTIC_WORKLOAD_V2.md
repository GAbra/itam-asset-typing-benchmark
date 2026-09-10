# Realistic Workload v2 — research branch

**English** · [Русский](REALISTIC_WORKLOAD_V2_RU.md)

This work is isolated from `main` on branch `research/realistic-workload-v2`. The original controlled microbenchmark v1 remains untouched as a reproducible historical baseline.

## Why v2 exists

v1 is useful for measuring the execution cost of the same rule-based semantics through custom HashMap+BitSet, CEL and DMN/KIE, but its synthetic workload is too close to the rules themselves. That limits claims about real classification accuracy.

v2 separates three concerns that were previously coupled:

1. **latent ground truth** — the actual asset type/subtype;
2. **raw source observations** — independent AD, Nmap, KSC, Zabbix and SIEM observations;
3. **normalization** — conversion of source-shaped fields into `AssetTypingContext` before any engine executes.

`RealisticWorkloadGenerator` does not import canonical rules, `FeatureExtractor`, or any typing engine. Ground truth is written to a separate JSONL sidecar and is joined only after normalization.

## Controlled noise

The default deterministic stress profile introduces missing optional sources, stale observations, conflicting OS values, host renames, false/missed service-account hints, ambiguous Nmap device types, incomplete software inventory and incorrect KSC CTYPE values.

Percentages in `NoiseProfile.stressDefault()` are **stress-test parameters, not claims about production prevalence**. They should later be calibrated against a labelled real sample or an agreed operational scenario.

## Cross-source correlation

Unlike v1, each latent device first receives a shared hostname/IP and source observations are produced around those identifiers. Noise can deliberately break that correlation, for example by leaving an old host name in Zabbix or producing an ambiguous Nmap device type. This separates normal multi-source cases from controlled inconsistency.

## Running it

After `mvn clean package`:

```bash
java -cp target/itam-asset-typing-benchmark-1.0.0.jar \
  ru.itam.typing.realistic.RealisticWorkloadCli all \
  --count 10000 \
  --seed 20260910 \
  --raw data/generated/realistic-v2-raw.jsonl \
  --truth data/generated/realistic-v2-truth.jsonl \
  --out data/generated/realistic-v2-normalized.jsonl
```

The existing `verify` and `benchmark` commands can then run against `realistic-v2-normalized.jsonl`. Use `--clean` for a control dataset without injected noise.

## Improvements already implemented

- ground truth is physically separate from raw observations;
- raw generation has no dependency on the ruleset or FeatureExtractor;
- fields stay source-shaped until a separate normalization step;
- hostname/IP correlation exists across sources in the normal case;
- deterministic missing/stale/conflicting cases are injected;
- a test requires the stress workload to create materially greater feature-state diversity and to expose current ruleset errors instead of producing artificial 100% accuracy.

## Remaining work before a strong research claim

The next stages on this branch are: an independent reference evaluator for custom BitSet validation; separate engine-only and end-to-end benchmarks; ruleset scaling; explicit coverage of `NOT_CLASSIFIED`, `AUTO_TYPE_ONLY`, `TYPE_CONFLICT` and `SUBTYPE_CONFLICT`; multiple noise regimes; calibrated source distributions; a blind holdout; and, for any production-accuracy claim, a labelled real or properly anonymized production-like corpus.

## Target scorecard

| Area | v1 estimate | v2 target | Evidence required |
|---|---:|---:|---|
| CEL runtime authenticity | 10/10 | 10/10 | real `dev.cel` runtime |
| KIE/DMN runtime authenticity | 10/10 | 10/10 | real Apache KIE DMN runtime |
| Custom BitSet correctness | 8/10 | 9.5–10/10 | independent reference evaluator + property/exhaustive tests |
| Source-field realism | 8/10 | 9+/10 | source-shaped fixtures + verifiable vendor schemas |
| Cross-source relationships | 4/10 | 9+/10 | shared latent identity + controlled rename/stale/conflict |
| Data diversity | 3/10 | 9+/10 | measured feature/status/source diversity |
| Errors and conflicts | 2/10 | 9+/10 | controlled noise matrix + all outcome classes covered |
| Ground-truth independence | 2/10 | 9.5/10 | separate truth sidecar + no rule dependency + blind holdout |
| Performance benchmark validity | 8/10 | 9+/10 | engine-only/end-to-end split + counterbalanced order/JMH track |
| Production accuracy claim | 2/10 | 8–10/10* | *10/10 cannot be claimed honestly without an externally labelled production-like sample |

The branch goal is not to raise scores by declaration: every score must be supported by a test, an artifact, or external validation.
