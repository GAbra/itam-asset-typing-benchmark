package ru.itam.typing.realistic;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Explicit synthetic scenario distribution over latent truth profiles.
 * Named scenarios are sensitivity-analysis inputs, not claims about production prevalence.
 */
public final class ProfileDistribution {
    private final EnumMap<RealisticWorkloadGenerator.TruthProfile, Integer> weights;
    private final int totalWeight;
    private final String name;

    public ProfileDistribution(String name, Map<RealisticWorkloadGenerator.TruthProfile, Integer> input) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("distribution name must not be blank");
        this.weights = new EnumMap<>(RealisticWorkloadGenerator.TruthProfile.class);
        int total = 0;
        for (RealisticWorkloadGenerator.TruthProfile p : RealisticWorkloadGenerator.TruthProfile.values()) {
            int value = input.getOrDefault(p, 0);
            if (value < 0) throw new IllegalArgumentException("negative weight for " + p);
            weights.put(p, value);
            total = Math.addExact(total, value);
        }
        if (total <= 0) throw new IllegalArgumentException("at least one profile weight must be > 0");
        this.totalWeight = total;
    }

    public RealisticWorkloadGenerator.TruthProfile choose(SplittableRandom random) {
        int slot = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (slot < cumulative) return entry.getKey();
        }
        throw new IllegalStateException("distribution selection failed");
    }

    public String name() { return name; }

    public Map<String, Integer> weightsByName() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        weights.forEach((k, v) -> out.put(k.name(), v));
        return Map.copyOf(out);
    }

    public static ProfileDistribution balanced() {
        return new ProfileDistribution("balanced", Map.of(
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_WORKSTATION, 1,
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_SERVER, 1,
                RealisticWorkloadGenerator.TruthProfile.LINUX_SERVER, 1,
                RealisticWorkloadGenerator.TruthProfile.NETWORK_DEVICE, 1,
                RealisticWorkloadGenerator.TruthProfile.USER_ACCOUNT, 1,
                RealisticWorkloadGenerator.TruthProfile.SERVICE_ACCOUNT, 1,
                RealisticWorkloadGenerator.TruthProfile.SECURITY_SOFTWARE, 1,
                RealisticWorkloadGenerator.TruthProfile.APPLICATION_SOFTWARE, 1));
    }

    public static ProfileDistribution deviceHeavy() {
        return new ProfileDistribution("device-heavy", Map.of(
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_WORKSTATION, 30,
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_SERVER, 18,
                RealisticWorkloadGenerator.TruthProfile.LINUX_SERVER, 14,
                RealisticWorkloadGenerator.TruthProfile.NETWORK_DEVICE, 12,
                RealisticWorkloadGenerator.TruthProfile.USER_ACCOUNT, 10,
                RealisticWorkloadGenerator.TruthProfile.SERVICE_ACCOUNT, 5,
                RealisticWorkloadGenerator.TruthProfile.SECURITY_SOFTWARE, 5,
                RealisticWorkloadGenerator.TruthProfile.APPLICATION_SOFTWARE, 6));
    }

    public static ProfileDistribution identityHeavy() {
        return new ProfileDistribution("identity-heavy", Map.of(
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_WORKSTATION, 15,
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_SERVER, 10,
                RealisticWorkloadGenerator.TruthProfile.LINUX_SERVER, 8,
                RealisticWorkloadGenerator.TruthProfile.NETWORK_DEVICE, 7,
                RealisticWorkloadGenerator.TruthProfile.USER_ACCOUNT, 35,
                RealisticWorkloadGenerator.TruthProfile.SERVICE_ACCOUNT, 15,
                RealisticWorkloadGenerator.TruthProfile.SECURITY_SOFTWARE, 5,
                RealisticWorkloadGenerator.TruthProfile.APPLICATION_SOFTWARE, 5));
    }

    public static ProfileDistribution softwareHeavy() {
        return new ProfileDistribution("software-heavy", Map.of(
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_WORKSTATION, 12,
                RealisticWorkloadGenerator.TruthProfile.WINDOWS_SERVER, 8,
                RealisticWorkloadGenerator.TruthProfile.LINUX_SERVER, 7,
                RealisticWorkloadGenerator.TruthProfile.NETWORK_DEVICE, 5,
                RealisticWorkloadGenerator.TruthProfile.USER_ACCOUNT, 8,
                RealisticWorkloadGenerator.TruthProfile.SERVICE_ACCOUNT, 5,
                RealisticWorkloadGenerator.TruthProfile.SECURITY_SOFTWARE, 20,
                RealisticWorkloadGenerator.TruthProfile.APPLICATION_SOFTWARE, 35));
    }

    public static ProfileDistribution named(String value) {
        String n = value == null ? "balanced" : value.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "balanced" -> balanced();
            case "device-heavy", "device" -> deviceHeavy();
            case "identity-heavy", "identity" -> identityHeavy();
            case "software-heavy", "software" -> softwareHeavy();
            default -> throw new IllegalArgumentException("Unknown distribution '" + value
                    + "'. Expected balanced|device-heavy|identity-heavy|software-heavy");
        };
    }
}
