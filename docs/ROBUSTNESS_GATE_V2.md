# Robustness / Abstention Gate v2

This gate evaluates whether a conservative resolver can reduce **confidently wrong subtype automation** when AD, Kaspersky, Nmap, Zabbix or SIEM evidence becomes contradictory.

It is intentionally separate from the performance benchmark. The historical/default engine behaviour remains `LEGACY_MAX_PRIORITY`; the conservative policy is opt-in and is evaluated on the same rule matches produced by all three real engines plus the independent reference implementation.

## Conservative policy

`ResolutionPolicy.CONSERVATIVE_80` uses a conflict-priority window of **80**.

1. Deduplicate matches by `ruleId`.
2. Find the maximum matched rule priority.
3. Treat every match with `priority >= maxPriority - 80` as conflict evidence.
4. If that evidence proposes more than one asset type, return `TYPE_CONFLICT`.
5. If it proposes one type but more than one non-null subtype, return `SUBTYPE_CONFLICT`.
6. Otherwise keep the historical max-priority winner unchanged. Lower-priority evidence never promotes a subtype by itself.

A window of 0 is exactly the historical max-priority behaviour.

The value 80 is a frozen research choice based on the canonical priority spacing and the exploratory 20260910 experiment: it covers important cross-source gaps such as Nmap network-device 330 vs AD workstation 260 / KSC workstation 250, AD server 320 vs KSC workstation 250, and KSC workstation 250 vs Zabbix server 220. It is **not claimed to be production-calibrated**.

To avoid evaluating the policy on the same deterministic corpus that exposed the exploratory failure modes, the robustness runner defaults to a new seed: `20260911`.

## Metrics

The accuracy report keeps the existing metrics and adds full-subtype automation metrics:

- `fullAutoCount`: results with status `AUTO`.
- `fullAutoWrong`: `AUTO` results whose type/subtype differs from ground truth.
- `fullAutoCoverage`: `fullAutoCount / total`.
- `fullAutoErrorRate`: `fullAutoWrong / fullAutoCount`.
- `wrongFullAutoPerTotal`: `fullAutoWrong / total`.
- `subtypeAbstentionRate`: fraction of results that are not full `AUTO` (`AUTO_TYPE_ONLY`, conflicts, or not classified).

`exactTypeSubtypeAccuracy` remains useful, but an abstaining resolver can intentionally lower it by replacing a wrong or risky automatic subtype with a conflict. Therefore the primary safety metric for this gate is `wrongFullAutoPerTotal`, not raw exact accuracy alone.

## Gate criteria

The generated `robustness-summary.json` checks:

- zero engine divergences in baseline and conservative modes;
- clean-corpus output is unchanged;
- conservative mode never increases the count of wrong full `AUTO` decisions;
- stress and severe scenarios capture at least one wrong full `AUTO` decision;
- full `AUTO` coverage remains at least 70% in stress and severe scenarios.

These are deliberately conservative first-stage criteria. If the first independent run passes, stricter risk-reduction targets can be frozen for later regression testing.

## Experiment matrix

The runner evaluates baseline (`window=0`) and conservative (`window=80`) on identical holdouts for:

- balanced population: clean, light, moderate, stress, severe;
- stress noise: device-heavy, identity-heavy, software-heavy.

The default sizes are 100,000 assets per noise scenario and 50,000 per population scenario. A full research run may increase these without changing the gate semantics.

## Run

Docker / Git Bash:

```bash
export ROBUSTNESS_COUNT=100000
export DISTRIBUTION_COUNT=50000
export SEED=20260911
export CONFLICT_WINDOW=80
export STRICT_GATE=1
sh scripts/run-robustness-v2-docker.sh
```

For a very small orchestration smoke, use `STRICT_GATE=0` because a tiny sample is not required to contain every stochastic failure mode.

## Interpretation limits

This gate measures robustness on controlled source-shaped synthetic data. Passing it does not establish production accuracy or prove that an 80-point window is universally correct for a real ITAM deployment. Production calibration still requires an independently labelled real-world or production-like corpus.
