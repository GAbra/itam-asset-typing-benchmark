package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.reference.ReferenceTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.RealisticDatasetMaterializer;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReferenceEngineEquivalenceTest {
    @TempDir Path temp;

    @Test
    void optimizedBitSetMatchesIndependentLinearReferenceOnRealisticStressWorkload() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        Path normalized = temp.resolve("normalized.jsonl");
        new RealisticWorkloadGenerator().generate(2_000, 770011L, raw, truth, NoiseProfile.stressDefault());
        new RealisticDatasetMaterializer().materialize(raw, truth, normalized);

        var ruleSet = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        var extractor = new FeatureExtractor();
        var reference = new ReferenceTypingEngine(ruleSet, extractor);
        var optimized = new BitSetTypingEngine(ruleSet, extractor);
        long[] checked = {0};

        new DatasetReader().forEach(normalized, record -> {
            assertEquals(reference.classify(record.context()), optimized.classify(record.context()),
                    "optimized engine diverged for " + record.context().assetId());
            checked[0]++;
        });
        assertEquals(2_000L, checked[0]);
    }
}
