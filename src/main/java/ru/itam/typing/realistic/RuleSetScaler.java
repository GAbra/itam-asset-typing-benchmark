package ru.itam.typing.realistic;

import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import java.util.ArrayList;
import java.util.List;

/** Creates larger active rulesets for scalability measurements while preserving base classification semantics. */
public final class RuleSetScaler {

    public RuleSet scale(RuleSet base, int targetEnabledRules) {
        List<CanonicalRule> enabled = base.rules().stream().filter(CanonicalRule::enabled).toList();
        if (targetEnabledRules < enabled.size()) {
            throw new IllegalArgumentException("targetEnabledRules must be >= base enabled rule count " + enabled.size());
        }
        if (enabled.isEmpty()) throw new IllegalArgumentException("base ruleset has no enabled rules");

        List<CanonicalRule> out = new ArrayList<>(base.rules());
        int active = enabled.size();
        int serial = 1;
        while (active < targetEnabledRules) {
            CanonicalRule source = enabled.get((serial - 1) % enabled.size());
            int clonePriority = Math.max(Integer.MIN_VALUE + 10_000, source.priority() - 1_000_000 - serial);
            out.add(new CanonicalRule(
                    source.ruleId() + "__SCALE_" + String.format("%04d", serial),
                    source.targetType(), source.targetSubtype(), clonePriority,
                    source.required(), source.any(), source.forbidden(), true));
            active++;
            serial++;
        }
        return new RuleSet(base.rulesetVersion() + "-scale-" + targetEnabledRules, out);
    }
}
