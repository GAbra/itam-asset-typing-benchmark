package ru.itam.typing.rules;

import java.util.List;

public record RuleSet(String rulesetVersion, List<CanonicalRule> rules) {
    public RuleSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
