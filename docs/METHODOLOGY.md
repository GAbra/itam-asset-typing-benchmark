# Methodology and reproducibility

## Research question

How do three concrete Java implementations of the same IT asset classification rules compare in output agreement and batched classification time as the number of synthetic assets grows?

The unit of input is one normalized asset. There is no asset-to-asset matching, deduplication, external API ingestion or database persistence.

## Archived experiment

The eight JSON files in `benchmark-results/` are the original baseline, imported in commit `e16e158`. They are preserved without modification. Performance reports are dated 2026-09-09; verification reports do not contain timestamps. This repository snapshot does not establish a full historical chain from each measurement to the exact compiled binary.

| Parameter | Archived baseline |
|:--|:--|
| CPU / physical RAM | Not recorded |
| Host OS / Docker version / CPU and memory limits | Not recorded |
| Java / JVM build and effective flags | Not recorded; project targets Java 21 |
| Intended library versions in source | CEL 0.14.0; KIE 10.2.0; Jackson 2.19.2 |
| Rules | Version 1.0.0; 14 canonical rules |
| Generator default seed | 20260909 (source/protocol; not embedded in old reports) |
| Asset counts | 10,000 / 100,000 / 500,000 / 1,000,000 |
| Batch sizes | 2,000 / 5,000 / 10,000 / 10,000 |
| Warmup | 2 iterations over the first min(batch, 5000) records |
| Measured passes | 5 full dataset passes |
| Engine order | HashMap + BitSet, CEL, DMN / KIE; fixed for every batch |
| JVM forks | One JVM per benchmark invocation; no independent forks |

The current machine's specifications cannot retroactively establish the old measurement environment. Absolute timings should be treated as illustrative observations, with the missing provenance retained as a limitation.

## Timed boundary

For each measured pass, JSONL is parsed into batches outside the timer. Each engine processes the same batch sequentially. The timed loop includes:

1. Feature extraction from normalized context.
2. Candidate selection and rule evaluation.
3. Shared match resolution and result construction.
4. Checksum calculation from the result.

The harness accumulates elapsed nanoseconds over batches. Input reading, engine construction, report serialization and the input hashing added in harness version 2 are outside those timed sections. I/O can still indirectly influence caches, allocation and GC between measured sections.

`assetsPerSecond = count × 1e9 / elapsedNs`; `nsPerAsset = elapsedNs / count`.
The headline is the median across five passes. Min/max indicate observed spread; they are not confidence intervals. Time per asset is a batch-derived average, not a request-latency percentile.

The XOR checksum makes results observable but is not a collision-resistant correctness check. Java object/enum hash codes are not a stable cross-process identifier. Compare verification SHA-256 digests for stable result fingerprints, not benchmark checksum numbers across JVMs.

## Initialization

Engine construction happens once, in fixed order, before warmup. Canonical YAML loading is reported separately. Engines share one JVM, so class loading and initialization may benefit later engines. The startup figure is a single observed initialization cost per engine, not repeated isolated cold-start latency.

## Correctness and coverage

Verification compares type, subtype, status and sorted winning rule IDs across engines. Per-engine SHA-256 includes the asset ID and these fields in input order. Generator labels are checked against the BitSet result; agreement then transfers those labels to the other engines.

All three share the extractor, canonical rules and resolver. Agreement cannot detect a shared bug. Labels reflect generator assumptions, not independent human annotations. The eight generated profiles primarily cover successful classifications; they do not estimate real-world class balance or error rates.

Source tests additionally exercise empty evidence, type-only fallback, type/subtype conflicts, forbidden features, disabled rules and duplicate DMN expansion matches. These are correctness cases, not additional performance workloads.

## Harness version 2

New reports add input SHA-256, ruleset version, enabled rule count and engine order. Benchmark reports also capture Java/VM/OS properties, available processors, maximum JVM heap, GC names and actual warmup record count. Hardware model, host RAM, container limits and JVM flags still need an environment note using the protocol template.

Generation now serializes sorted collections with LF newlines for stable bytes across JVM invocations and operating systems, and writes a `.meta.json` sidecar with the seed/count/distribution. Logical records remain seeded; byte hashes of older generated files can differ. Empty verification fails, invalid benchmark sizes are rejected, and nullable expected subtypes produce a mismatch report rather than a crash. No engine algorithm or canonical rules were changed.

Reports from different harness versions and environments should be retained separately, not combined into one scaling curve.

## Threats to validity and next experiments

Fixed engine order can bias cache/GC/JIT effects. Prefix warmup may be insufficient. Five passes in one JVM do not measure between-process variance. Only asset count varies while the ruleset stays fixed at 14; indexes and DMN tables may scale differently with more rules.

Useful follow-ups are independent JVM forks/JMH, allocation and GC profiling, randomized engine order, fixed-asset rule-count scaling, noisy/conflicting evidence and additional documented machines. These are future experiments, not claims established by the archived results.
