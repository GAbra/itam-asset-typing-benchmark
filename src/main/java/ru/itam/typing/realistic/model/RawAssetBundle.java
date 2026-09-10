package ru.itam.typing.realistic.model;

import java.util.List;
import java.util.Objects;

/** Raw observations for one latent asset. No expected type/subtype is stored here. */
public record RawAssetBundle(
        String assetId,
        List<SourceObservation> observations) {

    public RawAssetBundle {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(observations, "observations");
        if (assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        if (observations.isEmpty()) throw new IllegalArgumentException("observations must not be empty");
        observations = List.copyOf(observations);
    }
}
