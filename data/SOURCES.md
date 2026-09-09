# Provenance of synthetic source data

**English** · [Русский](SOURCES_RU.md)

All committed source samples are **SYNTHETIC**. They contain no production data, secrets, real user identifiers or customer addresses.

The goal is to reproduce the shape and semantics of common ITAM discovery/security sources closely enough for a local classification-engine comparison. The benchmark itself consumes normalized generated JSONL; these raw samples are illustrative fixtures, not live integrations.

## Active Directory

The AD sample uses standard LDAP/AD attributes such as `objectClass`, `objectCategory`, `sAMAccountName`, `userPrincipalName`, `distinguishedName`, `operatingSystem`, `dNSHostName` and `userAccountControl`.

Reference: Microsoft Active Directory schema and attribute documentation.

## Nmap

`raw-samples/nmap/scan.xml` follows Nmap XML structure: `host`, `status`, `address`, `hostnames`, `ports`, `service`, `osmatch` and `osclass`. Nmap documents device types including `general purpose`, `router`, `switch`, `WAP` and others.

References:
- https://nmap.org/book/output-formats-xml-output.html
- https://nmap.org/book/osdetect-device-types.html

## Kaspersky Security Center

The KSC samples use names documented in KSC 16.1 Open API. In particular, `KLHST_WKS_CTYPE` is a bit set where bit 0 means Workstation and bit 1 means Server. Other host attributes used include `KLHST_WKS_DN`, `KLHST_WKS_HOSTNAME`, `KLHST_WKS_FQDN`, `KLHST_WKS_OS_NAME`, `KLHST_WKS_STATUS`, `KLHST_WKS_RTP_STATE` and `KLHST_WKS_CPU_ARCH`.

Software inventory samples use documented `ProductID`, `bIsMsi`, `DisplayName`, `DisplayVersion`, `Publisher`, `InstallDate` and `InstallDir` attributes.

References:
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00012.html
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00197.html
- https://support.kaspersky.com/help/KSC/16.1/KSCAPI/a00561.html

## Zabbix

The Zabbix sample is shaped as a JSON-RPC `host.get` response with host properties plus `interfaces` and `inventory` subselects.

Reference:
- https://www.zabbix.com/documentation/current/en/manual/api/reference/host/get

## SIEM / CEF

The SIEM sample uses conventional CEF header/extension syntax and common extension keys such as `src`, `dhost`, `suser` and `cat`. Values are synthetic.

## Generator

Large datasets are generated locally instead of committed to Git. The generator uses a fixed seed and emits normalized `DatasetRecord` JSONL. Every generated record also contains expected type/subtype so correctness can be checked separately from engine-to-engine equivalence.

Baseline v2 preserves the generation sidecars, seed, dataset SHA-256 and rules SHA-256 with the controlled experiment. See [methodology](../docs/METHODOLOGY.md) for the distinction between synthetic-label validation and real-world accuracy.
