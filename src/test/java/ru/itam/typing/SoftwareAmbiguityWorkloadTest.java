package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.ObservationNormalizer;
import ru.itam.typing.realistic.ProfileDistribution;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.SoftwareAmbiguityInjector;
import ru.itam.typing.realistic.SoftwareAmbiguityProfile;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SoftwareAmbiguityWorkloadTest {
    @TempDir Path temp;

    @Test
    void severeSoftwareAmbiguityCreatesBothFalseNegativeAndFalsePositiveHintCasesDeterministically() throws Exception {
        Path raw = temp.resolve("base.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        Path injected1 = temp.resolve("injected-1.jsonl");
        Path injected2 = temp.resolve("injected-2.jsonl");

        new RealisticWorkloadGenerator().generate(5_000, 20260914L, raw, truth,
                NoiseProfile.clean(), ProfileDistribution.softwareOnly());
        SoftwareAmbiguityInjector injector = new SoftwareAmbiguityInjector();
        var report1 = injector.inject(raw, truth, injected1, 20260914L, SoftwareAmbiguityProfile.severe());
        var report2 = injector.inject(raw, truth, injected2, 20260914L, SoftwareAmbiguityProfile.severe());

        assertEquals(-1L, Files.mismatch(injected1, injected2), "same seed must produce byte-identical injected raw data");
        assertEquals(report1, report2);
        assertEquals(5_000, report1.softwareAssets());
        assertTrue(report1.eventCounts().getOrDefault("DECEPTIVE_APPLICATION_RECORD", 0L) > 0);
        assertTrue(report1.eventCounts().getOrDefault("MISSING_SOFTWARE_IDENTITY", 0L) > 0);
        assertTrue(report1.eventCounts().getOrDefault("SECURITY_COMPONENT_RECORD", 0L) > 0);
        assertTrue(report1.eventCounts().getOrDefault("NEUTRAL_SECURITY_RECORD", 0L) > 0);

        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        ObservationNormalizer normalizer = new ObservationNormalizer();
        FeatureExtractor extractor = new FeatureExtractor();
        long securityWithoutHint = 0;
        long applicationWithHint = 0;

        try (var rawLines = Files.lines(injected1); var truthLines = Files.lines(truth)) {
            var rawIterator = rawLines.iterator();
            var truthIterator = truthLines.iterator();
            while (rawIterator.hasNext() && truthIterator.hasNext()) {
                RawAssetBundle bundle = json.readValue(rawIterator.next(), RawAssetBundle.class);
                GroundTruthLabel label = json.readValue(truthIterator.next(), GroundTruthLabel.class);
                boolean hint = extractor.extract(normalizer.normalize(bundle)).get("SECURITY_SOFTWARE_HINT");
                if (label.subtype() == AssetSubtype.SECURITY_SOFTWARE && !hint) securityWithoutHint++;
                if (label.subtype() == AssetSubtype.APPLICATION_SOFTWARE && hint) applicationWithHint++;
            }
            assertEquals(rawIterator.hasNext(), truthIterator.hasNext(), "raw/truth must remain aligned");
        }

        assertTrue(securityWithoutHint > 100, "severe profile must create many security-software false-negative hint cases");
        assertTrue(applicationWithHint > 100, "severe profile must create many application false-positive hint cases");
    }
}
