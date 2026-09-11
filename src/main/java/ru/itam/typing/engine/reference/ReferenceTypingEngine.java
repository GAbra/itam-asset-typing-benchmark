package ru.itam.typing.engine.reference;

import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.*;

/**
 * Deliberately simple, unindexed reference implementation.
 * It does not use MatchResolver and exists to validate optimized adapters, not for performance claims.
 */
public final class ReferenceTypingEngine implements FeatureMapTypingEngine {
    private final FeatureExtractor featureExtractor;
    private final ResolutionPolicy resolutionPolicy;
    private final List<CanonicalRule> rules;

    public ReferenceTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor) {
        this(ruleSet, featureExtractor, ResolutionPolicy.LEGACY_MAX_PRIORITY);
    }

    public ReferenceTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor, ResolutionPolicy resolutionPolicy) {
        this.featureExtractor = Objects.requireNonNull(featureExtractor, "featureExtractor");
        this.resolutionPolicy = Objects.requireNonNull(resolutionPolicy, "resolutionPolicy");
        this.rules = ruleSet.rules().stream().filter(CanonicalRule::enabled).toList();
    }

    @Override
    public String name() {
        return "REFERENCE_LINEAR";
    }

    @Override
    public TypingResult classify(AssetTypingContext context) {
        return classifyFeatures(context.assetId(), featureExtractor.extract(context));
    }

    @Override
    public TypingResult classifyFeatures(String assetId, Map<String, Boolean> features) {
        LinkedHashMap<String, RuleMatch> matches = new LinkedHashMap<>();
        for (CanonicalRule rule : rules) {
            if (matches(features, rule)) {
                matches.putIfAbsent(rule.ruleId(), new RuleMatch(
                        rule.ruleId(), rule.targetType(), rule.targetSubtype(), rule.priority()));
            }
        }
        return resolveIndependently(assetId, new ArrayList<>(matches.values()), resolutionPolicy);
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

    private static TypingResult resolveIndependently(
            String assetId, List<RuleMatch> matches, ResolutionPolicy policy) {
        if (matches.isEmpty()) {
            return new TypingResult(assetId, null, null, TypingStatus.NOT_CLASSIFIED, List.of());
        }

        int maxPriority = matches.stream().mapToInt(RuleMatch::priority).max().orElseThrow();
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
        for (RuleMatch match : conflictEvidence) evidenceTypes.add(match.targetType());
        if (evidenceTypes.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    conflictEvidence.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType evidenceType = evidenceTypes.iterator().next();
        LinkedHashSet<AssetSubtype> evidenceSubtypes = new LinkedHashSet<>();
        for (RuleMatch match : conflictEvidence) {
            if (match.targetType() == evidenceType && match.targetSubtype() != null) {
                evidenceSubtypes.add(match.targetSubtype());
            }
        }
        if (evidenceSubtypes.size() > 1) {
            return new TypingResult(assetId, evidenceType, null, TypingStatus.SUBTYPE_CONFLICT,
                    conflictEvidence.stream().map(RuleMatch::ruleId).toList());
        }

        LinkedHashSet<AssetType> winnerTypes = new LinkedHashSet<>();
        for (RuleMatch winner : winners) winnerTypes.add(winner.targetType());
        if (winnerTypes.size() > 1) {
            return new TypingResult(assetId, null, null, TypingStatus.TYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetType type = winnerTypes.iterator().next();
        LinkedHashSet<AssetSubtype> winnerSubtypes = new LinkedHashSet<>();
        for (RuleMatch winner : winners) {
            if (winner.targetSubtype() != null) winnerSubtypes.add(winner.targetSubtype());
        }
        if (winnerSubtypes.size() > 1) {
            return new TypingResult(assetId, type, null, TypingStatus.SUBTYPE_CONFLICT,
                    winners.stream().map(RuleMatch::ruleId).toList());
        }

        AssetSubtype subtype = winnerSubtypes.isEmpty() ? null : winnerSubtypes.iterator().next();
        return new TypingResult(assetId, type, subtype,
                subtype == null ? TypingStatus.AUTO_TYPE_ONLY : TypingStatus.AUTO,
                winners.stream().map(RuleMatch::ruleId).toList());
    }
}
