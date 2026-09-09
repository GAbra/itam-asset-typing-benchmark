# Состояние проекта

[English](PROJECT_STATE.md) · **Русский**

Реализовано:

- три execution engine на одном каноническом ruleset;
- общее извлечение признаков и разрешение результата;
- детерминированные синтетические данные с expected type/subtype;
- дифференциальная проверка и проверка меток генератора;
- иллюстративные raw-source samples;
- пакетный benchmark и генерация отчётов;
- Docker, PowerShell и shell entry points;
- correctness/packaging checks в CI;
- исходные benchmark results сохранены без изменений;
- контролируемый baseline v2 с явным runtime/environment provenance до 1M активов;
- двуязычные English/Russian точки входа в документацию.

Архивный baseline охватывает 10K, 100K, 500K и 1M активов с нулевыми расхождениями, но provenance его исходной hardware/runtime среды неполный.

Контролируемый baseline v2 охватывает 100K, 500K и 1M активов с нулевыми расхождениями между движками и метками генератора и сохраняет exact image/JAR fingerprints, metadata хоста/контейнера, JVM settings, input/rule hashes и exact commands.

Перед интерпретацией абсолютной производительности и сравнением двух наборов прочитайте [методику](METHODOLOGY_RU.md). Это два отдельных эксперимента, а не серия «до/после оптимизации».

Версия Maven-проекта — `1.0.0`. В репозитории есть CI-логика для публикации versioned GitHub Release и GHCR image, но публикация произойдёт только после создания соответствующего тега `v1.0.0`.

Актуальный build status смотрите в [GitHub Actions](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml). Нереализованные направления перечислены в [roadmap русского README](../README_RU.md#план-дальнейших-исследований).
