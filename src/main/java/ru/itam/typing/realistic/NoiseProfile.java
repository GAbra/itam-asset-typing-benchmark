package ru.itam.typing.realistic;

/**
 * Tunable stress profile for source imperfections.
 * Values are percentages, not claimed production prevalence.
 */
public record NoiseProfile(
        int missingOptionalSourcePct,
        int staleObservationPct,
        int conflictingOsPct,
        int renamePct,
        int falseServiceHintPct,
        int missedServiceHintPct,
        int ambiguousNmapPct,
        int incompleteInventoryPct,
        int kscTypeFlipPct) {

    public NoiseProfile {
        validate("missingOptionalSourcePct", missingOptionalSourcePct);
        validate("staleObservationPct", staleObservationPct);
        validate("conflictingOsPct", conflictingOsPct);
        validate("renamePct", renamePct);
        validate("falseServiceHintPct", falseServiceHintPct);
        validate("missedServiceHintPct", missedServiceHintPct);
        validate("ambiguousNmapPct", ambiguousNmapPct);
        validate("incompleteInventoryPct", incompleteInventoryPct);
        validate("kscTypeFlipPct", kscTypeFlipPct);
    }

    public static NoiseProfile stressDefault() {
        return new NoiseProfile(12, 15, 4, 8, 3, 8, 6, 10, 3);
    }

    public static NoiseProfile clean() {
        return new NoiseProfile(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be in [0,100]");
    }
}
