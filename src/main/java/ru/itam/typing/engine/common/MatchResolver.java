package ru.itam.typing.engine.common;

import ru.itam.typing.model.*;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class MatchResolver {
    private MatchResolver() {}

    public static TypingResult resolve(String assetId, List<RuleMatch> rawMatches) {
        return resolve(assetId, rawMatches, ResolutionPolicy.LEGACY_MAX_PRIORITY);
    }

    public static TypingResult resolve(String assetId, List<RuleMatch> rawMatches, ResolutionPolicy policy) {
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

        long cutoff = (long) maxPriority - policy.conflictPriorityWindow();
        List<RuleMatch> conflictEvidence = matches.stream()
                .filter(m -> (long) m.priority() >= cutoff)
                .sorted(Comparator.comparingInt(RuleMatch::priority).reversed()
                        .thenComparing(RuleMatch::ruleId))
                .toList();

        LinkedHashSet<AssetType> evidenceTypes = new LinkedHashSet<>();
        conflictEvidence.forEach(m -> evidenceTypes.add(m.targetType()));
        if (evidenceTypes.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    conflictEvidence.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType evidenceType = evidenceTypes.iterator().next();
        LinkedHashSet<AssetSubtype> evidenceSubtypes = new LinkedHashSet<>();
        conflictEvidence.stream()
                .filter(m -> m.targetType() == evidenceType)
                .map(RuleMatch::targetSubtype)
                .filter(java.util.Objects::nonNull)
                .forEach(evidenceSubtypes::add);
        if (evidenceSubtypes.size() > 1) {
            return new TypingResult(assetId, evidenceType, null, TypingStatus.SUBTYPE_CONFLICT,
                    conflictEvidence.stream().map(RuleMatch::ruleId).toList());
        }

        LinkedHashSet<AssetType> winnerTypes = new LinkedHashSet<>();
        winners.forEach(m -> winnerTypes.add(m.targetType()));
        if (winnerTypes.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType type = winnerTypes.iterator().next();
        LinkedHashSet<AssetSubtype> winnerSubtypes = new LinkedHashSet<>();
        winners.stream().map(RuleMatch::targetSubtype).filter(java.util.Objects::nonNull).forEach(winnerSubtypes::add);
        if (winnerSubtypes.size() > 1) {
            return new TypingResult(assetId, type, null, TypingStatus.SUBTYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetSubtype subtype = winnerSubtypes.isEmpty() ? null : winnerSubtypes.iterator().next();
        TypingStatus status = subtype == null ? TypingStatus.AUTO_TYPE_ONLY : TypingStatus.AUTO;
        return new TypingResult(assetId, type, subtype, status,
                winners.stream().map(RuleMatch::ruleId).toList());
    }
}
