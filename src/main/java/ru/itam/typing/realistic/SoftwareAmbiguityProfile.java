package ru.itam.typing.realistic;

import java.util.Locale;
import java.util.Set;

/**
 * Controlled software-inventory ambiguity scenarios.
 * Percentages are experiment stress parameters, not production prevalence estimates.
 */
public record SoftwareAmbiguityProfile(
        int neutralSecurityRecordPct,
        int securityComponentRecordPct,
        int securityPublisherAliasPct,
        int deceptiveApplicationRecordPct,
        int missingSoftwareIdentityPct) {

    public SoftwareAmbiguityProfile {
        validate("neutralSecurityRecordPct", neutralSecurityRecordPct);
        validate("securityComponentRecordPct", securityComponentRecordPct);
        validate("securityPublisherAliasPct", securityPublisherAliasPct);
        validate("deceptiveApplicationRecordPct", deceptiveApplicationRecordPct);
        validate("missingSoftwareIdentityPct", missingSoftwareIdentityPct);
    }

    public static SoftwareAmbiguityProfile none() {
        return new SoftwareAmbiguityProfile(0, 0, 0, 0, 0);
    }

    public static SoftwareAmbiguityProfile light() {
        return new SoftwareAmbiguityProfile(4, 4, 8, 4, 2);
    }

    public static SoftwareAmbiguityProfile stress() {
        return new SoftwareAmbiguityProfile(12, 12, 20, 12, 6);
    }

    public static SoftwareAmbiguityProfile severe() {
        return new SoftwareAmbiguityProfile(25, 20, 35, 25, 12);
    }

    public static SoftwareAmbiguityProfile named(String name) {
        String normalized = name == null ? "none" : name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "none", "control" -> none();
            case "light" -> light();
            case "stress", "default" -> stress();
            case "severe" -> severe();
            default -> throw new IllegalArgumentException(
                    "Unknown software ambiguity profile '" + name + "'. Expected one of " + names());
        };
    }

    public static Set<String> names() {
        return Set.of("none", "light", "stress", "severe");
    }

    private static void validate(String name, int value) {
        if (value < 0 || value > 100) throw new IllegalArgumentException(name + " must be in [0,100]");
    }
}
