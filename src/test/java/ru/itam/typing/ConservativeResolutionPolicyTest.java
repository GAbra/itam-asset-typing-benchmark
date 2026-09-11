package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.common.MatchResolver;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.engine.reference.ReferenceTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConservativeResolutionPolicyTest {

    @Test
    void cleanCorroboratingEvidenceRemainsAutomatic() throws Exception {
        Map<String, Boolean> features = map(
                "SRC_AD", "OBJ_AD_COMPUTER", "OS_WINDOWS",
                "SRC_KSC", "OBJ_KSC_HOST", "KSC_WORKSTATION");

        TypingResult result = bitset(ResolutionPolicy.CONSERVATIVE_80)
                .classifyFeatures("clean-ws", features);

        assertEquals(TypingStatus.AUTO, result.status());
        assertEquals(AssetType.DEVICE, result.type());
        assertEquals(AssetSubtype.WORKSTATION, result.subtype());
    }

    @Test
    void ambiguousNmapSignalAbstainsInsteadOfConfidentWrongSubtype() throws Exception {
        Map<String, Boolean> features = map(
                "SRC_AD", "OBJ_AD_COMPUTER", "OS_WINDOWS",
                "SRC_KSC", "OBJ_KSC_HOST", "KSC_WORKSTATION",
                "SRC_NMAP", "OBJ_NMAP_HOST", "NMAP_NETWORK_DEVICE");

        TypingResult legacy = bitset(ResolutionPolicy.LEGACY_MAX_PRIORITY)
                .classifyFeatures("ambiguous-ws", features);
        TypingResult conservative = bitset(ResolutionPolicy.CONSERVATIVE_80)
                .classifyFeatures("ambiguous-ws", features);

        assertEquals(TypingStatus.AUTO, legacy.status());
        assertEquals(AssetSubtype.NETWORK_DEVICE, legacy.subtype());
        assertEquals(TypingStatus.SUBTYPE_CONFLICT, conservative.status());
        assertEquals(AssetType.DEVICE, conservative.type());
        assertNull(conservative.subtype());
        assertTrue(conservative.matchedRuleIds().contains("NMAP_NETWORK_DEVICE"));
        assertTrue(conservative.matchedRuleIds().contains("AD_WINDOWS_WORKSTATION"));
    }

    @Test
    void contradictoryKscAndZabbixServerEvidenceAbstains() throws Exception {
        Map<String, Boolean> features = map(
                "SRC_KSC", "OBJ_KSC_HOST", "KSC_WORKSTATION",
                "SRC_ZABBIX", "OBJ_ZABBIX_HOST", "OS_SERVER");

        TypingResult legacy = bitset(ResolutionPolicy.LEGACY_MAX_PRIORITY)
                .classifyFeatures("ksc-zabbix", features);
        TypingResult conservative = bitset(ResolutionPolicy.CONSERVATIVE_80)
                .classifyFeatures("ksc-zabbix", features);

        assertEquals(TypingStatus.AUTO, legacy.status());
        assertEquals(AssetSubtype.WORKSTATION, legacy.subtype());
        assertEquals(TypingStatus.SUBTYPE_CONFLICT, conservative.status());
    }

    @Test
    void evidenceOutsideWindowDoesNotCauseAbstention() {
        TypingResult result = MatchResolver.resolve("window", List.of(
                new RuleMatch("HIGH", AssetType.DEVICE, AssetSubtype.NETWORK_DEVICE, 330),
                new RuleMatch("LOW", AssetType.DEVICE, AssetSubtype.WORKSTATION, 249)),
                ResolutionPolicy.CONSERVATIVE_80);

        assertEquals(TypingStatus.AUTO, result.status());
        assertEquals(AssetSubtype.NETWORK_DEVICE, result.subtype());
        assertEquals(List.of("HIGH"), result.matchedRuleIds());
    }

    @Test
    void allEnginesAndIndependentReferenceAgreeUnderConservativePolicy() throws Exception {
        var rules = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        var extractor = new FeatureExtractor();
        var policy = ResolutionPolicy.CONSERVATIVE_80;
        List<FeatureMapTypingEngine> engines = List.of(
                new ReferenceTypingEngine(rules, extractor, policy),
                new BitSetTypingEngine(rules, extractor, policy),
                new CelTypingEngine(rules, extractor, policy),
                new DmnTypingEngine(rules, extractor, policy));
        Map<String, Boolean> features = map(
                "SRC_AD", "OBJ_AD_COMPUTER", "OS_WINDOWS",
                "SRC_KSC", "OBJ_KSC_HOST", "KSC_WORKSTATION",
                "SRC_NMAP", "OBJ_NMAP_HOST", "NMAP_NETWORK_DEVICE");

        TypingResult expected = engines.getFirst().classifyFeatures("equivalence", features);
        assertEquals(TypingStatus.SUBTYPE_CONFLICT, expected.status());
        for (FeatureMapTypingEngine engine : engines) {
            assertEquals(expected, engine.classifyFeatures("equivalence", features), engine.name());
        }
    }

    private static BitSetTypingEngine bitset(ResolutionPolicy policy) throws Exception {
        return new BitSetTypingEngine(
                RuleLoader.load(Path.of("rules/canonical-rules.yaml")),
                new FeatureExtractor(), policy);
    }

    private static Map<String, Boolean> map(String... enabled) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String feature : enabled) out.put(feature, true);
        return out;
    }
}
