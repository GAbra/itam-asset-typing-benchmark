package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.TypingStatus;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SoftwareConservativePolicyTest {

    @Test
    void strongSecurityEvidenceRemainsAutomatic() throws Exception {
        var ctx = software("Kaspersky Endpoint Security for Windows", "AO Kaspersky Lab");
        var features = new FeatureExtractor().extract(ctx);
        assertTrue(features.get("SECURITY_SOFTWARE_STRONG_HINT"));

        var result = candidate().classify(ctx);
        assertEquals(AssetType.SOFTWARE, result.type());
        assertEquals(AssetSubtype.SECURITY_SOFTWARE, result.subtype());
        assertEquals(TypingStatus.AUTO, result.status());
    }

    @Test
    void ordinaryApplicationRemainsAutomatic() throws Exception {
        var ctx = software("7-Zip", "Igor Pavlov");
        var features = new FeatureExtractor().extract(ctx);
        assertTrue(features.get("APPLICATION_SOFTWARE_HINT"));

        var result = candidate().classify(ctx);
        assertEquals(AssetSubtype.APPLICATION_SOFTWARE, result.subtype());
        assertEquals(TypingStatus.AUTO, result.status());
    }

    @Test
    void securityLookingApplicationAbstainsInsteadOfFalsePositive() throws Exception {
        var ctx = software("Endpoint Security Assessment Console", "Contoso IT Tools");
        var features = new FeatureExtractor().extract(ctx);
        assertTrue(features.get("SECURITY_SOFTWARE_NAME_HINT"));
        assertFalse(features.get("SECURITY_SOFTWARE_PUBLISHER_HINT"));
        assertFalse(features.get("SECURITY_SOFTWARE_STRONG_HINT"));
        assertFalse(features.get("APPLICATION_SOFTWARE_HINT"));

        var result = candidate().classify(ctx);
        assertEquals(AssetType.SOFTWARE, result.type());
        assertNull(result.subtype());
        assertEquals(TypingStatus.AUTO_TYPE_ONLY, result.status());
    }

    @Test
    void neutralComponentAndMissingIdentityAbstain() throws Exception {
        var component = software("Host Management Agent", "Enterprise Software Services Ltd");
        var componentFeatures = new FeatureExtractor().extract(component);
        assertTrue(componentFeatures.get("SOFTWARE_COMPONENT_HINT"));
        assertFalse(componentFeatures.get("APPLICATION_SOFTWARE_HINT"));
        assertEquals(TypingStatus.AUTO_TYPE_ONLY, candidate().classify(component).status());

        var missing = software(null, null);
        var missingFeatures = new FeatureExtractor().extract(missing);
        assertTrue(missingFeatures.get("SOFTWARE_IDENTITY_MISSING"));
        assertEquals(TypingStatus.AUTO_TYPE_ONLY, candidate().classify(missing).status());
    }

    @Test
    void canonicalLegacySecurityHintSemanticsAreStillAvailable() {
        var ctx = software("Windows Defender Log Analyzer", "Blue River Software");
        var features = new FeatureExtractor().extract(ctx);
        assertTrue(features.get("SECURITY_SOFTWARE_HINT"), "legacy canonical rules must keep historical hint semantics");
        assertTrue(features.get("SECURITY_SOFTWARE_NAME_HINT"));
        assertFalse(features.get("SECURITY_SOFTWARE_STRONG_HINT"));
    }

    private static BitSetTypingEngine candidate() throws Exception {
        return new BitSetTypingEngine(
                RuleLoader.load(Path.of("rules/software-conservative-v2.yaml")),
                new FeatureExtractor(), ResolutionPolicy.CONSERVATIVE_80);
    }

    private static AssetTypingContext software(String displayName, String publisher) {
        Map<String, String> attributes = new LinkedHashMap<>();
        if (displayName != null) attributes.put("ksc.DisplayName", displayName);
        if (publisher != null) attributes.put("ksc.Publisher", publisher);
        return new AssetTypingContext(
                "software-test",
                Set.of("KSC"),
                Set.of("ksc:software_inventory_application"),
                attributes,
                Map.of());
    }
}
