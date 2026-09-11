package ru.itam.typing.engine.common;

import java.util.Objects;

/**
 * Resolution policy applied after engines have produced the same set of rule matches.
 * conflictPriorityWindow=0 preserves the historical max-priority semantics.
 */
public record ResolutionPolicy(String name, int conflictPriorityWindow) {
    public static final ResolutionPolicy LEGACY_MAX_PRIORITY =
            new ResolutionPolicy("LEGACY_MAX_PRIORITY", 0);
    public static final ResolutionPolicy CONSERVATIVE_80 =
            new ResolutionPolicy("CONSERVATIVE_NEAR_PRIORITY", 80);

    public ResolutionPolicy {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (conflictPriorityWindow < 0) {
            throw new IllegalArgumentException("conflictPriorityWindow must be >= 0");
        }
    }

    public static ResolutionPolicy conservative(int conflictPriorityWindow) {
        if (conflictPriorityWindow == 0) return LEGACY_MAX_PRIORITY;
        return new ResolutionPolicy("CONSERVATIVE_NEAR_PRIORITY", conflictPriorityWindow);
    }
}
