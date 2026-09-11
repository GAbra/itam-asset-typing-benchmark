package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.realistic.HoldoutSplitCli;
import ru.itam.typing.realistic.KscIpv4Long;
import ru.itam.typing.realistic.KscTypedChunkAdapter;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.model.GroundTruthLabel;
import ru.itam.typing.realistic.model.RawAssetBundle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResearchDatasetProtocolTest {
    @TempDir Path temp;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    @Test
    void kscIpv4LongRegressionAndTypedChunkAreAuditable() throws Exception {
        assertEquals(174286280L, KscIpv4Long.encodeKscParamLong("10.99.101.200"));
        assertEquals("10.99.101.200", KscIpv4Long.decodeKscParamLong(174286280L));
        assertThrows(IllegalArgumentException.class, () -> KscIpv4Long.encodeKscParamLong("10.99.999.1"));

        var rows = KscTypedChunkAdapter.readHosts(Path.of("data/raw-samples/kaspersky/hosts-chunk.json"));
        assertEquals(2, rows.size());
        assertEquals("168427541", rows.get(0).attributes().get("KLHST_WKS_IP_LONG"));
        assertEquals("174286280", rows.get(1).attributes().get("KLHST_WKS_IP_LONG"));
        assertEquals("2", rows.get(0).attributes().get("KLHST_WKS_CTYPE"));
    }

    @Test
    void namedNoiseRegimesAreOrderedFromCleanToSevere() {
        assertEquals(NoiseProfile.clean(), NoiseProfile.named("clean"));
        assertEquals(NoiseProfile.stressDefault(), NoiseProfile.named("stress"));
        assertEquals(NoiseProfile.severe(), NoiseProfile.named("SEVERE"));
        assertTrue(total(NoiseProfile.light()) < total(NoiseProfile.moderate()));
        assertTrue(total(NoiseProfile.moderate()) < total(NoiseProfile.stressDefault()));
        assertTrue(total(NoiseProfile.stressDefault()) < total(NoiseProfile.severe()));
        assertThrows(IllegalArgumentException.class, () -> NoiseProfile.named("made-up"));
    }

    @Test
    void holdoutSplitIsDeterministicAlignedAndDisjoint() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        new RealisticWorkloadGenerator().generate(5_000, 991122L, raw, truth, NoiseProfile.moderate());

        SplitFiles first = split(raw, truth, "a");
        SplitFiles second = split(raw, truth, "b");

        assertArrayEquals(Files.readAllBytes(first.trainRaw), Files.readAllBytes(second.trainRaw));
        assertArrayEquals(Files.readAllBytes(first.holdoutRaw), Files.readAllBytes(second.holdoutRaw));
        assertArrayEquals(Files.readAllBytes(first.trainTruth), Files.readAllBytes(second.trainTruth));
        assertArrayEquals(Files.readAllBytes(first.holdoutTruth), Files.readAllBytes(second.holdoutTruth));

        Set<String> trainIds = rawIds(first.trainRaw);
        Set<String> holdoutIds = rawIds(first.holdoutRaw);
        Set<String> overlap = new HashSet<>(trainIds);
        overlap.retainAll(holdoutIds);
        assertTrue(overlap.isEmpty(), "train and holdout must be disjoint");
        assertEquals(5_000, trainIds.size() + holdoutIds.size());
        assertTrue(holdoutIds.size() > 850 && holdoutIds.size() < 1_150,
                "hash split should be close to 20% for a 5000-record corpus");
        assertEquals(trainIds, truthIds(first.trainTruth));
        assertEquals(holdoutIds, truthIds(first.holdoutTruth));
    }

    private SplitFiles split(Path raw, Path truth, String prefix) throws Exception {
        Path trainRaw = temp.resolve(prefix + "-train-raw.jsonl");
        Path trainTruth = temp.resolve(prefix + "-train-truth.jsonl");
        Path holdoutRaw = temp.resolve(prefix + "-holdout-raw.jsonl");
        Path holdoutTruth = temp.resolve(prefix + "-holdout-truth.jsonl");
        HoldoutSplitCli.main(new String[]{
                "--raw", raw.toString(), "--truth", truth.toString(),
                "--train-raw", trainRaw.toString(), "--train-truth", trainTruth.toString(),
                "--holdout-raw", holdoutRaw.toString(), "--holdout-truth", holdoutTruth.toString(),
                "--holdout-pct", "20", "--salt", HoldoutSplitCli.DEFAULT_SALT
        });
        return new SplitFiles(trainRaw, trainTruth, holdoutRaw, holdoutTruth);
    }

    private static Set<String> rawIds(Path path) throws Exception {
        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(path)) ids.add(JSON.readValue(line, RawAssetBundle.class).assetId());
        return ids;
    }

    private static Set<String> truthIds(Path path) throws Exception {
        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(path)) ids.add(JSON.readValue(line, GroundTruthLabel.class).assetId());
        return ids;
    }

    private static int total(NoiseProfile p) {
        return p.missingOptionalSourcePct() + p.staleObservationPct() + p.conflictingOsPct() + p.renamePct()
                + p.falseServiceHintPct() + p.missedServiceHintPct() + p.ambiguousNmapPct()
                + p.incompleteInventoryPct() + p.kscTypeFlipPct();
    }

    private record SplitFiles(Path trainRaw, Path trainTruth, Path holdoutRaw, Path holdoutTruth) {}
}
