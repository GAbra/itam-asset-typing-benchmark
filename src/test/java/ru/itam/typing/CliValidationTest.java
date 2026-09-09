package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.cli.Main;
import ru.itam.typing.data.DatasetGenerator;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliValidationTest {
    @TempDir Path temp;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void emptyDatasetCannotPassVerificationOrBenchmark() throws Exception {
        Path data = Files.writeString(temp.resolve("empty.jsonl"), "\n");
        Path out = temp.resolve("verify.json");
        assertEquals(2, Main.execute(new String[]{"verify", "--data", data.toString(), "--out", out.toString()}));
        assertEquals("FAIL", json.readTree(out.toFile()).path("result").asText());
        assertThrows(IllegalArgumentException.class, () -> Main.execute(new String[]{"benchmark", "--data", data.toString()}));
    }

    @Test
    void invalidLimitsAreRejectedBeforeLoadingEngines() {
        for (String option : new String[]{"--batch", "--runs", "--max"}) {
            assertThrows(IllegalArgumentException.class, () -> Main.execute(
                    new String[]{"benchmark", "--data", "unused.jsonl", option, "0"}));
        }
        assertThrows(IllegalArgumentException.class, () -> Main.execute(
                new String[]{"benchmark", "--data", "unused.jsonl", "--warmup", "-1"}));
        assertThrows(IllegalArgumentException.class, () -> Main.execute(
                new String[]{"verify", "--data", "unused.jsonl", "--max", "0"}));
    }

    @Test
    void nullableExpectedSubtypeProducesFailureReport() throws Exception {
        Path data = Files.writeString(temp.resolve("wrong.jsonl"), """
                {"context":{"assetId":"a","sources":[],"sourceObjectKinds":[],"attributes":{},"systemParameters":{}},"expectedType":"DEVICE","expectedSubtype":null}
                """);
        Path out = temp.resolve("wrong.json");
        assertEquals(2, Main.execute(new String[]{"verify", "--data", data.toString(), "--out", out.toString()}));
        var report = json.readTree(out.toFile());
        assertEquals(1, report.path("groundTruthMismatches").asInt());
        assertEquals(1, report.path("groundTruthChecked").asInt());
        assertTrue(report.path("samples").get(0).path("expectedSubtype").isNull());
    }

    @Test
    void benchmarkRecordsProvenanceAndRespectsWarmupLimit() throws Exception {
        Path data = temp.resolve("data.jsonl");
        new DatasetGenerator().generate(12, DatasetGenerator.DEFAULT_SEED, data);
        Path out = temp.resolve("benchmark.json");
        assertEquals(0, Main.execute(new String[]{"benchmark", "--data", data.toString(), "--max", "3",
                "--warmup", "0", "--runs", "1", "--batch", "10", "--out", out.toString()}));
        var report = json.readTree(out.toFile());
        assertEquals(3, report.path("warmupRecordsPerIteration").asInt());
        assertEquals(64, report.path("provenance").path("datasetSha256").asText().length());
        assertEquals(14, report.path("provenance").path("enabledRules").asInt());
        assertFalse(report.path("environment").path("java.version").asText().isEmpty());
        for (var run : report.path("runs")) assertEquals(3, run.path("count").asInt());
    }
}
