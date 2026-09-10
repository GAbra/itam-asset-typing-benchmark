package ru.itam.typing.engine;

import ru.itam.typing.model.TypingResult;

import java.util.Map;

/**
 * Optional research interface that separates feature extraction from rule execution.
 * classify(context) remains the production-facing contract; classifyFeatures is used by engine-only benchmarks.
 */
public interface FeatureMapTypingEngine extends TypingEngine {
    TypingResult classifyFeatures(String assetId, Map<String, Boolean> features);
}
