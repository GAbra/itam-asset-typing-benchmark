package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.data.DatasetGenerator;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.realistic.RuleSetScaler;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchBenchmarkFoundationTest {
    @TempDir Path temp;

    @Test
    void classifyFeaturesHasExactlyTheSameSemanticsAsEndToEndClassify() throws Exception {
        Path data = temp.resolve("dataset.jsonl");
        new DatasetGenerator().generate(100, 12345L, data);
        var ruleSet = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        FeatureExtractor extractor = new FeatureExtractor();
        List<FeatureMapTypingEngine> engines = List.of(
                new BitSetTypingEngine(ruleSet, extractor),
                new CelTypingEngine(ruleSet, extractor),
                new DmnTypingEngine(ruleSet, extractor));

        new DatasetReader().forEach(data, record -> {
            var features = extractor.extract(record.context());
            for (FeatureMapTypingEngine engine : engines) {
                assertEquals(engine.classify(record.context()),
                        engine.classifyFeatures(record.context().assetId(), features), engine.name());
            }
        });
    }

    @Test
    void scaledRulesetsPreserveBaseResultWhileIncreasingActiveRuleCount() throws Exception {
        Path data = temp.resolve("dataset.jsonl");
        new DatasetGenerator().generate(200, 67890L, data);
        var base = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        var scaled50 = new RuleSetScaler().scale(base, 50);
        var scaled100 = new RuleSetScaler().scale(base, 100);
        var scaled500 = new RuleSetScaler().scale(base, 500);
        assertEquals(50, scaled50.rules().stream().filter(r -> r.enabled()).count());
        assertEquals(100, scaled100.rules().stream().filter(r -> r.enabled()).count());
        assertEquals(500, scaled500.rules().stream().filter(r -> r.enabled()).count());

        FeatureExtractor extractor = new FeatureExtractor();
        var baseEngine = new BitSetTypingEngine(base, extractor);
        var scaledEngine = new BitSetTypingEngine(scaled500, extractor);
        new DatasetReader().forEach(data, record ->
                assertEquals(baseEngine.classify(record.context()), scaledEngine.classify(record.context())));
    }
}
