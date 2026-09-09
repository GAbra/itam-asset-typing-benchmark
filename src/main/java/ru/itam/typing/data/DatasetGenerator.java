package ru.itam.typing.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.model.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class DatasetGenerator {
    public static final long DEFAULT_SEED = 20260909L;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public GenerationSummary generate(long count, long seed, Path out) throws IOException {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        Files.createDirectories(out.toAbsolutePath().getParent());
        SplittableRandom random = new SplittableRandom(seed);
        EnumMap<Profile, Long> distribution = new EnumMap<>(Profile.class);
        for (Profile p : Profile.values()) distribution.put(p, 0L);

        try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (long i = 0; i < count; i++) {
                Profile profile = pickProfile(random);
                distribution.put(profile, distribution.get(profile) + 1);
                DatasetRecord record = buildRecord(i + 1, profile, random.split());
                writer.write(json.writeValueAsString(record));
                writer.newLine();
            }
        }
        return new GenerationSummary(count, seed, out.toString(), distribution);
    }

    private DatasetRecord buildRecord(long index, Profile profile, SplittableRandom r) {
        String id = String.format(Locale.ROOT, "asset-%09d", index);
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        LinkedHashSet<String> kinds = new LinkedHashSet<>();
        LinkedHashMap<String, String> a = new LinkedHashMap<>();
        LinkedHashMap<String, Long> p = new LinkedHashMap<>();

        switch (profile) {
            case WINDOWS_WORKSTATION -> {
                addAdComputer(sources, kinds, a, id, "Windows 11 Enterprise", false, r);
                addKscHost(sources, kinds, a, id, "Microsoft Windows 11 Enterprise", 1, r);
                maybeAddNmapGeneral(sources, kinds, a, id, "Microsoft", "Windows", r);
                maybeAddZabbix(sources, kinds, a, id, "Windows 11 Enterprise", r);
                maybeAddSiemHost(sources, kinds, a, id, r);
                return rec(id, sources, kinds, a, p, AssetType.DEVICE, AssetSubtype.WORKSTATION);
            }
            case WINDOWS_SERVER -> {
                addAdComputer(sources, kinds, a, id, "Windows Server 2022 Datacenter", true, r);
                addKscHost(sources, kinds, a, id, "Microsoft Windows Server 2022 Datacenter", 2, r);
                addNmapGeneral(sources, kinds, a, id, "Microsoft", "Windows", r);
                addZabbix(sources, kinds, a, id, "Windows Server 2022", r);
                maybeAddSiemHost(sources, kinds, a, id, r);
                p.put("cpuCores", 8L + r.nextInt(25));
                p.put("ramMiB", 16384L + 1024L * r.nextInt(113));
                return rec(id, sources, kinds, a, p, AssetType.DEVICE, AssetSubtype.SERVER);
            }
            case LINUX_SERVER -> {
                addKscHost(sources, kinds, a, id, "Ubuntu 24.04.3 LTS Server", 2, r);
                addNmapGeneral(sources, kinds, a, id, "Linux", "Linux", r);
                addZabbix(sources, kinds, a, id, "Ubuntu 24.04.3 LTS Server", r);
                maybeAddSiemHost(sources, kinds, a, id, r);
                p.put("cpuCores", 4L + r.nextInt(29));
                p.put("ramMiB", 8192L + 1024L * r.nextInt(121));
                return rec(id, sources, kinds, a, p, AssetType.DEVICE, AssetSubtype.SERVER);
            }
            case NETWORK_DEVICE -> {
                addNmapNetworkDevice(sources, kinds, a, id, r);
                addZabbix(sources, kinds, a, id, "Embedded network OS", r);
                maybeAddSiemHost(sources, kinds, a, id, r);
                return rec(id, sources, kinds, a, p, AssetType.DEVICE, AssetSubtype.NETWORK_DEVICE);
            }
            case USER_ACCOUNT -> {
                addAdUser(sources, kinds, a, id, false, r);
                addSiemPrincipal(sources, kinds, a, id, r);
                return rec(id, sources, kinds, a, p, AssetType.ACCOUNT, AssetSubtype.USER_ACCOUNT);
            }
            case SERVICE_ACCOUNT -> {
                addAdUser(sources, kinds, a, id, true, r);
                addSiemPrincipal(sources, kinds, a, id, r);
                return rec(id, sources, kinds, a, p, AssetType.ACCOUNT, AssetSubtype.SERVICE_ACCOUNT);
            }
            case SECURITY_SOFTWARE -> {
                addKscSoftware(sources, kinds, a, id, true, r);
                return rec(id, sources, kinds, a, p, AssetType.SOFTWARE, AssetSubtype.SECURITY_SOFTWARE);
            }
            case APPLICATION_SOFTWARE -> {
                addKscSoftware(sources, kinds, a, id, false, r);
                return rec(id, sources, kinds, a, p, AssetType.SOFTWARE, AssetSubtype.APPLICATION_SOFTWARE);
            }
            default -> throw new IllegalStateException("Unexpected profile: " + profile);
        }
    }

    private static DatasetRecord rec(String id, Set<String> sources, Set<String> kinds,
                                     Map<String, String> a, Map<String, Long> p,
                                     AssetType type, AssetSubtype subtype) {
        return new DatasetRecord(new AssetTypingContext(id, Set.copyOf(sources), Set.copyOf(kinds), Map.copyOf(a), Map.copyOf(p)), type, subtype);
    }

    private static Profile pickProfile(SplittableRandom r) {
        int x = r.nextInt(1000);
        if (x < 260) return Profile.WINDOWS_WORKSTATION;
        if (x < 410) return Profile.WINDOWS_SERVER;
        if (x < 540) return Profile.LINUX_SERVER;
        if (x < 650) return Profile.NETWORK_DEVICE;
        if (x < 790) return Profile.USER_ACCOUNT;
        if (x < 860) return Profile.SERVICE_ACCOUNT;
        if (x < 930) return Profile.SECURITY_SOFTWARE;
        return Profile.APPLICATION_SOFTWARE;
    }

    private static void addAdComputer(Set<String> s, Set<String> k, Map<String, String> a, String id, String os, boolean server, SplittableRandom r) {
        s.add("AD"); k.add("ad:computer");
        String host = (server ? "SRV-" : "WS-") + id.substring(id.length() - 6);
        a.put("ad.objectClass", "computer");
        a.put("ad.objectCategory", "CN=Computer,CN=Schema,CN=Configuration,DC=corp,DC=example");
        a.put("ad.sAMAccountName", host + "$");
        a.put("ad.distinguishedName", "CN=" + host + ",OU=" + (server ? "Servers" : "Workstations") + ",DC=corp,DC=example");
        a.put("ad.operatingSystem", os);
        a.put("ad.userAccountControl", "4096");
        a.put("ad.dNSHostName", host.toLowerCase(Locale.ROOT) + ".corp.example");
        if (r.nextInt(10) == 0) a.put("ad.description", server ? "Application server" : "Corporate workstation");
    }

    private static void addAdUser(Set<String> s, Set<String> k, Map<String, String> a, String id, boolean service, SplittableRandom r) {
        s.add("AD"); k.add("ad:user");
        String suffix = id.substring(id.length() - 6);
        String name = service ? "svc_app_" + suffix : "user" + suffix;
        a.put("ad.objectClass", "user");
        a.put("ad.objectCategory", "CN=Person,CN=Schema,CN=Configuration,DC=corp,DC=example");
        a.put("ad.sAMAccountName", name);
        a.put("ad.userPrincipalName", name + "@corp.example");
        a.put("ad.distinguishedName", "CN=" + name + ",OU=" + (service ? "Service Accounts" : "Users") + ",DC=corp,DC=example");
        a.put("ad.userAccountControl", service && r.nextBoolean() ? "66048" : "512");
        a.put("ad.description", service ? "Service account for application integration" : "Corporate user account");
    }

    private static void addKscHost(Set<String> s, Set<String> k, Map<String, String> a, String id, String os, int ctype, SplittableRandom r) {
        s.add("KSC"); k.add("ksc:host");
        String host = (ctype == 2 ? "srv-" : "ws-") + id.substring(id.length() - 6);
        a.put("ksc.KLHST_WKS_DN", host.toUpperCase(Locale.ROOT));
        a.put("ksc.KLHST_WKS_HOSTNAME", "{SYNTH-" + id + "}");
        a.put("ksc.KLHST_WKS_FQDN", host + ".corp.example");
        a.put("ksc.KLHST_WKS_CTYPE", Integer.toString(ctype));
        a.put("ksc.KLHST_WKS_OS_NAME", os);
        a.put("ksc.KLHST_WKS_STATUS", "29");
        a.put("ksc.KLHST_WKS_RTP_STATE", r.nextInt(20) == 0 ? "1" : "7");
    }

    private static void addKscSoftware(Set<String> s, Set<String> k, Map<String, String> a, String id, boolean security, SplittableRandom r) {
        s.add("KSC"); k.add("ksc:software_inventory_application");
        a.put("ksc.ProductID", "SYNTH-" + id);
        a.put("ksc.bIsMsi", Boolean.toString(r.nextBoolean()));
        if (security) {
            if (r.nextBoolean()) {
                a.put("ksc.DisplayName", "Kaspersky Endpoint Security for Windows");
                a.put("ksc.DisplayVersion", "12." + r.nextInt(1, 9) + ".0." + r.nextInt(100, 999));
                a.put("ksc.Publisher", "AO Kaspersky Lab");
            } else {
                a.put("ksc.DisplayName", "ESET Endpoint Security");
                a.put("ksc.DisplayVersion", "11." + r.nextInt(0, 3) + "." + r.nextInt(100, 999));
                a.put("ksc.Publisher", "ESET, spol. s r.o.");
            }
        } else {
            String[][] apps = {
                    {"7-Zip", "Igor Pavlov"}, {"Mozilla Firefox", "Mozilla"},
                    {"Microsoft Visual C++ 2015-2022 Redistributable", "Microsoft Corporation"},
                    {"Notepad++", "Notepad++ Team"}, {"VLC media player", "VideoLAN"}
            };
            String[] app = apps[r.nextInt(apps.length)];
            a.put("ksc.DisplayName", app[0]);
            a.put("ksc.DisplayVersion", r.nextInt(1, 25) + "." + r.nextInt(0, 10) + "." + r.nextInt(0, 100));
            a.put("ksc.Publisher", app[1]);
        }
        a.put("ksc.InstallDate", "2026" + String.format(Locale.ROOT, "%02d%02d", r.nextInt(1, 9), r.nextInt(1, 28)));
        a.put("ksc.InstallDir", "C:\\Program Files\\" + a.get("ksc.DisplayName").replaceAll("[^A-Za-z0-9 ]", ""));
    }

    private static void addNmapGeneral(Set<String> s, Set<String> k, Map<String, String> a, String id, String vendor, String family, SplittableRandom r) {
        s.add("NMAP"); k.add("nmap:host");
        a.put("nmap.status", "up");
        a.put("nmap.address", "10." + r.nextInt(1, 250) + "." + r.nextInt(0, 255) + "." + r.nextInt(1, 255));
        a.put("nmap.deviceType", "general purpose");
        a.put("nmap.vendor", vendor);
        a.put("nmap.osfamily", family);
        a.put("nmap.openPorts", family.equals("Windows") ? "135,445,3389" : "22,80,443");
    }

    private static void maybeAddNmapGeneral(Set<String> s, Set<String> k, Map<String, String> a, String id, String vendor, String family, SplittableRandom r) {
        if (r.nextInt(100) < 65) addNmapGeneral(s, k, a, id, vendor, family, r);
    }

    private static void addNmapNetworkDevice(Set<String> s, Set<String> k, Map<String, String> a, String id, SplittableRandom r) {
        s.add("NMAP"); k.add("nmap:host");
        String[] types = {"router", "switch", "WAP", "broadband router"};
        String type = types[r.nextInt(types.length)];
        a.put("nmap.status", "up");
        a.put("nmap.address", "10.200." + r.nextInt(0, 255) + "." + r.nextInt(1, 255));
        a.put("nmap.deviceType", type);
        a.put("nmap.vendor", r.nextBoolean() ? "Cisco" : "MikroTik");
        a.put("nmap.osfamily", r.nextBoolean() ? "IOS" : "RouterOS");
        a.put("nmap.openPorts", r.nextBoolean() ? "22,80,443,161" : "22,8291,161");
    }

    private static void addZabbix(Set<String> s, Set<String> k, Map<String, String> a, String id, String os, SplittableRandom r) {
        s.add("ZABBIX"); k.add("zabbix:host");
        a.put("zabbix.hostid", Long.toString(10000L + Math.abs(id.hashCode())));
        a.put("zabbix.host", id);
        a.put("zabbix.name", id.replace("asset-", "node-"));
        a.put("zabbix.status", "0");
        a.put("zabbix.inventory.os", os);
        a.put("zabbix.interface.ip", "10." + r.nextInt(1, 250) + "." + r.nextInt(0, 255) + "." + r.nextInt(1, 255));
    }

    private static void maybeAddZabbix(Set<String> s, Set<String> k, Map<String, String> a, String id, String os, SplittableRandom r) {
        if (r.nextInt(100) < 70) addZabbix(s, k, a, id, os, r);
    }

    private static void addSiemPrincipal(Set<String> s, Set<String> k, Map<String, String> a, String id, SplittableRandom r) {
        s.add("SIEM"); k.add("siem:principal");
        a.put("siem.deviceVendor", "Microsoft");
        a.put("siem.deviceProduct", "Windows Security");
        a.put("siem.deviceEventClassId", "4624");
        a.put("siem.category", "authentication");
        a.put("siem.suser", id);
        a.put("siem.src", "10.10." + r.nextInt(0, 255) + "." + r.nextInt(1, 255));
    }

    private static void maybeAddSiemHost(Set<String> s, Set<String> k, Map<String, String> a, String id, SplittableRandom r) {
        if (r.nextInt(100) < 45) {
            s.add("SIEM"); k.add("siem:host");
            a.put("siem.deviceVendor", "Generic");
            a.put("siem.deviceProduct", "Syslog");
            a.put("siem.deviceEventClassId", "host-observation");
            a.put("siem.dhost", id);
        }
    }

    public enum Profile {
        WINDOWS_WORKSTATION,
        WINDOWS_SERVER,
        LINUX_SERVER,
        NETWORK_DEVICE,
        USER_ACCOUNT,
        SERVICE_ACCOUNT,
        SECURITY_SOFTWARE,
        APPLICATION_SOFTWARE
    }

    public record GenerationSummary(long count, long seed, String output, Map<Profile, Long> distribution) {}
}
