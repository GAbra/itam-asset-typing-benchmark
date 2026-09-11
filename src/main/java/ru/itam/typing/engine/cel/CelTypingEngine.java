package ru.itam.typing.engine.cel;

import dev.cel.common.CelValidationException;
import dev.cel.common.types.MapType;
import dev.cel.common.types.SimpleType;
import dev.cel.compiler.CelCompiler;
import dev.cel.compiler.CelCompilerFactory;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import dev.cel.runtime.CelRuntimeFactory;
import ru.itam.typing.engine.FeatureMapTypingEngine;
import ru.itam.typing.engine.common.MatchResolver;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.RuleMatch;
import ru.itam.typing.model.TypingResult;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.*;

/**
 * CEL implementation. Expressions are compiled once when the rule set is loaded.
 * A lightweight anchor index prevents evaluation of obviously irrelevant rules.
 */
public final class CelTypingEngine implements FeatureMapTypingEngine {
    private final FeatureExtractor featureExtractor;
    private final ResolutionPolicy resolutionPolicy;
    private final List<CompiledRule> rules;
    private final Map<String, int[]> anchorIndex;
    private final int[] unanchoredRuleIndexes;

    public CelTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor) {
        this(ruleSet, featureExtractor, ResolutionPolicy.LEGACY_MAX_PRIORITY);
    }

    public CelTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor, ResolutionPolicy resolutionPolicy) {
        this.featureExtractor = featureExtractor;
        this.resolutionPolicy = Objects.requireNonNull(resolutionPolicy, "resolutionPolicy");
        CelCompiler compiler = CelCompilerFactory.standardCelCompilerBuilder()
                .addVar("features", MapType.create(SimpleType.STRING, SimpleType.BOOL))
                .setResultType(SimpleType.BOOL)
                .build();
        CelRuntime runtime = CelRuntimeFactory.plannerRuntimeBuilder().build();

        List<CompiledRule> compiled = new ArrayList<>();
        Map<String, List<Integer>> indexBuilder = new HashMap<>();
        List<Integer> unanchored = new ArrayList<>();

        for (CanonicalRule rule : ruleSet.rules()) {
            if (!rule.enabled()) continue;
            String expression = CelRuleExpression.from(rule);
            try {
                var ast = compiler.compile(expression).getAst();
                String anchor = chooseAnchor(rule.required());
                int index = compiled.size();
                compiled.add(new CompiledRule(rule, expression, runtime.createProgram(ast), anchor));
                if (anchor == null) unanchored.add(index);
                else indexBuilder.computeIfAbsent(anchor, k -> new ArrayList<>()).add(index);
            } catch (CelValidationException | CelEvaluationException e) {
                throw new IllegalArgumentException(
                        "CEL compile/program creation failed for " + rule.ruleId() + ": " + expression, e);
            }
        }
        this.rules = List.copyOf(compiled);
        Map<String, int[]> finalIndex = new HashMap<>();
        indexBuilder.forEach((k, v) -> finalIndex.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        this.anchorIndex = Map.copyOf(finalIndex);
        this.unanchoredRuleIndexes = unanchored.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String name() {
        return "CEL";
    }

    @Override
    public TypingResult classify(AssetTypingContext context) {
        return classifyFeatures(context.assetId(), featureExtractor.extract(context));
    }

    @Override
    public TypingResult classifyFeatures(String assetId, Map<String, Boolean> features) {
        BitSet candidates = new BitSet(rules.size());
        features.forEach((feature, present) -> {
            if (!Boolean.TRUE.equals(present)) return;
            int[] indexed = anchorIndex.get(feature);
            if (indexed != null) for (int i : indexed) candidates.set(i);
        });
        for (int i : unanchoredRuleIndexes) candidates.set(i);

        List<RuleMatch> matches = new ArrayList<>();
        for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
            CompiledRule compiled = rules.get(i);
            try {
                Object result = compiled.program().eval(Map.of("features", features));
                if (Boolean.TRUE.equals(result)) {
                    CanonicalRule r = compiled.rule();
                    matches.add(new RuleMatch(r.ruleId(), r.targetType(), r.targetSubtype(), r.priority()));
                }
            } catch (CelEvaluationException e) {
                throw new IllegalStateException("CEL evaluation failed for " + compiled.rule().ruleId(), e);
            }
        }
        return MatchResolver.resolve(assetId, matches, resolutionPolicy);
    }

    public List<String> compiledExpressions() {
        return rules.stream().map(r -> r.rule().ruleId() + " = " + r.expression()).toList();
    }

    private static String chooseAnchor(List<String> required) {
        return required.stream().min(Comparator.comparingInt(CelTypingEngine::anchorRank).thenComparing(s -> s)).orElse(null);
    }

    private static int anchorRank(String feature) {
        if (feature.startsWith("OBJ_")) return 0;
        if (feature.startsWith("KSC_") || feature.startsWith("NMAP_")) return 1;
        if (feature.startsWith("OS_") || feature.contains("HINT")) return 2;
        if (feature.startsWith("SRC_")) return 3;
        return 4;
    }

    private record CompiledRule(CanonicalRule rule, String expression, CelRuntime.Program program, String anchor) {}
}
