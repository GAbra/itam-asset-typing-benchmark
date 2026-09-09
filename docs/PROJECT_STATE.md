# Project state

## Реализовано

- canonical AssetTypingContext;
- canonical YAML ruleset;
- FeatureExtractor;
- HashMap + BitSet engine с опорным индексом;
- CEL engine с предварительной компиляцией и опорным индексом;
- DMN generator + Apache KIE runtime adapter;
- единый MatchResolver;
- синтетический генератор с фиксированным seed;
- 5 классов источников: AD, Nmap, Kaspersky Security Center, Zabbix, SIEM/CEF;
- raw samples;
- differential verification + SHA-256;
- простой пакетный benchmark;
- Docker/PowerShell запуск;
- автоматические unit/integration tests.

## Проверено GitHub CI

На Java 21 успешно выполнены:

- полная Maven-сборка с реальными зависимостями CEL 0.14.0 и Apache KIE/DMN 10.2.0;
- все unit/integration tests;
- генерация детерминированного smoke dataset на 1000 активов;
- differential verification `HashMap+BitSet = CEL = DMN`;
- проверка `docker compose config`;
- сборка Docker-образа;
- запуск Java внутри Docker-контейнера.

Последний подтверждённый CI gate: PASS.

## Следующая точка проверки

Первый запуск на целевом тестовом ПК:

```powershell
.\run-quick-demo.ps1
```

Он выполняет полный 10K-прогон и формирует два файла для анализа:

```text
benchmark-results/verify-10000.json
benchmark-results/benchmark-10000.json
```

Большие dataset-файлы намеренно не хранятся в Git и генерируются локально с фиксированным seed `20260909`.
