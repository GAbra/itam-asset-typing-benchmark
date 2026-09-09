package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.data.DatasetGenerator;
import ru.itam.typing.data.DatasetReader;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.cel.CelTypingEngine;
import ru.itam.typing.engine.dmn.DmnTypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class ThreeEnginesIntegrationTest {
    @TempDir Path temp;

    @Test
    void allThreeEnginesProduceSameGroundTruthForGeneratedData() throws Exception {
        var rules = RuleLoader.load(Path.of("rules/canonical-rules.yaml"));
        var features = new FeatureExtractor();
        var bitset = new BitSetTypingEngine(rules, features);
        var cel = new CelTypingEngine(rules, features);
        var dmn = new DmnTypingEngine(rules, features);

        Path dataset = temp.resolve("sample.jsonl");
        new DatasetGenerator().generate(500, DatasetGenerator.DEFAULT_SEED, dataset);
        final int[] checked = {0};
        new DatasetReader().forEach(dataset, record -> {
            TypingResult b = bitset.classify(record.context());
            TypingResult c = cel.classify(record.context());
            TypingResult d = dmn.classify(record.context());
            assertEquivalent(b, c);
            assertEquivalent(b, d);
            assertEquals(record.expectedType(), b.type(), record.context().assetId());
            assertEquals(record.expectedSubtype(), b.subtype(), record.context().assetId());
            checked[0]++;
        });
        assertEquals(500, checked[0]);
    }

    private static void assertEquivalent(TypingResult a, TypingResult b) {
        assertAll(
                () -> assertEquals(a.type(), b.type()),
                () -> assertEquals(a.subtype(), b.subtype()),
                () -> assertEquals(a.status(), b.status()),
                () -> assertEquals(new TreeSet<>(a.matchedRuleIds()), new TreeSet<>(b.matchedRuleIds()))
        );
    }
}
