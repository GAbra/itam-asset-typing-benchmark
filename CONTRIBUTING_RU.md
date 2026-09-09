# Участие в проекте

[English](CONTRIBUTING.md) · **Русский**

Для нового направления эксперимента или изменения семантики классификации сначала создайте issue. Небольшие исправления можно сразу оформлять pull request.

## Локальные проверки

Используйте Java 21 + Maven 3.9+ либо Docker по инструкции в README:

```sh
mvn -B clean verify
python scripts/validate-results.py
python scripts/validate-baseline.py
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

При изменении движков или правил выполните generate → verify по [протоколу](docs/TEST_PROTOCOL_RU.md). Для изменённой семантики добавьте целевые cross-engine tests. Все движки должны исполнять одни канонические правила и использовать общий resolver.

## Исследовательские изменения

Архивные `benchmark-results/*.json` должны оставаться неизменными. Не перезаписывайте `benchmark-results/baseline-v2/` результатами с другой машины или runtime. Нерегламентированные локальные запуски сохраняйте в `results/local/`; новые публикуемые эксперименты оформляйте в отдельно именованном каталоге с raw reports, exact commands, source commit, environment record и описанием ограничений.

Сначала подтверждайте correctness, только потом performance. Для измерений фиксируйте модель CPU, RAM, OS, Java vendor/version, JVM flags, image ID/digest, SHA-256 JAR, CPU/memory limits контейнера, seed, input/rule hashes, warmup, batch size и каждый measured run. Не загружайте environment variables, credentials, персональные пути и production data.

Не заменяйте старые цифры только потому, что новая машина быстрее. Разные среды — это разные эксперименты, а не доказательство оптимизации.

## Документация

Публичная документация поддерживается парами English/Russian. При изменении пользовательского Markdown-документа обновляйте соответствующий `_RU.md` в том же pull request, если он существует, и сохраняйте рабочие language-switch links в обе стороны. Русская навигация не должна неожиданно переводить читателя на English-only страницу.

Сохраняйте уважительный тон, обсуждайте проверяемые факты и инженерные компромиссы и указывайте авторство чужой работы. Contributions принимаются на условиях MIT license; сторонние материалы должны сохранять собственную атрибуцию и лицензию.
