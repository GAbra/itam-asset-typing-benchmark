package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.TypingEngine;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EngineEdgeCasesTest {
    @Test
    void allEnginesAgreeOnConflictsFallbacksAndFiltering() {
        var rules = new RuleSet("edge-cases", List.of(
                rule("DEVICE", AssetType.DEVICE, null, List.of("SRC_AD"), List.of(), List.of(), true),
                rule("ACCOUNT", AssetType.ACCOUNT, null, List.of("SRC_SIEM"), List.of(), List.of(), true),
                rule("SERVER", AssetType.DEVICE, AssetSubtype.SERVER, List.of("SRC_KSC"), List.of(), List.of(), true),
                rule("WORKSTATION", AssetType.DEVICE, AssetSubtype.WORKSTATION, List.of("SRC_ZABBIX"), List.of(), List.of(), true),
                rule("ANY", AssetType.DEVICE, null, List.of(), List.of("OS_WINDOWS", "OS_LINUX"), List.of("SRC_NMAP"), true),
                rule("DISABLED", AssetType.SOFTWARE, null, List.of(), List.of(), List.of(), false)));
        var features = new FeatureExtractor();
        List<TypingEngine> engines = List.of(new BitSetTypingEngine(rules, features),
                new CelTypingEngine(rules, features), new DmnTypingEngine(rules, features));
        check(engines, Set.of(), Map.of(), TypingStatus.NOT_CLASSIFIED, null, null, List.of());
        check(engines, Set.of("AD"), Map.of(), TypingStatus.AUTO_TYPE_ONLY, AssetType.DEVICE, null, List.of("DEVICE"));
        check(engines, Set.of("AD", "SIEM"), Map.of(), TypingStatus.TYPE_CONFLICT, null, null, List.of("ACCOUNT", "DEVICE"));
        check(engines, Set.of("KSC", "ZABBIX"), Map.of(), TypingStatus.SUBTYPE_CONFLICT, AssetType.DEVICE, null, List.of("SERVER", "WORKSTATION"));
        // Two true ANY features expand to multiple DMN rows, but resolve to one rule ID.
        check(engines, Set.of(), Map.of("ad.operatingSystem", "Windows Linux"),
                TypingStatus.AUTO_TYPE_ONLY, AssetType.DEVICE, null, List.of("ANY"));
        check(engines, Set.of("NMAP"), Map.of("ad.operatingSystem", "Windows Linux"),
                TypingStatus.NOT_CLASSIFIED, null, null, List.of());
    }

    private static CanonicalRule rule(String id, AssetType type, AssetSubtype subtype,
                                      List<String> required, List<String> any, List<String> forbidden, boolean enabled) {
        return new CanonicalRule(id, type, subtype, 100, required, any, forbidden, enabled);
    }

    private static void check(List<TypingEngine> engines, Set<String> sources, Map<String, String> attributes,
                              TypingStatus status, AssetType type, AssetSubtype subtype, List<String> ids) {
        var context = new AssetTypingContext("edge", sources, Set.of(), attributes, Map.of());
        for (TypingEngine engine : engines) {
            var result = engine.classify(context);
            assertAll(engine.name(), () -> assertEquals(status, result.status()),
                    () -> assertEquals(type, result.type()), () -> assertEquals(subtype, result.subtype()),
                    () -> assertEquals(new TreeSet<>(ids), new TreeSet<>(result.matchedRuleIds())));
        }
    }
}
