package ru.itam.typing.engine.reference;

import ru.itam.typing.engine.TypingEngine;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.*;

/**
 * Deliberately simple, unindexed reference implementation.
 * It does not use MatchResolver and exists to validate optimized adapters, not for performance claims.
 */
public final class ReferenceTypingEngine implements TypingEngine {
    private final FeatureExtractor featureExtractor;
    private final List<CanonicalRule> rules;

    public ReferenceTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor) {
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor");
        this.rules = ruleSet.rules().stream().filter(CanonicalRule::enabled).toList();
    }

    @Override
    public String name() {
        return "REFERENCE_LINEAR";
    }

    @Override
    public TypingResult classify(AssetTypingContext context) {
        Map<String, Boolean> features = featureExtractor.extract(context);
        LinkedHashMap<String, RuleMatch> matches = new LinkedHashMap<>();
        for (CanonicalRule rule : rules) {
            if (matches(features, rule)) {
                matches.putIfAbsent(rule.ruleId(), new RuleMatch(
                        rule.ruleId(), rule.targetType(), rule.targetSubtype(), rule.priority()));
            }
        }
        return resolveIndependently(context.assetId(), new ArrayList<>(matches.values()));
    }

    static boolean matches(Map<String, Boolean> features, CanonicalRule rule) {
        for (String required : rule.required()) {
            if (!Boolean.TRUE.equals(features.get(required))) return false;
        }
        for (String forbidden : rule.forbidden()) {
            if (Boolean.TRUE.equals(features.get(forbidden))) return false;
        }
        if (!rule.any().isEmpty()) {
            boolean any = false;
            for (String candidate : rule.any()) {
                if (Boolean.TRUE.equals(features.get(candidate))) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        return true;
    }

    private static TypingResult resolveIndependently(String assetId, List<RuleMatch> matches) {
        if (matches.isEmpty()) {
            return new TypingResult(assetId, null, null, TypingStatus.NOT_CLASSIFIED, List.of());
        }

        int maxPriority = matches.stream().mapToInt(RuleMatch::priority).max().orElseThrow();
        List<RuleMatch> winners = matches.stream()
                .filter(m -> m.priority() == maxPriority)
                .sorted(Comparator.comparing(RuleMatch::ruleId))
                .toList();

        LinkedHashSet<AssetType> types = new LinkedHashSet<>();
        for (RuleMatch winner : winners) types.add(winner.targetType());
        if (types.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType type = types.iterator().next();
        LinkedHashSet<AssetSubtype> subtypes = new LinkedHashSet<>();
        for (RuleMatch winner : winners) {
            if (winner.targetSubtype() != null) subtypes.add(winner.targetSubtype());
        }
        if (subtypes.size() > 1) {
            return new TypingResult(assetId, type, null, TypingStatus.SUBTYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetSubtype subtype = subtypes.isEmpty() ? null : subtypes.iterator().next();
        return new TypingResult(assetId, type, subtype,
                subtype == null ? TypingStatus.AUTO_TYPE_ONLY : TypingStatus.AUTO,
                winners.stream().map(RuleMatch::ruleId).toList());
    }
}
