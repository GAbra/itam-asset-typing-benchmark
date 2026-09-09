package ru.itam.typing.engine.common;

import ru.itam.typing.model.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class MatchResolver {
    private MatchResolver() {}

    public static TypingResult resolve(String assetId, List<RuleMatch> rawMatches) {
        if (rawMatches.isEmpty()) {
            return new TypingResult(assetId, null, null, TypingStatus.NOT_CLASSIFIED, List.of());
        }

        List<RuleMatch> matches = rawMatches.stream()
                .collect(java.util.stream.Collectors.toMap(
                        RuleMatch::ruleId,
                        m -> m,
                        (a, b) -> a,
                        java.util.LinkedHashMap::new))
                .values().stream().toList();

        int maxPriority = matches.stream().mapToInt(RuleMatch::priority).max().orElse(Integer.MIN_VALUE);
        List<RuleMatch> winners = matches.stream()
                .filter(m -> m.priority() == maxPriority)
                .sorted(Comparator.comparing(RuleMatch::ruleId))
                .toList();

        LinkedHashSet<AssetType> types = new LinkedHashSet<>();
        winners.forEach(m -> types.add(m.targetType()));
        if (types.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType type = types.iterator().next();
        LinkedHashSet<AssetSubtype> subtypes = new LinkedHashSet<>();
        winners.stream().map(RuleMatch::targetSubtype).filter(java.util.Objects::nonNull).forEach(subtypes::add);
        if (subtypes.size() > 1) {
            return new TypingResult(assetId, type, null, TypingStatus.SUBTYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetSubtype subtype = subtypes.isEmpty() ? null : subtypes.iterator().next();
        TypingStatus status = subtype == null ? TypingStatus.AUTO_TYPE_ONLY : TypingStatus.AUTO;
        return new TypingResult(assetId, type, subtype, status,
                winners.stream().map(RuleMatch::ruleId).toList());
    }
}
