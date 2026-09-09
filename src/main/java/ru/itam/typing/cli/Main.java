package ru.itam.typing.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.itam.typing.data.DatasetGenerator;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.TypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.DatasetRecord;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.rules.RuleLoader;
import ru.itam.typing.rules.RuleSet;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

public final class Main {
    private static final Path DEFAULT_RULES = Path.of("rules/canonical-rules.yaml");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private Main() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "--help".equalsIgnoreCase(args[0])) {
            printHelp();
            return;
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        Args a = new Args(Arrays.copyOfRange(args, 1, args.length));
        switch (command) {
            case "generate" -> generate(a);
            case "verify" -> verify(a);
            case "benchmark" -> benchmark(a);
            case "explain" -> explain(a);
            case "export-dmn" -> exportDmn(a);
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void generate(Args a) throws IOException {
        long count = a.longValue("--count", 10_000L);
        long seed = a.longValue("--seed", DatasetGenerator.DEFAULT_SEED);
        Path out = Path.of(a.value("--out", "data/generated/normalized-" + count + ".jsonl"));
        var summary = new DatasetGenerator().generate(count, seed, out);
        System.out.println(JSON.writeValueAsString(summary));
    }

    private static void verify(Args a) throws Exception {
        Path data = requiredPath(a, "--data");
        Path rulesPath = Path.of(a.value("--rules", DEFAULT_RULES.toString()));
        long max = a.longValue("--max", Long.MAX_VALUE);
        EngineBundle bundle = engines(rulesPath);

        VerificationStats stats = new VerificationStats();
        MessageDigest digestBitset = MessageDigest.getInstance("SHA-256");
        MessageDigest digestCel = MessageDigest.getInstance("SHA-256");
        MessageDigest digestDmn = MessageDigest.getInstance("SHA-256");

        new DatasetReader().forEach(data, record -> {
            if (stats.total >= max) return;
            TypingResult b = bundle.bitset.classify(record.context());
            TypingResult c = bundle.cel.classify(record.context());
            TypingResult d = bundle.dmn.classify(record.context());
            stats.total++;
            updateDigest(digestBitset, b);
            updateDigest(digestCel, c);
            updateDigest(digestDmn, d);
            if (!equivalent(b, c) || !equivalent(b, d)) {
                stats.engineMismatches++;
                if (stats.samples.size() < 20) {
                    stats.samples.add(Map.of("assetId", record.context().assetId(), "bitset", b, "cel", c, "dmn", d));
                }
            }
            if (record.expectedType() != null && (b.type() != record.expectedType() || b.subtype() != record.expectedSubtype())) {
                stats.groundTruthMismatches++;
                if (stats.samples.size() < 20) {
                    stats.samples.add(Map.of("assetId", record.context().assetId(), "expectedType", record.expectedType(),
                            "expectedSubtype", record.expectedSubtype(), "actual", b));
                }
            }
        });

        VerificationReport report = new VerificationReport(
                stats.engineMismatches == 0 && stats.groundTruthMismatches == 0 ? "PASS" : "FAIL",
                data.toString(), rulesPath.toString(), stats.total, stats.engineMismatches,
                stats.groundTruthMismatches, hex(digestBitset.digest()), hex(digestCel.digest()),
                hex(digestDmn.digest()), stats.samples);
        String outValue = a.value("--out", null);
        if (outValue != null) {
            Path out = Path.of(outValue);
            Files.createDirectories(out.toAbsolutePath().getParent());
            JSON.writeValue(out.toFile(), report);
        }
        System.out.println(JSON.writeValueAsString(report));
        if (!"PASS".equals(report.result())) System.exit(2);
    }

    private static void benchmark(Args a) throws Exception {
        Path data = requiredPath(a, "--data");
        Path rulesPath = Path.of(a.value("--rules", DEFAULT_RULES.toString()));
        int warmup = a.intValue("--warmup", 2);
        int runs = a.intValue("--runs", 5);
        int batch = a.intValue("--batch", 5_000);
        long max = a.longValue("--max", Long.MAX_VALUE);
        Path out = Path.of(a.value("--out", "benchmark-results/benchmark.json"));

        EngineBundle bundle = engines(rulesPath);
        List<TypingEngine> engines = List.of(bundle.bitset, bundle.cel, bundle.dmn);
        DatasetReader reader = new DatasetReader();
        List<DatasetRecord> warm = reader.first(data, Math.min(batch, 5_000));
        for (int i = 0; i < warmup; i++) {
            for (TypingEngine engine : engines) {
                long checksum = 0;
                for (DatasetRecord r : warm) checksum ^= resultHash(engine.classify(r.context()));
                if (checksum == Long.MIN_VALUE) System.out.print("");
            }
        }

        List<RunResult> allRuns = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            Map<String, MutableTiming> timings = new LinkedHashMap<>();
            engines.forEach(e -> timings.put(e.name(), new MutableTiming()));
            BatchAccumulator acc = new BatchAccumulator(batch, max, engines, timings);
            reader.forEach(data, acc::accept);
            acc.flush();
            for (TypingEngine engine : engines) {
                MutableTiming t = timings.get(engine.name());
                allRuns.add(new RunResult(run, engine.name(), t.count, t.elapsedNs,
                        t.count == 0 ? 0.0 : (t.count * 1_000_000_000.0 / t.elapsedNs),
                        t.count == 0 ? 0.0 : (t.elapsedNs / (double) t.count), t.checksum));
            }
        }

        List<EngineSummary> summaries = new ArrayList<>();
        for (TypingEngine engine : engines) {
            List<RunResult> r = allRuns.stream().filter(x -> x.engine.equals(engine.name())).toList();
            double medianThroughput = median(r.stream().mapToDouble(x -> x.assetsPerSecond).toArray());
            double medianNs = median(r.stream().mapToDouble(x -> x.nsPerAsset).toArray());
            summaries.add(new EngineSummary(engine.name(), medianThroughput, medianNs,
                    r.stream().mapToDouble(x -> x.assetsPerSecond).min().orElse(0),
                    r.stream().mapToDouble(x -> x.assetsPerSecond).max().orElse(0)));
        }

        BenchmarkReport report = new BenchmarkReport("OK", Instant.now().toString(), data.toString(), rulesPath.toString(),
                warmup, runs, batch, bundle.loadMs, allRuns, summaries);
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writeValue(out.toFile(), report);
        System.out.println(JSON.writeValueAsString(report));
    }

    private static void explain(Args a) throws Exception {
        Path data = requiredPath(a, "--data");
        Path rulesPath = Path.of(a.value("--rules", DEFAULT_RULES.toString()));
        String assetId = a.value("--asset", null);
        if (assetId == null) throw new IllegalArgumentException("--asset is required");
        EngineBundle bundle = engines(rulesPath);
        FeatureExtractor extractor = new FeatureExtractor();
        final DatasetRecord[] found = new DatasetRecord[1];
        new DatasetReader().forEach(data, r -> {
            if (found[0] == null && assetId.equals(r.context().assetId())) found[0] = r;
        });
        if (found[0] == null) throw new IllegalArgumentException("Asset not found: " + assetId);
        DatasetRecord r = found[0];
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("record", r);
        out.put("features", extractor.extract(r.context()));
        out.put("bitset", bundle.bitset.classify(r.context()));
        out.put("cel", bundle.cel.classify(r.context()));
        out.put("dmn", bundle.dmn.classify(r.context()));
        System.out.println(JSON.writeValueAsString(out));
    }

    private static void exportDmn(Args a) throws Exception {
        Path rulesPath = Path.of(a.value("--rules", DEFAULT_RULES.toString()));
        Path out = Path.of(a.value("--out", "rules/generated/itam-typing.dmn"));
        EngineBundle bundle = engines(rulesPath);
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, bundle.dmn.generatedDmn(), StandardCharsets.UTF_8);
        System.out.println(out.toAbsolutePath());
    }

    private static EngineBundle engines(Path rulesPath) throws Exception {
        long rulesStart = System.nanoTime();
        RuleSet ruleSet = RuleLoader.load(rulesPath);
        double ruleLoadMs = (System.nanoTime() - rulesStart) / 1_000_000.0;
        FeatureExtractor featureExtractor = new FeatureExtractor();

        long start = System.nanoTime();
        BitSetTypingEngine bitset = new BitSetTypingEngine(ruleSet, featureExtractor);
        double bitsetMs = (System.nanoTime() - start) / 1_000_000.0;

        start = System.nanoTime();
        CelTypingEngine cel = new CelTypingEngine(ruleSet, featureExtractor);
        double celMs = (System.nanoTime() - start) / 1_000_000.0;

        start = System.nanoTime();
        DmnTypingEngine dmn = new DmnTypingEngine(ruleSet, featureExtractor);
        double dmnMs = (System.nanoTime() - start) / 1_000_000.0;

        Map<String, Double> loadMs = new LinkedHashMap<>();
        loadMs.put("CANONICAL_RULESET", ruleLoadMs);
        loadMs.put(bitset.name(), bitsetMs);
        loadMs.put(cel.name(), celMs);
        loadMs.put(dmn.name(), dmnMs);
        return new EngineBundle(bitset, cel, dmn, Map.copyOf(loadMs));
    }

    private static boolean equivalent(TypingResult a, TypingResult b) {
        return a.type() == b.type() && a.subtype() == b.subtype() && a.status() == b.status()
                && Objects.equals(new TreeSet<>(a.matchedRuleIds()), new TreeSet<>(b.matchedRuleIds()));
    }

    private static void updateDigest(MessageDigest digest, TypingResult result) {
        digest.update((result.assetId() + "|" + result.type() + "|" + result.subtype() + "|" + result.status() + "|" +
                String.join(",", new TreeSet<>(result.matchedRuleIds())) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static long resultHash(TypingResult result) {
        return Objects.hash(result.type(), result.subtype(), result.status(), result.matchedRuleIds());
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    private static double median(double[] values) {
        if (values.length == 0) return 0.0;
        Arrays.sort(values);
        int n = values.length;
        return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2.0;
    }

    private static Path requiredPath(Args a, String key) {
        String value = a.value(key, null);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return Path.of(value);
    }

    private static void printHelp() {
        System.out.println("""
                ITAM Asset Typing Demo

                Commands:
                  generate  --count 10000 [--seed 20260909] [--out data/generated/normalized-10000.jsonl]
                  verify    --data <file> [--rules rules/canonical-rules.yaml] [--max N] [--out benchmark-results/verify.json]
                  benchmark --data <file> [--warmup 2] [--runs 5] [--batch 5000] [--max N] [--out benchmark-results/benchmark.json]
                  explain   --data <file> --asset <asset-id>
                  export-dmn [--out rules/generated/itam-typing.dmn]
                """);
    }

    private record EngineBundle(BitSetTypingEngine bitset, CelTypingEngine cel, DmnTypingEngine dmn, Map<String, Double> loadMs) {}

    private static final class VerificationStats {
        long total;
        long engineMismatches;
        long groundTruthMismatches;
        final List<Map<String, Object>> samples = new ArrayList<>();
    }

    private record VerificationReport(String result, String data, String rules, long checked,
                                      long engineMismatches, long groundTruthMismatches,
                                      String hashBitset, String hashCel, String hashDmn,
                                      List<Map<String, Object>> samples) {}

    private static final class MutableTiming {
        long count;
        long elapsedNs;
        long checksum;
    }

    private static final class BatchAccumulator {
        private final int batchSize;
        private final long max;
        private final List<TypingEngine> engines;
        private final Map<String, MutableTiming> timings;
        private final List<DatasetRecord> batch;
        private long seen;

        private BatchAccumulator(int batchSize, long max, List<TypingEngine> engines, Map<String, MutableTiming> timings) {
            this.batchSize = batchSize;
            this.max = max;
            this.engines = engines;
            this.timings = timings;
            this.batch = new ArrayList<>(batchSize);
        }

        void accept(DatasetRecord record) {
            if (seen >= max) return;
            seen++;
            batch.add(record);
            if (batch.size() >= batchSize) flush();
        }

        void flush() {
            if (batch.isEmpty()) return;
            for (TypingEngine engine : engines) {
                long checksum = 0;
                long start = System.nanoTime();
                for (DatasetRecord record : batch) checksum ^= resultHash(engine.classify(record.context()));
                long elapsed = System.nanoTime() - start;
                MutableTiming t = timings.get(engine.name());
                t.count += batch.size();
                t.elapsedNs += elapsed;
                t.checksum ^= checksum;
            }
            batch.clear();
        }
    }

    private record RunResult(int run, String engine, long count, long elapsedNs,
                             double assetsPerSecond, double nsPerAsset, long checksum) {}
    private record EngineSummary(String engine, double medianAssetsPerSecond, double medianNsPerAsset,
                                 double minAssetsPerSecond, double maxAssetsPerSecond) {}
    private record BenchmarkReport(String result, String generatedAt, String data, String rules,
                                   int warmupIterations, int measuredRuns, int batchSize,
                                   Map<String, Double> engineLoadMs,
                                   List<RunResult> runs, List<EngineSummary> summary) {}

    private static final class Args {
        private final Map<String, String> values = new LinkedHashMap<>();
        Args(String[] args) {
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                if (!key.startsWith("--")) throw new IllegalArgumentException("Expected option, got: " + key);
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    values.put(key, "true");
                } else {
                    values.put(key, args[++i]);
                }
            }
        }
        String value(String key, String defaultValue) { return values.getOrDefault(key, defaultValue); }
        int intValue(String key, int defaultValue) { return Integer.parseInt(value(key, Integer.toString(defaultValue))); }
        long longValue(String key, long defaultValue) { return Long.parseLong(value(key, Long.toString(defaultValue))); }
    }
}
