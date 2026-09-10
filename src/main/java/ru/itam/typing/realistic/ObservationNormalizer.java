package ru.itam.typing.realistic;

import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;

import java.util.*;

/** Converts source-shaped observations into the normalized context consumed by all engines. */
public final class ObservationNormalizer {

    public AssetTypingContext normalize(RawAssetBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        TreeSet<String> sources = new TreeSet<>();
        TreeSet<String> kinds = new TreeSet<>();
        TreeMap<String, String> attributes = new TreeMap<>();
        TreeMap<String, Long> systemParameters = new TreeMap<>();

        List<SourceObservation> ordered = new ArrayList<>(bundle.observations());
        ordered.sort(Comparator.comparingLong(SourceObservation::observedAtEpochMs)
                .thenComparing(SourceObservation::source)
                .thenComparing(SourceObservation::objectKind));

        long newest = Long.MIN_VALUE;
        for (SourceObservation observation : ordered) {
            String source = observation.source().toUpperCase(Locale.ROOT);
            sources.add(source);
            kinds.add(observation.objectKind());
            newest = Math.max(newest, observation.observedAtEpochMs());
            String prefix = source.toLowerCase(Locale.ROOT) + ".";
            observation.attributes().forEach((key, value) -> attributes.put(prefix + key, value));
        }

        systemParameters.put("observationCount", (long) ordered.size());
        if (newest != Long.MIN_VALUE) systemParameters.put("newestObservationEpochMs", newest);
        return new AssetTypingContext(bundle.assetId(), sources, kinds, attributes, systemParameters);
    }
}
