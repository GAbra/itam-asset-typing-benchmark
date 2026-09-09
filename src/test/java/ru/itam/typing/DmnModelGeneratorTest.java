package ru.itam.typing;

import org.junit.jupiter.api.Test;
import ru.itam.typing.engine.dmn.DmnModelGenerator;
import ru.itam.typing.model.AssetType;
import ru.itam.typing.rules.CanonicalRule;
import ru.itam.typing.rules.RuleSet;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DmnModelGeneratorTest {
    @Test
    void generatedDmnIsWellFormedXmlAndUsesCollect() throws Exception {
        RuleSet rules = new RuleSet("test", List.of(
                new CanonicalRule("R", AssetType.DEVICE, null, 1,
                        List.of("SRC_NMAP"), List.of("NMAP_NETWORK_DEVICE", "NMAP_GENERAL_PURPOSE"), List.of(), true)));
        String xml = new DmnModelGenerator().generate(rules);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("definitions", doc.getDocumentElement().getLocalName());
        assertTrue(xml.contains("hitPolicy=\"COLLECT\""));
        assertEquals(2, xml.split("<semantic:rule ", -1).length - 1, "ANY rule must expand into two equivalent DMN rows");
    }
}
