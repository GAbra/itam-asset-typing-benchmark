package ru.itam.typing.features;

import ru.itam.typing.model.AssetTypingContext;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FeatureExtractor {
    public static final Set<String> KNOWN_FEATURES = Set.of(
            "SRC_AD", "SRC_NMAP", "SRC_KSC", "SRC_ZABBIX", "SRC_SIEM",
            "OBJ_AD_USER", "OBJ_AD_COMPUTER", "OBJ_NMAP_HOST", "OBJ_KSC_HOST",
            "OBJ_KSC_SOFTWARE", "OBJ_ZABBIX_HOST", "OBJ_SIEM_PRINCIPAL", "OBJ_SIEM_HOST",
            "ACCOUNT_SERVICE_HINT", "OS_WINDOWS", "OS_LINUX", "OS_SERVER",
            "KSC_WORKSTATION", "KSC_SERVER", "NMAP_NETWORK_DEVICE", "NMAP_GENERAL_PURPOSE",
            "SECURITY_SOFTWARE_HINT",
            "SOFTWARE_IDENTITY_COMPLETE", "SOFTWARE_IDENTITY_MISSING",
            "SECURITY_SOFTWARE_NAME_HINT", "SECURITY_SOFTWARE_PUBLISHER_HINT",
            "SECURITY_SOFTWARE_STRONG_HINT", "SOFTWARE_COMPONENT_HINT", "APPLICATION_SOFTWARE_HINT"
    );

    public Map<String, Boolean> extract(AssetTypingContext ctx) {
        Map<String, Boolean> f = new LinkedHashMap<>();
        KNOWN_FEATURES.stream().sorted().forEach(k -> f.put(k, false));

        for (String source : ctx.sources()) {
            set(f, "SRC_" + source.toUpperCase(Locale.ROOT).replace('-', '_'));
        }

        boolean softwareObject = false;
        for (String kind : ctx.sourceObjectKinds()) {
            switch (kind) {
                case "ad:user" -> set(f, "OBJ_AD_USER");
                case "ad:computer" -> set(f, "OBJ_AD_COMPUTER");
                case "nmap:host" -> set(f, "OBJ_NMAP_HOST");
                case "ksc:host" -> set(f, "OBJ_KSC_HOST");
                case "ksc:software_inventory_application" -> {
                    set(f, "OBJ_KSC_SOFTWARE");
                    softwareObject = true;
                }
                case "zabbix:host" -> set(f, "OBJ_ZABBIX_HOST");
                case "siem:principal" -> set(f, "OBJ_SIEM_PRINCIPAL");
                case "siem:host" -> set(f, "OBJ_SIEM_HOST");
                default -> { }
            }
        }

        String adName = lower(ctx.attributes().get("ad.sAMAccountName"));
        String adDescription = lower(ctx.attributes().get("ad.description"));
        if (adName.startsWith("svc_") || adName.startsWith("sa_") || adName.endsWith("_svc")
                || adDescription.contains("service account")) {
            set(f, "ACCOUNT_SERVICE_HINT");
        }

        String os = String.join(" ",
                lower(ctx.attributes().get("ad.operatingSystem")),
                lower(ctx.attributes().get("ksc.KLHST_WKS_OS_NAME")),
                lower(ctx.attributes().get("zabbix.inventory.os")),
                lower(ctx.attributes().get("nmap.osfamily")));
        if (os.contains("windows")) set(f, "OS_WINDOWS");
        if (os.contains("linux") || os.contains("ubuntu") || os.contains("debian") || os.contains("rhel")) set(f, "OS_LINUX");
        if (os.contains("server")) set(f, "OS_SERVER");

        long ctype = parseLong(ctx.attributes().get("ksc.KLHST_WKS_CTYPE"));
        if ((ctype & 1L) != 0L) set(f, "KSC_WORKSTATION");
        if ((ctype & 2L) != 0L) set(f, "KSC_SERVER");

        String nmapDeviceType = lower(ctx.attributes().get("nmap.deviceType"));
        if (Set.of("router", "switch", "wap", "broadband router", "bridge", "firewall", "security-misc")
                .contains(nmapDeviceType)) {
            set(f, "NMAP_NETWORK_DEVICE");
        }
        if ("general purpose".equals(nmapDeviceType)) {
            set(f, "NMAP_GENERAL_PURPOSE");
        }

        String displayName = lower(ctx.attributes().get("ksc.DisplayName"));
        String publisher = lower(ctx.attributes().get("ksc.Publisher"));

        // Preserve the legacy feature exactly so the canonical rules keep their historical semantics.
        if (displayName.contains("kaspersky") || displayName.contains("endpoint security")
                || displayName.contains("defender") || displayName.contains("eset")
                || displayName.contains("sophos") || publisher.contains("kaspersky lab")
                || publisher.contains("eset") || publisher.contains("sophos")) {
            set(f, "SECURITY_SOFTWARE_HINT");
        }

        if (softwareObject) {
            boolean identityComplete = !displayName.isBlank() && !publisher.isBlank();
            if (identityComplete) set(f, "SOFTWARE_IDENTITY_COMPLETE");
            else set(f, "SOFTWARE_IDENTITY_MISSING");

            boolean securityNameHint = containsAny(displayName,
                    "kaspersky", "endpoint security", "defender", "eset", "sophos",
                    "antivirus", "anti-virus", "antimalware", "anti-malware",
                    "endpoint protection", "threat protection", "host protection");
            boolean securityPublisherHint = containsAny(publisher,
                    "kaspersky", "eset", "sophos", "crowdstrike", "sentinelone",
                    "bitdefender", "trellix", "mcafee", "trend micro");
            boolean componentHint = containsAny(displayName,
                    " agent", "agent ", "component", "runtime", " module", "module ",
                    " service", "service ", "updater", "update service", " core", "core ",
                    " driver", "driver ", " plugin", "plugin ");

            if (securityNameHint) set(f, "SECURITY_SOFTWARE_NAME_HINT");
            if (securityPublisherHint) set(f, "SECURITY_SOFTWARE_PUBLISHER_HINT");
            if (componentHint) set(f, "SOFTWARE_COMPONENT_HINT");

            // Strong subtype evidence requires two independent signals: product name and publisher.
            if (identityComplete && securityNameHint && securityPublisherHint) {
                set(f, "SECURITY_SOFTWARE_STRONG_HINT");
            }

            // Application subtype is automatic only when identity is complete and no security/component ambiguity exists.
            if (identityComplete && !securityNameHint && !securityPublisherHint && !componentHint) {
                set(f, "APPLICATION_SOFTWARE_HINT");
            }
        }
        return f;
    }

    private static void set(Map<String, Boolean> features, String key) {
        if (features.containsKey(key)) features.put(key, true);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) return true;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
