package ru.itam.typing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.realistic.KscIpv4Long;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;
import ru.itam.typing.realistic.model.RawAssetBundle;
import ru.itam.typing.realistic.model.SourceObservation;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RealisticSourceCorrelationTest {
    @TempDir Path temp;

    @Test
    void cleanDeviceObservationsPreserveOneIpAcrossKscNmapAndZabbixEncodings() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        new RealisticWorkloadGenerator().generate(500, 313370L, raw, truth, NoiseProfile.clean());
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        long[] checked = {0};

        try (var lines = Files.lines(raw)) {
            lines.forEach(line -> {
                try {
                    RawAssetBundle bundle = json.readValue(line, RawAssetBundle.class);
                    SourceObservation ksc = bundle.observations().stream()
                            .filter(o -> "ksc:host".equals(o.objectKind())).findFirst().orElse(null);
                    if (ksc == null) return;
                    String rawLong = ksc.attributes().get("KLHST_WKS_IP_LONG");
                    assertNotNull(rawLong);
                    assertTrue(rawLong.matches("\\d+"), "KSC IP_LONG must be a decimal paramLong representation");
                    String decoded = KscIpv4Long.decodeDocumentedLittleEndian(Long.parseLong(rawLong));

                    bundle.observations().stream().filter(o -> "nmap:host".equals(o.objectKind())).findFirst()
                            .ifPresent(o -> assertEquals(decoded, o.attributes().get("address"), bundle.assetId()));
                    bundle.observations().stream().filter(o -> "zabbix:host".equals(o.objectKind())).findFirst()
                            .ifPresent(o -> assertEquals(decoded, o.attributes().get("interface.ip"), bundle.assetId()));
                    checked[0]++;
                } catch (RuntimeException | java.io.IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue(checked[0] > 100, "clean sample should contain many multi-source device bundles");
    }
}
