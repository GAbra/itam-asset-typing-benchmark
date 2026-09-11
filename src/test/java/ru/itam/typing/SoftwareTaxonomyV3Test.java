package ru.itam.typing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.itam.typing.engine.bitset.BitSetTypingEngine;
import ru.itam.typing.engine.common.ResolutionPolicy;
import ru.itam.typing.features.FeatureExtractor;
import ru.itam.typing.features.SoftwareEvidenceClassifier;
import ru.itam.typing.model.AssetSubtype;
import ru.itam.typing.model.AssetTypingContext;
import ru.itam.typing.model.TypingStatus;
import ru.itam.typing.realistic.SoftwareTaxonomyV3Generator;
import ru.itam.typing.rules.RuleLoader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SoftwareTaxonomyV3Test {
    @TempDir Path temp;

    @Test
    void representativeRussianEnterpriseExamplesUseDistinctSubtypes() {
        FeatureExtractor extractor = new FeatureExtractor();
        assertCategory(extractor, "Astra Linux Special Edition", "ГК Астра", "Astra Linux", "", AssetSubtype.OPERATING_SYSTEM);
        assertCategory(extractor, "Microsoft Office LTSC Professional Plus 2021", "Microsoft Corporation", "Microsoft Office", "", AssetSubtype.OFFICE_SOFTWARE);
        assertCategory(extractor, "1С:Предприятие 8", "Фирма 1С", "1С:Предприятие", "1cv8.exe", AssetSubtype.BUSINESS_SOFTWARE);
        assertCategory(extractor, "1C:EDT", "Фирма 1С", "1C Enterprise Development Tools", "1cedt.exe", AssetSubtype.IDE);
        assertCategory(extractor, "Яндекс Браузер", "YANDEX LLC", "Yandex Browser", "browser.exe", AssetSubtype.BROWSER);
        assertCategory(extractor, "DBeaver Community", "DBeaver Corp", "DBeaver", "dbeaver.exe", AssetSubtype.DATABASE_TOOL);
        assertCategory(extractor, "Postgres Pro Standard", "Postgres Professional", "Postgres Pro", "postgres.exe", AssetSubtype.DATABASE_SERVER);
        assertCategory(extractor, "draw.io", "JGraph Ltd", "diagrams.net Desktop", "draw.io.exe", AssetSubtype.DESIGN_MODELING);
        assertCategory(extractor, "Figma", "Figma, Inc.", "Figma Desktop", "Figma.exe", AssetSubtype.DESIGN_MODELING);
        assertCategory(extractor, "WinDbg", "Microsoft Corporation", "WinDbg Debugger", "windbg.exe", AssetSubtype.DEV_TOOL);
        assertCategory(extractor, "КриптоПро CSP", "КРИПТО-ПРО", "КриптоПро CSP", "csptest.exe", AssetSubtype.CRYPTO_SOFTWARE);
    }

    @Test
    void lookalikeFamiliesAreSeparatedByMultipleAttributes() {
        FeatureExtractor extractor = new FeatureExtractor();
        assertCategory(extractor, "Microsoft Visual Studio 2022 Professional", "Microsoft Corporation",
                "Microsoft Visual Studio", "devenv.exe", AssetSubtype.IDE);
        assertCategory(extractor, "Microsoft Visual C++ 2015-2022 Redistributable", "Microsoft Corporation",
                "Visual C++ Redistributable", "", AssetSubtype.RUNTIME_PLATFORM);
        assertCategory(extractor, "Microsoft SQL Server 2022", "Microsoft Corporation",
                "Microsoft SQL Server", "sqlservr.exe", AssetSubtype.DATABASE_SERVER);
        assertCategory(extractor, "SQL Server Management Studio", "Microsoft Corporation",
                "SQL Server Management Studio", "Ssms.exe", AssetSubtype.DATABASE_TOOL);
        assertCategory(extractor, "Kaspersky Security Center Network Agent", "AO Kaspersky Lab",
                "Kaspersky Network Agent", "klnagent.exe", AssetSubtype.COMPONENT_AGENT);
        assertCategory(extractor, "Kaspersky Endpoint Security for Windows", "AO Kaspersky Lab",
                "Kaspersky Endpoint Security", "avp.exe", AssetSubtype.SECURITY_SOFTWARE);
    }

    @Test
    void componentIdentityWinsWhenDisplayNameIsMissingOrGeneric() {
        assertEquals(AssetSubtype.COMPONENT_AGENT, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Enterprise Software Component",
                "ksc.Publisher", "AO Kaspersky Lab",
                "ksc.Executables", "klnagent.exe")));

        assertEquals(AssetSubtype.COMPONENT_AGENT, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.Publisher", "Фирма 1С",
                "ksc.ProductFamily", "1C:Enterprise Server Agent")));

        assertEquals(AssetSubtype.COMPONENT_AGENT, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.Publisher", "Фирма 1С",
                "ksc.Executables", "ragent.exe;rmngr.exe")));

        assertEquals(AssetSubtype.COMPONENT_AGENT, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.Publisher", "Microsoft Corporation",
                "ksc.Executables", "msedgewebview2.exe")));
    }

    @Test
    void parentVendorAloneDoesNotInventSecurityOrBusinessSubtype() {
        assertNull(SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Enterprise Software Component",
                "ksc.Publisher", "AO Kaspersky Lab")));
        assertNull(SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Enterprise Software Component",
                "ksc.Publisher", "Doctor Web")));
        assertNull(SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Enterprise Software Component",
                "ksc.Publisher", "ESET, spol. s r.o.")));
        assertNull(SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Enterprise Software Component",
                "ksc.Publisher", "Фирма 1С")));
    }

    @Test
    void explicitParentProductsRemainClassified() {
        assertEquals(AssetSubtype.SECURITY_SOFTWARE, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.ProductFamily", "Kaspersky Endpoint Security",
                "ksc.Publisher", "AO Kaspersky Lab")));
        assertEquals(AssetSubtype.SECURITY_SOFTWARE, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.DisplayName", "Dr.Web Security Space",
                "ksc.Publisher", "Doctor Web")));
        assertEquals(AssetSubtype.BUSINESS_SOFTWARE, SoftwareEvidenceClassifier.classify(Map.of(
                "ksc.ProductFamily", "1С:Предприятие",
                "ksc.Publisher", "Фирма 1С")));
    }

    @Test
    void unknownSoftwareFallsBackToTypeOnly() throws Exception {
        AssetTypingContext ctx = context("Enterprise Software Component", "Unknown Vendor", "", "");
        var engine = new BitSetTypingEngine(
                RuleLoader.load(Path.of("rules/software-taxonomy-v3.yaml")),
                new FeatureExtractor(), ResolutionPolicy.CONSERVATIVE_80);
        var result = engine.classify(ctx);
        assertEquals(TypingStatus.AUTO_TYPE_ONLY, result.status());
        assertNull(result.subtype());
    }

    @Test
    void checkedInCatalogGeneratesBroadDeterministicWorkload() throws Exception {
        Path raw = temp.resolve("raw.jsonl");
        Path truth = temp.resolve("truth.jsonl");
        var report = new SoftwareTaxonomyV3Generator().generate(
                Path.of("data/software-catalog-v3.yaml"), 5_000, 20260916L,
                SoftwareTaxonomyV3Generator.Noise.CLEAN, raw, truth);
        assertTrue(report.catalogProducts() >= 50);
        assertTrue(report.subtypeCounts().size() >= 14);
        assertEquals(5_000, report.count());
        assertTrue(report.domesticSelections() > 0);
    }

    private static void assertCategory(FeatureExtractor extractor, String name, String publisher,
                                       String family, String executables, AssetSubtype subtype) {
        Map<String, Boolean> features = extractor.extract(context(name, publisher, family, executables));
        assertTrue(features.get("SOFTWARE_CATEGORY_" + subtype.name()), name + " -> " + subtype);
    }

    private static AssetTypingContext context(String name, String publisher, String family, String executables) {
        Map<String, String> attrs = new LinkedHashMap<>();
        if (!name.isBlank()) attrs.put("ksc.DisplayName", name);
        if (!publisher.isBlank()) attrs.put("ksc.Publisher", publisher);
        if (!family.isBlank()) attrs.put("ksc.ProductFamily", family);
        if (!executables.isBlank()) attrs.put("ksc.Executables", executables);
        return new AssetTypingContext("software-test", Set.of("KSC"),
                Set.of("ksc:software_inventory_application"), attrs, Map.of());
    }
}
