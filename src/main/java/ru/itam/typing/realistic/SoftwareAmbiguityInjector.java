package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/**
 * Injects software-inventory ambiguity into raw KSC observations while leaving the truth sidecar unchanged.
 * This is a dataset stressor only: it does not read rules and does not invoke the feature extractor or an engine.
 */
public final class SoftwareAmbiguityInjector {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    public InjectionReport inject(Path rawIn, Path truth, Path rawOut, long seed,
                                  SoftwareAmbiguityProfile profile) throws Exception {
        Files.createDirectories(rawOut.toAbsolutePath().getParent());
        Map<String, Long> events = new TreeMap<>();
        long total = 0;
        long software = 0;
        long security = 0;
        long application = 0;

        try (BufferedReader rawReader = Files.newBufferedReader(rawIn, StandardCharsets.UTF_8);
             BufferedReader truthReader = Files.newBufferedReader(truth, StandardCharsets.UTF_8);
             BufferedWriter writer = Files.newBufferedWriter(rawOut, StandardCharsets.UTF_8)) {
            while (true) {
                String rawLine = rawReader.readLine();
                String truthLine = truthReader.readLine();
                if (rawLine == null || truthLine == null) {
                    if (rawLine != null || truthLine != null) {
                        throw new IllegalStateException("raw/truth line count mismatch");
                    }
                    break;
                }

                RawAssetBundle bundle = JSON.readValue(rawLine, RawAssetBundle.class);
                GroundTruthLabel label = JSON.readValue(truthLine, GroundTruthLabel.class);
                if (!bundle.assetId().equals(label.assetId())) {
                    throw new IllegalStateException("raw/truth asset mismatch: " + bundle.assetId() + " != " + label.assetId());
                }

                total++;
                RawAssetBundle output = bundle;
                if (label.type() == AssetType.SOFTWARE) {
                    software++;
                    if (label.subtype() == AssetSubtype.SECURITY_SOFTWARE) security++;
                    if (label.subtype() == AssetSubtype.APPLICATION_SOFTWARE) application++;
                    output = mutate(bundle, label, seed, profile, events);
                }
                writer.write(JSON.writeValueAsString(output));
                writer.write('\n');
            }
        }

        return new InjectionReport(total, software, security, application, seed, profile, Map.copyOf(events));
    }

    private RawAssetBundle mutate(RawAssetBundle bundle, GroundTruthLabel label, long seed,
                                  SoftwareAmbiguityProfile profile, Map<String, Long> events) {
        SplittableRandom random = new SplittableRandom(stableSeed(seed, bundle.assetId()));
        List<SourceObservation> observations = new ArrayList<>(bundle.observations().size());
        for (SourceObservation observation : bundle.observations()) {
            if (!"KSC".equalsIgnoreCase(observation.source())
                    || !"ksc:software_inventory_application".equals(observation.objectKind())) {
                observations.add(observation);
                continue;
            }

            Map<String, String> attributes = new LinkedHashMap<>(observation.attributes());
            if (label.subtype() == AssetSubtype.SECURITY_SOFTWARE) {
                mutateSecurity(attributes, random, profile, events);
            } else if (label.subtype() == AssetSubtype.APPLICATION_SOFTWARE) {
                mutateApplication(attributes, random, profile, events);
            }
            observations.add(new SourceObservation(observation.source(), observation.objectKind(),
                    observation.observedAtEpochMs(), attributes));
        }
        return new RawAssetBundle(bundle.assetId(), observations);
    }

    private void mutateSecurity(Map<String, String> a, SplittableRandom r,
                                SoftwareAmbiguityProfile profile, Map<String, Long> events) {
        if (chance(r, profile.missingSoftwareIdentityPct())) {
            a.remove("DisplayName");
            a.remove("Publisher");
            mark(events, "MISSING_SOFTWARE_IDENTITY");
            return;
        }

        if (chance(r, profile.securityComponentRecordPct())) {
            String[][] components = {
                    {"Telemetry Agent Core", "Enterprise Software Operations Ltd"},
                    {"Network Agent", "Systems Management Services LLC"},
                    {"Update Service Component", "Enterprise Platform Services Ltd"},
                    {"Host Protection Runtime", "Endpoint Operations LLC"}
            };
            String[] selected = components[r.nextInt(components.length)];
            a.put("DisplayName", selected[0]);
            a.put("Publisher", selected[1]);
            mark(events, "SECURITY_COMPONENT_RECORD");
        } else if (chance(r, profile.neutralSecurityRecordPct())) {
            String[][] neutral = {
                    {"Host Management Agent", "Enterprise Software Services Ltd"},
                    {"Workstation Runtime", "Corporate Systems LLC"},
                    {"Client Support Module", "Infrastructure Tools Ltd"}
            };
            String[] selected = neutral[r.nextInt(neutral.length)];
            a.put("DisplayName", selected[0]);
            a.put("Publisher", selected[1]);
            mark(events, "NEUTRAL_SECURITY_RECORD");
        }

        if (chance(r, profile.securityPublisherAliasPct())) {
            a.put("Publisher", r.nextBoolean() ? "KL Digital Services JSC" : "Endpoint Software Group s.r.o.");
            mark(events, "SECURITY_PUBLISHER_ALIAS");
        }
    }

    private void mutateApplication(Map<String, String> a, SplittableRandom r,
                                   SoftwareAmbiguityProfile profile, Map<String, Long> events) {
        if (!chance(r, profile.deceptiveApplicationRecordPct())) return;
        String[][] deceptive = {
                {"Endpoint Security Assessment Console", "Contoso IT Tools"},
                {"Windows Defender Log Analyzer", "Blue River Software"},
                {"ESET Migration Utility", "Migration Tools Group"},
                {"Sophos Compatibility Checker", "Application Compatibility Labs"}
        };
        String[] selected = deceptive[r.nextInt(deceptive.length)];
        a.put("DisplayName", selected[0]);
        a.put("Publisher", selected[1]);
        mark(events, "DECEPTIVE_APPLICATION_RECORD");
    }

    private static boolean chance(SplittableRandom random, int pct) {
        return pct > 0 && random.nextInt(100) < pct;
    }

    private static void mark(Map<String, Long> events, String event) {
        events.merge(event, 1L, Long::sum);
    }

    private static long stableSeed(long seed, String assetId) {
        long hash = 0xcbf29ce484222325L ^ seed;
        for (byte b : assetId.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public record InjectionReport(
            long total,
            long softwareAssets,
            long securitySoftwareAssets,
            long applicationSoftwareAssets,
            long seed,
            SoftwareAmbiguityProfile profile,
            Map<String, Long> eventCounts) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path rawIn = Path.of(required(a, "--raw-in"));
        Path truth = Path.of(required(a, "--truth"));
        Path rawOut = Path.of(required(a, "--raw-out"));
        Path out = Path.of(a.getOrDefault("--out", rawOut + ".ambiguity.json"));
        long seed = Long.parseLong(a.getOrDefault("--seed", "20260914"));
        SoftwareAmbiguityProfile profile = SoftwareAmbiguityProfile.named(a.getOrDefault("--profile", "stress"));
        InjectionReport report = new SoftwareAmbiguityInjector().inject(rawIn, truth, rawOut, seed, profile);
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --key value pairs");
            }
            out.put(args[i], args[++i]);
        }
        return out;
    }

    private static String required(Map<String, String> args, String key) {
        String value = args.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }
}
