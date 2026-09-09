# Среда эксперимента

[English](ENVIRONMENT_TEMPLATE.md) · **Русский**

Скопируйте этот шаблон в каталог каждого нового эксперимента. Неизвестные значения записывайте как `unknown`; не восстанавливайте их позднее догадками.

- ID эксперимента / UTC время начала и завершения:
- Source commit и состояние dirty worktree:
- Commit измерительного скрипта, если отличается:
- Модель CPU / физические ядра / логические процессоры:
- Физическая RAM:
- Host OS и версия/build:
- Java vendor / полная версия / JVM:
- JVM flags / heap limits / garbage collector:
- Docker/Desktop/Engine version, если используется:
- Image ID или digest / architecture:
- SHA-256 JAR:
- CPU limit контейнера / memory limit / memory+swap:
- Виртуализация хоста / версия WSL, если применимо:
- Power mode / фоновая нагрузка / другие workloads:
- Dataset count / seed / SHA-256 / generation sidecar:
- Версия ruleset / число включённых правил / SHA-256:
- Порядок движков:
- Число warmup iterations / warmup records:
- Measured passes / batch size / max limit:
- Exact commands:
- Verification report / ground-truth coverage:
- Raw benchmark reports для каждого запуска:
- Примечания, сведения о прерываниях/возобновлении и ограничения:

Разные машины и конфигурации храните в отдельных каталогах экспериментов. Не публикуйте usernames, приватные пути, dumps переменных окружения, credentials, secrets или production data.
