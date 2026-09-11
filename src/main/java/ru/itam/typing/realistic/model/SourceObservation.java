package ru.itam.typing.realistic.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One raw observation emitted by a source system before ITAM normalization. */
public record SourceObservation(
        String source,
        String objectKind,
        long observedAtEpochMs,
        Map<String, String> attributes) {

    public SourceObservation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(objectKind, "objectKind");
        Objects.requireNonNull(attributes, "attributes");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (objectKind.isBlank()) throw new IllegalArgumentException("objectKind must not be blank");
        attributes = Collections.unmodifiableMap(new TreeMap<>(attributes));
    }
}
