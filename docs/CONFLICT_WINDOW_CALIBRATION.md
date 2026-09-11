# Conflict-window calibration

This calibration chooses one value for the conservative conflict-priority window. It does not add per-source weights and does not change canonical rule priorities.

The tested values are `20 40 60 80 100 120`. Each value is evaluated on the same deterministic balanced corpora for `clean`, `light`, `moderate`, `stress`, and `severe` noise. The calibration seed is `20260912`, intentionally different from the exploratory and robustness seeds.

A candidate window is eligible only when all of the following hold:

- clean output is unchanged relative to the legacy window `0`;
- engine divergence count remains zero;
- aggregate abstention precision across light/moderate/stress/severe is at least 90%;
- FULL AUTO coverage is at least 90% under `stress`;
- FULL AUTO coverage is at least 85% under `severe`;
- wrong FULL AUTO count never increases.

Among eligible values, the runner selects the one with the lowest aggregate `wrongFullAutoPerTotal` across the four noisy cases. If two values tie, the smaller window wins.

This is a calibration rule, not a production guarantee. After selection, the chosen value must be confirmed once on a new deterministic seed that was not used for selection.

Run from the research branch:

```bash
export CALIBRATION_COUNT=200000
export SEED=20260912
export WINDOWS="20 40 60 80 100 120"
export STRICT_GATE=1
sh scripts/run-conflict-window-sweep-docker.sh
```

The main compact artifact is `sweep-summary.json`; raw JSONL corpora and logs remain ignored by Git.
