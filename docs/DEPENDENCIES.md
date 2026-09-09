# Dependencies and attribution

The build targets Java 21. Direct dependencies and Maven plugins are versioned in [pom.xml](../pom.xml). Library upgrades should include renewed correctness verification and separate measurements.

| Component | Version | Upstream |
|:--|:--|:--|
| CEL-Java | 0.14.0 | [google/cel-java](https://github.com/google/cel-java) |
| Apache KIE DMN | 10.2.0 | [apache/incubator-kie-drools](https://github.com/apache/incubator-kie-drools) |
| Jackson | 2.19.2 | [FasterXML/jackson](https://github.com/FasterXML/jackson) |
| SLF4J simple | 2.0.17 | [qos-ch/slf4j](https://github.com/qos-ch/slf4j) |
| JUnit Jupiter (tests) | 5.13.4 | [junit-team/junit-framework](https://github.com/junit-team/junit-framework) |

This repository's MIT license covers its original code and synthetic fixtures. It does not relicense dependencies. Their upstream license/notice files remain authoritative. The shaded build appends overlapping `META-INF/LICENSE`, `NOTICE` and their `.txt` variants and merges service providers.

CEL programs are compiled once; KIE executes a real DMN runtime. Jackson handles input/rules/report serialization outside the per-engine classification loop.

The development Dockerfile uses `maven:3.9.11-eclipse-temurin-21`; the runtime uses `eclipse-temurin:21-jre`. These tags can move as upstream images are rebuilt. Record image IDs/digests for a measurement; the portable CI artifact contains its image ID and source commit. A pinned language version alone is not a fully pinned environment.

Plotting dependencies are in [scripts/requirements-figures.txt](../scripts/requirements-figures.txt). The archived-report validator uses only Python's standard library.
