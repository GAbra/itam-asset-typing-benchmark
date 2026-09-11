# Research track — final summary

This document summarizes the completed `research/realistic-workload-v2` track. It is a controlled synthetic research result, not a production-accuracy claim.

## What was validated

The research branch extends the original benchmark with:

- source-shaped AD, Nmap, Kaspersky Security Center, Zabbix and SIEM observations;
- independently stored ground truth and deterministic 80/20 holdout;
- controlled missing/stale/conflicting evidence scenarios;
- an independent linear reference evaluator in addition to BitSet, CEL and DMN/KIE;
- rule-count scaling (14 / 50 / 100 / 500 rules), end-to-end timing, engine-only timing and JMH cross-checks;
- a conservative conflict policy selected by a predefined calibration procedure;
- a multi-category RU-oriented enterprise software taxonomy using several inventory attributes rather than `DisplayName` alone.

The branch intentionally keeps all production-accuracy claims out of scope until a separately labelled real or properly anonymized production-like corpus is available.

## Performance result

The full realistic experiment used 1,000,000 assets and preserved zero engine divergences. At 14 rules, END_TO_END throughput was approximately:

| Engine | Assets/s | ns/asset |
|:--|--:|--:|
| HashMap + BitSet | 325,376 | 3,073 |
| CEL | 115,012 | 8,695 |
| DMN / KIE | 48,938 | 20,434 |

The ranking remained consistent as the controlled ruleset was scaled to 50, 100 and 500 rules. These are measurements of the concrete adapters in this repository, not universal technology rankings.

## Conflict policy

The legacy resolver could remain confidently automatic under contradictory evidence. The research branch therefore adds a conservative policy that considers near-priority contradictory matches before returning a subtype.

No per-source weights were introduced. The only additional parameter is the **conflict-priority window**: the maximum priority distance within which contradictory evidence is treated as significant.

A frozen sweep tested `20, 40, 60, 80, 100, 120` with predefined eligibility criteria. The selected value was:

```text
conflictPriorityWindow = 80
```

The selection rule required unchanged clean output, zero engine divergences, at least 90% aggregate abstention precision, minimum AUTO coverage under stress/severe, and no increase in wrong FULL AUTO decisions. Window `80` was selected by the predefined objective; it was then checked again on a fresh seed.

In the independent confirmation run (`seed=20260913`):

| Scenario | Wrong FULL AUTO: baseline → conservative | Reduction | Conservative FULL AUTO coverage |
|:--|--:|--:|--:|
| Light | 737 → 233 | 68.4% | 98.11% |
| Moderate | 1,405 → 411 | 70.7% | 96.33% |
| Stress | 2,206 → 654 | 70.4% | 94.39% |
| Severe | 4,871 → 1,515 | 68.9% | 87.77% |

Clean output remained unchanged. The policy deliberately trades a small amount of automatic subtype coverage for a large reduction in confidently wrong subtype decisions.

See [Conflict-window calibration](CONFLICT_WINDOW_CALIBRATION.md) and the committed [confirmation summary](../results/robustness-v2/a620b78030cb-20260911T095249Z/robustness-summary.json).

## Software Taxonomy v3

The original binary software split (`SECURITY_SOFTWARE` / `APPLICATION_SOFTWARE`) was too coarse. The final research taxonomy covers 16 software subtypes, including operating systems, office/business software, browsers, IDEs, database tools/servers, security/crypto software, runtimes, developer tools, components/agents and others.

The checked-in catalog contains 74 representative products/families relevant to a RU-oriented enterprise workload. It is coverage-oriented and must not be interpreted as a Russian market-share model.

The classifier may use normalized `DisplayName`, `Publisher`, product family, package/product identifiers, install path, executable/service names, platform and related inventory evidence. Insufficient evidence falls back to `SOFTWARE / AUTO_TYPE_ONLY` instead of inventing a subtype.

The final confirmation run used `seed=20260918`, `SOFTWARE_COUNT=200000` and the already fixed `conflictPriorityWindow=80`. On the ~39.9k holdout:

| Scenario | Exact subtype accuracy | FULL AUTO coverage | FULL AUTO error rate | COMPONENT_AGENT accuracy |
|:--|--:|--:|--:|--:|
| Clean | 100.00% | 100.00% | 0.00% | 100.00% |
| Light | 99.62% | 99.62% | 0.00% | 99.88% |
| Stress | 97.94% | 97.94% | 0.00% | 98.38% |
| Severe | 92.44% | 92.44% | 0.00% | 93.13% |

All frozen acceptance criteria passed and `failures=[]`.

See [Software Taxonomy v3](SOFTWARE_TAXONOMY_V3.md) and the committed [confirmation summary](../results/software-taxonomy-v3/94b4ce6efe9e-20260911T124257Z/software-taxonomy-summary.json).

## What PASS means

A PASS means that, for the committed deterministic synthetic workload and frozen acceptance gates:

1. the supported execution engines remain behaviorally equivalent;
2. clean data remains stable;
3. conservative conflict handling materially reduces confidently wrong automatic decisions;
4. the expanded software taxonomy retains high automatic coverage under controlled missing/ambiguous evidence;
5. the experiment is reproducible from committed code, parameters, reports and hashes.

It does **not** mean that the classifier has a measured production accuracy of 92–100%. Production validation still requires independently labelled real data.

## Research-track status

The experimental design, calibration, independent confirmation runs and software-taxonomy refinement are complete. Further synthetic tuning is not required for the current prototype. The next meaningful validation step is external: run the same pipeline on an independently labelled real or properly anonymized production-like corpus and compare the resulting confusion/abstention profile with the synthetic stress tests.
