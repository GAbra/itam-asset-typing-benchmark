package ru.itam.typing.model;

import java.util.Map;
import java.util.Set;

public record AssetTypingContext(
        String assetId,
        Set<String> sources,
        Set<String> sourceObjectKinds,
        Map<String, String> attributes,
        Map<String, Long> systemParameters) {
}
