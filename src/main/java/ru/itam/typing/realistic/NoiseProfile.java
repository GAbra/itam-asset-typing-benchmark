package ru.itam.typing.realistic;

import java.util.Locale;
import java.util.Set;

/**
 * Tunable stress profile for source imperfections.
 * Values are percentages and model test scenarios, not production prevalence.
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

    public static NoiseProfile clean() {
        return new NoiseProfile(0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    public static NoiseProfile light() {
        return new NoiseProfile(4, 5, 1, 2, 1, 3, 2, 4, 1);
    }

    public static NoiseProfile moderate() {
        return new NoiseProfile(8, 10, 2, 5, 2, 5, 4, 7, 2);
    }

    public static NoiseProfile stressDefault() {
        return new NoiseProfile(12, 15, 4, 8, 3, 8, 6, 10, 3);
    }

    public static NoiseProfile severe() {
        return new NoiseProfile(25, 30, 10, 15, 8, 15, 15, 25, 8);
    }

    public static NoiseProfile named(String name) {
        String normalized = name == null ? "stress" : name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "clean" -> clean();
            case "light" -> light();
            case "moderate" -> moderate();
            case "stress", "default" -> stressDefault();
            case "severe" -> severe();
            default -> throw new IllegalArgumentException("Unknown noise profile '" + name + "'. Expected one of " + names());
        };
    }

    public static Set<String> names() {
        return Set.of("clean", "light", "moderate", "stress", "severe");
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be in [0,100]");
    }
}
