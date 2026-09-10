package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.DatasetRecord;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.ObservationNormalizer;
import ru.itam.typing.realistic.RealisticDatasetMaterializer;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RealisticWorkloadV2Test {
    @TempDir Path temp;

    @Test
    void generationIsDeterministicAndRawFileContainsNoGroundTruth() throws Exception {
        Path raw1 = temp.resolve("raw1.jsonl");
        Path truth1 = temp.resolve("truth1.jsonl");
        Path raw2 = temp.resolve("raw2.jsonl");
        Path truth2 = temp.resolve("truth2.jsonl");
        var generator = new RealisticWorkloadGenerator();

        var s1 = generator.generate(500, 424242L, raw1, truth1, NoiseProfile.stressDefault());
        generator.generate(500, 424242L, raw2, truth2, NoiseProfile.stressDefault());

        assertArrayEquals(Files.readAllBytes(raw1), Files.readAllBytes(raw2));
        assertArrayEquals(Files.readAllBytes(truth1), Files.readAllBytes(truth2));
        String raw = Files.readString(raw1);
        assertFalse(raw.contains("expectedType"));
        assertFalse(raw.contains("expectedSubtype"));
        assertFalse(raw.contains("targetType"));
        assertTrue(s1.noiseEventCounts().values().stream().mapToLong(Long::longValue).sum() > 0);
    }

    @Test
    void normalizerPrefixesRawFieldsAndNewestObservationWins() {
        SourceObservation oldObs = new SourceObservation("KSC", "ksc:host", 100L,
                Map.of("KLHST_WKS_OS_NAME", "Windows 11 Enterprise"));
        SourceObservation newObs = new SourceObservation("KSC", "ksc:host", 200L,
                Map.of("KLHST_WKS_OS_NAME", "Windows Server 2022 Datacenter"));
        RawAssetBundle bundle = new RawAssetBundle("asset-x", List.of(newObs, oldObs));

        var ctx = new ObservationNormalizer().normalize(bundle);
        assertEquals("Windows Server 2022 Datacenter", ctx.attributes().get("ksc.KLHST_WKS_OS_NAME"));
        assertEquals(Set.of("KSC"), ctx.sources());
        assertEquals(Set.of("ksc:host"), ctx.sourceObjectKinds());
        assertEquals(2L, ctx.systemParameters().get("observationCount"));
        assertEquals(200L, ctx.systemParameters().get("newestObservationEpochMs"));
    }

    @Test
    void materializedStressWorkloadIsNotAReverseEncodingOfCurrentRules() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        Path normalized = temp.resolve("normalized.jsonl");
        new RealisticWorkloadGenerator().generate(2_000, 20260910L, raw, truth, NoiseProfile.stressDefault());
        new RealisticDatasetMaterializer().materialize(raw, truth, normalized);

        FeatureExtractor extractor = new FeatureExtractor();
        Set<String> featureVectors = new HashSet<>();
        BitSetTypingEngine engine = new BitSetTypingEngine(RuleLoader.load(Path.of("rules/canonical-rules.yaml")), extractor);
        long[] checkedAndMismatches = new long[2];

        new DatasetReader().forEach(normalized, record -> {
            featureVectors.add(fingerprint(extractor.extract(record.context())));
            checkedAndMismatches[0]++;
            var result = engine.classify(record.context());
            if (result.type() != record.expectedType() || result.subtype() != record.expectedSubtype()) {
                checkedAndMismatches[1]++;
            }
        });

        assertEquals(2_000L, checkedAndMismatches[0]);
        assertTrue(featureVectors.size() > 20, "noise should produce materially more than the old tiny feature-state space");
        assertTrue(checkedAndMismatches[1] > 0,
                "stress workload must expose current rule errors; zero mismatches would suggest target leakage");
    }

    private static String fingerprint(Map<String, Boolean> features) {
        StringBuilder out = new StringBuilder();
        features.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.append(Boolean.TRUE.equals(e.getValue()) ? '1' : '0'));
        return out.toString();
    }
}
