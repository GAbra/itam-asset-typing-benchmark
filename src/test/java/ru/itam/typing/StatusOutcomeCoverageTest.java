package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.engine.reference.ReferenceTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.model.TypingStatus;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit executable coverage for every resolver outcome class. */
class StatusOutcomeCoverageTest {

    @Test
    void everyOutcomeStatusIsProducedIdenticallyByReferenceBitsetCelAndDmn() {
        RuleSet rules = statusCoverageRules();
        FeatureExtractor extractor = new FeatureExtractor();
        List<FeatureMapTypingEngine> engines = List.of(
                new ReferenceTypingEngine(rules, extractor),
                new BitSetTypingEngine(rules, extractor),
                new CelTypingEngine(rules, extractor),
                new DmnTypingEngine(rules, extractor));

        assertScenario(engines, "none", features(), TypingStatus.NOT_CLASSIFIED, null, null, List.of());
        assertScenario(engines, "type-only", features("A"), TypingStatus.AUTO_TYPE_ONLY,
                AssetType.DEVICE, null, List.of("R_TYPE_ONLY"));
        assertScenario(engines, "auto", features("B"), TypingStatus.AUTO,
                AssetType.DEVICE, AssetSubtype.SERVER, List.of("R_AUTO"));
        assertScenario(engines, "type-conflict", features("C", "D"), TypingStatus.TYPE_CONFLICT,
                null, null, List.of("R_TYPE_CONFLICT_ACCOUNT", "R_TYPE_CONFLICT_DEVICE"));
        assertScenario(engines, "subtype-conflict", features("E", "F"), TypingStatus.SUBTYPE_CONFLICT,
                AssetType.DEVICE, null, List.of("R_SUB_SERVER", "R_SUB_WORKSTATION"));
    }

    private static void assertScenario(List<FeatureMapTypingEngine> engines, String assetId,
                                       Map<String, Boolean> features, TypingStatus status,
                                       AssetType type, AssetSubtype subtype, List<String> matched) {
        TypingResult expected = new TypingResult(assetId, type, subtype, status, matched);
        for (FeatureMapTypingEngine engine : engines) {
            assertEquals(expected, engine.classifyFeatures(assetId, features), engine.name() + " / " + assetId);
        }
    }

    private static Map<String, Boolean> features(String... enabled) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String f : List.of("A", "B", "C", "D", "E", "F")) result.put(f, false);
        for (String f : enabled) result.put(f, true);
        return result;
    }

    private static RuleSet statusCoverageRules() {
        return new RuleSet("status-coverage-test", List.of(
                rule("R_TYPE_ONLY", AssetType.DEVICE, null, 100, "A"),
                rule("R_AUTO", AssetType.DEVICE, AssetSubtype.SERVER, 200, "B"),
                rule("R_TYPE_CONFLICT_DEVICE", AssetType.DEVICE, AssetSubtype.SERVER, 300, "C"),
                rule("R_TYPE_CONFLICT_ACCOUNT", AssetType.ACCOUNT, AssetSubtype.USER_ACCOUNT, 300, "D"),
                rule("R_SUB_SERVER", AssetType.DEVICE, AssetSubtype.SERVER, 400, "E"),
                rule("R_SUB_WORKSTATION", AssetType.DEVICE, AssetSubtype.WORKSTATION, 400, "F")
        ));
    }

    private static CanonicalRule rule(String id, AssetType type, AssetSubtype subtype, int priority, String required) {
        return new CanonicalRule(id, type, subtype, priority, List.of(required), List.of(), List.of(), true);
    }
}
