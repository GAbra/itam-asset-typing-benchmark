package ru.itam.typing.bench;

import org.openjdk.jmh.annotations.*;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.ProfileDistribution;
import ru.itam.typing.realistic.RealisticDatasetMaterializer;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.RuleSetScaler;
import ru.itam.typing.rules.RuleLoader;
import ru.itam.typing.rules.RuleSet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Forked JMH cross-check for engine-only and end-to-end single-record latency. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
@Fork(3)
@Threads(1)
@State(Scope.Benchmark)
public class EngineJmhBenchmark {
    private static final int CORPUS_SIZE = 4096;
    private static final int MASK = CORPUS_SIZE - 1;

    @Param({"14", "100", "500"})
    public int ruleCount;

    private FeatureExtractor extractor;
    private FeatureMapTypingEngine bitset;
    private FeatureMapTypingEngine cel;
    private FeatureMapTypingEngine dmn;
    private List<AssetTypingContext> contexts;
    private List<Map<String, Boolean>> featureMaps;
    private Path tempDir;
    private int cursor;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        tempDir = Files.createTempDirectory("itam-jmh-");
        Path raw = tempDir.resolve("raw.jsonl");
        Path truth = tempDir.resolve("truth.jsonl");
        Path normalized = tempDir.resolve("normalized.jsonl");
        new RealisticWorkloadGenerator().generate(CORPUS_SIZE, 20260910L, raw, truth,
                NoiseProfile.stressDefault(), ProfileDistribution.balanced());
        new RealisticDatasetMaterializer().materialize(raw, truth, normalized);

        RuleSet base = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        RuleSet rules = ruleCount == base.rules().stream().filter(r -> r.enabled()).count()
                ? base : new RuleSetScaler().scale(base, ruleCount);
        extractor = new FeatureExtractor();
        bitset = new BitSetTypingEngine(rules, extractor);
        cel = new CelTypingEngine(rules, extractor);
        dmn = new DmnTypingEngine(rules, extractor);

        List<AssetTypingContext> c = new ArrayList<>(CORPUS_SIZE);
        new DatasetReader().forEach(normalized, r -> c.add(r.context()));
        if (c.size() != CORPUS_SIZE) throw new IllegalStateException("unexpected JMH corpus size " + c.size());
        contexts = List.copyOf(c);
        List<Map<String, Boolean>> f = new ArrayList<>(CORPUS_SIZE);
        for (AssetTypingContext context : contexts) f.add(extractor.extract(context));
        featureMaps = List.copyOf(f);
        cursor = 0;
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (tempDir == null || !Files.exists(tempDir)) return;
        try (var walk = Files.walk(tempDir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            });
        }
    }

    @Benchmark public TypingResult bitsetEndToEnd() { return bitset.classify(nextContext()); }
    @Benchmark public TypingResult celEndToEnd() { return cel.classify(nextContext()); }
    @Benchmark public TypingResult dmnEndToEnd() { return dmn.classify(nextContext()); }

    @Benchmark public TypingResult bitsetEngineOnly() {
        int i = nextIndex();
        return bitset.classifyFeatures(contexts.get(i).assetId(), featureMaps.get(i));
    }

    @Benchmark public TypingResult celEngineOnly() {
        int i = nextIndex();
        return cel.classifyFeatures(contexts.get(i).assetId(), featureMaps.get(i));
    }

    @Benchmark public TypingResult dmnEngineOnly() {
        int i = nextIndex();
        return dmn.classifyFeatures(contexts.get(i).assetId(), featureMaps.get(i));
    }

    private AssetTypingContext nextContext() { return contexts.get(nextIndex()); }

    private int nextIndex() {
        int i = cursor & MASK;
        cursor++;
        return i;
    }
}
