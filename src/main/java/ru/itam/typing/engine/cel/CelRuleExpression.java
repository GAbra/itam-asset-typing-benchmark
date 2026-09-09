package ru.itam.typing.engine.cel;

import ru.itam.typing.rules.CanonicalRule;

import java.util.ArrayList;
import java.util.List;

final class CelRuleExpression {
    private CelRuleExpression() {}

    static String from(CanonicalRule rule) {
        List<String> clauses = new ArrayList<>();
        rule.required().forEach(f -> clauses.add(access(f)));
        rule.forbidden().forEach(f -> clauses.add("!" + access(f)));
        if (!rule.any().isEmpty()) {
            clauses.add("(" + rule.any().stream().map(CelRuleExpression::access).reduce((a, b) -> a + " || " + b).orElse("false") + ")");
        }
        return clauses.isEmpty() ? "true" : String.join(" && ", clauses);
    }

    private static String access(String feature) {
        return "features[\"" + feature.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]";
    }
}
