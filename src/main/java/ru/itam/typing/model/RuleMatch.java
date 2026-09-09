package ru.itam.typing.model;

public record RuleMatch(
        String ruleId,
        AssetType targetType,
        AssetSubtype targetSubtype,
        int priority) {
}
