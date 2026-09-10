package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.realistic.NoiseProfile;
import ru.itam.typing.realistic.ProfileDistribution;
import ru.itam.typing.realistic.RealisticWorkloadGenerator;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProfileDistributionTest {
    @TempDir Path temp;

    @Test
    void namedScenarioDistributionsAreExplicitAndDeterministic() throws Exception {
        var device = ProfileDistribution.named("device-heavy");
        assertEquals("device-heavy", device.name());
        assertEquals(30, device.weightsByName().get("WINDOWS_WORKSTATION"));
        assertEquals(5, device.weightsByName().get("SERVICE_ACCOUNT"));

        var generator = new RealisticWorkloadGenerator();
        Path raw1 = temp.resolve("r1.jsonl");
        Path truth1 = temp.resolve("t1.jsonl");
        Path raw2 = temp.resolve("r2.jsonl");
        Path truth2 = temp.resolve("t2.jsonl");
        var a = generator.generate(10_000, 443322L, raw1, truth1, NoiseProfile.clean(), device);
        var b = generator.generate(10_000, 443322L, raw2, truth2, NoiseProfile.clean(), device);

        assertEquals(a.profileCounts(), b.profileCounts());
        assertEquals(a.profileWeights(), b.profileWeights());
        long deviceCount = a.profileCounts().get("WINDOWS_WORKSTATION")
                + a.profileCounts().get("WINDOWS_SERVER")
                + a.profileCounts().get("LINUX_SERVER")
                + a.profileCounts().get("NETWORK_DEVICE");
        assertTrue(deviceCount > 6_900 && deviceCount < 7_900,
                "device-heavy scenario should materially differ from a balanced distribution");
        assertThrows(IllegalArgumentException.class, () -> ProfileDistribution.named("production"));
    }
}
