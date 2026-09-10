package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.model.DatasetRecord;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Joins raw observations with the independent truth sidecar only after normalization. */
public final class RealisticDatasetMaterializer {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final ObservationNormalizer normalizer = new ObservationNormalizer();

    public MaterializationSummary materialize(Path rawPath, Path truthPath, Path outPath) throws IOException {
        Files.createDirectories(outPath.toAbsolutePath().getParent());
        long count = 0;
        try (BufferedReader raw = Files.newBufferedReader(rawPath, StandardCharsets.UTF_8);
             BufferedReader truth = Files.newBufferedReader(truthPath, StandardCharsets.UTF_8);
             BufferedWriter out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8)) {
            while (true) {
                String rawLine = raw.readLine();
                String truthLine = truth.readLine();
                if (rawLine == null || truthLine == null) {
                    if (rawLine != null || truthLine != null) {
                        throw new IllegalStateException("raw/truth line count mismatch");
                    }
                    break;
                }
                RawAssetBundle bundle = json.readValue(rawLine, RawAssetBundle.class);
                GroundTruthLabel label = json.readValue(truthLine, GroundTruthLabel.class);
                if (!bundle.assetId().equals(label.assetId())) {
                    throw new IllegalStateException("raw/truth asset order mismatch: " + bundle.assetId() + " != " + label.assetId());
                }
                DatasetRecord record = new DatasetRecord(normalizer.normalize(bundle), label.type(), label.subtype());
                out.write(json.writeValueAsString(record));
                out.write('\n');
                count++;
            }
        }
        return new MaterializationSummary(count, rawPath.toString(), truthPath.toString(), outPath.toString());
    }

    public record MaterializationSummary(long count, String rawInput, String truthInput, String normalizedOutput) {}
}
