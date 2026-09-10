package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.DatasetRecord;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Research benchmark that separates feature extraction from engine execution and rotates engine order.
 * It is intentionally separate from the historical v1 benchmark path.
 */
public final class ResearchBenchmarkCli {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ResearchBenchmarkCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path data = Path.of(required(a, "--data"));
        Path rulesPath = Path.of(a.getOrDefault("--rules", "rules/canonical-rules.yaml"));
        int warmup = Integer.parseInt(a.getOrDefault("--warmup", "2"));
        int runs = Integer.parseInt(a.getOrDefault("--runs", "6"));
        int batch = Integer.parseInt(a.getOrDefault("--batch", "5000"));
        long max = Long.parseLong(a.getOrDefault("--max", Long.toString(Long.MAX_VALUE)));
        Path out = Path.of(a.getOrDefault("--out", "results/local/research-benchmark.json"));
        if (warmup < 0 || runs < 3 || batch <= 0 || max <= 0) {
            throw new IllegalArgumentException("--warmup >= 0, --runs >= 3, --batch > 0 and --max > 0 required");
        }

        var ruleSet = RuleLoader.load(rulesPath);
        FeatureExtractor extractor = new FeatureExtractor();
        List<FeatureMapTypingEngine> canonical = List.of(
                new BitSetTypingEngine(ruleSet, extractor),
                new CelTypingEngine(ruleSet, extractor),
                new DmnTypingEngine(ruleSet, extractor));
        DatasetReader reader = new DatasetReader();
        List<DatasetRecord> warm = reader.first(data, (int) Math.min(max, Math.min(batch, 5_000)));
        if (warm.isEmpty()) throw new IllegalArgumentException("Cannot benchmark an empty dataset");

        for (int i = 0; i < warmup; i++) {
            for (FeatureMapTypingEngine engine : canonical) {
                long checksum = 0;
                for (DatasetRecord record : warm) {
                    Map<String, Boolean> features = extractor.extract(record.context());
                    checksum ^= resultHash(engine.classify(record.context()));
                    checksum ^= resultHash(engine.classifyFeatures(record.context().assetId(), features));
                }
                if (checksum == Long.MIN_VALUE) System.out.print("");
            }
        }

        List<RunResult> results = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            List<FeatureMapTypingEngine> order = rotate(canonical, (run - 1) % canonical.size());
            runMode(reader, data, batch, max, order, extractor, Mode.END_TO_END, run, results);
            runMode(reader, data, batch, max, order, extractor, Mode.ENGINE_ONLY, run, results);
        }

        List<Summary> summaries = new ArrayList<>();
        for (Mode mode : Mode.values()) {
            for (FeatureMapTypingEngine engine : canonical) {
                List<RunResult> subset = results.stream()
                        .filter(r -> r.mode == mode && r.engine.equals(engine.name())).toList();
                summaries.add(new Summary(mode.name(), engine.name(),
                        median(subset.stream().mapToDouble(RunResult::assetsPerSecond).toArray()),
                        median(subset.stream().mapToDouble(RunResult::nsPerAsset).toArray()),
                        subset.stream().mapToDouble(RunResult::assetsPerSecond).min().orElse(0),
                        subset.stream().mapToDouble(RunResult::assetsPerSecond).max().orElse(0)));
            }
        }

        ResearchBenchmarkReport report = new ResearchBenchmarkReport(
                "OK", Instant.now().toString(), data.toString(), rulesPath.toString(),
                ruleSet.rules().stream().filter(r -> r.enabled()).count(), warmup, runs, batch,
                "rotating Latin-order: run1 B-C-D, run2 C-D-B, run3 D-B-C, then repeat",
                results, summaries);
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writeValue(out.toFile(), report);
        System.out.println(JSON.writeValueAsString(report));
    }

    private static void runMode(DatasetReader reader, Path data, int batchSize, long max,
                                List<FeatureMapTypingEngine> engines, FeatureExtractor extractor,
                                Mode mode, int run, List<RunResult> results) throws Exception {
        LinkedHashMap<String, MutableTiming> timings = new LinkedHashMap<>();
        for (FeatureMapTypingEngine engine : engines) timings.put(engine.name(), new MutableTiming());
        List<PreparedRecord> batch = new ArrayList<>(batchSize);
        long[] seen = {0};

        reader.forEach(data, record -> {
            if (seen[0] >= max) return;
            seen[0]++;
            Map<String, Boolean> features = mode == Mode.ENGINE_ONLY ? extractor.extract(record.context()) : null;
            batch.add(new PreparedRecord(record.context(), features));
            if (batch.size() >= batchSize) {
                measureBatch(batch, engines, timings, mode);
                batch.clear();
            }
        });
        if (!batch.isEmpty()) measureBatch(batch, engines, timings, mode);

        int ordinal = 0;
        for (FeatureMapTypingEngine engine : engines) {
            MutableTiming t = timings.get(engine.name());
            ordinal++;
            results.add(new RunResult(run, mode, engine.name(), ordinal, t.count, t.elapsedNs,
                    t.count == 0 ? 0 : t.count * 1_000_000_000.0 / t.elapsedNs,
                    t.count == 0 ? 0 : t.elapsedNs / (double) t.count, t.checksum));
        }
    }

    private static void measureBatch(List<PreparedRecord> batch, List<FeatureMapTypingEngine> engines,
                                     Map<String, MutableTiming> timings, Mode mode) {
        for (FeatureMapTypingEngine engine : engines) {
            MutableTiming timing = timings.get(engine.name());
            long checksum = 0;
            long start = System.nanoTime();
            if (mode == Mode.END_TO_END) {
                for (PreparedRecord record : batch) checksum ^= resultHash(engine.classify(record.context));
            } else {
                for (PreparedRecord record : batch) {
                    checksum ^= resultHash(engine.classifyFeatures(record.context.assetId(), record.features));
                }
            }
            long elapsed = System.nanoTime() - start;
            timing.count += batch.size();
            timing.elapsedNs += elapsed;
            timing.checksum ^= checksum;
        }
    }

    private static List<FeatureMapTypingEngine> rotate(List<FeatureMapTypingEngine> engines, int shift) {
        List<FeatureMapTypingEngine> out = new ArrayList<>(engines.size());
        for (int i = 0; i < engines.size(); i++) out.add(engines.get((i + shift) % engines.size()));
        return List.copyOf(out);
    }

    private static long resultHash(TypingResult result) {
        return Objects.hash(result.type(), result.subtype(), result.status(), result.matchedRuleIds());
    }

    private static double median(double[] values) {
        Arrays.sort(values);
        if (values.length == 0) return 0;
        int n = values.length;
        return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2.0;
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
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private enum Mode { END_TO_END, ENGINE_ONLY }

    private record PreparedRecord(AssetTypingContext context, Map<String, Boolean> features) {}

    private static final class MutableTiming {
        long count;
        long elapsedNs;
        long checksum;
    }

    public record RunResult(int run, Mode mode, String engine, int ordinalPosition, long count,
                            long elapsedNs, double assetsPerSecond, double nsPerAsset, long checksum) {}

    public record Summary(String mode, String engine, double medianAssetsPerSecond, double medianNsPerAsset,
                          double minAssetsPerSecond, double maxAssetsPerSecond) {}

    public record ResearchBenchmarkReport(String result, String generatedAtUtc, String data, String rules,
                                          long enabledRules, int warmup, int runs, int batch,
                                          String engineOrderPolicy, List<RunResult> runResults,
                                          List<Summary> summary) {}
}
