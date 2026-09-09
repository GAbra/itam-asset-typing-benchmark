package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BitSetTypingEngineTest {
    @Test
    void requiredAndForbiddenMasksWork() {
        RuleSet rules = new RuleSet("test", List.of(
                new CanonicalRule("WS", AssetType.DEVICE, AssetSubtype.WORKSTATION, 100,
                        List.of("SRC_AD", "OBJ_AD_COMPUTER", "OS_WINDOWS"), List.of(), List.of("OS_SERVER"), true),
                new CanonicalRule("SRV", AssetType.DEVICE, AssetSubtype.SERVER, 200,
                        List.of("SRC_AD", "OBJ_AD_COMPUTER", "OS_WINDOWS", "OS_SERVER"), List.of(), List.of(), true)));
        var engine = new BitSetTypingEngine(rules, new FeatureExtractor());

        var ws = new AssetTypingContext("ws", Set.of("AD"), Set.of("ad:computer"),
                Map.of("ad.operatingSystem", "Windows 11 Enterprise"), Map.of());
        var srv = new AssetTypingContext("srv", Set.of("AD"), Set.of("ad:computer"),
                Map.of("ad.operatingSystem", "Windows Server 2022"), Map.of());

        assertEquals(AssetSubtype.WORKSTATION, engine.classify(ws).subtype());
        assertEquals(AssetSubtype.SERVER, engine.classify(srv).subtype());
    }

    @Test
    void anyMaskWorks() {
        RuleSet rules = new RuleSet("test", List.of(
                new CanonicalRule("ANY", AssetType.DEVICE, null, 10,
                        List.of("SRC_NMAP"), List.of("NMAP_NETWORK_DEVICE", "NMAP_GENERAL_PURPOSE"), List.of(), true)));
        var engine = new BitSetTypingEngine(rules, new FeatureExtractor());
        var ctx = new AssetTypingContext("n", Set.of("NMAP"), Set.of("nmap:host"),
                Map.of("nmap.deviceType", "router"), Map.of());
        assertEquals(TypingStatus.AUTO_TYPE_ONLY, engine.classify(ctx).status());
    }
}
