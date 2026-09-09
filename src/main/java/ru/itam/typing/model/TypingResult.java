package ru.itam.typing.model;

import java.util.List;

public record TypingResult(
        String assetId,
        AssetType type,
        AssetSubtype subtype,
        TypingStatus status,
        List<String> matchedRuleIds) {
}
