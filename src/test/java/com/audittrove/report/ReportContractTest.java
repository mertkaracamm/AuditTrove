package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sayfa atfı ve dil sözleşmesinin belgeden bağımsız invariantları. */
class ReportContractTest {

    @Test
    void everyMarkerFormIsStrippedAndParsed() {
        var p = PageRefs.strip("Söz konusu varlıklar yönetimin tahminlerine bağlıdır [REPORT PAGE 6-7]. (Page 6)");
        assertThat(p.text()).isEqualTo("Söz konusu varlıklar yönetimin tahminlerine bağlıdır.");
        assertThat(p.pages()).containsExactly(6, 7);

        assertThat(PageRefs.strip("Hedge losses of 24,833,723 thousand TL [REPORT PAGES 5-6]").pages()).containsExactly(5, 6);
        assertThat(PageRefs.strip("Net borç arttı (Sayfa 5, 11)").pages()).containsExactly(5, 11);
        assertThat(PageRefs.strip("Net borç arttı (Rapor Sayfa 8)").pages()).containsExactly(8);
        assertThat(PageRefs.strip("Costs rose (Report Page 10) and (pp. 12-13)").pages()).containsExactly(10, 12, 13);
        assertThat(PageRefs.strip("Costs rose [report page 3, 9]").pages()).containsExactly(3, 9);
        assertThat(PageRefs.strip("Sürekliliğe dikkat çekilir (Sayfa 2 ve 3)").pages()).containsExactly(2, 3);
        assertThat(PageRefs.strip("Finansman gideri arttı (Rapor Sayfası 1)").pages()).containsExactly(1);
        assertThat(PageRefs.strip("Covenant breached (Page 2 of 2)").pages()).containsExactly(2);
        assertThat(PageRefs.strip("Covenant breached (Pages 1 and 2)").text()).isEqualTo("Covenant breached");
    }

    @Test
    void unreasonableRangeKeepsOnlyEndpoints() {
        assertThat(PageRefs.strip("x [REPORT PAGES 3-40]").pages()).containsExactly(3, 40);
    }

    @Test
    void textWithoutMarkersIsUntouched() {
        var p = PageRefs.strip("Sales to related parties constitute 80% of total sales (2023: 75%).");
        assertThat(p.text()).isEqualTo("Sales to related parties constitute 80% of total sales (2023: 75%).");
        assertThat(p.pages()).isEmpty();
    }

    @Test
    void languageCheckNamesTheFieldThatLeaks() {
        var r = new AuditResponse(60, "The report shows a decline in profit and rising leverage.",
                "The consolidated statements for 2024 show a marked decline in operating profit and net income.",
                List.of(new AuditResponse.Risk("Related party concentration", "MEDIUM", "",
                        "Grup'un satışlarının %80'ini ilişkili taraflara yapmakta olduğunu ve alacaklarının %65'inin bu taraflardan oluştuğunu açıklayan dipnota dikkat çekilir.")),
                List.of("Evaluate the credit risk arising from the concentration of sales with related parties."),
                List.of(), List.of(), List.of());
        assertThat(LanguageCheck.mismatches(r, Lang.EN)).containsExactly("risk[1].evidence");
        assertThat(LanguageCheck.mismatches(r, Lang.TR)).contains("summary", "scoreRationale", "recommendation[1]");
    }

    @Test
    void acronymsAndTurkishNounsDoNotFlipEnglishSentences() {
        assertThat(LanguageCheck.detect("The contract specifies that the annual rent increase is applied according to the CPI announced by TÜİK.")).isEqualTo(Lang.EN);
        assertThat(LanguageCheck.detect("Yıllık artış, TÜİK tarafından açıklanan on iki aylık TÜFE ortalaması oranında uygulanır.")).isEqualTo(Lang.TR);
        assertThat(LanguageCheck.detect("Article 3 states the monthly rent is 42,500 TL, payable in advance by the 5th of each month.")).isEqualTo(Lang.EN);
    }

    @Test
    void shortOrNumericTextIsNotJudged() {
        assertThat(LanguageCheck.detect("28.984.491 63.551.075")).isNull();
        assertThat(LanguageCheck.detect("Operating profit 2024")).isNull();
    }

    @Test
    void rawTableRowIsNotEvidence() {
        assertThat(ReportGate.looksLikeRawRow("Esas Faaliyet KARI (ZARARI) 28.984.491 63.551.075; Net Dönem Karı veya Zararı 38.863.566 70.826.085 .")).isTrue();
        assertThat(ReportGate.looksLikeRawRow("Operating profit fell from 63,551,075 thousand TL to 28,984,491 thousand TL, a 54.4% decrease.")).isFalse();
        assertThat(ReportGate.looksLikeRawRow("Sales to related parties constitute 80% of total sales (2023: 75%).")).isFalse();

        var raw = new AuditResponse.Risk("Decline", "MEDIUM", "Operating profit and net income fell sharply in 2024.",
                "Esas Faaliyet KARI (ZARARI) 28.984.491 63.551.075; Net Dönem Karı 38.863.566 70.826.085", List.of(13));
        assertThat(ReportGate.gateRisk(raw).evidence()).isEqualTo("Operating profit and net income fell sharply in 2024.");
        var hopeless = new AuditResponse.Risk("Decline", "MEDIUM", "28.984.491 63.551.075", "38.863.566 70.826.085", List.of(13));
        assertThat(ReportGate.gateRisk(hopeless)).isNull();
    }

    @Test
    void metricValueIsNumberAndUnitIsSeparate() {
        var embedded = ReportGate.gateMetric(new AuditResponse.KeyMetric("Revenue", "594,995,138 thousand TL", "", ""));
        assertThat(embedded.value()).isEqualTo("594,995,138");
        assertThat(embedded.unit()).isEqualTo("thousand TL");

        var pct = ReportGate.gateMetric(new AuditResponse.KeyMetric("Related party sales", "%80", "", ""));
        assertThat(pct.value()).isEqualTo("80");
        assertThat(pct.unit()).isEqualTo("%");

        var date = ReportGate.gateMetric(new AuditResponse.KeyMetric("Period end", "31 Aralık 2024", "", ""));
        assertThat(date.value()).isEqualTo("31 Aralık 2024");

        var sentence = ReportGate.gateMetric(new AuditResponse.KeyMetric("Opinion", "The auditor issued an unqualified opinion on the statements.", "", ""));
        assertThat(sentence).isNull();

        var keepsGivenUnit = ReportGate.gateMetric(new AuditResponse.KeyMetric("Net income", "38.863.566", "bin TL", "x"));
        assertThat(keepsGivenUnit.unit()).isEqualTo("bin TL");

        // Önde para birimi: "EUR 84,000", "£1,250", "EUR 84,000 gross"
        var prefixed = ReportGate.gateMetric(new AuditResponse.KeyMetric("Base Salary", "EUR 84,000", "", ""));
        assertThat(prefixed.value()).isEqualTo("84,000");
        assertThat(prefixed.unit()).isEqualTo("EUR");
        var symbol = ReportGate.gateMetric(new AuditResponse.KeyMetric("Rent", "£1,250", "", ""));
        assertThat(symbol.value()).isEqualTo("1,250");
        assertThat(symbol.unit()).isEqualTo("£");
        var both = ReportGate.gateMetric(new AuditResponse.KeyMetric("Salary", "EUR 84,000 gross", "", ""));
        assertThat(both.value()).isEqualTo("84,000");
        assertThat(both.unit()).isEqualTo("EUR gross");
    }

    @Test
    void rubricItemsAreFixedPerKindAndBothLanguages() {
        assertThat(RubricItem.forKind(RubricItem.Kind.FINANCIAL)).contains(RubricItem.FIN_GOING_CONCERN, RubricItem.FIN_AUDIT_OPINION_MODIFIED);
        assertThat(RubricItem.forKind(RubricItem.Kind.RENTAL)).doesNotContain(RubricItem.FIN_GOING_CONCERN);
        assertThat(RubricItem.FIN_GOING_CONCERN.severity()).isEqualTo("HIGH");
        assertThat(RubricItem.FIN_GOING_CONCERN.title(Lang.TR)).isEqualTo("İşletmenin sürekliliğine ilişkin önemli belirsizlik");
        assertThat(RubricItem.FIN_GOING_CONCERN.title(Lang.EN)).isEqualTo("Material uncertainty about going concern");
        assertThat(RubricItem.fromId("rent_auto_renewal")).isEqualTo(RubricItem.RENT_AUTO_RENEWAL);
        assertThat(RubricItem.kindOf("subscription")).isEqualTo(RubricItem.Kind.SUBSCRIPTION);
        assertThat(RubricItem.kindOf("nonsense")).isEqualTo(RubricItem.Kind.OTHER);
    }

    @Test
    void summarySentencesNeedAnAnchorInTheDocumentOrAFinding() {
        var pages = Map.of(
                1, "Northwind Traders Inc. Consolidated Statement of Income. Revenue 594,995,138 2024 2023",
                2, "Operating profit 28,984,491 63,551,075. Net income 38,863,566 70,826,085.");
        var risks = List.of(new AuditResponse.Risk("Operating profit decline", "MEDIUM",
                "Operating profit fell from 63,551,075 to 28,984,491, a 54.4% decrease.",
                "Operating profit fell from 63,551,075 to 28,984,491, a 54.4% decrease.", List.of(2), AuditResponse.Risk.ENGINE));
        String summary = "Northwind Traders reported revenue of 594,995,138 for 2024. "
                + "Operating profit declined sharply compared with the prior year. "
                + "The company generated positive net cash flows from operations and maintains a strong liquidity position. "
                + "Management expects margins to recover next year.";
        String grounded = SummaryGate.ground(summary, pages, risks);
        assertThat(grounded).contains("revenue of 594,995,138");
        assertThat(grounded).contains("Operating profit declined sharply");
        assertThat(grounded).doesNotContain("positive net cash flows");
        assertThat(grounded).doesNotContain("Management expects");

        // Rapor dili belge dilinden farklıysa sayı ve özel ad yine dayanak olur.
        String tr = "Northwind Traders 2024 yılında 594.995.138 gelir açıkladı. Şirket güçlü bir likidite pozisyonunu korumaktadır.";
        assertThat(SummaryGate.ground(tr, pages, risks)).isEqualTo("Northwind Traders 2024 yılında 594.995.138 gelir açıkladı.");
        assertThat(SummaryGate.ground("Görünüm olumludur.", pages, risks)).isEmpty();
    }

    @Test
    void rationaleAndFallbackSummaryAreWrittenFromTheCountedFindings() {
        var risks = List.of(
                new AuditResponse.Risk("Material uncertainty about going concern", "HIGH", "f", "e", List.of(2), AuditResponse.Risk.RUBRIC),
                new AuditResponse.Risk("Operating profit decline", "MEDIUM", "f", "e", List.of(2), AuditResponse.Risk.ENGINE),
                new AuditResponse.Risk("Extra observation", "HIGH", "f", "e", List.of(1), AuditResponse.Risk.MODEL));
        assertThat(SummaryGate.rationale(risks, Lang.TR))
                .isEqualTo("Skoru yüksek seviyedeki en ağır bulgu belirledi; toplam 2 bulgu (1 yüksek, 1 orta) skora dahil edildi.");
        assertThat(SummaryGate.rationale(risks, Lang.EN))
                .isEqualTo("The score is set by the most severe finding (high); 2 findings (1 high, 1 medium) count toward it.");
        assertThat(SummaryGate.fallbackSummary(risks, Lang.TR, 14))
                .isEqualTo("14 sayfalık belgede 2 bulgu tespit edildi (1 yüksek, 1 orta). En önemlisi: Material uncertainty about going concern.");
        assertThat(SummaryGate.rationale(List.of(), Lang.EN)).contains("No finding counts toward the score");
        assertThat(SummaryGate.fallbackSummary(List.of(), Lang.TR, 3)).isEqualTo("3 sayfalık belgede dikkat gerektiren bir bulguya rastlanmadı.");
    }

    @Test
    void modelFindingsCarrySourceAndDefault() {
        var free = new AuditResponse.Risk("x", "MEDIUM", "f", "e");
        assertThat(free.source()).isEqualTo(AuditResponse.Risk.MODEL);
        assertThat(free.isModel()).isTrue();
        var rubric = new AuditResponse.Risk("x", "HIGH", "f", "e", List.of(2), AuditResponse.Risk.RUBRIC);
        assertThat(rubric.isModel()).isFalse();
    }
}