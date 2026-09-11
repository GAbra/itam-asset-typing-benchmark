# Robustness / Abstention Gate v2

Этот gate проверяет, снижает ли консервативный resolver число **уверенно неправильных автоматических подтипов**, когда данные AD, Kaspersky, Nmap, Zabbix или SIEM начинают противоречить друг другу.

Он специально отделён от performance benchmark. Историческое и дефолтное поведение движков остаётся `LEGACY_MAX_PRIORITY`; консервативная политика включается только явно и применяется к одному и тому же набору совпавших правил во всех трёх реальных движках и в независимой reference-реализации.

## Консервативная политика

`ResolutionPolicy.CONSERVATIVE_80` использует окно приоритетов **80**.

1. Совпадения дедуплицируются по `ruleId`.
2. Определяется максимальный приоритет совпавшего правила.
3. Все совпадения с `priority >= maxPriority - 80` считаются значимым конфликтующим evidence.
4. Если в этом evidence более одного типа актива — возвращается `TYPE_CONFLICT`.
5. Если тип один, но присутствует более одного ненулевого подтипа — `SUBTYPE_CONFLICT`.
6. Если конфликта нет, остаётся прежний победитель по максимальному приоритету. Более слабый сигнал сам по себе не повышает решение до подтипа.

Окно 0 полностью соответствует прежней семантике max-priority.

Значение 80 зафиксировано как исследовательская настройка на основании расстояний между приоритетами канонических правил и exploratory-прогона 20260910. Оно покрывает важные пары: Nmap network-device 330 против AD workstation 260 / KSC workstation 250, AD server 320 против KSC workstation 250, KSC workstation 250 против Zabbix server 220. Это **не production-калибровка**.

Чтобы не оценивать политику на том же детерминированном корпусе, на котором были обнаружены исходные ошибки, robustness runner по умолчанию использует новый seed `20260911`.

## Метрики

К прежним accuracy-метрикам добавлены метрики полного автоматического назначения подтипа:

- `fullAutoCount` — число результатов со статусом `AUTO`;
- `fullAutoWrong` — число `AUTO`, у которых type/subtype не совпадает с ground truth;
- `fullAutoCoverage` — `fullAutoCount / total`;
- `fullAutoErrorRate` — `fullAutoWrong / fullAutoCount`;
- `wrongFullAutoPerTotal` — `fullAutoWrong / total`;
- `subtypeAbstentionRate` — доля результатов без полного `AUTO`: `AUTO_TYPE_ONLY`, конфликты или `NOT_CLASSIFIED`.

`exactTypeSubtypeAccuracy` остаётся полезной метрикой, но при корректном abstention она может уменьшаться: рискованное или ошибочное автоматическое решение заменяется конфликтом. Поэтому основной safety-показатель этого gate — `wrongFullAutoPerTotal`, а не только общая exact accuracy.

## Критерии gate

`robustness-summary.json` проверяет:

- `engineDivergences = 0` и для baseline, и для conservative;
- clean-корпус не меняется;
- conservative не увеличивает число ошибочных полных `AUTO`;
- в stress и severe захватывается хотя бы одно ошибочное полное `AUTO`;
- `fullAutoCoverage` в stress и severe остаётся не ниже 70%.

Это намеренно осторожные критерии первого этапа. Если первый независимый прогон их проходит, более жёсткие целевые значения снижения риска можно зафиксировать уже как regression gate.

## Матрица эксперимента

Baseline (`window=0`) и conservative (`window=80`) запускаются на идентичных holdout:

- balanced: clean, light, moderate, stress, severe;
- stress: device-heavy, identity-heavy, software-heavy.

По умолчанию используется 100 000 активов на noise-сценарий и 50 000 на population-сценарий. Размер можно увеличить без изменения логики gate.

## Запуск

Docker / Git Bash:

```bash
export ROBUSTNESS_COUNT=100000
export DISTRIBUTION_COUNT=50000
export SEED=20260911
export CONFLICT_WINDOW=80
export STRICT_GATE=1
sh scripts/run-robustness-v2-docker.sh
```

Для маленького smoke используйте `STRICT_GATE=0`: крошечная случайная выборка не обязана содержать каждый тип шумовой ошибки.

## Ограничения интерпретации

Gate проверяет устойчивость на контролируемых source-shaped synthetic данных. Его прохождение не доказывает production accuracy и не означает, что окно 80 универсально правильно для реального ITAM. Для production-калибровки всё ещё нужен независимо размеченный real-world или production-like корпус.
