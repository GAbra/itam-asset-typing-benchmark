package ru.itam.typing.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Path;

public final class RuleLoader {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();

    private RuleLoader() {}

    public static RuleSet load(Path path) throws IOException {
        RuleSet ruleSet = YAML.readValue(path.toFile(), RuleSet.class);
        validate(ruleSet);
        return ruleSet;
    }

    public static void validate(RuleSet ruleSet) {
        if (ruleSet.rulesetVersion() == null || ruleSet.rulesetVersion().isBlank()) {
            throw new IllegalArgumentException("rulesetVersion is required");
        }
        var ids = new java.util.HashSet<String>();
        for (CanonicalRule rule : ruleSet.rules()) {
            if (rule.ruleId() == null || rule.ruleId().isBlank()) {
                throw new IllegalArgumentException("ruleId is required");
            }
            if (!ids.add(rule.ruleId())) {
                throw new IllegalArgumentException("Duplicate ruleId: " + rule.ruleId());
            }
            if (rule.targetType() == null) {
                throw new IllegalArgumentException("targetType is required for " + rule.ruleId());
            }
            var overlap = new java.util.HashSet<>(rule.required());
            overlap.retainAll(rule.forbidden());
            if (!overlap.isEmpty()) {
                throw new IllegalArgumentException("Feature is both required and forbidden in " + rule.ruleId() + ": " + overlap);
            }
        }
    }
}
