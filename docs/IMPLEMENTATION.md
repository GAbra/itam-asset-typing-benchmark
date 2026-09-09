# Detailed software implementation

**English** · [Русский](IMPLEMENTATION_RU.md)

This page describes the actual class/module execution path of the current implementation. The high-level architecture is in [ARCHITECTURE.md](ARCHITECTURE.md), while the three engine internals are in [ENGINE_IMPLEMENTATION.md](ENGINE_IMPLEMENTATION.md).

For the two most branch-heavy flows, `verify` and `benchmark`, static SVG renderings and editable draw.io sources are stored next to the Mermaid source. The English page uses English SVGs so the diagrams remain readable in GitHub clients that do not render Mermaid. The draw.io files remain the shared editable source.

- `verify`: [English SVG](diagrams/verify-flow-en.svg) · [draw.io](diagrams/verify-flow.drawio)
- `benchmark`: [English SVG](diagrams/benchmark-flow-en.svg) · [draw.io](diagrams/benchmark-flow.drawio)

## Package map

```text
src/main/java/ru/itam/typing/
├── cli/
│   └── Main.java
├── data/
│   ├── DatasetGenerator.java
│   └── DatasetReader.java
├── engine/
│   ├── TypingEngine.java
│   ├── bitset/BitSetTypingEngine.java
│   ├── cel/CelRuleExpression.java
│   ├── cel/CelTypingEngine.java
│   ├── common/MatchResolver.java
│   ├── dmn/DmnModelGenerator.java
│   └── dmn/DmnTypingEngine.java
├── features/
│   └── FeatureExtractor.java
├── model/
│   ├── AssetType.java
│   ├── AssetSubtype.java
│   ├── AssetTypingContext.java
│   ├── DatasetRecord.java
│   ├── RuleMatch.java
│   ├── TypingResult.java
│   └── TypingStatus.java
└── rules/
    ├── CanonicalRule.java
    ├── RuleLoader.java
    └── RuleSet.java
```

## Complete runtime flow

```mermaid
flowchart TD
    CLI[Main.execute] --> CMD{Command}
    CMD -->|generate| GEN[DatasetGenerator]
    GEN --> JSONL[DatasetRecord JSONL]
    GEN --> META[generation metadata]
    CMD -->|verify / benchmark / explain| LOAD[RuleLoader.load]
    LOAD --> VALID[RuleLoader.validate]
    VALID --> RS[RuleSet / CanonicalRule]
    RS --> BUILD[Main.engines]
    BUILD --> FX[FeatureExtractor]
    BUILD --> B[BitSetTypingEngine]
    BUILD --> C[CelTypingEngine]
    BUILD --> D[DmnTypingEngine]
    CMD -->|verify / benchmark / explain| READ[DatasetReader]
    READ --> REC[DatasetRecord]
    REC --> CTX[AssetTypingContext]
    CTX --> B & C & D
    B --> RB[RuleMatch list]
    C --> RC[RuleMatch list]
    D --> RD[RuleMatch list]
    RB --> RES[MatchResolver.resolve]
    RC --> RES
    RD --> RES
    RES --> TR[TypingResult]
    TR --> VERIFY[verify: compare + SHA-256 + labels]
    TR --> BENCH[benchmark: checksum + timing]
    TR --> EXPLAIN[explain: JSON]
    CMD -->|export-dmn| D
    D --> DMNXML[generated DMN XML]
```

`Main` is the orchestration layer: it parses CLI arguments, loads rules, creates one `FeatureExtractor` and all three engines, reads datasets and emits reports. `TypingEngine` defines the common `name()` + `classify(AssetTypingContext)` contract.

## Input and output model

`DatasetRecord` contains an `AssetTypingContext` plus expected `expectedType` / `expectedSubtype` labels. The context contains `assetId`, `sources`, `sourceObjectKinds`, `attributes` and `systemParameters`. `TypingResult` contains `assetId`, final `type`, `subtype`, `status` and `matchedRuleIds`.

## Rule loading and validation

The single rule source is `rules/canonical-rules.yaml`. `RuleLoader` deserializes YAML into `RuleSet` and validates it before engine construction. Validation covers ruleset version, non-empty and unique rule IDs, required target type, and required/forbidden overlap.

## Feature extraction

Every `classify` call invokes the shared `FeatureExtractor`, which creates the same 22-feature boolean map from the normalized context. All three engines receive the same semantic feature map and differ only in rule execution.

## Engine construction

`Main.engines()` loads the `RuleSet`, creates one `FeatureExtractor`, and then constructs `BitSetTypingEngine`, `CelTypingEngine`, and `DmnTypingEngine` sequentially. Rule-load and constructor times are captured in `engineLoadMs` and remain outside per-engine classification timing.

## `generate`

`Main.generate()` calls `DatasetGenerator.generate(count, seed, out)`, writes JSONL and a `<dataset>.meta.json` sidecar.

## `verify`

Verification constructs all three engines once and streams the dataset through `DatasetReader.forEach`.

For every record all three classifiers execute and all three SHA-256 digests are updated. Their results are then compared. If the results are not equivalent, `engineMismatches` is incremented and a diagnostic sample may be retained; processing still continues. If the results are equivalent, the mismatch counter is unchanged and processing moves directly to the ground-truth branch.

If `expectedType` is absent, that record is not counted in ground-truth coverage and processing moves to the next record. If `expectedType` is present, `groundTruthChecked` is incremented and the BitSet `type/subtype` is compared with the generator label. A match leaves `groundTruthMismatches` unchanged; a mismatch increments it and stores a diagnostic sample. After EOF, `VerificationReport` is built and only then is PASS/FAIL decided.

![Complete verify flow in English](diagrams/verify-flow-en.svg)

[Open English SVG](diagrams/verify-flow-en.svg) · [Editable draw.io](diagrams/verify-flow.drawio)

<details>
<summary>Mermaid source for verify</summary>

```mermaid
flowchart TD
    R[DatasetReader.forEach] --> X[DatasetRecord]
    X --> B[BitSet classify]
    X --> C[CEL classify]
    X --> D[DMN classify]
    B --> H[Update SHA-256 BitSet]
    C --> H2[Update SHA-256 CEL]
    D --> H3[Update SHA-256 DMN]
    B --> EQ{Equivalent?}
    C --> EQ
    D --> EQ
    EQ -->|no| EM[engineMismatches++ + sample]
    EQ -->|yes| GT{expectedType present?}
    EM --> GT
    GT -->|no| NEXT{Another record?}
    GT -->|yes| GTC[groundTruthChecked++]
    GTC --> GC{type/subtype match label?}
    GC -->|yes| NEXT
    GC -->|no| GM[groundTruthMismatches++ + sample]
    GM --> NEXT
    NEXT -->|yes| X
    NEXT -->|no / EOF| REP[Build VerificationReport]
    REP --> P{checked > 0 AND engineMismatches = 0 AND groundTruthMismatches = 0?}
    P -->|yes| OK[PASS / exit 0]
    P -->|no| FAIL[FAIL / exit 2]
```

</details>

`PASS` requires a non-empty dataset, zero engine mismatches and zero generator-label mismatches. `FAIL` returns exit code `2`. The generator-label check is performed directly against BitSet; CEL and DMN are covered by differential comparison.

## `benchmark`

Benchmark creates the engines once. `DatasetReader.first` reads the warmup prefix `min(max, batch, 5000)` and warmup iterations run outside measured results.

Each measured run streams the dataset again. `BatchAccumulator.accept` first checks `seen >= max`. If the limit has already been reached, that record is not appended to the batch and the reader proceeds toward the next record/EOF. Otherwise `seen` is incremented and the record is appended. If the batch is not full yet, the next record is read. If it reaches `batchSize`, `flush()` executes.

Inside `flush()`, engines execute **sequentially**, not in parallel, in fixed order `HASHMAP_BITSET → CEL → DMN_KIE`. Each engine gets a separate timer around the already parsed batch; classification, `resultHash()` and XOR checksum are accumulated into `MutableTiming`. After DMN finishes, the batch is cleared and input processing continues.

If EOF arrives while the batch is non-empty, `Main.benchmark()` performs a final `acc.flush()`. If the batch is already empty, there is no extra classification. Only after the complete measured run are three `RunResult` objects created; after all measured runs, median/min/max summaries are calculated and `BenchmarkReport` is written.

![Complete benchmark flow in English](diagrams/benchmark-flow-en.svg)

[Open English SVG](diagrams/benchmark-flow-en.svg) · [Editable draw.io](diagrams/benchmark-flow.drawio)

<details>
<summary>Mermaid source for benchmark</summary>

```mermaid
flowchart TD
    S[benchmark] --> W[DatasetReader.first min(max,batch,5000)]
    W --> WI[Warmup × warmupIterations]
    WI --> RUN[Start measured run]
    RUN --> F[DatasetReader.forEach]
    F --> A[BatchAccumulator.accept]
    A --> MAX{seen >= max?}
    MAX -->|yes| MORE{Another record?}
    MAX -->|no| ADD[seen++ and add record to batch]
    ADD --> Q{batch.size >= batchSize?}
    Q -->|no| MORE
    Q -->|yes / flush| B[timer HASHMAP_BITSET]
    B --> BT[accumulate timing]
    BT --> C[timer CEL]
    C --> CT[accumulate timing]
    CT --> D[timer DMN_KIE]
    D --> DT[accumulate timing and clear batch]
    DT --> MORE
    MORE -->|yes| A
    MORE -->|no / EOF| E{batch empty?}
    E -->|no / final flush| B
    E -->|yes| RR[Create 3 RunResult from MutableTiming]
    RR --> NR{More measured runs?}
    NR -->|yes| RUN
    NR -->|no| SUM[median / min / max]
    SUM --> REP[BenchmarkReport]
```

</details>

The timed section includes `engine.classify(record.context())`, `resultHash()` and XOR checksum. JSONL parsing is outside the per-engine timer. `RunResult` is created after the complete run, not after each `flush()`.

## `explain` and `export-dmn`

`explain` locates one asset by `assetId`, prints its 22 features and all three engine results. `export-dmn` constructs the same `DmnTypingEngine` used by verify/benchmark and writes its `generatedDmn()` string.

## Class responsibility matrix

| Class | Responsibility |
|:--|:--|
| `Main` | CLI orchestration, engine lifecycle, verify, benchmark, provenance, reports |
| `DatasetGenerator` | deterministic synthetic dataset generation |
| `DatasetReader` | streaming JSONL and warmup-prefix reading |
| `AssetTypingContext` | normalized classification input |
| `DatasetRecord` | context + expected labels |
| `FeatureExtractor` | context → 22 boolean features |
| `RuleLoader` | YAML loading and basic rule validation |
| `RuleSet` / `CanonicalRule` | canonical in-memory rule model |
| `TypingEngine` | common engine interface |
| `BitSetTypingEngine` | masks + candidate index + BitSet evaluation |
| `CelRuleExpression` | canonical rule → CEL expression |
| `CelTypingEngine` | compile/cache CEL programs + candidate evaluation |
| `DmnModelGenerator` | canonical rules → DMN XML decision table |
| `DmnTypingEngine` | load/evaluate DMN through Apache KIE |
| `RuleMatch` | intermediate matched-rule representation |
| `MatchResolver` | shared priority/conflict resolution semantics |
| `TypingResult` | final classification result |

## Out of scope

The benchmark does not contain live AD/Nmap/KSC/Zabbix/SIEM connectors, persistence, an ITAM API, queues, a background service, asset deduplication or multithreaded production serving. It executes on normalized synthetic JSONL.

For the internals of **HashMap + BitSet, CEL and DMN/KIE**, continue with [ENGINE_IMPLEMENTATION.md](ENGINE_IMPLEMENTATION.md).