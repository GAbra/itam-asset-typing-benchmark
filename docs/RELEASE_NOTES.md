# v2.0.0 release notes

**English** · [Русский](RELEASE_NOTES_RU.md)

Version `v2.0.0` freezes the completed **synthetic validation stage** before the project starts external validation on independently labelled real-world or appropriately anonymized production-like data.

## What changed since v1.0.0

- Added the Realistic Workload v2 path with source-shaped AD, Nmap, Kaspersky Security Center, Zabbix and SIEM observations.
- Separated research ground truth from observations and added deterministic label-independent holdout splitting.
- Added an independent linear reference evaluator alongside HashMap + BitSet, CEL and DMN/KIE differential checks.
- Added missing/stale/conflicting evidence scenarios and multiple population distributions.
- Added `END_TO_END` and `ENGINE_ONLY` measurements, counterbalanced engine order, 14/50/100/500-rule scaling and forked JMH cross-checks.
- Added conservative conflict handling and froze `conflictPriorityWindow=80` after a predefined calibration sweep and an independent confirmation seed.
- Added Software Taxonomy v3 with 16 software subtypes and a 74-product/family research catalog using multi-attribute evidence.
- Added reproducible provenance, SHA-256 manifests and compact checked-in research reports.
- Added a preregistered [real-world validation protocol](REAL_WORLD_VALIDATION_PROTOCOL.md) before any real-world model results are inspected.

## Key synthetic results

At 14 rules on the 1,000,000-asset realistic performance corpus, median `END_TO_END` throughput was approximately:

| Engine | assets/s | ns/asset |
|:--|--:|--:|
| HashMap + BitSet | 325,376 | 3,073 |
| CEL | 115,012 | 8,695 |
| DMN / KIE | 48,938 | 20,434 |

The qualitative engine ordering also held in the controlled 50/100/500-rule scaling experiments. These values characterize the concrete adapters and environment used by this repository; they are not a universal technology ranking or production service capacity claim.

The conservative conflict policy reduced wrong confident FULL AUTO decisions by roughly 68–71% across the confirmed light/moderate/stress/severe synthetic scenarios while preserving the clean result. This trades some subtype automation coverage for fewer confidently wrong decisions.

Software Taxonomy v3 passed its predefined synthetic gates on a separate confirmation seed. The reported 92–100% subtype accuracy values are **synthetic holdout results, not production accuracy**.

## Research freeze

`v2.0.0` intentionally freezes the following before real-world validation:

- current type/subtype taxonomy;
- current rulesets;
- current normalization and resolution semantics;
- `conflictPriorityWindow=80`;
- current synthetic evidence model and archived results.

Do not retroactively modify these and continue to call the result `v2.0.0`. Any change motivated by real-world error analysis must receive a new version so the frozen baseline remains comparable.

## Real-world validation boundary

The next stage is external validation, not more synthetic tuning. Real observations and labels must remain outside the public repository unless they have been independently reviewed for confidentiality. The validation protocol requires independent annotation before classifier output is shown, deterministic development/validation/locked-test separation, open-world/unsupported cases, and joint reporting of `AUTO` precision and coverage.

The repository classifies **already-correlated assets**. Cross-source entity resolution/deduplication is outside the classifier and must be documented separately when real data are prepared.

## Release artifacts

The release workflow publishes:

- versioned runnable JAR;
- portable Linux amd64 Docker image archive;
- SHA-256 checksums;
- `SOURCE_COMMIT` and `IMAGE_ID` provenance;
- versioned container image:

```text
ghcr.io/gabra/itam-asset-typing-benchmark:v2.0.0
```

`latest` remains a moving convenience tag. Validate release downloads against `SHA256SUMS` and retain the source commit/image ID with research records.

## Scope statement

`v2.0.0` is a stable reproducible research snapshot and a pre-registered baseline for external validation. It is **not** a claim of production readiness, production accuracy, live source integration, database persistence, background scheduling, or end-to-end ITAM service capacity.
