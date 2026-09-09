package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.model.AssetTypingContext;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FeatureExtractorTest {
    private final FeatureExtractor extractor = new FeatureExtractor();

    @Test
    void recognizesDocumentedKscComputerTypeBits() {
        var server = new AssetTypingContext("srv", Set.of("KSC"), Set.of("ksc:host"),
                Map.of("ksc.KLHST_WKS_CTYPE", "2", "ksc.KLHST_WKS_OS_NAME", "Windows Server 2022"), Map.of());
        var f = extractor.extract(server);
        assertTrue(f.get("SRC_KSC"));
        assertTrue(f.get("OBJ_KSC_HOST"));
        assertTrue(f.get("KSC_SERVER"));
        assertFalse(f.get("KSC_WORKSTATION"));
        assertTrue(f.get("OS_WINDOWS"));
        assertTrue(f.get("OS_SERVER"));
    }

    @Test
    void recognizesServiceAccountHintWithoutTreatingEveryUserAsService() {
        var service = new AssetTypingContext("svc", Set.of("AD"), Set.of("ad:user"),
                Map.of("ad.sAMAccountName", "svc_backup_01", "ad.description", "Service account for backup"), Map.of());
        var user = new AssetTypingContext("u", Set.of("AD"), Set.of("ad:user"),
                Map.of("ad.sAMAccountName", "ivan.petrov", "ad.description", "Corporate user account"), Map.of());
        assertTrue(extractor.extract(service).get("ACCOUNT_SERVICE_HINT"));
        assertFalse(extractor.extract(user).get("ACCOUNT_SERVICE_HINT"));
    }

    @Test
    void recognizesNmapNetworkDeviceType() {
        var ctx = new AssetTypingContext("r1", Set.of("NMAP"), Set.of("nmap:host"),
                Map.of("nmap.deviceType", "router", "nmap.osfamily", "IOS"), Map.of());
        var f = extractor.extract(ctx);
        assertTrue(f.get("NMAP_NETWORK_DEVICE"));
        assertFalse(f.get("NMAP_GENERAL_PURPOSE"));
    }
}
