# Протокол первого теста

## Шаг 1

Запустить:

```powershell
.\scripts\run-quick-demo.ps1
```

## Шаг 2: обязательная проверка корректности

В `benchmark-results/verify-10000.json` ожидается:

```text
result = PASS
engineMismatches = 0
groundTruthMismatches = 0
hashBitset = hashCel = hashDmn
```

Если хотя бы одно условие не выполнено, показатели скорости не считаются валидными до устранения причины.

## Шаг 3: первичный замер

`benchmark-results/benchmark-10000.json` содержит:

- время загрузки/компиляции каждого движка;
- 5 отдельных прогонов;
- количество обработанных активов;
- elapsedNs;
- assetsPerSecond;
- nsPerAsset;
- median/min/max throughput.

## Что прислать для следующего шага

1. `benchmark-results/verify-10000.json`;
2. `benchmark-results/benchmark-10000.json`;
3. текст консоли, если сборка или выполнение завершились ошибкой;
4. по возможности вывод:

```powershell
docker version
docker compose version
```

После успешного 10K-прогона имеет смысл переходить к 100K/500K/1M и уже затем добавлять более строгий JMH/CPU/RAM профиль.
