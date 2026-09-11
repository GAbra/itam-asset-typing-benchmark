# Real-World Validation Protocol

**English** · [Русский](REAL_WORLD_VALIDATION_PROTOCOL_RU.md)

## Purpose

This protocol freezes the next research stage **before any real-world model results are inspected**. The goal is to measure external validity, not to tune rules until one particular export looks good.

Release `v2.0.0` is the frozen synthetic baseline. Its rules, taxonomy, `conflictPriorityWindow=80`, normalization and conflict-resolution semantics are not retroactively changed after real labels are observed. Any change driven by real-world error analysis receives a new version and is compared against unchanged `v2.0.0`.

## Unit of analysis

One observation unit is **one already-correlated ITAM asset** with observations from the available sources. This repository does not perform deduplication or entity resolution across AD, Nmap, Kaspersky Security Center, Zabbix, SIEM or other systems.

If source records are correlated before entering the benchmark, the correlation procedure must be documented separately. Correlation errors must not be silently counted as classifier errors.

## Frozen baseline manifest

Before the first real-data run, record:

- Git tag and commit SHA (`v2.0.0`);
- ruleset SHA-256;
- taxonomy version;
- `conflictPriorityWindow=80`;
- input schema version;
- anonymization/sanitizer version;
- available source list;
- corpus creation date;
- deterministic split definition;
- predefined metrics and interpretation criteria.

After this manifest is created, the baseline is immutable.

## Anonymization and data handling

Raw production data and identity-mapping tables must **not** be committed to the public repository.

Minimum requirements:

1. Replace `assetId` with a stable pseudonym, for example HMAC of an internal identifier using a secret kept outside the repository.
2. Remove or transform direct user/infrastructure identifiers such as personal names, email addresses, phone numbers, logins, hostnames/FQDNs, IP/MAC addresses, SIDs/GUIDs and domain names unless a specific feature is required by the algorithm.
3. If a structural naming feature is required, retain only the needed semantics or use a consistent non-reversible transformation.
4. Shift or bucket timestamps when appropriate while preserving the freshness relationships and ordering actually used by the algorithm.
5. Treat software, service and infrastructure inventory as potentially confidential even when it contains no personal data.
6. Version the sanitizer/schema manifest so the same transformed corpus can be reproduced inside the controlled environment.

`.gitignore` is only a guard against accidental commits; it is not a security control.

## Ground truth

Reference labels must be produced **before the annotator is shown classifier output**.

Each record should contain at least:

- pseudonymous `assetId`;
- reference `type`;
- reference `subtype` when it can be determined reliably;
- `supportedByTaxonomy` flag;
- annotation confidence/quality status;
- an optional reason when a subtype cannot be assigned reliably.

Annotators must not be forced to choose a known subtype for an object that does not fit the current taxonomy. Such records are necessary for open-world evaluation.

Prefer blind second annotation of at least 10% of a stratified sample, followed by adjudication. Report agreement and reasons for disagreement. If dual annotation is unavailable, state this explicitly as a study limitation.

## Split and leakage control

Create the split **before model error analysis**, using only the pseudonymous `assetId`, never the labels.

Recommended split for a sufficiently large corpus:

- 70% `development` — error analysis and future rule work;
- 15% `validation` — selection among predefined candidates;
- 15% `locked_test` — final evaluation only after the next candidate is frozen.

The exact percentages and salt must be written to the manifest before the first prediction is inspected. A smaller corpus may require a different design, but that decision must also be made in advance.

At the final `locked_test` stage, run both unchanged `v2.0.0` and the frozen next-version candidate on the same rows. Once `locked_test` has been inspected, it is not reused for further tuning of that candidate.

## Primary metrics

Performance throughput is not an accuracy gate. External validation reports separately:

- type accuracy;
- exact type/subtype accuracy;
- per-class precision, recall and F1;
- macro-F1;
- confusion matrix;
- `AUTO` precision;
- `AUTO` coverage;
- confident `AUTO` error rate;
- `AUTO_TYPE_ONLY` rate;
- `TYPE_CONFLICT`, `SUBTYPE_CONFLICT`, `NOT_CLASSIFIED` and overall abstention rate;
- false-AUTO rate for `supportedByTaxonomy=false` records;
- metrics by `DEVICE`, `SOFTWARE`, `ACCOUNT`;
- metrics by predefined source/attribute completeness strata.

Always report `AUTO` precision together with `AUTO` coverage. A zero confident-error rate without coverage is not a sufficient quality statement.

Use 95% confidence intervals for proportions (for example Wilson intervals) and bootstrap 95% confidence intervals for aggregate measures such as macro-F1. Show absolute sample counts next to percentages for small classes.

## Open-world evaluation

Keep records that are outside the current taxonomy or insufficiently evidenced. Do not remove unknown products, devices or accounts merely because the rules do not recognize them.

For unsupported cases, safe behavior may be `AUTO_TYPE_ONLY`, a conflict status or `NOT_CLASSIFIED`. Confidently mapping an unsupported subtype to a known subtype is tracked separately as a dangerous false AUTO.

## Prohibited post-hoc changes

Without a new version, do not:

- change rules while continuing to call the result `v2.0.0`;
- recalibrate `conflictPriorityWindow` on `locked_test`;
- alter the taxonomy after inspecting a confusion matrix just to improve that same dataset;
- remove inconvenient classes or unknown objects from the denominator without a predefined rule;
- mix normalization/correlation errors with classification errors without separate reporting;
- claim high accuracy through abstention without reporting coverage.

## Study sequence

1. Release and freeze `v2.0.0`.
2. Approve the anonymization and input schema.
3. Create independent ground truth without classifier output.
4. Freeze the manifest and deterministic split.
5. Validate import quality and verify that labels cannot leak into features.
6. Run the `v2.0.0` baseline.
7. Analyze errors only on the permitted development portion.
8. If needed, develop a new rules/taxonomy version.
9. Freeze the next candidate.
10. Run the one-time final comparison on `locked_test`.
11. Publish only privacy-reviewed aggregate reports and provenance.

## Public-repository boundary

After a separate confidentiality review, the repository may contain aggregate metrics/confusion matrices, safe class/status counts, corpus fingerprints, schema/sanitizer versions, Git and ruleset hashes, split/annotation methodology, and a final aggregate report.

Do not commit raw real observations, original identifiers, mapping tables, pseudonymization secrets or other confidential exports.

## Success criterion

The external study must answer three separate questions:

1. how well frozen `v2.0.0` transfers from the synthetic model to real data;
2. what `AUTO` coverage is achievable at an acceptable precision of confident decisions;
3. which errors come from typing rules, input-data quality, entity correlation, or taxonomy scope.

Until that evidence exists, synthetic percentages remain properties of the controlled research model and are not production accuracy claims.
