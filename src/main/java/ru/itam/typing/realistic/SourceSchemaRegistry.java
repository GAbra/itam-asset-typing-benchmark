package ru.itam.typing.realistic;

import ru.itam.typing.realistic.model.SourceObservation;

import java.util.*;

/** Allow-list and lightweight shape validation for source-shaped research fields. */
public final class SourceSchemaRegistry {
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "ad:computer", Set.of("objectClass", "objectCategory", "sAMAccountName", "distinguishedName",
                    "operatingSystem", "userAccountControl", "dNSHostName"),
            "ad:user", Set.of("objectClass", "objectCategory", "sAMAccountName", "userPrincipalName",
                    "distinguishedName", "userAccountControl", "description"),
            "ksc:host", Set.of("KLHST_WKS_DN", "KLHST_WKS_HOSTNAME", "KLHST_WKS_FQDN", "KLHST_WKS_IP_LONG",
                    "KLHST_WKS_CTYPE", "KLHST_WKS_OS_NAME", "KLHST_WKS_STATUS", "KLHST_WKS_RTP_STATE"),
            "ksc:software_inventory_application", Set.of("ProductID", "bIsMsi", "DisplayName", "DisplayVersion",
                    "Publisher", "InstallDate", "InstallDir"),
            "nmap:host", Set.of("status", "address", "hostname", "deviceType", "vendor", "osfamily", "openPorts"),
            "zabbix:host", Set.of("hostid", "host", "name", "status", "inventory.os", "interface.ip"),
            "siem:principal", Set.of("deviceVendor", "deviceProduct", "deviceEventClassId", "category", "suser", "src"),
            "siem:host", Set.of("deviceVendor", "deviceProduct", "deviceEventClassId", "category", "dhost", "src")
    );

    private SourceSchemaRegistry() {}

    public static void validate(SourceObservation observation) {
        Set<String> allowed = FIELDS.get(observation.objectKind());
        if (allowed == null) throw new IllegalArgumentException("Unknown source object kind: " + observation.objectKind());
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
            parseLong(a, "KLHST_WKS_CTYPE");
            parseLong(a, "KLHST_WKS_STATUS");
            parseLong(a, "KLHST_WKS_RTP_STATE");
            String ipLong = a.get("KLHST_WKS_IP_LONG");
            if (ipLong != null) {
                long value = parseLong(a, "KLHST_WKS_IP_LONG");
                if (value < 0 || value > 0xffff_ffffL) {
                    throw new IllegalArgumentException("KLHST_WKS_IP_LONG must fit unsigned IPv4: " + value);
                }
                KscIpv4Long.decodeKscParamLong(value);
            }
        }
        if ("ksc:software_inventory_application".equals(observation.objectKind())) {
            String date = a.get("InstallDate");
            if (date != null && !date.matches("\\d{8}")) {
                throw new IllegalArgumentException("KSC InstallDate must be YYYYMMDD: " + date);
            }
        }
    }

    private static long parseLong(Map<String, String> attrs, String key) {
        String value = attrs.get(key);
        if (value == null) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be numeric: " + value, e);
        }
    }

    public static Map<String, Set<String>> documentedFields() {
        return FIELDS;
    }
}
