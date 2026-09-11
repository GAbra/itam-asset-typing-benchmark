package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.realistic.model.SourceObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parser for a typed KSC Open API pChunk/KLCSP_ITERATOR_ARRAY host payload. */
public final class KscTypedChunkAdapter {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final long FIXTURE_EPOCH_MS = Instant.parse("2026-09-10T00:00:00Z").toEpochMilli();

    private KscTypedChunkAdapter() {}

    public static List<SourceObservation> readHosts(Path path) throws IOException {
        JsonNode root = JSON.readTree(path.toFile());
        JsonNode chunk = root.has("pChunk") ? root.get("pChunk") : root;
        JsonNode rows = chunk == null ? null : chunk.get("KLCSP_ITERATOR_ARRAY");
        if (rows == null || !rows.isArray()) {
            throw new IllegalArgumentException("KSC typed fixture must contain pChunk.KLCSP_ITERATOR_ARRAY[]");
        }
        List<SourceObservation> out = new ArrayList<>();
        for (JsonNode row : rows) {
            Map<String, String> attrs = new LinkedHashMap<>();
            for (String field : SourceSchemaRegistry.documentedFields().get("ksc:host")) {
                JsonNode value = row.get(field);
                if (value == null || value.isNull()) continue;
                JsonNode unwrapped = unwrapTypedParam(value, field);
                if (unwrapped != null && !unwrapped.isContainerNode()) attrs.put(field, unwrapped.asText());
            }
            SourceObservation observation = new SourceObservation("KSC", "ksc:host", FIXTURE_EPOCH_MS, attrs);
            SourceSchemaRegistry.validate(observation);
            out.add(observation);
        }
        return List.copyOf(out);
    }

    static JsonNode unwrapTypedParam(JsonNode node, String field) {
        if (!node.isObject()) return node;
        JsonNode type = node.get("type");
        JsonNode value = node.get("value");
        if (type == null || value == null) {
            throw new IllegalArgumentException("KSC typed param for " + field + " must contain type and value");
        }
        String t = type.asText();
        if (!("long".equals(t) || "int".equals(t) || "string".equals(t) || "bool".equals(t) || "boolean".equals(t))) {
            throw new IllegalArgumentException("Unsupported KSC typed param type for " + field + ": " + t);
        }
        return value;
    }
}
