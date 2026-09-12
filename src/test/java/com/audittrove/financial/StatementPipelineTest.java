package com.audittrove.financial;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Çıkarım → doğrulama → kural zincirinin belgeden bağımsız invariantları.
 * Fixture, KAP gelir tablosu yerleşimini taklit eden sentetik bir sayfadır (cari sütun önce, giderler eksili).
 */
class StatementPipelineTest {

    private static final String PAGE_13 = """
            Sunum Para Birimi 1.000 TL
            Dipnot Referansı
            Cari Dönem 01.01.2024 - 31.12.2024
            Önceki Dönem 01.01.2023 - 31.12.2023
            Hasılat 18 500.000.000 480.000.000
            Genel Yönetim Giderleri 19 -8.000.000 -9.000.000
            Pazarlama Giderleri 19 -13.000.000 -10.000.000
            ESAS FAALİYET KARI (ZARARI) 30.000.000 60.000.000
            Finansman Giderleri 23 -35.000.000 -43.000.000
            DÖNEM KARI (ZARARI) 40.000.000 70.000.000
            """;

    private static final Map<Integer, String> PAGES = Map.of(12, "Bilanço sayfası 1.234.567", 13, PAGE_13);

    private static final StatementExtraction.Unit THOUSAND_TRY = new StatementExtraction.Unit("TRY", "thousand");
    private static final StatementExtraction.Periods PERIODS =
            new StatementExtraction.Periods("01.01.2024 - 31.12.2024", "01.01.2023 - 31.12.2023");

    // LLM tutarları İngiliz biçimiyle yazmış olsun; belgede Türk biçimi var.
    private static StatementExtraction goodExtraction() {
        return new StatementExtraction(true, THOUSAND_TRY, PERIODS, List.of(
                item("operating_profit", "30,000,000", "60,000,000", 13),
                item("net_profit", "40,000,000", "70,000,000", 13),
                item("marketing_expenses", "-13,000,000", "-10,000,000", 13),
                item("admin_expenses", "-8,000,000", "-9,000,000", 13),
                item("finance_costs", "-35,000,000", "-43,000,000", 12)));
    }

    private static StatementExtraction.Item item(String key, String cur, String prev, int page) {
        return new StatementExtraction.Item(key, key, cur, prev, page);
    }

    @Test
    void directionComesFromNumbersAndUnitFromDeclaration() {
        var verified = StatementVerifier.verify(goodExtraction(), PAGES);
        assertThat(verified.items()).hasSize(5);
        assertThat(verified.unit()).isEqualTo(THOUSAND_TRY);

        var tr = FinancialRuleEngine.evaluate(verified, Lang.TR);
        assertThat(tr.findings()).extracting(AuditResponse.Risk::title).containsExactly(
                "Esas faaliyet kârı %50,0 oranında düştü",
                "Dönem kârı %42,9 oranında düştü",
                "Pazarlama giderleri %30,0 oranında arttı");
        // Finansman gideri %18,6 azaldı: eşik altı ve doğru yönde, bulgu yok ama kalem "cevaplanmış".
        assertThat(tr.covered()).contains(LineItemKey.FINANCE_COSTS, LineItemKey.ADMIN_EXPENSES);
        assertThat(tr.findings().get(0).evidence())
                .contains("60.000.000 bin TL").contains("30.000.000 bin TL").endsWith("[REPORT PAGE 13]");
    }

    @Test
    void sameNumbersGiveSameFindingsInBothLanguages() {
        var verified = StatementVerifier.verify(goodExtraction(), PAGES);
        var tr = FinancialRuleEngine.evaluate(verified, Lang.TR);
        var en = FinancialRuleEngine.evaluate(verified, Lang.EN);
        assertThat(en.findings()).hasSameSizeAs(tr.findings());
        assertThat(en.covered()).isEqualTo(tr.covered());
        assertThat(en.findings().get(0).evidence())
                .isEqualTo("Operating profit fell from 60,000,000 thousand TL to 30,000,000 thousand TL, a 50.0% decrease. [REPORT PAGE 13]");
    }

    @Test
    void wrongPageIsCorrectedToWhereNumbersActuallyAre() {
        var verified = StatementVerifier.verify(goodExtraction(), PAGES);
        var finance = verified.items().stream().filter(i -> i.key() == LineItemKey.FINANCE_COSTS).findFirst().orElseThrow();
        assertThat(finance.page()).isEqualTo(13);
    }

    @Test
    void numberNotInDocumentIsDropped() {
        var ex = new StatementExtraction(true, THOUSAND_TRY, PERIODS, List.of(
                item("operating_profit", "31,000,000", "60,000,000", 13)));
        assertThat(StatementVerifier.verify(ex, PAGES).items()).isEmpty();
    }

    @Test
    void swappedCurrentAndPreviousIsDroppedInsteadOfInvertingDirection() {
        // Değerler belgede var ama sütun sırası başlıkla çelişiyor: yön tersine dönerdi, kalem düşer.
        var ex = new StatementExtraction(true, THOUSAND_TRY, PERIODS, List.of(
                item("finance_costs", "-43,000,000", "-35,000,000", 13)));
        assertThat(StatementVerifier.verify(ex, PAGES).items()).isEmpty();
    }

    @Test
    void reversedPeriodHeadersRejectWholeExtraction() {
        var reversed = new StatementExtraction.Periods("2023", "2024");
        var ex = new StatementExtraction(true, THOUSAND_TRY, reversed, goodExtraction().items());
        assertThat(StatementVerifier.verify(ex, PAGES).isEmpty()).isTrue();
    }

    @Test
    void unknownUnitIsOmittedNotInvented() {
        var ex = new StatementExtraction(true, new StatementExtraction.Unit("TRY", "lakh"), PERIODS, goodExtraction().items());
        var verified = StatementVerifier.verify(ex, PAGES);
        assertThat(verified.unit()).isNull();
        var en = FinancialRuleEngine.evaluate(verified, Lang.EN);
        assertThat(en.findings().get(0).evidence()).startsWith("Operating profit fell from 60,000,000 to 30,000,000,");
    }

    @Test
    void llmFindingAboutCoveredItemYieldsToRuleAnswer() {
        var covered = FinancialRuleEngine.evaluate(StatementVerifier.verify(goodExtraction(), PAGES), Lang.EN).covered();
        var wrongLlm = new AuditResponse.Risk("Rising finance costs", "MEDIUM",
                "Finance costs increased 18%", "Finance costs increased 18% [REPORT PAGE 13]");
        var unrelated = new AuditResponse.Risk("Related party concentration", "MEDIUM",
                "80% of sales to related parties", "Sales to related parties 80% (Page 3)");
        assertThat(FinancialRuleEngine.coveredByRules(wrongLlm, covered)).isTrue();
        assertThat(FinancialRuleEngine.coveredByRules(unrelated, covered)).isFalse();
    }

    @Test
    void numberTextReadsAllPrintedForms() {
        assertThat(NumberText.parse("19.917,1")).isEqualTo(19917.1);
        assertThat(NumberText.parse("12,345.6")).isEqualTo(12345.6);
        assertThat(NumberText.parse("12,345")).isEqualTo(12345.0);
        assertThat(NumberText.parse("74,7")).isEqualTo(74.7);
        assertThat(NumberText.parse("(2.609,7)")).isEqualTo(-2609.7);
        assertThat(NumberText.parse("-8.799.018")).isEqualTo(-8799018.0);
        assertThat(NumberText.digits("28.984.491")).isEqualTo(NumberText.digits("28,984,491"));
    }

    @Test
    void langResolvesLooselyAndDefaultsToEnglish() {
        assertThat(Lang.of("tr")).isEqualTo(Lang.TR);
        assertThat(Lang.of("TR-tr")).isEqualTo(Lang.TR);
        assertThat(Lang.of("en-US")).isEqualTo(Lang.EN);
        assertThat(Lang.of(null)).isEqualTo(Lang.EN);
    }
}