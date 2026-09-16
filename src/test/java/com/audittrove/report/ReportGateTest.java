package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kanıt kapısı: ekrana cümle çıkar, tablo satırı çıkmaz. */
class ReportGateTest {

    @Test
    void tableRowsAreRecognised() {
        assertThat(ReportGate.looksLikeRawRow("Esas Faaliyet KARI 28.984.491 63.551.075")).isTrue();
        assertThat(ReportGate.looksLikeRawRow("Total assets 1,234,567 2,345,678")).isTrue();
        // Ondalıklı iki değer yan yana: boşluk binlik ayracı sanılıp tek sayı sayılıyordu, kapı açılmıyordu.
        assertThat(ReportGate.looksLikeRawRow("Net Income 402.5 612.1")).isTrue();
    }

    @Test
    void sentencesWithNumbersAreNotTableRows() {
        assertThat(ReportGate.looksLikeRawRow(
                "Aylık kira 42.500 TL'den 47.000 TL'ye yükseldi.")).isFalse();
        assertThat(ReportGate.looksLikeRawRow(
                "Revenue rose from 18 420,6 to 24 100,2 thousand TL in the period.")).isFalse();
        assertThat(ReportGate.looksLikeRawRow(
                "Madde 1.2'de 10 gün gecikme sonrası fesih hükmü yer almaktadır.")).isFalse();
    }

    @Test
    void spaceGroupedNumberStaysOneNumber() {
        // "18 420,6" tek sayıdır; iki sayı sayılırsa cümle tablo satırı sanılırdı.
        assertThat(ReportGate.looksLikeRawRow("Net borç 18 420,6")).isFalse();
    }

    @Test
    void tableRowEvidenceFallsBackToTheFindingSentence() {
        AuditResponse.Risk risk = new AuditResponse.Risk(
                "Kâr düşüşü", "HIGH",
                "Esas faaliyet kârı bir önceki döneme göre geriledi.",
                "Esas Faaliyet KARI 28.984.491 63.551.075",
                List.of(3), AuditResponse.Risk.ENGINE, "");
        AuditResponse.Risk gated = ReportGate.gateRisk(risk);
        assertThat(gated).isNotNull();
        assertThat(gated.evidence()).isEqualTo("Esas faaliyet kârı bir önceki döneme göre geriledi.");
    }

    @Test
    void findingAlsoATableRowDropsTheWholeRisk() {
        AuditResponse.Risk risk = new AuditResponse.Risk(
                "Kâr düşüşü", "HIGH",
                "Net Income 402.5 612.1",
                "Esas Faaliyet KARI 28.984.491 63.551.075",
                List.of(3), AuditResponse.Risk.ENGINE, "");
        assertThat(ReportGate.gateRisk(risk)).isNull();
    }

    @Test
    void aRateWrittenAsAPhraseIsNotShownAsACard() {
        // "Gunluk binde 3" sayi one gelmedigi icin ayristirilamiyor; karta yarim ifade basmiyoruz.
        assertThat(ReportGate.gateMetric(new AuditResponse.KeyMetric(
                "Gecikme Cezası", "Günlük binde 3", "", ""))).isNull();
        assertThat(ReportGate.gateMetric(new AuditResponse.KeyMetric(
                "Erken Kapama", "Kalan anaparanın %2'si", "", ""))).isNull();
    }

    @Test
    void aPlainTextValueAndALongDateStillPass() {
        assertThat(ReportGate.gateMetric(new AuditResponse.KeyMetric(
                "Denetçi Görüşü", "Olumlu görüş", "", ""))).isNotNull();
        assertThat(ReportGate.gateMetric(new AuditResponse.KeyMetric(
                "Bilanço Tarihi", "31 Aralık 2024", "", ""))).isNotNull();
    }
}
