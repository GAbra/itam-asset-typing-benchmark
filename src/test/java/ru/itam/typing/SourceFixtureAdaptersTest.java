package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.realistic.KscIpv4Long;
import ru.itam.typing.realistic.SourceFixtureAdapters;
import ru.itam.typing.realistic.SourceSchemaRegistry;
import ru.itam.typing.realistic.model.SourceObservation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceFixtureAdaptersTest {

    @Test
    void checkedInFixturesAreActuallyParsedThroughSourceSpecificAdapters() throws Exception {
        var ad = SourceFixtureAdapters.readAdJsonl(Path.of("data/raw-samples/ad/computers-users.jsonl"));
        var kscHosts = SourceFixtureAdapters.readKscHostsJsonl(Path.of("data/raw-samples/kaspersky/hosts.jsonl"));
        var kscSoftware = SourceFixtureAdapters.readKscSoftwareJsonl(Path.of("data/raw-samples/kaspersky/software-inventory.jsonl"));
        var nmap = SourceFixtureAdapters.readNmapXml(Path.of("data/raw-samples/nmap/scan.xml"));
        var zabbix = SourceFixtureAdapters.readZabbixHostGet(Path.of("data/raw-samples/zabbix/host-get.json"));
        var cef = SourceFixtureAdapters.readCef(Path.of("data/raw-samples/siem/events.cef"));

        assertEquals(3, ad.size());
        assertEquals(2, kscHosts.size());
        assertEquals(2, kscSoftware.size());
        assertEquals(2, nmap.size());
        assertEquals(2, zabbix.size());
        assertEquals(3, cef.size());

        assertTrue(ad.stream().anyMatch(o -> "ad:user".equals(o.objectKind())));
        assertTrue(ad.stream().anyMatch(o -> "ad:computer".equals(o.objectKind())));
        assertTrue(nmap.stream().anyMatch(o -> "router".equalsIgnoreCase(o.attributes().get("deviceType"))));
        assertTrue(nmap.stream().anyMatch(o -> "general purpose".equalsIgnoreCase(o.attributes().get("deviceType"))));
        assertTrue(zabbix.stream().anyMatch(o -> o.attributes().getOrDefault("inventory.os", "").contains("Ubuntu")));
        assertTrue(cef.stream().anyMatch(o -> "siem:principal".equals(o.objectKind())));
        assertTrue(cef.stream().anyMatch(o -> "siem:host".equals(o.objectKind())));

        List<SourceObservation> all = new ArrayList<>();
        all.addAll(ad);
        all.addAll(kscHosts);
        all.addAll(kscSoftware);
        all.addAll(nmap);
        all.addAll(zabbix);
        all.addAll(cef);
        all.forEach(SourceSchemaRegistry::validate);
    }

    @Test
    void documentedKscIpLongEncodingIsNumericAndRoundTrips() {
        for (String ip : List.of("10.10.0.21", "10.99.101.200", "127.0.0.1", "192.168.255.254")) {
            long encoded = KscIpv4Long.encodeDocumentedLittleEndian(ip);
            assertTrue(encoded >= 0 && encoded <= 0xffff_ffffL);
            assertEquals(ip, KscIpv4Long.decodeDocumentedLittleEndian(encoded));
            assertTrue(Long.toUnsignedString(encoded).matches("\\d+"));
        }
    }

    @Test
    void malformedKscIpLongInputsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> KscIpv4Long.encodeDocumentedLittleEndian("10.0.0"));
        assertThrows(IllegalArgumentException.class, () -> KscIpv4Long.encodeDocumentedLittleEndian("10.0.0.999"));
        assertThrows(IllegalArgumentException.class, () -> KscIpv4Long.decodeDocumentedLittleEndian(-1));
        assertThrows(IllegalArgumentException.class, () -> KscIpv4Long.decodeDocumentedLittleEndian(0x1_0000_0000L));
    }
}
