package ru.itam.typing.realistic;

import ru.itam.typing.realistic.model.SourceObservation;

import java.util.*;

/**
 * Audited allow-list and shape checks for source-shaped fields used by the research workload.
 * The registry intentionally validates source syntax only; it never decides an asset type.
 */
public final class SourceSchemaRegistry {
    private static final Map<String, String> SOURCE_BY_KIND = Map.of(
            "ad:computer", "AD",
            "ad:user", "AD",
            "ksc:host", "KSC",
            "ksc:software_inventory_application", "KSC",
            "nmap:host", "NMAP",
            "zabbix:host", "ZABBIX",
            "siem:principal", "SIEM",
            "siem:host", "SIEM"
    );

    private static final Map<String, Set<String>> FIELDS = Map.of(
            "ad:computer", Set.of("objectClass", "objectCategory", "sAMAccountName", "distinguishedName",
                    "operatingSystem", "userAccountControl", "dNSHostName"),
            "ad:user", Set.of("objectClass", "objectCategory", "sAMAccountName", "userPrincipalName",
                    "distinguishedName", "userAccountControl", "description"),
            "ksc:host", Set.of("KLHST_WKS_DN", "KLHST_WKS_HOSTNAME", "KLHST_WKS_WINHOSTNAME",
                    "KLHST_WKS_DNSDOMAIN", "KLHST_WKS_DNSNAME", "KLHST_WKS_FQDN", "KLHST_WKS_IP_LONG",
                    "KLHST_WKS_CTYPE", "KLHST_WKS_OS_NAME", "KLHST_WKS_STATUS", "KLHST_WKS_RTP_STATE",
                    "KLHST_WKS_CPU_ARCH"),
            "ksc:software_inventory_application", Set.of("ProductID", "bIsMsi", "DisplayName", "DisplayVersion",
                    "Publisher", "InstallDate", "InstallDir"),
            "nmap:host", Set.of("status", "address", "hostname", "deviceType", "vendor", "osfamily", "openPorts"),
            "zabbix:host", Set.of("hostid", "host", "name", "status", "inventory.os", "interface.ip"),
            "siem:principal", Set.of("deviceVendor", "deviceProduct", "deviceEventClassId", "category", "suser", "src"),
            "siem:host", Set.of("deviceVendor", "deviceProduct", "deviceEventClassId", "category", "dhost", "src")
    );

    private SourceSchemaRegistry() {}

    public static void validate(SourceObservation observation) {
        Objects.requireNonNull(observation, "observation");
        String expectedSource = SOURCE_BY_KIND.get(observation.objectKind());
        if (expectedSource == null) {
            throw new IllegalArgumentException("Unknown source object kind: " + observation.objectKind());
        }
        if (!expectedSource.equalsIgnoreCase(observation.source())) {
            throw new IllegalArgumentException("Source/object kind mismatch: " + observation.source()
                    + " cannot emit " + observation.objectKind());
        }
        if (observation.observedAtEpochMs() < 0) {
            throw new IllegalArgumentException("observedAtEpochMs must be >= 0");
        }

        Set<String> allowed = FIELDS.get(observation.objectKind());
        Set<String> unexpected = new TreeSet<>(observation.attributes().keySet());
        unexpected.removeAll(allowed);
        if (!unexpected.isEmpty()) {
            throw new IllegalArgumentException(observation.objectKind() + " has undocumented fields: " + unexpected);
        }
        validateFieldShapes(observation);
    }

    private static void validateFieldShapes(SourceObservation observation) {
        Map<String, String> a = observation.attributes();
        if ("ksc:host".equals(observation.objectKind())) {
            long ctype = parseLong(a, "KLHST_WKS_CTYPE", -1L);
            if (ctype < 0 && a.containsKey("KLHST_WKS_CTYPE")) {
                throw new IllegalArgumentException("KLHST_WKS_CTYPE must be non-negative");
            }
            long status = parseLong(a, "KLHST_WKS_STATUS", -1L);
            if (status < 0 && a.containsKey("KLHST_WKS_STATUS")) {
                throw new IllegalArgumentException("KLHST_WKS_STATUS must be non-negative");
            }
            long rtp = parseLong(a, "KLHST_WKS_RTP_STATE", -1L);
            if (a.containsKey("KLHST_WKS_RTP_STATE") && (rtp < 0 || rtp > 11)) {
                throw new IllegalArgumentException("KLHST_WKS_RTP_STATE must be in [0,11]: " + rtp);
            }
            if (a.containsKey("KLHST_WKS_IP_LONG")) {
                long ipLong = parseLong(a, "KLHST_WKS_IP_LONG", -1L);
                KscIpv4Long.decodeDocumentedLittleEndian(ipLong);
            }
        }
        if ("ksc:software_inventory_application".equals(observation.objectKind())) {
            String date = a.get("InstallDate");
            if (date != null && !date.matches("\\d{8}")) {
                throw new IllegalArgumentException("KSC InstallDate must be YYYYMMDD: " + date);
            }
            String isMsi = a.get("bIsMsi");
            if (isMsi != null && !"true".equalsIgnoreCase(isMsi) && !"false".equalsIgnoreCase(isMsi)) {
                throw new IllegalArgumentException("KSC bIsMsi must be boolean: " + isMsi);
            }
        }
        if ("nmap:host".equals(observation.objectKind())) {
            validateIpv4IfPresent(a.get("address"), "nmap.address");
        }
        if ("zabbix:host".equals(observation.objectKind())) {
            validateIpv4IfPresent(a.get("interface.ip"), "zabbix.interface.ip");
        }
    }

    private static long parseLong(Map<String, String> attrs, String key, long missing) {
        String value = attrs.get(key);
        if (value == null) return missing;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be numeric: " + value, e);
        }
    }

    private static void validateIpv4IfPresent(String value, String key) {
        if (value == null || value.isBlank()) return;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException(key + " must be IPv4: " + value);
        for (String part : parts) {
            try {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(key + " must be IPv4: " + value, e);
            }
        }
    }

    public static Map<String, Set<String>> documentedFields() {
        return FIELDS;
    }
}
