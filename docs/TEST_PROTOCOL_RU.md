# Протокол воспроизведения

[English](TEST_PROTOCOL.md) · **Русский**

Все команды выполняются из корня репозитория. Требования: Docker с Compose/Linux containers либо Java 21 + Maven 3.9+.

## Быстрая проверка и измерение

Windows PowerShell:

```powershell
.\run-quick-demo.ps1
```

Linux/macOS/Git Bash:

```sh
sh run-quick-demo.sh
```

Эти entry points обеспечивают последовательность build/test → generate → verify → benchmark → DMN export. Любая ошибка native command останавливает сценарий. Сгенерированные данные записываются в `data/generated/`, а локальные отчёты — в игнорируемый `results/local/`.

Сам по себе `benchmark` не доказывает корректность. Для публикуемых измерений всегда сначала выполняйте verification.

## Correctness gate

Для полностью размеченного синтетического набора требуется:

```text
result = PASS
checked = requested asset count
groundTruthChecked = checked
engineMismatches = 0
groundTruthMismatches = 0
hashBitset = hashCel = hashDmn
```

Пустой dataset должен провалить verification. Расхождение или некорректный input даёт ненулевой exit code. Если используется `--max`, классифицируется только указанный префикс; это ограничение нужно записать в описание эксперимента.

## Профиль контролируемого baseline v2

Сохранённый эталонный baseline использует фиксированные параметры:

```text
seed = 20260909
warmup = 2
measured runs = 5
engine order = HASHMAP_BITSET -> CEL -> DMN_KIE
container CPUs = 4
container memory = 4 GiB
container memory+swap = 4 GiB
JVM = -Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4
```

Соотношение dataset/batch:

| Активов | Batch |
|--:|--:|
| 100 000 | 5 000 |
| 500 000 | 10 000 |
| 1 000 000 | 10 000 |

Exact image ID и checksum JAR, использованные в сохранённом запуске, находятся в `benchmark-results/baseline-v2/environment.json`, а нормализованная последовательность команд — в `benchmark-results/baseline-v2/commands.txt`.

Нельзя незаметно подменять image или JAR и после этого утверждать, что воспроизведён exact baseline. Запуск на другой машине/runtime является новым экспериментом и должен сохраняться в отдельно именованном каталоге результатов.

## Воспроизведение через Docker

Для нового контролируемого эксперимента сначала соберите или загрузите runtime image, зафиксируйте его ID, затем явно ограничьте Docker. Пример шаблона:

```sh
IMAGE=<image-or-digest>
REPO="$(pwd)"

docker run --rm \
  --cpus 4 \
  --memory 4g \
  --memory-swap 4g \
  -e 'JAVA_TOOL_OPTIONS=-Xms2g -Xmx2g -XX:+UseG1GC -XX:ActiveProcessorCount=4' \
  -v "$REPO:/workspace" \
  -w /workspace \
  "$IMAGE" \
  generate --count 100000 --seed 20260909 --out data/generated/experiment-100000.jsonl
```

Далее запустите `verify`, убедитесь в `PASS` и только потом выполняйте `benchmark` с нужным batch size.

Git Bash на Windows может переписывать пути Docker, похожие на POSIX. Если `/workspace` неожиданно превращается в Windows path, используйте существующие Windows scripts проекта или отключите MSYS path conversion для Docker invocation.

## Локальная Java

Соберите проект один раз:

```sh
mvn -B clean verify
```

Далее запускайте generate → verify → benchmark с теми же seed/warmup/runs/batch, что и в протоколе. Зафиксируйте полный `java -version`, фактические JVM flags, CPU/RAM хоста и source commit. Результат local Java нельзя напрямую сравнивать с Docker baseline, если среда выполнения специально не выровнена.

## Фиксация среды

Перед измерением скопируйте [ENVIRONMENT_TEMPLATE_RU.md](ENVIRONMENT_TEMPLATE_RU.md) в каталог эксперимента и заполните его. При использовании Docker фиксируйте и хост, и контейнер.

Минимально нужно записать:

- source commit и состояние worktree;
- CPU model, cores/threads и физическую RAM;
- host OS, Docker/virtualization version, если применимо;
- Java vendor/full version, JVM flags, heap и GC;
- image ID/digest и SHA-256 JAR;
- CPU/memory limits контейнера;
- dataset count, seed, SHA-256 и generation sidecar;
- ruleset version/count/SHA-256;
- warmup, batch, measured passes и exact commands;
- verification report и все raw benchmark reports;
- сведения о фоновой нагрузке и известных ограничениях.

Не публикуйте dumps переменных окружения, credentials, приватные пути и production data.

## Проверка сохранённых результатов

Архивный baseline:

```sh
python scripts/validate-results.py
```

Контролируемый baseline v2:

```sh
python scripts/validate-baseline.py
```

Ожидаемый вывод:

```text
PASS: baseline v2, 100,000 assets
PASS: baseline v2, 500,000 assets
PASS: baseline v2, 1,000,000 assets
```

Архивные графики проверяются командами:

```sh
python -m pip install -r scripts/requirements-figures.txt
python scripts/render-results.py --check
```

CI запускает correctness и packaging checks, а также smoke benchmark без performance threshold. Скорости GitHub runner не считаются частью исследовательского baseline.

Интерпретация результатов описана в [методике](METHODOLOGY_RU.md), а причины разделения archived и controlled runs — в [описании результатов](../benchmark-results/README_RU.md).
