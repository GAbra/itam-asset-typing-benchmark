package ru.itam.typing.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.model.DatasetRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class DatasetReader {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public void forEach(Path path, Consumer<DatasetRecord> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) consumer.accept(json.readValue(line, DatasetRecord.class));
            }
        }
    }

    public List<DatasetRecord> first(Path path, int limit) throws IOException {
        List<DatasetRecord> result = new ArrayList<>(Math.max(0, limit));
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while (result.size() < limit && (line = reader.readLine()) != null) {
                if (!line.isBlank()) result.add(json.readValue(line, DatasetRecord.class));
            }
        }
        return result;
    }
}
