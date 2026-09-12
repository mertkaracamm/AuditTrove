package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Kanıt → sayfa üzerindeki satırlar. PDF'siz: satırlar ve kutuları elle verilir, kural test edilir. */
class EvidenceLocatorTest {

    private static PageText.Line line(String text, int row) {
        return new PageText.Line(text, new AuditResponse.Rect(0.1, 0.1 + row * 0.02, 0.8, 0.015));
    }

    private static final PageText STATEMENT = new PageText(13, List.of(
            line("Kar veya Zarar Tablosu", 0),
            line("Hasılat 18 594.995.138 594.705.176", 1),
            line("Genel Yönetim Giderleri 19 -8.799.018 -9.282.832", 2),
            line("ESAS FAALİYET KARI (ZARARI) 28.984.491 63.551.075", 3),
            line("Finansman Giderleri 23 -35.349.681 -43.205.414", 4),
            line("DÖNEM KARI (ZARARI) 38.863.566 70.826.085", 5)));

    private static final PageText CONTRACT = new PageText(1, List.of(
            line("Madde 3 - Kira bedeli. Aylık kira bedeli 42.500 TL olup her ayın 5'ine kadar peşin", 0),
            line("ödenir. Yıllık artış, TÜİK tarafından açıklanan on iki aylık TÜFE ortalaması oranında", 1),
            line("uygulanır.", 2),
            line("Madde 4 - Depozito. Kiracı 2 (iki) aylık kira bedeli tutarında, 85.000 TL depozito verir.", 3),
            line("Madde 5 - Gecikme. Kira bedelinin geç ödenmesi halinde geciken her gün için aylık", 4),
            line("kiranın %0,5'i oranında gecikme bedeli uygulanır. İki ay üst üste ödeme yapılmaması", 5),
            line("halinde kiraya veren sözleşmeyi tek taraflı feshedebilir.", 6),
            line("Madde 6 - Giderler. Aidat, elektrik, su, doğalgaz ve internet giderleri kiracıya aittir.", 7)));

    @Test
    void numbersInEvidenceSelectTheStatementRowWhateverTheFormat() {
        var rects = EvidenceLocator.locate("Operating profit fell from 63,551,075 thousand TL to 28,984,491 thousand TL, a 54.4% decrease.", STATEMENT);
        assertThat(rects).hasSize(1);
        assertThat(rects.get(0).y()).isCloseTo(0.16, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void wordsSelectTheParagraphAndTrimTheNeighbour() {
        var rects = EvidenceLocator.locate(
                "Sözleşme, kira bedelinin geç ödenmesi halinde her gün için aylık kiranın %0,5'i oranında gecikme bedeli öngörür.", CONTRACT);
        // %0,5 yüzde çıpası + kelimeler: Madde 5'in üç satırı tek dikdörtgen; Madde 4 ve 6 dışarıda.
        assertThat(rects).hasSize(1);
        assertThat(rects.get(0).y()).isCloseTo(0.18, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(rects.get(0).h()).isCloseTo(0.055, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void tableNeighboursAreNotPulledIntoTheHighlight() {
        // Komşu satırlar aynı kelimeleri taşısa da ("Esas Faaliyet...") sayı dolu tablo satırıdır, boyanmaz.
        var rects = EvidenceLocator.locate("Esas faaliyet kârı 63.551.075'ten 28.984.491'e geriledi.", new PageText(13, List.of(
                line("Esas Faaliyetlerden Diğer Gelirler 21 12.818.008 22.889.377", 0),
                line("Esas Faaliyetlerden Diğer Giderler 21 -12.989.018 -12.246.901", 1),
                line("ESAS FAALİYET KARI (ZARARI) 28.984.491 63.551.075", 2),
                line("Yatırım Faaliyetlerinden Gelirler 30 6.934.094 4.149.642", 3))));
        assertThat(rects).hasSize(1);
        assertThat(rects.get(0).h()).isCloseTo(0.015, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void unrelatedEvidenceIsNotPainted() {
        assertThat(EvidenceLocator.locate("The auditor expressed an unqualified opinion on the consolidated statements.", CONTRACT)).isEmpty();
        assertThat(EvidenceLocator.locate("", CONTRACT)).isEmpty();
    }

    @Test
    void crossLanguageEvidenceStillAnchorsOnNumbers() {
        var rects = EvidenceLocator.locate("Faaliyet kârı 63.551.075'ten 28.984.491'e geriledi.", STATEMENT);
        assertThat(rects).hasSize(1);
    }

    @Test
    void annotateAddsAnchorsPerPageAndKeepsRisksWithoutPages() {
        var risks = List.of(
                new AuditResponse.Risk("Decline", "MEDIUM", "f", "Operating profit fell from 63,551,075 to 28,984,491.", List.of(13), AuditResponse.Risk.ENGINE),
                new AuditResponse.Risk("Note", "LOW", "f", "no pages", List.of(), AuditResponse.Risk.MODEL),
                new AuditResponse.Risk("Missing", "LOW", "f", "Something the page does not say at all here.", List.of(13), AuditResponse.Risk.RUBRIC));
        var r = new AuditResponse(60, "", "", risks, List.of(), List.of(), List.of(), List.of(), "en", 20);
        var out = EvidenceLocator.annotate(r, Map.of(13, STATEMENT));
        assertThat(out.risks().get(0).anchors()).hasSize(1);
        assertThat(out.risks().get(0).anchors().get(0).page()).isEqualTo(13);
        assertThat(out.risks().get(0).anchors().get(0).rects()).hasSize(1);
        assertThat(out.risks().get(1).anchors()).isEmpty();
        // Sayfa var ama satır bulunamadı: çıpa sayfayı taşır, dikdörtgen boş (arayüz kenar şeridi gösterir).
        assertThat(out.risks().get(2).anchors()).hasSize(1);
        assertThat(out.risks().get(2).anchors().get(0).rects()).isEmpty();
    }
}