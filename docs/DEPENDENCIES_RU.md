# Зависимости и атрибуция

[English](DEPENDENCIES.md) · **Русский**

Проект собирается под Java 21. Версии прямых зависимостей и Maven plugins зафиксированы в [pom.xml](../pom.xml). Обновление библиотек должно сопровождаться повторной проверкой корректности и отдельными performance measurements.

| Компонент | Версия | Upstream |
|:--|:--|:--|
| CEL-Java | 0.14.0 | [google/cel-java](https://github.com/google/cel-java) |
| Apache KIE DMN | 10.2.0 | [apache/incubator-kie-drools](https://github.com/apache/incubator-kie-drools) |
| Jackson | 2.19.2 | [FasterXML/jackson](https://github.com/FasterXML/jackson) |
| SLF4J simple | 2.0.17 | [qos-ch/slf4j](https://github.com/qos-ch/slf4j) |
| JUnit Jupiter (tests) | 5.13.4 | [junit-team/junit-framework](https://github.com/junit-team/junit-framework) |

MIT-лицензия этого репозитория распространяется на его собственный код и синтетические fixtures, но не перелицензирует сторонние зависимости. Авторитетными остаются их upstream license/notice files. Shaded build объединяет service providers и сохраняет пересекающиеся `META-INF/LICENSE`, `NOTICE` и `.txt` variants.

CEL programs компилируются один раз; KIE использует реальный DMN runtime. Jackson отвечает за сериализацию input/rules/reports вне per-engine classification loop.

Development Dockerfile использует `maven:3.9.11-eclipse-temurin-21`, runtime — `eclipse-temurin:21-jre`. Upstream tags могут изменяться при пересборке образов, поэтому для измерений нужно фиксировать image ID/digest. В baseline v2 дополнительно сохранены exact image ID и SHA-256 JAR контролируемого запуска.

Зависимости для построения графиков находятся в [scripts/requirements-figures.txt](../scripts/requirements-figures.txt). Валидаторы архивных отчётов и baseline v2 используют стандартную библиотеку Python.
