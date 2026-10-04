package com.audittrove.chat;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Soru-cevap: sayfa seçimi ve kod tarafı doğrulama. Model çağrısı yok; kurallar tek başına test edilir. */
class ReportChatServiceTest {

    private static final List<AuditResponse.PageContent> PAGES = List.of(
            new AuditResponse.PageContent(1, "KİRA SÖZLEŞMESİ\nMadde 1 - Taraflar. Kiraya veren Ali Veli, kiracı Ayşe Yılmaz."),
            new AuditResponse.PageContent(2, "Madde 3 - Kira bedeli. Aylık kira bedeli 42.500 TL olup her ayın 5'ine kadar ödenir.\nMadde 4 - Depozito 85.000 TL."),
            new AuditResponse.PageContent(3, "Madde 7 - Süre. Sözleşme 12 ay sürelidir, 01.10.2026 tarihinde başlar."),
            new AuditResponse.PageContent(4, "Madde 9 - Tebligat adresleri ve imzalar."));

    private static final AuditResponse REPORT = new AuditResponse(60, "", "Aylık kira 42.500 TL.",
            List.of(new AuditResponse.Risk("Depozito yüksek", "MEDIUM", "Depozito iki aylık kiradan fazla.", "85.000 TL depozito", List.of(2), AuditResponse.Risk.RUBRIC)),
            List.of(), List.of(new AuditResponse.KeyMetric("Aylık kira", "42.500", "TL", "")), List.of(), List.of(), "tr", 4);

    @Test
    void pagesAreRankedByQuestionOverlapAndFindingPages() {
        var picked = ReportChatService.selectPages("Depozito ne kadar?", PAGES, REPORT);
        // Küçük belge: tümü girer ama sayfa sırası korunur; seçim sıralaması puanla yapılır.
        assertThat(picked).extracting(AuditResponse.PageContent::page).containsExactly(1, 2, 3, 4);

        var big = new java.util.ArrayList<>(PAGES);
        for (int i = 5; i <= 20; i++) big.add(new AuditResponse.PageContent(i, "Ek " + i + " - genel hükümler."));
        var pickedBig = ReportChatService.selectPages("Kira bedeli aylık ne kadar ve depozito?", big, REPORT);
        // Büyük belgede yalnız örtüşen sayfalar: kira/depozito sayfası 2 kesin, 1 (başlık "kira") de girer; 5-20 girmez.
        assertThat(pickedBig).extracting(AuditResponse.PageContent::page).contains(2).doesNotContain(7, 15, 20);
        assertThat(pickedBig.size()).isLessThanOrEqualTo(ReportChatService.MAX_PAGES_IN_PROMPT);
    }

    @Test
    void numbersInTheAnswerMustExistInCitedPagesOrReport() {
        var sent = ReportChatService.selectPages("Depozito ne kadar?", PAGES, REPORT);
        var ok = ReportChatService.verify("Depozito 85.000 TL olarak belirlenmiştir.", List.of(2), true, sent, REPORT);
        assertThat(ok.grounded()).isTrue();
        assertThat(ok.pages()).containsExactly(2);

        // Biçim farkı sorun değil: 42,500 ile 42.500 aynı sayı.
        var fmt = ReportChatService.verify("Monthly rent is 42,500 TL.", List.of(2), true, sent, REPORT);
        assertThat(fmt.grounded()).isTrue();

        // Uydurulmuş sayı: belgede 95.000 yok → dayanaksız.
        var bad = ReportChatService.verify("Depozito 95.000 TL'dir.", List.of(2), true, sent, REPORT);
        assertThat(bad.grounded()).isFalse();
        assertThat(bad.answer()).isEqualTo("Depozito 95.000 TL'dir.");
    }

    @Test
    void citedPagesOutsideTheSentSetAreDroppedAndModelGroundedFalseStays() {
        var sent = ReportChatService.selectPages("Kira ne zaman başlıyor?", PAGES, REPORT);
        var r = ReportChatService.verify("Sözleşme 01.10.2026 tarihinde başlar.", List.of(3, 9), true, sent, REPORT);
        assertThat(r.pages()).containsExactly(3);
        assertThat(r.grounded()).isTrue();

        var notFound = ReportChatService.verify("Belgede bu bilgi yer almıyor.", List.of(), false, sent, REPORT);
        assertThat(notFound.grounded()).isFalse();
        assertThat(notFound.pages()).isEmpty();
    }

    @Test
    void emptyPagesStillWorkWithReportOnly() {
        var sent = ReportChatService.selectPages("Depozito?", List.of(), REPORT);
        assertThat(sent).isEmpty();
        var r = ReportChatService.verify("Depozito 85.000 TL.", List.of(), true, sent, REPORT);
        assertThat(r.grounded()).isTrue();
    }
}
