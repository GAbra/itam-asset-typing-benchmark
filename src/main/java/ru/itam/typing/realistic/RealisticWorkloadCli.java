package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone branch-only CLI for the realistic workload v2 research track. */
public final class RealisticWorkloadCli {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private RealisticWorkloadCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || "help".equalsIgnoreCase(args[0]) || "--help".equalsIgnoreCase(args[0])) {
            help();
            return;
        }
        String command = args[0];
        Map<String, String> a = parse(args);
        Path raw = Path.of(a.getOrDefault("--raw", "data/generated/realistic-v2-raw.jsonl"));
        Path truth = Path.of(a.getOrDefault("--truth", "data/generated/realistic-v2-truth.jsonl"));
        Path out = Path.of(a.getOrDefault("--out", "data/generated/realistic-v2-normalized.jsonl"));

        if ("generate".equals(command) || "all".equals(command)) {
            long count = Long.parseLong(a.getOrDefault("--count", "10000"));
            long seed = Long.parseLong(a.getOrDefault("--seed", Long.toString(RealisticWorkloadGenerator.DEFAULT_SEED)));
            if (a.containsKey("--clean") && a.containsKey("--noise")) {
                throw new IllegalArgumentException("Use either --clean or --noise, not both");
            }
            NoiseProfile noise = a.containsKey("--clean")
                    ? NoiseProfile.clean()
                    : NoiseProfile.named(a.getOrDefault("--noise", "stress"));
            ProfileDistribution distribution = ProfileDistribution.named(a.getOrDefault("--distribution", "balanced"));
            var summary = new RealisticWorkloadGenerator().generate(count, seed, raw, truth, noise, distribution);
            JSON.writeValue(Path.of(raw + ".meta.json").toFile(), summary);
            System.out.println(JSON.writeValueAsString(summary));
        }

        if ("materialize".equals(command) || "all".equals(command)) {
            var summary = new RealisticDatasetMaterializer().materialize(raw, truth, out);
            JSON.writeValue(Path.of(out + ".meta.json").toFile(), summary);
            System.out.println(JSON.writeValueAsString(summary));
        }

        if (!"generate".equals(command) && !"materialize".equals(command) && !"all".equals(command)) {
            throw new IllegalArgumentException("Unknown realistic workload command: " + command);
        }
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 1; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) throw new IllegalArgumentException("Unexpected argument: " + key);
            if ("--clean".equals(key)) {
                result.put(key, "true");
                continue;
            }
            if (i + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + key);
            result.put(key, args[++i]);
        }
        return result;
    }

    private static void help() {
        System.out.println("""
                Realistic workload v2 research CLI

                  generate    [--count 10000] [--seed 20260910] [--raw file] [--truth file]
                              [--noise clean|light|moderate|stress|severe]
                              [--distribution balanced|device-heavy|identity-heavy|software-heavy] [--clean]
                  materialize --raw file --truth file [--out file]
                  all         [--count 10000] [--seed 20260910] [--raw file] [--truth file] [--out file]
                              [--noise clean|light|moderate|stress|severe]
                              [--distribution balanced|device-heavy|identity-heavy|software-heavy] [--clean]

                Noise rates and named profile distributions are controlled sensitivity-analysis scenarios,
                not claims about production prevalence. Ground truth is written separately from raw observations.
                """);
    }
}
