package ru.itam.typing.engine.bitset;

import ru.itam.typing.engine.TypingEngine;
import ru.itam.typing.engine.common.MatchResolver;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.RuleMatch;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.*;

public final class BitSetTypingEngine implements TypingEngine {
    private final FeatureExtractor featureExtractor;
    private final Map<String, Integer> featureIds;
    private final List<CompiledRule> compiledRules;
    private final Map<Integer, int[]> anchorIndex;
    private final int[] unanchoredRuleIds;

    public BitSetTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
        SortedSet<String> allFeatures = new TreeSet<>();
        for (CanonicalRule rule : ruleSet.rules()) {
            if (!rule.enabled()) continue;
            allFeatures.addAll(rule.required());
            allFeatures.addAll(rule.any());
            allFeatures.addAll(rule.forbidden());
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        int id = 0;
        for (String feature : allFeatures) ids.put(feature, id++);
        this.featureIds = Collections.unmodifiableMap(ids);

        List<CompiledRule> compiled = new ArrayList<>();
        Map<Integer, List<Integer>> indexBuilder = new HashMap<>();
        List<Integer> unanchored = new ArrayList<>();
        for (CanonicalRule rule : ruleSet.rules()) {
            if (!rule.enabled()) continue;
            BitSet required = mask(rule.required());
            BitSet any = mask(rule.any());
            BitSet forbidden = mask(rule.forbidden());
            Integer anchor = chooseAnchor(rule.required());
            int compiledIndex = compiled.size();
            compiled.add(new CompiledRule(rule, required, any, forbidden, anchor));
            if (anchor == null) {
                unanchored.add(compiledIndex);
            } else {
                indexBuilder.computeIfAbsent(anchor, k -> new ArrayList<>()).add(compiledIndex);
            }
        }
        this.compiledRules = List.copyOf(compiled);
        Map<Integer, int[]> finalIndex = new HashMap<>();
        indexBuilder.forEach((k, v) -> finalIndex.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        this.anchorIndex = Map.copyOf(finalIndex);
        this.unanchoredRuleIds = unanchored.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String name() {
        return "HASHMAP_BITSET";
    }

    @Override
    public TypingResult classify(AssetTypingContext context) {
        Map<String, Boolean> features = featureExtractor.extract(context);
        BitSet asset = new BitSet(featureIds.size());
        features.forEach((name, present) -> {
            if (Boolean.TRUE.equals(present)) {
                Integer featureId = featureIds.get(name);
                if (featureId != null) asset.set(featureId);
            }
        });

        BitSet candidateIds = new BitSet(compiledRules.size());
        for (int featureId = asset.nextSetBit(0); featureId >= 0; featureId = asset.nextSetBit(featureId + 1)) {
            int[] rules = anchorIndex.get(featureId);
            if (rules != null) for (int ruleId : rules) candidateIds.set(ruleId);
        }
        for (int ruleId : unanchoredRuleIds) candidateIds.set(ruleId);

        List<RuleMatch> matches = new ArrayList<>();
        for (int i = candidateIds.nextSetBit(0); i >= 0; i = candidateIds.nextSetBit(i + 1)) {
            CompiledRule rule = compiledRules.get(i);
            if (matches(asset, rule)) {
                CanonicalRule r = rule.rule();
                matches.add(new RuleMatch(r.ruleId(), r.targetType(), r.targetSubtype(), r.priority()));
            }
        }
        return MatchResolver.resolve(context.assetId(), matches);
    }

    public int featureCount() {
        return featureIds.size();
    }

    private boolean matches(BitSet asset, CompiledRule rule) {
        BitSet missingRequired = (BitSet) rule.required().clone();
        missingRequired.andNot(asset);
        if (!missingRequired.isEmpty()) return false;

        BitSet forbiddenPresent = (BitSet) rule.forbidden().clone();
        forbiddenPresent.and(asset);
        if (!forbiddenPresent.isEmpty()) return false;

        if (!rule.any().isEmpty()) {
            BitSet anyPresent = (BitSet) rule.any().clone();
            anyPresent.and(asset);
            if (anyPresent.isEmpty()) return false;
        }
        return true;
    }

    private BitSet mask(List<String> names) {
        BitSet mask = new BitSet(featureIds.size());
        for (String name : names) {
            Integer id = featureIds.get(name);
            if (id == null) throw new IllegalArgumentException("Unknown feature in rule: " + name);
            mask.set(id);
        }
        return mask;
    }

    private Integer chooseAnchor(List<String> required) {
        return required.stream()
                .filter(featureIds::containsKey)
                .min(Comparator.comparingInt(this::anchorRank).thenComparing(s -> s))
                .map(featureIds::get)
                .orElse(null);
    }

    private int anchorRank(String feature) {
        if (feature.startsWith("OBJ_")) return 0;
        if (feature.startsWith("KSC_") || feature.startsWith("NMAP_")) return 1;
        if (feature.startsWith("OS_") || feature.contains("HINT")) return 2;
        if (feature.startsWith("SRC_")) return 3;
        return 4;
    }

    private record CompiledRule(CanonicalRule rule, BitSet required, BitSet any, BitSet forbidden, Integer anchor) {}
}
