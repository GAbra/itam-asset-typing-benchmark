# Realistic Workload v2 — исследовательская ветка

[English](REALISTIC_WORKLOAD_V2.md) · **Русский**

Эта работа ведётся отдельно от `main` в ветке `research/realistic-workload-v2`. Исходный controlled microbenchmark v1 не переписывается и остаётся воспроизводимым историческим baseline.

## Зачем нужен v2

v1 хорошо отвечает на вопрос о стоимости исполнения одинаковой rule-based логики через custom HashMap+BitSet, CEL и DMN/KIE, но его synthetic workload слишком близок к самим правилам. Это ограничивает выводы о реальной точности типизации.

v2 разделяет три сущности, которые раньше были фактически слиты вместе:

1. **latent ground truth** — истинный тип/подтип актива;
2. **raw source observations** — отдельные наблюдения AD, Nmap, KSC, Zabbix и SIEM;
3. **normalization** — преобразование source-shaped полей в `AssetTypingContext` перед запуском движка.

`RealisticWorkloadGenerator` не импортирует canonical rules, `FeatureExtractor` или какой-либо typing engine. Ground truth пишется в отдельный JSONL sidecar и присоединяется только после нормализации.

## Controlled noise

По умолчанию включён детерминированный stress-profile: пропуски необязательных источников, stale observations, конфликтующий OS, переименование host, false/missed service-account hints, ambiguous Nmap device type, неполный software inventory и ошибочный KSC CTYPE.

Проценты в `NoiseProfile.stressDefault()` — **параметры стресс-теста, а не заявленная частота таких проблем в production**. Позже они должны калиброваться по реальной размеченной выборке или по согласованному сценарию эксплуатации.

## Корреляция между источниками

В отличие от v1, один latent device сначала получает общий hostname/IP, после чего source observations строятся вокруг этих идентификаторов. Noise может намеренно нарушить корреляцию, например оставить старое имя в Zabbix или ошибочный device type в Nmap. Это позволяет отличить нормальный multi-source case от controlled inconsistency.

## Запуск

После `mvn clean package`:

```bash
java -cp target/itam-asset-typing-benchmark-1.0.0.jar \
  ru.itam.typing.realistic.RealisticWorkloadCli all \
  --count 10000 \
  --seed 20260910 \
  --raw data/generated/realistic-v2-raw.jsonl \
  --truth data/generated/realistic-v2-truth.jsonl \
  --out data/generated/realistic-v2-normalized.jsonl
```

После этого существующие `verify` и `benchmark` можно запускать на `realistic-v2-normalized.jsonl`. Для чистого контрольного набора без noise используется `--clean`.

## Что уже улучшено

- ground truth физически отделён от raw observations;
- генерация raw observations не зависит от ruleset и FeatureExtractor;
- поля источников сохраняются source-shaped до отдельного normalization step;
- cross-source hostname/IP в нормальном случае коррелированы;
- введены детерминированные missing/stale/conflicting cases;
- тест требует, чтобы stress workload создавал больше feature-state diversity и выявлял ошибки текущего ruleset, а не давал искусственные 100%.

## Что ещё необходимо до сильного research claim

Следующие этапы этой же ветки: независимый reference evaluator для проверки custom BitSet; отдельные engine-only и end-to-end benchmarks; ruleset scaling; явное покрытие `NOT_CLASSIFIED`, `AUTO_TYPE_ONLY`, `TYPE_CONFLICT`, `SUBTYPE_CONFLICT`; несколько noise regimes; калибровка source distributions; blind holdout; и, если требуется утверждение о production accuracy, размеченный реальный или обезличенный корпус.

## Целевой scorecard

| Направление | v1 оценка | Цель v2 | Условие для цели |
|---|---:|---:|---|
| Подлинность CEL runtime | 10/10 | 10/10 | реальный `dev.cel` runtime |
| Подлинность KIE/DMN runtime | 10/10 | 10/10 | реальный Apache KIE DMN runtime |
| Корректность custom BitSet | 8/10 | 9.5–10/10 | независимый reference evaluator + property/exhaustive tests |
| Реалистичность source fields | 8/10 | 9+/10 | source-shaped fixtures + проверяемые vendor schemas |
| Связи между источниками | 4/10 | 9+/10 | shared latent identity + controlled rename/stale/conflict |
| Разнообразие данных | 3/10 | 9+/10 | измеряемая feature/status/source diversity |
| Ошибки и конфликты | 2/10 | 9+/10 | controlled noise matrix + coverage всех outcome classes |
| Независимость ground truth | 2/10 | 9.5/10 | separate truth sidecar + no rule dependency + blind holdout |
| Performance benchmark | 8/10 | 9+/10 | engine-only/end-to-end split + counterbalanced order/JMH track |
| Production accuracy claim | 2/10 | 8–10/10* | *10/10 нельзя честно заявлять без внешней размеченной production-like выборки |

Цель ветки — не получить красивые баллы декларативно, а сделать каждый балл проверяемым тестом, артефактом или внешней валидацией.
