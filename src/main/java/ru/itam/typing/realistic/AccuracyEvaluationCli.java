package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.engine.reference.ReferenceTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.DatasetRecord;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.model.TypingStatus;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.rules.RuleLoader;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Accuracy/coverage evaluation against the independent truth sidecar. */
public final class AccuracyEvaluationCli {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AccuracyEvaluationCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path data = Path.of(required(a, "--data"));
        Path truth = Path.of(required(a, "--truth"));
        Path rules = Path.of(a.getOrDefault("--rules", "rules/canonical-rules.yaml"));
        Path out = Path.of(a.getOrDefault("--out", "results/local/realistic-v2-accuracy.json"));
        int conflictWindow = Integer.parseInt(a.getOrDefault("--conflict-window", "0"));
        ResolutionPolicy policy = ResolutionPolicy.conservative(conflictWindow);

        var ruleSet = RuleLoader.load(rules);
        FeatureExtractor extractor = new FeatureExtractor();
        ReferenceTypingEngine reference = new ReferenceTypingEngine(ruleSet, extractor, policy);
        List<FeatureMapTypingEngine> engines = List.of(
                new BitSetTypingEngine(ruleSet, extractor, policy),
                new CelTypingEngine(ruleSet, extractor, policy),
                new DmnTypingEngine(ruleSet, extractor, policy));

        Stats stats = new Stats();
        try (BufferedReader dataReader = Files.newBufferedReader(data, StandardCharsets.UTF_8);
             BufferedReader truthReader = Files.newBufferedReader(truth, StandardCharsets.UTF_8)) {
            while (true) {
                String dataLine = dataReader.readLine();
                String truthLine = truthReader.readLine();
                if (dataLine == null || truthLine == null) {
                    if (dataLine != null || truthLine != null) throw new IllegalStateException("data/truth line count mismatch");
                    break;
                }
                DatasetRecord record = JSON.readValue(dataLine, DatasetRecord.class);
                GroundTruthLabel label = JSON.readValue(truthLine, GroundTruthLabel.class);
                if (!record.context().assetId().equals(label.assetId())) {
                    throw new IllegalStateException("data/truth asset mismatch: " + record.context().assetId() + " != " + label.assetId());
                }

                TypingResult expectedExecution = reference.classify(record.context());
                TypingResult actual = null;
                for (FeatureMapTypingEngine engine : engines) {
                    TypingResult result = engine.classify(record.context());
                    if (!result.equals(expectedExecution)) {
                        stats.engineDivergences++;
                        if (stats.samples.size() < 20) stats.samples.add(Map.of(
                                "kind", "ENGINE_DIVERGENCE", "assetId", label.assetId(),
                                "engine", engine.name(), "reference", expectedExecution, "actual", result));
                    }
                    if (actual == null) actual = result;
                }
                stats.accept(label, Objects.requireNonNull(actual));
            }
        }

        EvaluationReport report = stats.report(data, truth, rules, policy);
        Files.createDirectories(out.toAbsolutePath().getParent());
        JSON.writeValue(out.toFile(), report);
        System.out.println(JSON.writeValueAsString(report));
    }

    private static final class Stats {
        long total;
        long typeCorrect;
        long exactCorrect;
        long autoCount;
        long autoWrong;
        long fullAutoCount;
        long fullAutoWrong;
        long subtypeAbstained;
        long unresolved;
        long engineDivergences;
        final Map<String, Long> statusCounts = new TreeMap<>();
        final Map<String, Long> profileCounts = new TreeMap<>();
        final Map<String, Long> profileExactCorrect = new TreeMap<>();
        final Map<String, Map<String, Long>> confusion = new TreeMap<>();
        final List<Map<String, Object>> samples = new ArrayList<>();

        void accept(GroundTruthLabel label, TypingResult actual) {
            total++;
            profileCounts.merge(label.profile(), 1L, Long::sum);
            statusCounts.merge(actual.status().name(), 1L, Long::sum);
            boolean typeOk = actual.type() == label.type();
            boolean exactOk = typeOk && actual.subtype() == label.subtype();
            if (typeOk) typeCorrect++;
            if (exactOk) {
                exactCorrect++;
                profileExactCorrect.merge(label.profile(), 1L, Long::sum);
            }

            boolean auto = actual.status() == TypingStatus.AUTO || actual.status() == TypingStatus.AUTO_TYPE_ONLY;
            if (auto) {
                autoCount++;
                if (!exactOk) autoWrong++;
            }

            if (actual.status() == TypingStatus.AUTO) {
                fullAutoCount++;
                if (!exactOk) fullAutoWrong++;
            } else {
                subtypeAbstained++;
            }

            if (actual.status() == TypingStatus.NOT_CLASSIFIED || actual.status() == TypingStatus.TYPE_CONFLICT
                    || actual.status() == TypingStatus.SUBTYPE_CONFLICT) unresolved++;

            String expectedKey = label.type() + "/" + label.subtype();
            String actualKey = actual.type() + "/" + actual.subtype() + "/" + actual.status();
            confusion.computeIfAbsent(expectedKey, k -> new TreeMap<>()).merge(actualKey, 1L, Long::sum);
            if (!exactOk && samples.size() < 20) samples.add(Map.of(
                    "kind", "GROUND_TRUTH_ERROR", "assetId", label.assetId(), "profile", label.profile(),
                    "expectedType", label.type(), "expectedSubtype", label.subtype(), "actual", actual));
        }

        EvaluationReport report(Path data, Path truth, Path rules, ResolutionPolicy policy) {
            Map<String, Double> profileAccuracy = new TreeMap<>();
            profileCounts.forEach((profile, count) -> profileAccuracy.put(profile,
                    count == 0 ? 0.0 : profileExactCorrect.getOrDefault(profile, 0L) / (double) count));
            return new EvaluationReport(
                    engineDivergences == 0 ? "OK" : "ENGINE_DIVERGENCE",
                    data.toString(), truth.toString(), rules.toString(),
                    policy.name(), policy.conflictPriorityWindow(),
                    total, engineDivergences,
                    ratio(typeCorrect, total), ratio(exactCorrect, total), ratio(autoCount, total),
                    ratio(autoWrong, autoCount), ratio(unresolved, total),
                    fullAutoCount, fullAutoWrong,
                    ratio(fullAutoCount, total), ratio(fullAutoWrong, fullAutoCount),
                    ratio(fullAutoWrong, total), ratio(subtypeAbstained, total),
                    Map.copyOf(statusCounts), Map.copyOf(profileCounts), Map.copyOf(profileAccuracy),
                    deepCopy(confusion), List.copyOf(samples));
        }
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : numerator / (double) denominator;
    }

    private static Map<String, Map<String, Long>> deepCopy(Map<String, Map<String, Long>> input) {
        Map<String, Map<String, Long>> out = new TreeMap<>();
        input.forEach((k, v) -> out.put(k, Map.copyOf(v)));
        return Map.copyOf(out);
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Expected --key value pairs");
            out.put(args[i], args[++i]);
        }
        return out;
    }

    private static String required(Map<String, String> a, String key) {
        String value = a.get(key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    public record EvaluationReport(
            String result,
            String data,
            String truth,
            String rules,
            String resolutionPolicy,
            int conflictPriorityWindow,
            long total,
            long engineDivergences,
            double typeAccuracy,
            double exactTypeSubtypeAccuracy,
            double autoCoverage,
            double autoErrorRate,
            double unresolvedRate,
            long fullAutoCount,
            long fullAutoWrong,
            double fullAutoCoverage,
            double fullAutoErrorRate,
            double wrongFullAutoPerTotal,
            double subtypeAbstentionRate,
            Map<String, Long> statusCounts,
            Map<String, Long> profileCounts,
            Map<String, Double> profileExactAccuracy,
            Map<String, Map<String, Long>> confusion,
            List<Map<String, Object>> samples) {}
}
