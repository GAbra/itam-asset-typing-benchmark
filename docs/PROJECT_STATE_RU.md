# Состояние проекта

[English](PROJECT_STATE.md) · **Русский**

## Стабильный baseline

Реализовано и сохранено:

- три execution engine на одном каноническом ruleset;
- общее извлечение признаков и разрешение результата;
- детерминированные синтетические данные с expected type/subtype;
- дифференциальная проверка и проверка меток генератора;
- иллюстративные raw-source samples;
- пакетный benchmark и генерация отчётов;
- Docker, PowerShell и shell entry points;
- correctness/packaging checks в CI;
- исходные benchmark results сохранены без изменений;
- контролируемый baseline v2 с явным runtime/environment provenance до 1M активов.

Архивный baseline охватывает 10K, 100K, 500K и 1M активов с нулевыми расхождениями, но provenance исходной hardware/runtime среды неполный.

Контролируемый baseline v2 охватывает 100K, 500K и 1M активов с нулевыми расхождениями между движками и метками генератора и сохраняет image/JAR fingerprints, metadata хоста/контейнера, JVM settings, input/rule hashes и exact commands.

## Исследовательская ветка

`research/realistic-workload-v2` завершена на уровне синтетического прототипа. В ней добавлены:

- source-shaped наблюдения AD/Nmap/KSC/Zabbix/SIEM;
- отдельно хранимый ground truth и детерминированный holdout;
- сценарии отсутствующих, устаревших и противоречивых данных;
- независимый reference evaluator;
- масштабирование количества правил, END_TO_END, ENGINE_ONLY и JMH замеры;
- консервативная обработка противоречий с откалиброванным `conflictPriorityWindow=80`;
- подтверждающий robustness-прогон на новом seed;
- Software Taxonomy v3 с 16 подтипами ПО и RU-oriented каталогом из 74 продуктов/семейств;
- финальный подтверждающий Software Taxonomy v3 прогон на новом seed с прохождением всех заранее зафиксированных gate-критериев.

Дальнейшее синтетическое «докручивание» для текущего прототипа не требуется. Следующий содержательный этап — независимо размеченный реальный или корректно обезличенный production-like корпус.

См. [итоговую исследовательскую сводку](RESEARCH_SUMMARY_RU.md).

## Ограничения

Репозиторий подтверждает воспроизводимость, согласованность движков и поведение решения в контролируемых сценариях. Синтетические показатели точности нельзя выдавать за production accuracy.

Версия Maven-проекта остаётся `1.0.0`. Публикация Release/GHCR всё ещё зависит от отдельного решения по тегу/релизу.

Актуальный build status смотрите в [GitHub Actions](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml). Оставшаяся внешняя валидация указана в [roadmap русского README](../README_RU.md#research-roadmap).
