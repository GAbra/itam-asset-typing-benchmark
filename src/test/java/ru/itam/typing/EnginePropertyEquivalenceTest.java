package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.engine.reference.ReferenceTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.realistic.RuleSetScaler;
import ru.itam.typing.rules.RuleLoader;
import ru.itam.typing.rules.RuleSet;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Deterministic property-style differential testing over the complete known feature vocabulary. */
class EnginePropertyEquivalenceTest {

    @Test
    void canonicalRulesAgreeForBoundarySingletonPairAndRandomFeatureMaps() throws Exception {
        RuleSet rules = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        FeatureExtractor extractor = new FeatureExtractor();
        List<String> features = FeatureExtractor.KNOWN_FEATURES.stream().sorted().toList();
        List<FeatureMapTypingEngine> engines = engines(rules, extractor);

        long[] caseNo = {0};
        assertAllEquivalent(engines, "prop-" + caseNo[0]++, map(features));
        assertAllEquivalent(engines, "prop-" + caseNo[0]++, map(features, features.toArray(String[]::new)));

        for (String feature : features) {
            assertAllEquivalent(engines, "prop-" + caseNo[0]++, map(features, feature));
        }
        for (int i = 0; i < features.size(); i++) {
            for (int j = i + 1; j < features.size(); j++) {
                assertAllEquivalent(engines, "prop-" + caseNo[0]++, map(features, features.get(i), features.get(j)));
            }
        }

        SplittableRandom random = new SplittableRandom(20260910L);
        for (int n = 0; n < 512; n++) {
            Map<String, Boolean> values = new LinkedHashMap<>();
            for (String feature : features) values.put(feature, random.nextBoolean());
            assertAllEquivalent(engines, "prop-" + caseNo[0]++, values);
        }

        assertEquals(1 + 1 + features.size() + features.size() * (features.size() - 1) / 2 + 512, caseNo[0]);
    }

    @Test
    void scaledHundredRuleWorkloadStillAgreesOnIndependentRandomFeatureMaps() throws Exception {
        RuleSet base = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        RuleSet scaled = new RuleSetScaler().scale(base, 100);
        FeatureExtractor extractor = new FeatureExtractor();
        List<String> features = FeatureExtractor.KNOWN_FEATURES.stream().sorted().toList();
        List<FeatureMapTypingEngine> engines = engines(scaled, extractor);
        SplittableRandom random = new SplittableRandom(991177L);

        for (int n = 0; n < 128; n++) {
            Map<String, Boolean> values = new LinkedHashMap<>();
            for (String feature : features) values.put(feature, random.nextInt(100) < 35);
            assertAllEquivalent(engines, "scale-prop-" + n, values);
        }
    }

    private static List<FeatureMapTypingEngine> engines(RuleSet rules, FeatureExtractor extractor) {
        return List.of(
                new ReferenceTypingEngine(rules, extractor),
                new BitSetTypingEngine(rules, extractor),
                new CelTypingEngine(rules, extractor),
                new DmnTypingEngine(rules, extractor));
    }

    private static void assertAllEquivalent(List<FeatureMapTypingEngine> engines, String assetId,
                                            Map<String, Boolean> features) {
        var expected = engines.get(0).classifyFeatures(assetId, features);
        for (int i = 1; i < engines.size(); i++) {
            var actual = engines.get(i).classifyFeatures(assetId, features);
            assertEquals(expected, actual, engines.get(i).name() + " diverged for " + assetId + " / " + enabled(features));
        }
    }

    private static Map<String, Boolean> map(List<String> all, String... enabled) {
        Set<String> selected = Set.of(enabled);
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String feature : all) result.put(feature, selected.contains(feature));
        return result;
    }

    private static List<String> enabled(Map<String, Boolean> features) {
        return features.entrySet().stream().filter(e -> Boolean.TRUE.equals(e.getValue()))
                .map(Map.Entry::getKey).sorted().toList();
    }
}
