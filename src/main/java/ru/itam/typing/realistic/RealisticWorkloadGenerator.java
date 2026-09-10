package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Generates latent assets first, then source observations with independent imperfections.
 * It never reads canonical rules and never invokes FeatureExtractor or a typing engine.
 */
public final class RealisticWorkloadGenerator {
    public static final long DEFAULT_SEED = 20260910L;
    private static final long REFERENCE_EPOCH_MS = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli();
    private static final long DAY_MS = 86_400_000L;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public GenerationSummary generate(long count, long seed, Path rawOut, Path truthOut, NoiseProfile noise) throws IOException {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        Objects.requireNonNull(noise, "noise");
        Files.createDirectories(rawOut.toAbsolutePath().getParent());
        Files.createDirectories(truthOut.toAbsolutePath().getParent());

        SplittableRandom root = new SplittableRandom(seed);
        EnumMap<TruthProfile, Long> profiles = new EnumMap<>(TruthProfile.class);
        EnumMap<NoiseKind, Long> events = new EnumMap<>(NoiseKind.class);
        for (TruthProfile p : TruthProfile.values()) profiles.put(p, 0L);
        for (NoiseKind n : NoiseKind.values()) events.put(n, 0L);

        try (BufferedWriter raw = Files.newBufferedWriter(rawOut, StandardCharsets.UTF_8);
             BufferedWriter truth = Files.newBufferedWriter(truthOut, StandardCharsets.UTF_8)) {
            for (long i = 0; i < count; i++) {
                TruthProfile profile = TruthProfile.values()[root.nextInt(TruthProfile.values().length)];
                profiles.merge(profile, 1L, Long::sum);
                String assetId = String.format(Locale.ROOT, "real-%09d", i + 1);
                SplittableRandom r = root.split();
                RawAssetBundle bundle = buildBundle(assetId, profile, r, noise, events);
                GroundTruthLabel label = new GroundTruthLabel(assetId, profile.type, profile.subtype, profile.name());
                raw.write(json.writeValueAsString(bundle));
                raw.write('\n');
                truth.write(json.writeValueAsString(label));
                truth.write('\n');
            }
        }

        Map<String, Long> profileCounts = new TreeMap<>();
        profiles.forEach((k, v) -> profileCounts.put(k.name(), v));
        Map<String, Long> noiseCounts = new TreeMap<>();
        events.forEach((k, v) -> noiseCounts.put(k.name(), v));
        return new GenerationSummary(count, seed, rawOut.toString(), truthOut.toString(), noise, profileCounts, noiseCounts);
    }

    private RawAssetBundle buildBundle(String id, TruthProfile profile, SplittableRandom r,
                                       NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        List<SourceObservation> out = new ArrayList<>();
        String suffix = id.substring(id.length() - 6);
        String baseHost = switch (profile) {
            case WINDOWS_WORKSTATION -> "ws-" + suffix;
            case WINDOWS_SERVER, LINUX_SERVER -> "srv-" + suffix;
            case NETWORK_DEVICE -> "edge-" + suffix;
            default -> "node-" + suffix;
        };
        String ip = "10." + r.nextInt(1, 220) + "." + r.nextInt(0, 255) + "." + r.nextInt(1, 255);

        switch (profile) {
            case WINDOWS_WORKSTATION -> {
                out.add(adComputer(baseHost, "Windows 11 Enterprise", r, noise, events));
                out.add(kscHost(baseHost, ip, "Microsoft Windows 11 Enterprise", 1, r, noise, events));
                addOptional(out, nmapHost(baseHost, ip, "Microsoft", "Windows", "general purpose", r, noise, events), r, noise, events);
                addOptional(out, zabbixHost(maybeRename(baseHost, r, noise, events), ip, "Windows 11 Enterprise", r, noise, events), r, noise, events);
                addOptional(out, siemHost(baseHost, ip, r, noise, events), r, noise, events);
            }
            case WINDOWS_SERVER -> {
                out.add(adComputer(baseHost, "Windows Server 2022 Datacenter", r, noise, events));
                out.add(kscHost(baseHost, ip, "Microsoft Windows Server 2022 Datacenter", 2, r, noise, events));
                addOptional(out, nmapHost(baseHost, ip, "Microsoft", "Windows", "general purpose", r, noise, events), r, noise, events);
                addOptional(out, zabbixHost(maybeRename(baseHost, r, noise, events), ip, "Windows Server 2022", r, noise, events), r, noise, events);
                addOptional(out, siemHost(baseHost, ip, r, noise, events), r, noise, events);
            }
            case LINUX_SERVER -> {
                out.add(kscHost(baseHost, ip, "Ubuntu 24.04 LTS Server", 2, r, noise, events));
                out.add(nmapHost(baseHost, ip, "Linux", "Linux", "general purpose", r, noise, events));
                addOptional(out, zabbixHost(maybeRename(baseHost, r, noise, events), ip, "Ubuntu 24.04 LTS Server", r, noise, events), r, noise, events);
                addOptional(out, siemHost(baseHost, ip, r, noise, events), r, noise, events);
            }
            case NETWORK_DEVICE -> {
                String[] deviceTypes = {"router", "switch", "WAP", "broadband router"};
                String type = deviceTypes[r.nextInt(deviceTypes.length)];
                if (chance(r, noise.ambiguousNmapPct())) {
                    type = "general purpose";
                    mark(events, NoiseKind.AMBIGUOUS_NMAP);
                }
                boolean cisco = r.nextBoolean();
                out.add(nmapHost(baseHost, ip, cisco ? "Cisco" : "MikroTik",
                        cisco ? "IOS" : "RouterOS", type, r, noise, events));
                out.add(zabbixHost(maybeRename(baseHost, r, noise, events), ip, "Embedded network OS", r, noise, events));
                addOptional(out, siemHost(baseHost, ip, r, noise, events), r, noise, events);
            }
            case USER_ACCOUNT -> {
                out.add(adUser(id, false, r, noise, events));
                addOptional(out, siemPrincipal(id, ip, r, noise, events), r, noise, events);
            }
            case SERVICE_ACCOUNT -> {
                out.add(adUser(id, true, r, noise, events));
                addOptional(out, siemPrincipal(id, ip, r, noise, events), r, noise, events);
            }
            case SECURITY_SOFTWARE -> out.add(kscSoftware(id, true, r, noise, events));
            case APPLICATION_SOFTWARE -> out.add(kscSoftware(id, false, r, noise, events));
        }
        return new RawAssetBundle(id, out);
    }

    private SourceObservation adComputer(String host, String os, SplittableRandom r, NoiseProfile noise,
                                         EnumMap<NoiseKind, Long> events) {
        String observedOs = maybeConflictingOs(os, r, noise, events);
        Map<String, String> a = attrs(
                "objectClass", "computer",
                "objectCategory", "CN=Computer,CN=Schema,CN=Configuration,DC=corp,DC=example",
                "sAMAccountName", host.toUpperCase(Locale.ROOT) + "$",
                "distinguishedName", "CN=" + host.toUpperCase(Locale.ROOT) + ",OU=Computers,DC=corp,DC=example",
                "operatingSystem", observedOs,
                "userAccountControl", "4096",
                "dNSHostName", host + ".corp.example");
        return obs("AD", "ad:computer", timestamp(r, noise, events), a);
    }

    private SourceObservation adUser(String id, boolean service, SplittableRandom r, NoiseProfile noise,
                                     EnumMap<NoiseKind, Long> events) {
        String suffix = id.substring(id.length() - 6);
        boolean missed = service && chance(r, noise.missedServiceHintPct());
        boolean falseHint = !service && chance(r, noise.falseServiceHintPct());
        if (missed) mark(events, NoiseKind.MISSED_SERVICE_HINT);
        if (falseHint) mark(events, NoiseKind.FALSE_SERVICE_HINT);
        String name = service && !missed ? "svc_app_" + suffix : falseHint ? "svc_temp_" + suffix : "user" + suffix;
        String description = service && !missed ? "Service account for application integration"
                : falseHint ? "Temporary migration service account owner" : "Corporate user account";
        Map<String, String> a = attrs(
                "objectClass", "user",
                "objectCategory", "CN=Person,CN=Schema,CN=Configuration,DC=corp,DC=example",
                "sAMAccountName", name,
                "userPrincipalName", name + "@corp.example",
                "distinguishedName", "CN=" + name + ",OU=Identities,DC=corp,DC=example",
                "userAccountControl", service && r.nextBoolean() ? "66048" : "512",
                "description", description);
        return obs("AD", "ad:user", timestamp(r, noise, events), a);
    }

    private SourceObservation kscHost(String host, String ip, String os, int trueCtype, SplittableRandom r,
                                      NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        int ctype = trueCtype;
        if (chance(r, noise.kscTypeFlipPct())) {
            ctype = trueCtype == 1 ? 2 : 1;
            mark(events, NoiseKind.KSC_TYPE_FLIP);
        }
        Map<String, String> a = attrs(
                "KLHST_WKS_DN", host.toUpperCase(Locale.ROOT),
                "KLHST_WKS_HOSTNAME", "{SYNTH-" + host + "}",
                "KLHST_WKS_FQDN", host + ".corp.example",
                "KLHST_WKS_IP_LONG", ip,
                "KLHST_WKS_CTYPE", Integer.toString(ctype),
                "KLHST_WKS_OS_NAME", maybeConflictingOs(os, r, noise, events),
                "KLHST_WKS_STATUS", "29",
                "KLHST_WKS_RTP_STATE", r.nextInt(20) == 0 ? "1" : "7");
        return obs("KSC", "ksc:host", timestamp(r, noise, events), a);
    }

    private SourceObservation kscSoftware(String id, boolean security, SplittableRandom r, NoiseProfile noise,
                                          EnumMap<NoiseKind, Long> events) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("ProductID", "SYNTH-" + id);
        a.put("bIsMsi", Boolean.toString(r.nextBoolean()));
        if (security) {
            if (r.nextBoolean()) {
                a.put("DisplayName", "Kaspersky Endpoint Security for Windows");
                a.put("Publisher", "AO Kaspersky Lab");
            } else {
                a.put("DisplayName", "ESET Endpoint Security");
                a.put("Publisher", "ESET, spol. s r.o.");
            }
        } else {
            String[][] apps = {{"7-Zip", "Igor Pavlov"}, {"Mozilla Firefox", "Mozilla"},
                    {"Notepad++", "Notepad++ Team"}, {"VLC media player", "VideoLAN"}};
            String[] app = apps[r.nextInt(apps.length)];
            a.put("DisplayName", app[0]);
            a.put("Publisher", app[1]);
        }
        a.put("DisplayVersion", r.nextInt(1, 25) + "." + r.nextInt(0, 10) + "." + r.nextInt(0, 100));
        a.put("InstallDate", "2026" + String.format(Locale.ROOT, "%02d%02d", r.nextInt(1, 10), r.nextInt(1, 28)));
        if (chance(r, noise.incompleteInventoryPct())) {
            if (r.nextBoolean()) a.remove("Publisher"); else a.remove("DisplayName");
            mark(events, NoiseKind.INCOMPLETE_INVENTORY);
        }
        return obs("KSC", "ksc:software_inventory_application", timestamp(r, noise, events), a);
    }

    private SourceObservation nmapHost(String host, String ip, String vendor, String family, String deviceType,
                                       SplittableRandom r, NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        String actualType = deviceType;
        if (!"general purpose".equals(deviceType) && chance(r, noise.ambiguousNmapPct())) {
            actualType = "general purpose";
            mark(events, NoiseKind.AMBIGUOUS_NMAP);
        } else if ("general purpose".equals(deviceType) && chance(r, noise.ambiguousNmapPct())) {
            actualType = r.nextBoolean() ? "router" : "firewall";
            mark(events, NoiseKind.AMBIGUOUS_NMAP);
        }
        Map<String, String> a = attrs(
                "status", "up",
                "address", ip,
                "hostname", host + ".corp.example",
                "deviceType", actualType,
                "vendor", vendor,
                "osfamily", family,
                "openPorts", "Windows".equals(family) ? "135,445,3389" : "22,80,443");
        return obs("NMAP", "nmap:host", timestamp(r, noise, events), a);
    }

    private SourceObservation zabbixHost(String host, String ip, String os, SplittableRandom r, NoiseProfile noise,
                                         EnumMap<NoiseKind, Long> events) {
        Map<String, String> a = attrs(
                "hostid", Long.toString(10000L + Math.abs(host.hashCode())),
                "host", host,
                "name", host,
                "status", "0",
                "inventory.os", maybeConflictingOs(os, r, noise, events),
                "interface.ip", ip);
        return obs("ZABBIX", "zabbix:host", timestamp(r, noise, events), a);
    }

    private SourceObservation siemPrincipal(String id, String ip, SplittableRandom r, NoiseProfile noise,
                                            EnumMap<NoiseKind, Long> events) {
        return obs("SIEM", "siem:principal", timestamp(r, noise, events), attrs(
                "deviceVendor", "Microsoft", "deviceProduct", "Windows Security",
                "deviceEventClassId", "4624", "category", "authentication",
                "suser", id, "src", ip));
    }

    private SourceObservation siemHost(String host, String ip, SplittableRandom r, NoiseProfile noise,
                                       EnumMap<NoiseKind, Long> events) {
        return obs("SIEM", "siem:host", timestamp(r, noise, events), attrs(
                "deviceVendor", "Generic", "deviceProduct", "Syslog",
                "deviceEventClassId", "host-observation", "dhost", host + ".corp.example", "src", ip));
    }

    private void addOptional(List<SourceObservation> out, SourceObservation observation, SplittableRandom r,
                             NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        if (chance(r, noise.missingOptionalSourcePct())) {
            mark(events, NoiseKind.MISSING_OPTIONAL_SOURCE);
        } else {
            out.add(observation);
        }
    }

    private String maybeRename(String host, SplittableRandom r, NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        if (!chance(r, noise.renamePct())) return host;
        mark(events, NoiseKind.RENAMED_HOST);
        return "old-" + host;
    }

    private String maybeConflictingOs(String os, SplittableRandom r, NoiseProfile noise,
                                      EnumMap<NoiseKind, Long> events) {
        if (!chance(r, noise.conflictingOsPct())) return os;
        mark(events, NoiseKind.CONFLICTING_OS);
        if (os.toLowerCase(Locale.ROOT).contains("server")) return "Windows 11 Enterprise";
        return "Windows Server 2022 Datacenter";
    }

    private long timestamp(SplittableRandom r, NoiseProfile noise, EnumMap<NoiseKind, Long> events) {
        if (chance(r, noise.staleObservationPct())) {
            mark(events, NoiseKind.STALE_OBSERVATION);
            return REFERENCE_EPOCH_MS - DAY_MS * r.nextLong(45, 181);
        }
        return REFERENCE_EPOCH_MS - DAY_MS * r.nextLong(0, 8);
    }

    private static boolean chance(SplittableRandom r, int pct) {
        return pct > 0 && r.nextInt(100) < pct;
    }

    private static void mark(EnumMap<NoiseKind, Long> events, NoiseKind kind) {
        events.merge(kind, 1L, Long::sum);
    }

    private static SourceObservation obs(String source, String kind, long ts, Map<String, String> attrs) {
        return new SourceObservation(source, kind, ts, attrs);
    }

    private static Map<String, String> attrs(String... kv) {
        if ((kv.length & 1) != 0) throw new IllegalArgumentException("attrs requires key/value pairs");
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) result.put(kv[i], kv[i + 1]);
        return result;
    }

    public enum TruthProfile {
        WINDOWS_WORKSTATION(AssetType.DEVICE, AssetSubtype.WORKSTATION),
        WINDOWS_SERVER(AssetType.DEVICE, AssetSubtype.SERVER),
        LINUX_SERVER(AssetType.DEVICE, AssetSubtype.SERVER),
        NETWORK_DEVICE(AssetType.DEVICE, AssetSubtype.NETWORK_DEVICE),
        USER_ACCOUNT(AssetType.ACCOUNT, AssetSubtype.USER_ACCOUNT),
        SERVICE_ACCOUNT(AssetType.ACCOUNT, AssetSubtype.SERVICE_ACCOUNT),
        SECURITY_SOFTWARE(AssetType.SOFTWARE, AssetSubtype.SECURITY_SOFTWARE),
        APPLICATION_SOFTWARE(AssetType.SOFTWARE, AssetSubtype.APPLICATION_SOFTWARE);

        private final AssetType type;
        private final AssetSubtype subtype;
        TruthProfile(AssetType type, AssetSubtype subtype) { this.type = type; this.subtype = subtype; }
    }

    public enum NoiseKind {
        MISSING_OPTIONAL_SOURCE, STALE_OBSERVATION, CONFLICTING_OS, RENAMED_HOST,
        FALSE_SERVICE_HINT, MISSED_SERVICE_HINT, AMBIGUOUS_NMAP, INCOMPLETE_INVENTORY, KSC_TYPE_FLIP
    }

    public record GenerationSummary(
            long count,
            long seed,
            String rawOutput,
            String truthOutput,
            NoiseProfile noiseProfile,
            Map<String, Long> profileCounts,
            Map<String, Long> noiseEventCounts) {}
}
