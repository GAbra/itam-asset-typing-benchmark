# Происхождение синтетических исходных данных

[English](SOURCES.md) · **Русский**

Все source samples, сохранённые в репозитории, являются **СИНТЕТИЧЕСКИМИ**. Они не содержат production data, secrets, реальных идентификаторов пользователей или адресов заказчиков.

Цель — достаточно близко воспроизвести форму и семантику типичных источников ITAM/discovery/security, чтобы локально сравнивать классификационные движки. Сам benchmark работает с нормализованным сгенерированным JSONL; raw samples являются иллюстративными fixtures, а не live integrations.

## Active Directory

AD sample использует стандартные LDAP/AD attributes: `objectClass`, `objectCategory`, `sAMAccountName`, `userPrincipalName`, `distinguishedName`, `operatingSystem`, `dNSHostName` и `userAccountControl`.

Справочная база: документация Microsoft по схеме и атрибутам Active Directory.

## Nmap

`raw-samples/nmap/scan.xml` повторяет структуру Nmap XML: `host`, `status`, `address`, `hostnames`, `ports`, `service`, `osmatch` и `osclass`. Nmap документирует device types, включая `general purpose`, `router`, `switch`, `WAP` и другие.

Ссылки:
- https://nmap.org/book/output-formats-xml-output.html
- https://nmap.org/book/osdetect-device-types.html

## Kaspersky Security Center

KSC samples используют имена из KSC 16.1 Open API. В частности, `KLHST_WKS_CTYPE` — bit set, где bit 0 означает Workstation, а bit 1 — Server. Также используются host attributes `KLHST_WKS_DN`, `KLHST_WKS_HOSTNAME`, `KLHST_WKS_FQDN`, `KLHST_WKS_OS_NAME`, `KLHST_WKS_STATUS`, `KLHST_WKS_RTP_STATE` и `KLHST_WKS_CPU_ARCH`.

Software inventory samples используют документированные `ProductID`, `bIsMsi`, `DisplayName`, `DisplayVersion`, `Publisher`, `InstallDate` и `InstallDir`.

Ссылки:
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00012.html
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00197.html
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00561.html

## Zabbix

Zabbix sample оформлен как JSON-RPC response метода `host.get` с host properties и subselect для `interfaces` и `inventory`.

Ссылка:
- https://www.zabbix.com/documentation/current/en/manual/api/reference/host/get

## SIEM / CEF

SIEM sample использует стандартную CEF header/extension syntax и распространённые extension keys, например `src`, `dhost`, `suser` и `cat`. Все значения синтетические.

## Генератор

Большие datasets генерируются локально и не коммитятся в Git. Генератор использует фиксированный seed и создаёт нормализованный `DatasetRecord` JSONL. Каждая запись также содержит expected type/subtype, чтобы correctness можно было проверять отдельно от простого совпадения движков друг с другом.

Baseline v2 сохраняет generation sidecars, seed, SHA-256 dataset и SHA-256 rules вместе с контролируемым экспериментом. Различие между проверкой синтетических меток и реальной точностью на production data описано в [методике](../docs/METHODOLOGY_RU.md).
