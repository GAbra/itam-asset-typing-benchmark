package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;

/** Generates a labelled software-only workload from the checked-in v3 catalog. */
public final class SoftwareTaxonomyV3Generator {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final ObjectMapper PRETTY = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final long REFERENCE_EPOCH_MS = Instant.parse("2026-09-16T00:00:00Z").toEpochMilli();

    public GenerationReport generate(Path catalogPath, long count, long seed, Noise noise,
                                     Path rawOut, Path truthOut) throws Exception {
        if (count <= 0) throw new IllegalArgumentException("count must be > 0");
        Catalog catalog = YAML.readValue(catalogPath.toFile(), Catalog.class);
        if (catalog.products() == null || catalog.products().isEmpty())
            throw new IllegalArgumentException("software catalog has no products");
        Files.createDirectories(rawOut.toAbsolutePath().getParent());
        Files.createDirectories(truthOut.toAbsolutePath().getParent());

        Map<String, Long> subtypeCounts = new TreeMap<>();
        Map<String, Long> eventCounts = new TreeMap<>();
        long domesticCount = 0;

        try (BufferedWriter raw = Files.newBufferedWriter(rawOut, StandardCharsets.UTF_8);
             BufferedWriter truth = Files.newBufferedWriter(truthOut, StandardCharsets.UTF_8)) {
            for (long i = 0; i < count; i++) {
                String assetId = String.format(Locale.ROOT, "swv3-%09d", i + 1);
                SplittableRandom selector = new SplittableRandom(stableSeed(seed, "product:" + i));
                Product product = catalog.products().get(selector.nextInt(catalog.products().size()));
                if (product.subtype() == null) throw new IllegalArgumentException("missing subtype for " + product.id());
                subtypeCounts.merge(product.subtype().name(), 1L, Long::sum);
                if (product.domestic()) domesticCount++;

                SplittableRandom r = new SplittableRandom(stableSeed(seed, "noise:" + assetId));
                Map<String, String> attributes = attributes(product, r);
                applyNoise(attributes, noise, r, eventCounts);
                SourceObservation observation = new SourceObservation(
                        "KSC", "ksc:software_inventory_application",
                        REFERENCE_EPOCH_MS - 86_400_000L * r.nextLong(0, 15), attributes);
                RawAssetBundle bundle = new RawAssetBundle(assetId, List.of(observation));
                GroundTruthLabel label = new GroundTruthLabel(
                        assetId, AssetType.SOFTWARE, product.subtype(), product.subtype().name());
                raw.write(JSON.writeValueAsString(bundle)); raw.write('\n');
                truth.write(JSON.writeValueAsString(label)); truth.write('\n');
            }
        }
        return new GenerationReport(catalog.catalogVersion(), catalog.purpose(), count, seed,
                noise.name().toLowerCase(Locale.ROOT), catalog.products().size(), domesticCount,
                Map.copyOf(subtypeCounts), Map.copyOf(eventCounts));
    }

    private static Map<String, String> attributes(Product p, SplittableRandom r) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("ProductID", "PID-" + Long.toUnsignedString(stableSeed(17L, p.id()), 16));
        a.put("DisplayName", value(p.displayName()));
        a.put("Publisher", value(p.publisher()));
        a.put("ProductFamily", value(p.productFamily()));
        a.put("PackageId", value(p.packageId()));
        a.put("InstallDir", value(p.installLocation()));
        a.put("Executables", value(p.executables()));
        a.put("Services", value(p.services()));
        a.put("Platform", value(p.platform()));
        a.put("Architecture", value(p.architecture()));
        a.put("DisplayVersion", r.nextInt(1, 27) + "." + r.nextInt(0, 10) + "." + r.nextInt(0, 100));
        a.put("InstallDate", "2026" + String.format(Locale.ROOT, "%02d%02d", r.nextInt(1, 10), r.nextInt(1, 28)));
        a.put("bIsMsi", Boolean.toString(r.nextBoolean()));
        return a;
    }

    private static void applyNoise(Map<String, String> a, Noise noise, SplittableRandom r,
                                   Map<String, Long> events) {
        if (chance(r, noise.missingDisplayNamePct)) {
            a.remove("DisplayName"); mark(events, "MISSING_DISPLAY_NAME");
        } else if (chance(r, noise.genericDisplayNamePct)) {
            a.put("DisplayName", "Enterprise Software Component"); mark(events, "GENERIC_DISPLAY_NAME");
        }
        if (chance(r, noise.missingPublisherPct)) {
            a.remove("Publisher"); mark(events, "MISSING_PUBLISHER");
        }
        if (chance(r, noise.missingSupportingEvidencePct)) {
            a.remove("ProductFamily"); a.remove("PackageId"); a.remove("InstallDir");
            a.remove("Executables"); a.remove("Services");
            mark(events, "MISSING_SUPPORTING_EVIDENCE");
        }
    }

    private static boolean chance(SplittableRandom r, int pct) { return pct > 0 && r.nextInt(100) < pct; }
    private static void mark(Map<String, Long> events, String key) { events.merge(key, 1L, Long::sum); }
    private static String value(String value) { return value == null ? "" : value; }
    private static long stableSeed(long seed, String value) {
        long hash = 0xcbf29ce484222325L ^ seed;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) { hash ^= b & 0xffL; hash *= 0x100000001b3L; }
        return hash;
    }

    public enum Noise {
        CLEAN(0, 0, 0, 0), LIGHT(2, 3, 4, 2), STRESS(6, 8, 12, 8), SEVERE(12, 15, 25, 18);
        final int missingDisplayNamePct, missingPublisherPct, missingSupportingEvidencePct, genericDisplayNamePct;
        Noise(int name, int publisher, int support, int generic) {
            this.missingDisplayNamePct = name; this.missingPublisherPct = publisher;
            this.missingSupportingEvidencePct = support; this.genericDisplayNamePct = generic;
        }
        public static Noise named(String value) { return Noise.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
    }

    public record Catalog(String catalogVersion, String purpose, List<Product> products) {}
    public record Product(String id, AssetSubtype subtype, String displayName, String publisher,
                          String productFamily, String packageId, String installLocation,
                          String executables, String services, String platform,
                          String architecture, boolean domestic) {}
    public record GenerationReport(String catalogVersion, String purpose, long count, long seed,
                                   String noise, int catalogProducts, long domesticSelections,
                                   Map<String, Long> subtypeCounts, Map<String, Long> noiseEventCounts) {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path catalog = Path.of(a.getOrDefault("--catalog", "data/software-catalog-v3.yaml"));
        long count = Long.parseLong(a.getOrDefault("--count", "10000"));
        long seed = Long.parseLong(a.getOrDefault("--seed", "20260916"));
        Noise noise = Noise.named(a.getOrDefault("--noise", "stress"));
        Path raw = Path.of(a.getOrDefault("--raw", "data/generated/software-v3-raw.jsonl"));
        Path truth = Path.of(a.getOrDefault("--truth", "data/generated/software-v3-truth.jsonl"));
        Path out = Path.of(a.getOrDefault("--out", raw + ".meta.json"));
        GenerationReport report = new SoftwareTaxonomyV3Generator().generate(catalog, count, seed, noise, raw, truth);
        Files.createDirectories(out.toAbsolutePath().getParent());
        PRETTY.writeValue(out.toFile(), report);
        System.out.println(PRETTY.writeValueAsString(report));
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Expected --key value pairs");
            out.put(args[i], args[++i]);
        }
        return out;
    }
}
