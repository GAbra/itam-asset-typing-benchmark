package ru.itam.typing.realistic.model;

import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;

import java.util.Objects;

/** Independent label sidecar for a latent asset. */
public record GroundTruthLabel(
        String assetId,
        AssetType type,
        AssetSubtype subtype,
        String profile) {

    public GroundTruthLabel {
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(profile, "profile");
        if (assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        if (profile.isBlank()) throw new IllegalArgumentException("profile must not be blank");
    }
}
