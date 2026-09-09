# Готовый portable runtime

[English](PREBUILT_RUNTIME.md) · **Русский**

## Текущий статус

Версия Maven-проекта — `1.0.0`, а CI уже подготовлен к публикации постоянного GitHub Release и GHCR image после создания тега `v1.0.0`. Пока самого тега и Release нет, используйте artifact успешного CI на `main` или собирайте runtime локально.

Когда versioned release будет опубликован, для постоянного использования предпочтительнее брать его assets, потому что они не имеют 14-дневного срока хранения Actions artifacts.

Планируемое имя versioned container:

```text
ghcr.io/gabra/itam-asset-typing-benchmark:v1.0.0
```

`latest` — перемещаемый удобный alias и не должен быть единственным идентификатором исследовательского запуска.

## Временный CI artifact

1. Откройте [запуски CI](https://github.com/GAbra/itam-asset-typing-benchmark/actions/workflows/ci.yml) и выберите успешный запуск на `main`.
2. Скачайте `itam-asset-typing-benchmark-prebuilt`.
3. Извлеките `itam-asset-typing-benchmark-prebuilt.tar`, `SHA256SUMS`, `SOURCE_COMMIT`, `IMAGE_ID` и JAR.
4. Проверьте SHA-256 tar-файла по `SHA256SUMS`.
5. Используйте `SOURCE_COMMIT` и `IMAGE_ID` как provenance контролируемого эксперимента.

Windows PowerShell:

```powershell
Get-FileHash .\itam-asset-typing-benchmark-prebuilt.tar -Algorithm SHA256
docker load -i .\itam-asset-typing-benchmark-prebuilt.tar
.\run-quick-demo-prebuilt.ps1
```

Linux/macOS:

```sh
sha256sum -c SHA256SUMS
docker load -i itam-asset-typing-benchmark-prebuilt.tar
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt generate --count 10000 --seed 20260909
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt verify --data data/generated/normalized-10000.jsonl --out results/local/verify-10000.json
```

После успешного verification:

```sh
docker run --rm -v "$PWD:/workspace" itam-asset-typing-benchmark:prebuilt benchmark --data data/generated/normalized-10000.jsonl --warmup 2 --runs 5 --batch 2000 --out results/local/benchmark-10000.json
```

Образ содержит собранный в CI shaded JAR, Java 21 JRE и канонические правила. Подмонтированный репозиторий предоставляет datasets и получает results. Запуск runtime не выполняет source tests повторно.

## Использование в исследовании

Для performance experiments обязательно фиксируйте exact image ID/digest и SHA-256 JAR. Если воспроизводится baseline v2, нужно также повторить CPU/memory limits и JVM flags из [протокола](TEST_PROTOCOL_RU.md) и `benchmark-results/baseline-v2/environment.json`.

Готовый artifact рассчитан на Linux amd64. Эмуляция на другой CPU architecture может искажать производительность; для сравнительных измерений лучше собирать native image.
