package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.SourceSchemaRegistry;
import ru.itam.typing.realistic.model.RawAssetBundle;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSchemaRegistryTest {
    @TempDir Path temp;

    @Test
    void generatedRawObservationsStayInsideAuditedSourceFieldAllowLists() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        new RealisticWorkloadGenerator().generate(1_000, 110022L, raw, truth, NoiseProfile.stressDefault());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        long[] observations = {0};
        try (var lines = Files.lines(raw)) {
            lines.forEach(line -> {
                try {
                    RawAssetBundle bundle = json.readValue(line, RawAssetBundle.class);
                    bundle.observations().forEach(observation -> {
                        SourceSchemaRegistry.validate(observation);
                        observations[0]++;
                    });
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(observations[0] > 1_000, "multi-source workload should contain more observations than assets");
    }
}
