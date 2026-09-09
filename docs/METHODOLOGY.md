# Methodology and reproducibility

**English** · [Русский](METHODOLOGY_RU.md)

## Research question

How do three concrete Java implementations of the same IT asset classification rules compare in output agreement and batched classification time as the number of synthetic assets grows?

The unit of input is one normalized asset. There is no asset-to-asset matching, deduplication, external API ingestion or database persistence.

## Two baselines, two purposes

The repository intentionally contains two result sets. They answer the same functional question but have different provenance quality and therefore must not be merged into one performance curve.

### Archived original baseline

The eight JSON files in the root of `benchmark-results/` are the original 10K / 100K / 500K / 1M experiment imported in commit `e16e158`. They are preserved unchanged.

| Parameter | Archived baseline |
|:--|:--|
| CPU / physical RAM | Not recorded |
| Host OS / Docker version / CPU and memory limits | Not recorded |
| Java / JVM build and effective flags | Not recorded; project targets Java 21 |
| Rules | Version 1.0.0; 14 canonical rules |
| Generator default seed | 20260909 from source/protocol; not embedded in old reports |
| Asset counts | 10,000 / 100,000 / 500,000 / 1,000,000 |
| Batch sizes | 2,000 / 5,000 / 10,000 / 10,000 |
| Warmup | 2 iterations over the first `min(batch, 5000)` records |
| Measured passes | 5 full dataset passes |
| Engine order | HashMap + BitSet, CEL, DMN / KIE; fixed |
| Exact binary/image provenance | Not recorded |

The current machine cannot retroactively establish the old environment. Absolute timings are historical observations. They remain useful for preserving the first experiment and for checking that the qualitative engine ordering was not unique to one later run.

### Controlled baseline v2

`benchmark-results/baseline-v2/` is the current reference baseline. It was run after adding harness-v2 provenance capture and an explicit controlled container configuration.

| Parameter | Controlled baseline v2 |
|:--|:--|
| Host CPU | AMD Ryzen 9 7950X, 16 cores / 32 logical processors |
| Host physical RAM | 33,408,700,416 bytes (~32 GiB) |
| Host OS | Windows 11 Pro, build 26200 |
| Docker | Docker Desktop 4.40.0; Engine 28.0.4; WSL2 kernel 6.6.87.1 |
| Container architecture | Linux amd64 |
| Container CPU limit | 4 CPUs |
| Container memory | 4 GiB; memory+swap also 4 GiB |
| Java | Eclipse Adoptium 21.0.12 |
| JVM flags | `-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4` |
| GC | G1 |
| Seed | 20260909 |
| Rules | version 1.0.0; 14 enabled rules; SHA-256 recorded |
| Asset counts | 100,000 / 500,000 / 1,000,000 |
| Batch sizes | 5,000 / 10,000 / 10,000 |
| Warmup | 2 iterations, 5,000 records each |
| Measured passes | 5 full dataset passes |
| Engine order | HashMap + BitSet → CEL → DMN / KIE; fixed |
| Image / JAR | Exact image ID and JAR SHA-256 recorded |
| Background load | Not fully controlled; no other Docker containers at start |

The exact image ID, JAR checksum, host/container metadata, commands and completion note are in `benchmark-results/baseline-v2/environment.json` and `commands.txt`. Each benchmark report also contains Java/VM/OS metadata and data/rule fingerprints.

All baseline-v2 verification reports passed with zero engine mismatches and zero generator-label mismatches.

| Assets | BitSet median assets/s | CEL | DMN / KIE |
|--:|--:|--:|--:|
| 100,000 | 337,868 | 102,782 | 48,071 |
| 500,000 | 338,754 | 102,850 | 49,285 |
| 1,000,000 | 334,237 | 104,800 | 49,150 |

From 500K to 1M, median throughput changes only modestly for all three adapters under the same batch size: approximately -1.33% for BitSet, +1.90% for CEL and -0.27% for DMN. This supports a narrow claim that steady-state throughput is stable over that asset-count increase with the ruleset fixed at 14 rules. It does not establish rule-count scalability or production capacity.

## Why the second experiment was necessary

The second run was not performed to replace an inconvenient result or to claim an optimization. It was needed because the original performance reports lacked enough environment and executable provenance for a third party to reproduce the absolute numbers confidently.

The controlled baseline closes that gap by recording the exact runnable image/JAR, CPU and memory limits, JVM configuration, Java version, seed, rule/input hashes and commands. The original files remain unchanged so the history is auditable.

Differences between archived and controlled absolute speeds must **not** be described as code improvements. The old environment is unknown, so there is no valid before/after optimization experiment.

## Timed boundary

For each measured pass, JSONL is parsed into batches outside the timer. Each engine processes the same batch sequentially. The timed loop includes:

1. Feature extraction from normalized context.
2. Candidate selection and rule evaluation.
3. Shared match resolution and result construction.
4. Checksum calculation from the result.

The harness accumulates elapsed nanoseconds over batches. Input reading, engine construction, report serialization and input hashing are outside those timed sections. I/O can still indirectly influence caches, allocation and GC between measured sections.

`assetsPerSecond = count × 1e9 / elapsedNs`; `nsPerAsset = elapsedNs / count`.

The headline is the median across five passes. Min/max indicate observed spread; they are not confidence intervals. Time per asset is a batch-derived average, not a request-latency percentile.

The XOR benchmark checksum makes results observable inside a run but is not a collision-resistant correctness check. Compare verification SHA-256 digests for stable result fingerprints.

## Initialization

Engine construction happens once, in fixed order, before warmup. Canonical YAML loading is reported separately. Engines share one JVM, so class loading and initialization may benefit later engines. The startup number is a single observed initialization cost per engine, not repeated isolated cold-start latency.

## Correctness and coverage

Verification compares type, subtype, status and sorted winning rule IDs across engines. Per-engine SHA-256 includes the asset ID and these fields in input order. Generator labels are checked against the result type/subtype separately.

All three engines share the extractor, canonical rules and resolver. Agreement therefore cannot detect a shared bug. Generator labels reflect generator assumptions, not independent human annotations or production truth.

Source tests additionally exercise empty evidence, type-only fallback, type/subtype conflicts, forbidden features, disabled rules and duplicate DMN expansion matches. These are correctness cases, not additional performance workloads.

## Harness version 2

Harness v2 adds dataset SHA-256, rules SHA-256, ruleset version, enabled rule count and engine order to reports. Benchmark reports also capture Java/VM/OS properties, available processors, maximum JVM heap, GC names and actual warmup record count.

The controlled baseline supplements those in-report fields with host CPU/RAM, Docker/WSL information, container limits, JVM flags, image ID and JAR checksum in `environment.json`.

Generation serializes sorted collections with LF newlines for stable bytes across JVM invocations and operating systems and writes a `.meta.json` sidecar with seed/count/distribution. Empty verification fails, invalid benchmark sizes are rejected and nullable expected subtypes fail safely. No engine algorithm or canonical classification rule was changed merely to create baseline v2.

## Threats to validity and next experiments

Fixed engine order can bias cache/GC/JIT effects. Prefix warmup may be insufficient. Five passes in one JVM do not measure between-process variance. Host background workload was not fully controlled. Only asset count varies while the ruleset stays fixed at 14; indexes and DMN tables may scale differently with more rules.

Useful follow-ups are independent JVM forks/JMH, allocation and GC profiling, randomized engine order, fixed-asset rule-count scaling, noisy/conflicting evidence and additional documented machines. These are future experiments, not claims established by the current baselines.
