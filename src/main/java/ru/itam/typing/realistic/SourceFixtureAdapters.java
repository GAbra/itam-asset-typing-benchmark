package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import ru.itam.typing.realistic.model.SourceObservation;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsers for the checked-in source-shaped fixtures. These adapters prove that the research
 * normalization boundary can ingest representations shaped like AD exports, KSC Open API,
 * Nmap XML, Zabbix JSON-RPC and CEF instead of only consuming generator-native objects.
 */
public final class SourceFixtureAdapters {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final long FIXTURE_EPOCH_MS = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli();
    private static final Pattern CEF_KEY = Pattern.compile("(?:^|\\s)([A-Za-z][A-Za-z0-9_.]*)=");

    private SourceFixtureAdapters() {}

    public static List<SourceObservation> readAdJsonl(Path path) throws IOException {
        List<SourceObservation> out = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = JSON.readTree(line);
            boolean computer = containsText(node.get("objectClass"), "computer")
                    || text(node, "objectCategory").toLowerCase(Locale.ROOT).contains("cn=computer");
            String kind = computer ? "ad:computer" : "ad:user";
            Map<String, String> attrs = copyAllowed(node, kind);
            attrs.put("objectClass", computer ? "computer" : "user");
            out.add(audited("AD", kind, attrs));
        }
        return List.copyOf(out);
    }

    public static List<SourceObservation> readKscHostsJsonl(Path path) throws IOException {
        return readJsonObjectLines(path, "KSC", "ksc:host");
    }

    public static List<SourceObservation> readKscSoftwareJsonl(Path path) throws IOException {
        return readJsonObjectLines(path, "KSC", "ksc:software_inventory_application");
    }

    public static List<SourceObservation> readNmapXml(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(path.toFile());
        NodeList hosts = document.getElementsByTagName("host");
        List<SourceObservation> out = new ArrayList<>();
        for (int i = 0; i < hosts.getLength(); i++) {
            Element host = (Element) hosts.item(i);
            Map<String, String> attrs = new LinkedHashMap<>();

            Element status = first(host, "status");
            putAttr(attrs, "status", status, "state");

            NodeList addresses = host.getElementsByTagName("address");
            for (int j = 0; j < addresses.getLength(); j++) {
                Element address = (Element) addresses.item(j);
                if ("ipv4".equalsIgnoreCase(address.getAttribute("addrtype"))) {
                    putAttr(attrs, "address", address, "addr");
                    break;
                }
            }

            Element hostname = first(host, "hostname");
            putAttr(attrs, "hostname", hostname, "name");

            Element osclass = first(host, "osclass");
            putAttr(attrs, "deviceType", osclass, "type");
            putAttr(attrs, "vendor", osclass, "vendor");
            putAttr(attrs, "osfamily", osclass, "osfamily");

            List<String> openPorts = new ArrayList<>();
            NodeList ports = host.getElementsByTagName("port");
            for (int j = 0; j < ports.getLength(); j++) {
                Element port = (Element) ports.item(j);
                Element state = first(port, "state");
                if (state != null && "open".equalsIgnoreCase(state.getAttribute("state"))) {
                    String portId = port.getAttribute("portid");
                    if (!portId.isBlank()) openPorts.add(portId);
                }
            }
            if (!openPorts.isEmpty()) attrs.put("openPorts", String.join(",", openPorts));
            out.add(audited("NMAP", "nmap:host", attrs));
        }
        return List.copyOf(out);
    }

    public static List<SourceObservation> readZabbixHostGet(Path path) throws IOException {
        JsonNode root = JSON.readTree(path.toFile());
        JsonNode result = root.get("result");
        if (result == null || !result.isArray()) throw new IllegalArgumentException("Zabbix fixture must contain result[]");
        List<SourceObservation> out = new ArrayList<>();
        for (JsonNode host : result) {
            Map<String, String> attrs = new LinkedHashMap<>();
            copyText(host, attrs, "hostid", "hostid");
            copyText(host, attrs, "host", "host");
            copyText(host, attrs, "name", "name");
            copyText(host, attrs, "status", "status");
            JsonNode interfaces = host.get("interfaces");
            if (interfaces != null && interfaces.isArray() && !interfaces.isEmpty()) {
                copyText(interfaces.get(0), attrs, "ip", "interface.ip");
            }
            JsonNode inventory = host.get("inventory");
            if (inventory != null && inventory.isObject()) copyText(inventory, attrs, "os", "inventory.os");
            out.add(audited("ZABBIX", "zabbix:host", attrs));
        }
        return List.copyOf(out);
    }

    public static List<SourceObservation> readCef(Path path) throws IOException {
        List<SourceObservation> out = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            List<String> parts = splitCefHeader(line);
            if (parts.size() != 8 || !parts.get(0).startsWith("CEF:")) {
                throw new IllegalArgumentException("Invalid CEF record: " + line);
            }
            Map<String, String> ext = parseCefExtension(parts.get(7));
            boolean principal = ext.containsKey("suser");
            String kind = principal ? "siem:principal" : "siem:host";
            Map<String, String> attrs = new LinkedHashMap<>();
            attrs.put("deviceVendor", unescapeCef(parts.get(1)));
            attrs.put("deviceProduct", unescapeCef(parts.get(2)));
            attrs.put("deviceEventClassId", unescapeCef(parts.get(4)));
            copy(ext, attrs, "cat", "category");
            copy(ext, attrs, "src", "src");
            if (principal) copy(ext, attrs, "suser", "suser");
            else copy(ext, attrs, "dhost", "dhost");
            out.add(audited("SIEM", kind, attrs));
        }
        return List.copyOf(out);
    }

    private static List<SourceObservation> readJsonObjectLines(Path path, String source, String kind) throws IOException {
        List<SourceObservation> out = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = JSON.readTree(line);
            out.add(audited(source, kind, copyAllowed(node, kind)));
        }
        return List.copyOf(out);
    }

    private static Map<String, String> copyAllowed(JsonNode node, String kind) {
        Map<String, String> attrs = new LinkedHashMap<>();
        for (String field : SourceSchemaRegistry.documentedFields().get(kind)) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.isContainerNode()) attrs.put(field, value.asText());
        }
        return attrs;
    }

    private static SourceObservation audited(String source, String kind, Map<String, String> attrs) {
        SourceObservation observation = new SourceObservation(source, kind, FIXTURE_EPOCH_MS, attrs);
        SourceSchemaRegistry.validate(observation);
        return observation;
    }

    private static boolean containsText(JsonNode node, String expected) {
        if (node == null) return false;
        if (node.isArray()) {
            for (JsonNode item : node) if (expected.equalsIgnoreCase(item.asText())) return true;
            return false;
        }
        return expected.equalsIgnoreCase(node.asText());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static void copyText(JsonNode source, Map<String, String> target, String sourceKey, String targetKey) {
        JsonNode value = source.get(sourceKey);
        if (value != null && !value.isNull() && !value.isContainerNode()) target.put(targetKey, value.asText());
    }

    private static void putAttr(Map<String, String> out, String key, Element element, String attribute) {
        if (element == null) return;
        String value = element.getAttribute(attribute);
        if (!value.isBlank()) out.put(key, value);
    }

    private static Element first(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        return list.getLength() == 0 ? null : (Element) list.item(0);
    }

    private static List<String> splitCefHeader(String line) {
        List<String> parts = new ArrayList<>(8);
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (escaped) {
                current.append('\\').append(c);
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '|' && parts.size() < 7) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (escaped) current.append('\\');
        parts.add(current.toString());
        return parts;
    }

    private static Map<String, String> parseCefExtension(String extension) {
        Map<String, String> result = new LinkedHashMap<>();
        Matcher matcher = CEF_KEY.matcher(extension);
        List<String> keys = new ArrayList<>();
        List<Integer> valueStarts = new ArrayList<>();
        List<Integer> keyStarts = new ArrayList<>();
        while (matcher.find()) {
            keys.add(matcher.group(1));
            valueStarts.add(matcher.end());
            keyStarts.add(matcher.start());
        }
        for (int i = 0; i < keys.size(); i++) {
            int end = i + 1 < keys.size() ? keyStarts.get(i + 1) : extension.length();
            result.put(keys.get(i), unescapeCef(extension.substring(valueStarts.get(i), end).trim()));
        }
        return result;
    }

    private static String unescapeCef(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!escaped && c == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                if (c == 'n') out.append('\n');
                else if (c == 'r') out.append('\r');
                else out.append(c);
                escaped = false;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private static void copy(Map<String, String> source, Map<String, String> target, String sourceKey, String targetKey) {
        String value = source.get(sourceKey);
        if (value != null && !value.isBlank()) target.put(targetKey, value);
    }
}
