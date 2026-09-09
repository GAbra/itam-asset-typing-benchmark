package ru.itam.typing.engine.dmn;

import org.kie.api.io.Resource;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.core.api.DMNFactory;
import org.kie.dmn.core.internal.utils.DMNRuntimeBuilder;
import org.kie.internal.io.ResourceFactory;
import ru.itam.typing.engine.TypingEngine;
import ru.itam.typing.engine.common.MatchResolver;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.*;
import ru.itam.typing.rules.RuleSet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public final class DmnTypingEngine implements TypingEngine {
    private final FeatureExtractor featureExtractor;
    private final DMNRuntime runtime;
    private final DMNModel model;
    private final String generatedDmn;

    public DmnTypingEngine(RuleSet ruleSet, FeatureExtractor featureExtractor) {
        this.featureExtractor = featureExtractor;
        this.generatedDmn = new DmnModelGenerator().generate(ruleSet);
        Resource resource = ResourceFactory.newByteArrayResource(generatedDmn.getBytes(StandardCharsets.UTF_8));
        resource.setSourcePath("generated/itam-typing.dmn");
        this.runtime = DMNRuntimeBuilder.fromDefaults()
                .buildConfiguration()
                .fromResources(List.of(resource))
                .getOrElseThrow(RuntimeException::new);
        this.model = runtime.getModel(DmnModelGenerator.NAMESPACE, DmnModelGenerator.MODEL_NAME);
        if (model == null) {
            throw new IllegalStateException("Generated DMN model was not loaded");
        }
        if (model.hasErrors()) {
            throw new IllegalStateException("Generated DMN model has errors: " + model.getMessages());
        }
    }

    @Override
    public String name() {
        return "DMN_KIE";
    }

    @Override
    public TypingResult classify(AssetTypingContext context) {
        Map<String, Boolean> features = featureExtractor.extract(context);
        DMNContext dmnContext = DMNFactory.newContext();
        features.forEach(dmnContext::set);
        DMNResult result = runtime.evaluateAll(model, dmnContext);
        if (result.hasErrors()) {
            throw new IllegalStateException("DMN evaluation failed for " + context.assetId() + ": " + result.getMessages());
        }
        DMNDecisionResult decision = result.getDecisionResultByName(DmnModelGenerator.DECISION_NAME);
        if (decision == null) {
            throw new IllegalStateException("DMN decision not found: " + DmnModelGenerator.DECISION_NAME);
        }

        List<RuleMatch> matches = new ArrayList<>();
        Object raw = decision.getResult();
        if (raw instanceof Collection<?> collection) {
            for (Object item : collection) addMatch(item, matches);
        } else if (raw != null) {
            addMatch(raw, matches);
        }
        return MatchResolver.resolve(context.assetId(), matches);
    }

    public String generatedDmn() {
        return generatedDmn;
    }

    private static void addMatch(Object raw, List<RuleMatch> matches) {
        String code = String.valueOf(raw);
        String[] parts = code.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalStateException("Unexpected DMN matchCode: " + code);
        }
        matches.add(new RuleMatch(
                parts[0],
                AssetType.valueOf(parts[1]),
                parts[2].isBlank() ? null : AssetSubtype.valueOf(parts[2]),
                Integer.parseInt(parts[3])));
    }
}
