package ru.itam.typing.realistic;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Standalone generator for controlled ruleset-size scalability studies. */
public final class RuleScaleCli {
    private RuleScaleCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path input = Path.of(a.getOrDefault("--rules", "rules/canonical-rules.yaml"));
        int count = Integer.parseInt(required(a, "--count"));
        Path out = Path.of(a.getOrDefault("--out", "rules/generated/canonical-scale-" + count + ".yaml"));

        var scaled = new RuleSetScaler().scale(RuleLoader.load(input), count);
        Files.createDirectories(out.toAbsolutePath().getParent());
        ObjectMapper yaml = new ObjectMapper(new YAMLFactory()).findAndRegisterModules();
        yaml.writeValue(out.toFile(), scaled);
        System.out.println(out.toAbsolutePath());
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --key value pairs");
            }
            out.put(args[i], args[++i]);
        }
        return out;
    }

    private static String required(Map<String, String> a, String key) {
        String value = a.get(key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }
}
