package ru.itam.typing.features;

import ru.itam.typing.model.AssetSubtype;

import java.util.Locale;
import java.util.Map;

/**
 * Deterministic software evidence classifier used by the research taxonomy v3 rules.
 * It never reads the labelled catalog. Unknown/insufficient evidence returns null.
 */
public final class SoftwareEvidenceClassifier {
    private SoftwareEvidenceClassifier() {}

    public static AssetSubtype classify(Map<String, String> attributes) {
        String name = lower(attributes.get("ksc.DisplayName"));
        String publisher = lower(attributes.get("ksc.Publisher"));
        String family = lower(attributes.get("ksc.ProductFamily"));
        String productId = lower(attributes.get("ksc.ProductID"));
        String packageId = lower(attributes.get("ksc.PackageId"));
        String install = lower(first(attributes, "ksc.InstallDir", "ksc.InstallLocation"));
        String executables = lower(attributes.get("ksc.Executables"));
        String services = lower(attributes.get("ksc.Services"));
        String platform = lower(attributes.get("ksc.Platform"));
        String evidence = String.join(" | ", name, family, productId, packageId, install, executables, services, platform);

        // Components/agents must be recognized before their parent vendor/product family.
        // Executable/service identities remain useful when DisplayName is missing or generalized.
        if (containsAny(evidence,
                "kaspersky security center network agent", "kaspersky network agent", "klnagent.exe", "klnagent",
                "microsoft edge webview2", "webview2 runtime", "msedgewebview2.exe", "msedgewebview2",
                "1c server agent", "1с сервер агент", "1cv8 server agent",
                "1c:enterprise server agent", "1с:предприятие server agent",
                "1c:enterprise 8.3 server agent", "ragent.exe", "rmngr.exe",
                "update service component"))
            return AssetSubtype.COMPONENT_AGENT;

        if (containsAny(evidence,
                "windows 11", "windows 10", "windows server", "astra linux", "ред ос", "red os",
                "альт рабочая станция", "alt workstation", "альт сервер", "alt server",
                "ubuntu desktop", "ubuntu server", "debian gnu/linux"))
            return AssetSubtype.OPERATING_SYSTEM;

        if (containsAny(evidence,
                "microsoft 365 apps", "microsoft office", "office professional", "office standard",
                "р7-офис", "r7-office", "мойофис", "myoffice", "libreoffice"))
            return AssetSubtype.OFFICE_SOFTWARE;

        // 1C:EDT is an IDE, not a business application.
        if (containsAny(evidence,
                "1c:edt", "1с:edt", "1c enterprise development tools", "intellij idea", "pycharm",
                "webstorm", "jetbrains rider", "visual studio code", "vscode", "microsoft visual studio",
                "visualstudio"))
            return AssetSubtype.IDE;

        if (containsAny(evidence,
                "1с:предприятие", "1c:enterprise", "1с:erp", "1c:erp", "1с:бухгалтерия",
                "1с:зарплата", "1с:зуп", "1c:zup", "1с:документооборот", "consultantplus",
                "консультантплюс"))
            return AssetSubtype.BUSINESS_SOFTWARE;

        if (containsAny(evidence,
                "яндекс браузер", "yandex browser", "google chrome", "mozilla firefox",
                "microsoft edge browser") || packageId.contains("yandex.browser"))
            return AssetSubtype.BROWSER;

        // Administration clients before server tokens such as SQL Server/PostgreSQL.
        if (containsAny(evidence,
                "dbeaver", "datagrip", "pgadmin", "sql server management studio", "ssms",
                "oracle sql developer", "heidisql"))
            return AssetSubtype.DATABASE_TOOL;

        if (containsAny(evidence,
                "postgres pro", "postgresql", "microsoft sql server", "mysql server", "mariadb server"))
            return AssetSubtype.DATABASE_SERVER;

        if (containsAny(evidence,
                "figma", "draw.io", "diagrams.net", "autocad", "nanocad"))
            return AssetSubtype.DESIGN_MODELING;

        if (containsAny(evidence,
                "криптопро csp", "cryptopro csp", "криптопро эцп browser plug-in",
                "cryptopro browser plug-in", "cryptopro extension")
                || publisher.contains("крипто-про") || publisher.contains("cryptopro"))
            return AssetSubtype.CRYPTO_SOFTWARE;

        // A parent-vendor publisher alone is not enough to call something endpoint security.
        // This prevents degraded Network Agent records from becoming SECURITY_SOFTWARE merely
        // because the publisher is Kaspersky/Doctor Web/ESET. Explicit product/family/package,
        // executable, service or path evidence is still sufficient through the evidence string.
        if (containsAny(evidence,
                "kaspersky endpoint security", "dr.web", "doctor web", "eset endpoint security",
                "endpoint protection platform", "kaspersky security center"))
            return AssetSubtype.SECURITY_SOFTWARE;

        // Runtimes before generic developer tools.
        if (containsAny(evidence,
                "microsoft visual c++ redistributable", "visual c++ redistributable", ".net runtime",
                ".net sdk", "openjdk", "temurin", "java runtime", "jre ", "jdk ",
                "python 3", "node.js", "nodejs"))
            return AssetSubtype.RUNTIME_PLATFORM;

        if (containsAny(evidence,
                "git for windows", "apache maven", "gradle", "docker desktop", "postman",
                "windbg", "gnu debugger", " gdb", "cmake"))
            return AssetSubtype.DEV_TOOL;

        if (containsAny(evidence,
                "7-zip", "winrar", "notepad++", "putty", "winscp", "far manager"))
            return AssetSubtype.UTILITY;

        if (containsAny(evidence,
                "telegram desktop", "microsoft teams", "zoom workplace", "zoom meetings",
                "trueconf", "vk teams"))
            return AssetSubtype.COMMUNICATION;

        if (containsAny(evidence,
                "adobe acrobat reader", "acrobat reader", "vlc media player", "obs studio"))
            return AssetSubtype.APPLICATION_SOFTWARE;

        return null;
    }

    public static String feature(AssetSubtype subtype) {
        return "SOFTWARE_CATEGORY_" + subtype.name();
    }

    private static String first(Map<String, String> attributes, String... keys) {
        for (String key : keys) {
            String value = attributes.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
