# Протокол эксперимента Realistic Workload v2

[English](EXPERIMENT_PROTOCOL_V2.md) · **Русский**

Этот протокол относится только к ветке `research/realistic-workload-v2`. Исторический baseline v1 не изменяется и не используется как «до» для заявления об ускорении.

## Что фиксируется до запуска

Эксперимент запускается только из чистого Git worktree. Скрипт фиксирует SHA исходного commit, seed, размеры корпусов, параметры warmup/runs/batch, JVM flags, Docker image ID, Java/Maven/Linux environment и SHA-256 всех сформированных артефактов.

Контейнер ограничивается `4 CPU`, `4 GiB RAM`, без дополнительного swap. JVM получает `-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4 -Dfile.encoding=UTF-8`.

## Матрица accuracy

Accuracy не измеряется на тех же строках, которые рассматриваются при настройке правил. Для каждого корпуса выполняется детерминированный 80/20 split по `SHA-256(salt | assetId)`. Решение о попадании в holdout не использует type/subtype или другие поля ground truth.

Основная noise sensitivity matrix содержит `clean`, `light`, `moderate`, `stress`, `severe` при `balanced` распределении профилей. Дополнительно при `stress` проверяются `device-heavy`, `identity-heavy`, `software-heavy`. Эти распределения и noise rates являются сценариями sensitivity analysis, а не заявленной статистикой production.

Для каждого holdout формируются: type accuracy, exact type/subtype accuracy, auto coverage, auto error rate, unresolved rate, распределение `TypingStatus`, accuracy по latent profile и confusion matrix. Одновременно BitSet/CEL/DMN сравниваются с независимым `REFERENCE_LINEAR`; `engineDivergences` должен оставаться равным нулю.

## Матрица performance

Один frozen corpus `stress + balanced` используется для всех движков и размеров ruleset. `ResearchBenchmarkCli` запускается для `14 / 50 / 100 / 500` enabled rules. Для 50/100/500 дополнительные правила являются synthetic scaling rules с пониженным priority и не должны менять базовый classification result.

Application-level benchmark отдельно измеряет `END_TO_END` и `ENGINE_ONLY`, использует одинаковый batch и шесть measured runs. Порядок движков counterbalanced: `B-C-D`, `C-D-B`, `D-B-C`, затем цикл повторяется.

Дополнительно Maven profile `jmh` собирает отдельный `*-jmh.jar`. JMH измеряет те же три движка в `END_TO_END` и `ENGINE_ONLY` режимах для 14/100/500 rules: single thread, 5 × 1 s warmup, 8 × 1 s measurement, 3 независимых fork JVM.

## Запуск из Git Bash

```bash
git checkout research/realistic-workload-v2
git pull --ff-only origin research/realistic-workload-v2
sh scripts/run-research-v2-docker.sh
```

По умолчанию accuracy использует 100 000 assets на noise regime, distribution sensitivity — 50 000, performance corpus — 500 000. Для финального более тяжёлого прогона можно перед запуском задать, например:

```bash
export ACCURACY_COUNT=200000
export DISTRIBUTION_COUNT=100000
export PERF_COUNT=1000000
sh scripts/run-research-v2-docker.sh
```

Не изменяй `SEED`, `WARMUP`, `RUNS`, `BATCH` между сравниваемыми движками в одном эксперименте. Перед финальным performance run желательно закрыть тяжёлые фоновые задачи и остановить другие Docker containers; сам скрипт ничего на хосте принудительно не завершает.

## Выходные артефакты

Все результаты сохраняются под `results/research-v2/<commit>-<UTC>/`. Там находятся accuracy reports, split manifests, performance reports, JMH JSON, логи, environment/protocol metadata и `SHA256SUMS.txt`. Каталог `results/` уже исключён из Git, поэтому измерения не загрязняют worktree.

В конце автоматически запускается `scripts/validate-research-v2.py`. PASS означает целостность артефактов, наличие всех ожидаемых матриц и отсутствие divergence движков. PASS **не означает** 100% production accuracy и не превращает synthetic scenario distributions в production statistics.

## Критерий готовности исследования

Внутренняя методология считается готовой для оценки после успешного full run, когда: все три движка эквивалентны независимому reference evaluator; holdout не пересекается с train; source fixtures и source-shaped generator проходят schema gates; все пять `TypingStatus` покрыты тестами; результаты получены для нескольких noise regimes, нескольких profile distributions и нескольких ruleset sizes; application benchmark подтверждён forked JMH.

Единственный принципиально внешний gate для утверждений о production accuracy — независимая размеченная real-world или качественно обезличенная production-like выборка. Без неё synthetic experiment оценивает robustness и сравнительную реализацию, но не измеряет реальную частоту ошибок в конкретной инфраструктуре.
