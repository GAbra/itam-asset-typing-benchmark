# Realistic Workload v2 experiment protocol

**English** · [Русский](EXPERIMENT_PROTOCOL_V2_RU.md)

This protocol applies only to branch `research/realistic-workload-v2`. Historical baseline v1 remains unchanged and is not treated as a before-state for speedup claims.

## State frozen before a run

The experiment only runs from a clean Git worktree. The runner records the source commit SHA, seed, corpus sizes, warmup/run/batch parameters, JVM flags, Docker image ID, Java/Maven/Linux environment, and SHA-256 for every generated artifact.

The container is limited to `4 CPU`, `4 GiB RAM`, with no additional swap. The JVM uses `-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8`.

## Accuracy matrix

Accuracy is not evaluated on the same rows considered while tuning rules. Every corpus is deterministically split 80/20 using `SHA-256(salt | assetId)`. Holdout assignment does not read type/subtype or any other ground-truth field.

The main noise sensitivity matrix contains `clean`, `light`, `moderate`, `stress`, and `severe` under the `balanced` profile distribution. Under `stress`, the runner additionally evaluates `device-heavy`, `identity-heavy`, and `software-heavy`. These distributions and noise rates are controlled sensitivity-analysis scenarios, not claims about production prevalence.

Each holdout report contains type accuracy, exact type/subtype accuracy, auto coverage, auto error rate, unresolved rate, `TypingStatus` distribution, accuracy by latent profile, and a confusion matrix. BitSet/CEL/DMN are also checked against the independent `REFERENCE_LINEAR`; `engineDivergences` must remain zero.

## Performance matrix

One frozen `stress + balanced` corpus is reused for every engine and ruleset size. `ResearchBenchmarkCli` is run with `14 / 50 / 100 / 500` enabled rules. Rules added for 50/100/500 are synthetic scaling rules with lower priority and must not alter the base classification result.

The application-level benchmark measures `END_TO_END` and `ENGINE_ONLY` separately, uses one batch size and six measured runs, and counterbalances engine position as `B-C-D`, `C-D-B`, `D-B-C`, then repeats.

A separate Maven `jmh` profile builds a `*-jmh.jar`. JMH measures all three engines in both modes for 14/100/500 rules using one thread, 5 × 1 s warmup, 8 × 1 s measurement, and 3 independent fork JVMs.

## Run from Git Bash

```bash
git checkout research/realistic-workload-v2
git pull --ff-only origin research/realistic-workload-v2
sh scripts/run-research-v2-docker.sh
```

Defaults are 100,000 assets per noise-regime accuracy corpus, 50,000 per distribution-sensitivity corpus, and 500,000 for the performance corpus. A heavier final run can be requested before launch, for example:

```bash
export ACCURACY_COUNT=200000
export DISTRIBUTION_COUNT=100000
export PERF_COUNT=1000000
sh scripts/run-research-v2-docker.sh
```

Do not change `SEED`, `WARMUP`, `RUNS`, or `BATCH` between engines within the same experiment. Before a final performance run, close heavy background tasks and stop unrelated Docker containers where practical; the script does not forcibly alter the host.

## Output artifacts

All results are written under `results/research-v2/<commit>-<UTC>/`: accuracy reports, split manifests, performance reports, JMH JSON, logs, environment/protocol metadata, and `SHA256SUMS.txt`. `results/` is already ignored by Git, so measurements do not dirty the worktree.

The runner finishes with `scripts/validate-research-v2.py`. PASS means artifact integrity, expected matrix coverage, and zero engine divergence. PASS does **not** mean 100% production accuracy and does not make synthetic scenario distributions production statistics.

## Research-readiness gate

The internal methodology is ready for evaluation after a successful full run when all three engines match the independent reference evaluator, holdout and train are disjoint, source fixtures and generated observations pass schema gates, all five `TypingStatus` outcomes are covered by tests, results exist across multiple noise regimes/profile distributions/ruleset sizes, and the application benchmark is cross-checked by forked JMH.

The one irreducibly external gate for production-accuracy claims is an independently labelled real-world or well-anonymized production-like corpus. Without that dataset, the synthetic experiment can establish robustness and implementation comparisons, but not the real error prevalence of a specific environment.
