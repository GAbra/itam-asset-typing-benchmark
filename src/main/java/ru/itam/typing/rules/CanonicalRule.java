package ru.itam.typing.rules;

import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetType;

import java.util.List;

public record CanonicalRule(
        String ruleId,
        AssetType targetType,
        AssetSubtype targetSubtype,
        int priority,
        List<String> required,
        List<String> any,
        List<String> forbidden,
        boolean enabled) {

    public CanonicalRule {
        required = required == null ? List.of() : List.copyOf(required);
        any = any == null ? List.of() : List.copyOf(any);
        forbidden = forbidden == null ? List.of() : List.copyOf(forbidden);
    }
}
