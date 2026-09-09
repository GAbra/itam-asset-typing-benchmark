# Примечания к релизу v1.0.0

[English](RELEASE_NOTES.md) · **Русский**

Первый versioned release синтетического benchmark по типизации IT-активов.

- Три реальных движка: HashMap + BitSet, compiled CEL и DMN / Apache KIE.
- Общие канонические правила, differential verification и targeted correctness tests.
- Полноценная English/Russian документация с сохранением языка при навигации.
- Явно описанные границы измерения и раздельные archived/controlled result sets.
- Контролируемый baseline v2 с exact runtime/environment provenance до 1M активов.
- Versioned JAR и portable Linux amd64 Docker image с SHA-256 после срабатывания release workflow на теге `v1.0.0`.

Исторический baseline сохранён без изменений, а отсутствие полного provenance его среды явно отмечено. Отдельно документированный контролируемый эксперимент находится в `benchmark-results/baseline-v2/` и не должен трактоваться как сравнение «до/после оптимизации» с архивом.

Версия 1.0.0 обозначает стабильный запускаемый research snapshot, а не завершение всех пунктов roadmap и не production readiness.

Планируемое имя versioned container после публикации тега:

```text
ghcr.io/gabra/itam-asset-typing-benchmark:v1.0.0
```

`latest` — перемещаемый convenience tag. Release assets включают checksums и build provenance; проверяйте загрузки по `SHA256SUMS` и сохраняйте `SOURCE_COMMIT` / `IMAGE_ID` вместе с исследовательскими результатами.
