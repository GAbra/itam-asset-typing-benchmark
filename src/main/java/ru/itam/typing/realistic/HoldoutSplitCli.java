package ru.itam.typing.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic asset-id hash split that keeps raw observations and truth sidecars aligned. */
public final class HoldoutSplitCli {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    public static final String DEFAULT_SALT = "realistic-v2-holdout-v1";

    private HoldoutSplitCli() {}

    public static void main(String[] args) throws Exception {
        Map<String, String> a = parse(args);
        Path raw = Path.of(required(a, "--raw"));
        Path truth = Path.of(required(a, "--truth"));
        Path trainRaw = Path.of(a.getOrDefault("--train-raw", "data/generated/realistic-v2-train-raw.jsonl"));
        Path trainTruth = Path.of(a.getOrDefault("--train-truth", "data/generated/realistic-v2-train-truth.jsonl"));
        Path holdoutRaw = Path.of(a.getOrDefault("--holdout-raw", "data/generated/realistic-v2-holdout-raw.jsonl"));
        Path holdoutTruth = Path.of(a.getOrDefault("--holdout-truth", "data/generated/realistic-v2-holdout-truth.jsonl"));
        int pct = Integer.parseInt(a.getOrDefault("--holdout-pct", "20"));
        String salt = a.getOrDefault("--salt", DEFAULT_SALT);
        if (pct <= 0 || pct >= 100) throw new IllegalArgumentException("--holdout-pct must be in 1..99");

        for (Path p : new Path[]{trainRaw, trainTruth, holdoutRaw, holdoutTruth}) {
            Path parent = p.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
        }

        long train = 0, holdout = 0;
        try (BufferedReader rr = Files.newBufferedReader(raw, StandardCharsets.UTF_8);
             BufferedReader tr = Files.newBufferedReader(truth, StandardCharsets.UTF_8);
             BufferedWriter rTrain = Files.newBufferedWriter(trainRaw, StandardCharsets.UTF_8);
             BufferedWriter tTrain = Files.newBufferedWriter(trainTruth, StandardCharsets.UTF_8);
             BufferedWriter rHold = Files.newBufferedWriter(holdoutRaw, StandardCharsets.UTF_8);
             BufferedWriter tHold = Files.newBufferedWriter(holdoutTruth, StandardCharsets.UTF_8)) {
            while (true) {
                String rawLine = rr.readLine();
                String truthLine = tr.readLine();
                if (rawLine == null || truthLine == null) {
                    if (rawLine != null || truthLine != null) throw new IllegalStateException("raw/truth line count mismatch");
                    break;
                }
                RawAssetBundle bundle = JSON.readValue(rawLine, RawAssetBundle.class);
                GroundTruthLabel label = JSON.readValue(truthLine, GroundTruthLabel.class);
                if (!bundle.assetId().equals(label.assetId())) {
                    throw new IllegalStateException("raw/truth asset mismatch: " + bundle.assetId() + " != " + label.assetId());
                }
                if (isHoldout(bundle.assetId(), pct, salt)) {
                    rHold.write(rawLine); rHold.write('\n');
                    tHold.write(truthLine); tHold.write('\n');
                    holdout++;
                } else {
                    rTrain.write(rawLine); rTrain.write('\n');
                    tTrain.write(truthLine); tTrain.write('\n');
                    train++;
                }
            }
        }

        SplitSummary summary = new SplitSummary(raw.toString(), truth.toString(), pct, salt, train, holdout,
                sha256(trainRaw), sha256(trainTruth), sha256(holdoutRaw), sha256(holdoutTruth));
        String out = a.get("--out");
        if (out != null) {
            Path p = Path.of(out);
            Path parent = p.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            JSON.writeValue(p.toFile(), summary);
        }
        System.out.println(JSON.writeValueAsString(summary));
    }

    public static boolean isHoldout(String assetId, int holdoutPct, String salt) {
        if (assetId == null || assetId.isBlank()) throw new IllegalArgumentException("assetId must not be blank");
        if (holdoutPct <= 0 || holdoutPct >= 100) throw new IllegalArgumentException("holdoutPct must be in 1..99");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + "|" + assetId).getBytes(StandardCharsets.UTF_8));
            long bucketSource = Integer.toUnsignedLong(ByteBuffer.wrap(hash, 0, 4).getInt());
            return bucketSource % 10_000L < holdoutPct * 100L;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) >= 0) if (n > 0) digest.update(buffer, 0, n);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("Expected --key value pairs");
            out.put(args[i], args[++i]);
        }
        return out;
    }

    private static String required(Map<String, String> a, String key) {
        String value = a.get(key);
        if (value == null) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    public record SplitSummary(String rawInput, String truthInput, int holdoutPct, String salt,
                               long trainCount, long holdoutCount,
                               String trainRawSha256, String trainTruthSha256,
                               String holdoutRawSha256, String holdoutTruthSha256) {}
}
