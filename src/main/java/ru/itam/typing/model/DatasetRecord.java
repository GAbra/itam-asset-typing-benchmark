package ru.itam.typing.model;

public record DatasetRecord(
        AssetTypingContext context,
        AssetType expectedType,
        AssetSubtype expectedSubtype) {
}
