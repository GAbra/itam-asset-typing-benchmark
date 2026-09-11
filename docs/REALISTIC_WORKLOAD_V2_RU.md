# Realistic Workload v2 — исследовательская ветка

[English](REALISTIC_WORKLOAD_V2.md) · **Русский** · [Протокол эксперимента](EXPERIMENT_PROTOCOL_V2_RU.md)

Работа изолирована от `main` в ветке `research/realistic-workload-v2`. Исходный controlled microbenchmark v1 не переписывается и остаётся воспроизводимым историческим baseline.

## Зачем нужен v2

v1 хорошо измеряет стоимость исполнения одинаковой rule-based логики через custom HashMap+BitSet, CEL и DMN/KIE, но его synthetic workload слишком близок к самим правилам. Поэтому v2 разделяет latent ground truth, raw observations источников и normalization; расширяет отрицательные и конфликтные сценарии; отдельно проверяет корректность движков и производительность.

`RealisticWorkloadGenerator` не читает canonical rules, `FeatureExtractor` или typing engine. Ground truth записывается в отдельный JSONL sidecar. Raw observations сначала имеют source-shaped вид AD/Nmap/KSC/Zabbix/SIEM и только затем преобразуются `ObservationNormalizer` в `AssetTypingContext`.

## Что реализовано

- независимый `REFERENCE_LINEAR`, не использующий BitSet candidate index, CEL, KIE или `MatchResolver`;
- differential/property gates для BitSet/CEL/DMN/reference, включая edge cases и scaled rulesets;
- реальные source-specific parsers checked-in fixtures: AD JSON, KSC JSON/typed `pChunk`, Nmap XML, Zabbix JSON-RPC, CEF;
- `SourceSchemaRegistry` и field-shape validation, включая numeric KSC IPv4 field;
- correlated latent hostname/IP и controlled missing/stale/rename/OS/KSC/Nmap/service-account/software-inventory noise;
- пять именованных noise regimes: `clean`, `light`, `moderate`, `stress`, `severe`;
- четыре явных profile-distribution scenarios: `balanced`, `device-heavy`, `identity-heavy`, `software-heavy`;
- deterministic 80/20 holdout по hash от `assetId`, без использования type/subtype при распределении строк;
- accuracy report: type/exact accuracy, auto coverage/error, unresolved rate, status distribution, profile accuracy, confusion matrix;
- проверяемое покрытие всех `NOT_CLASSIFIED`, `AUTO_TYPE_ONLY`, `AUTO`, `TYPE_CONFLICT`, `SUBTYPE_CONFLICT`;
- controlled ruleset scaling `14 / 50 / 100 / 500`;
- application benchmark с отдельными `END_TO_END` и `ENGINE_ONLY` режимами и counterbalanced engine order;
- отдельный forked JMH track для независимой проверки application-level timing;
- frozen experiment runner, environment/provenance capture, SHA-256 manifests и автоматический validator.

Noise rates и profile distributions — **сценарии sensitivity analysis, а не заявленная статистика production**. До появления размеченного внешнего корпуса они нужны для проверки устойчивости выводов к изменению состава и качества входных данных.

## Быстрый запуск генератора

```bash
mvn clean package
java -cp target/itam-asset-typing-benchmark-1.0.0.jar \
  ru.itam.typing.realistic.RealisticWorkloadCli all \
  --count 10000 --seed 20260910 \
  --noise stress --distribution balanced \
  --raw data/generated/realistic-v2-raw.jsonl \
  --truth data/generated/realistic-v2-truth.jsonl \
  --out data/generated/realistic-v2-normalized.jsonl
```

Для полного воспроизводимого исследования используется [отдельный протокол](EXPERIMENT_PROTOCOL_V2_RU.md) и `scripts/run-research-v2-docker.sh`.

## Текущий research gate

| Направление | Состояние ветки | Что ещё требуется для максимального claim |
|---|---:|---|
| Подлинность CEL runtime | 10/10 | реальный `dev.cel` runtime уже используется |
| Подлинность KIE/DMN runtime | 10/10 | реальный Apache KIE DMN runtime уже используется |
| Корректность custom BitSet | 9.5/10 | независимый reference + differential/property tests есть; финально подтвердить full run |
| Реалистичность source fields | ~9/10 | parsers/fixtures/schema gates есть; расширять только по проверяемым vendor/source данным |
| Связи между источниками | ~9/10 | shared latent identity + controlled breakage; внешний corpus остаётся сильнейшим подтверждением |
| Разнообразие данных | ~9/10 | noise/distribution/status/ruleset matrices реализованы; оценить фактические отчёты |
| Ошибки и конфликты | ~9/10 | все outcome classes покрыты, есть несколько noise regimes |
| Независимость ground truth | 9.5/10 | separate truth + rule-independent generation + label-independent holdout |
| Performance methodology | 9.5/10 | engine-only/end-to-end + counterbalanced application benchmark + forked JMH |
| Production accuracy | пока не оценивается | нужен независимый размеченный real-world / anonymized production-like corpus |

Баллы здесь являются readiness-оценкой методики, а не результатами будущего эксперимента. После запуска пользователя они должны подтверждаться реальными report artifacts, а не самим фактом наличия кода.

## Что остаётся внешним ограничением

Синтетический стенд можно сделать строгим, разнообразным и воспроизводимым, но нельзя математически превратить его в доказательство production accuracy конкретной ITAM-инфраструктуры. Для этого нужен внешний размеченный корпус. Поэтому итог исследования должен разделять три вывода: **semantic correctness движков**, **robustness на controlled source-quality scenarios** и **performance/scaling**. Production accuracy публикуется только при наличии независимых labels.
