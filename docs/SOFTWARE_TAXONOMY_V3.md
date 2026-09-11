# Software Taxonomy v3 — RU-oriented enterprise workload

## Why v3 exists

The software ambiguity experiment showed that the original binary split (`SECURITY_SOFTWARE` vs `APPLICATION_SOFTWARE`) is too coarse for realistic ITAM classification. Under controlled ambiguity the legacy rules remained fully automatic even when subtype error rates became high. v3 therefore expands the software taxonomy and tests classification with a broader set of attributes.

This is still a research workload. It does **not** claim production accuracy, product market share, or prevalence in Russian organizations.

## Taxonomy

`SOFTWARE` is classified into these subtypes:

- `OPERATING_SYSTEM`
- `OFFICE_SOFTWARE`
- `BUSINESS_SOFTWARE`
- `BROWSER`
- `IDE`
- `DATABASE_TOOL`
- `DATABASE_SERVER`
- `DESIGN_MODELING`
- `SECURITY_SOFTWARE`
- `CRYPTO_SOFTWARE`
- `RUNTIME_PLATFORM`
- `DEV_TOOL`
- `UTILITY`
- `COMMUNICATION`
- `COMPONENT_AGENT`
- `APPLICATION_SOFTWARE` (other known application software)

If the evidence is insufficient, the candidate ruleset returns `SOFTWARE` with no subtype (`AUTO_TYPE_ONLY`) rather than inventing a category.

Operating systems are software assets. Device role (`SERVER`, `WORKSTATION`, etc.) remains a separate device classification concern.

## Representative catalog

`data/software-catalog-v3.yaml` is a coverage-oriented catalog of product families that may occur in Russian enterprise and IT environments. It intentionally mixes Russian/domestic and international products. Examples include:

- Astra Linux, RED OS, ALT, Windows, Ubuntu, Debian;
- 1C:Enterprise family, 1C:EDT, ConsultantPlus;
- Microsoft Office / Microsoft 365 Apps, MyOffice, R7 Office, LibreOffice;
- Yandex Browser, Chrome, Edge, Firefox;
- IntelliJ IDEA, PyCharm, WebStorm, Rider, Visual Studio, VS Code;
- DBeaver, DataGrip, pgAdmin, SQL Server Management Studio, Oracle SQL Developer;
- PostgreSQL, Postgres Pro, Microsoft SQL Server, MySQL, MariaDB;
- Figma, draw.io, AutoCAD, nanoCAD;
- Kaspersky products, Dr.Web, ESET, CryptoPro;
- JDK/.NET/Python/Node.js runtimes, Git, Maven, Gradle, Docker Desktop, Postman, WinDbg, GDB;
- common utilities, communication clients and software components/agents.

Catalog composition is **not** a usage-frequency model. Entries are included to exercise families, lookalikes and attribute combinations. Controlled package IDs and install paths are research evidence values, not guaranteed vendor-exact installer contracts.

Official product references used to keep representative product names/families grounded include:

- 1C platform: https://v8.1c.ru/platforma/
- Astra Linux: https://astralinux.ru/
- RED OS: https://redos.red-soft.ru/
- BaseALT: https://www.basealt.ru/
- Yandex Browser: https://browser.yandex.ru/
- Kaspersky Endpoint Security: https://www.kaspersky.ru/small-to-medium-business-security/endpoint-windows
- Postgres Pro: https://postgrespro.ru/products
- DBeaver documentation: https://dbeaver.com/docs/dbeaver/
- Figma desktop downloads: https://www.figma.com/downloads/

These references support product/family existence and naming only; they are not evidence of market share.

## Evidence model

The v3 classifier does not classify from `DisplayName` alone. It may use normalized inventory evidence such as:

- `DisplayName`
- `Publisher`
- `ProductFamily`
- `PackageId`
- opaque `ProductID`
- `InstallDir` / `InstallLocation`
- executable names
- service names
- platform / OS family
- architecture
- version and inventory metadata

Examples of deliberate separations:

- Visual Studio → `IDE`; Visual C++ Redistributable → `RUNTIME_PLATFORM`.
- Microsoft SQL Server → `DATABASE_SERVER`; SQL Server Management Studio → `DATABASE_TOOL`.
- 1C:Enterprise → `BUSINESS_SOFTWARE`; 1C:EDT → `IDE`.
- Kaspersky Endpoint Security → `SECURITY_SOFTWARE`; Kaspersky Network Agent → `COMPONENT_AGENT`.
- Edge browser → `BROWSER`; WebView2 Runtime → `COMPONENT_AGENT`.

## Independence and leakage controls

The workload generator reads the checked-in catalog and writes ground truth separately. It does not read typing rules or `FeatureExtractor`.

The typing classifier does not load the labelled catalog. It sees only normalized inventory attributes. Synthetic `ProductID` values are opaque hashes rather than catalog keys so the catalog ID cannot act as a direct label shortcut.

All engines receive the same feature map and candidate ruleset. The independent reference evaluator remains part of the differential gate.

## Noise scenarios

The same deterministic product selection is reused across `clean`, `light`, `stress` and `severe`; only evidence degradation changes. Noise can remove or generalize `DisplayName`, remove `Publisher`, and remove supporting evidence (`ProductFamily`, package ID, paths, executables, services).

These rates are stress-test parameters, not estimates of production data quality.

## Decision policy

No new per-source weights are introduced. The previously calibrated conservative conflict-priority window remains fixed at `80`.

## Run

```bash
export SOFTWARE_COUNT=200000
export SEED=20260916
export CONFLICT_WINDOW=80
export STRICT_GATE=1
sh scripts/run-software-taxonomy-v3-docker.sh
```

The run produces paired accuracy reports plus `software-taxonomy-summary.json`, provenance and SHA-256 manifests. Large generated JSONL corpora and logs remain ignored by Git.

## Scope limitation

A PASS demonstrates deterministic engine agreement and decision quality on this controlled RU-oriented synthetic workload. It does **not** establish real-world production accuracy. That still requires an independently labelled real or properly anonymized production-like corpus.
