package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Alıntının belge üzerindeki yeri: satır sonuna denk gelse de bulunmalı, rapor dilinden etkilenmemeli. */
class EvidenceLocatorTest {

    private static PageText.Line line(String text, double y) {
        return new PageText.Line(text, new AuditResponse.Rect(0.0857, y, 0.8295, 0.0063));
    }

    /** Araç satış sözleşmesi: alıntı iki satıra bölünmüş ("... sold \"as" / "is\"."). */
    private static final PageText VEHICLE_PAGE = new PageText(1, List.of(
            line("Clause 2 - Condition of the Vehicle", 0.3900),
            line("2.1. The buyer accepts the vehicle as seen, inspected and test driven. The vehicle is sold \"as", 0.4059),
            line("is\".", 0.4338),
            line("2.2. The seller gives no undertaking as to the accuracy of the recorded mileage.", 0.4775)));

    @Test
    void quoteSplitAcrossTwoLinesIsFound() {
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "The vehicle is sold \"as is\".", VEHICLE_PAGE);
        assertThat(rects).hasSize(1);
        // İki satır birleşerek tek kutu olur: üstte 2.1 satırı, altta devamı.
        assertThat(rects.get(0).y()).isEqualTo(0.4059);
        assertThat(rects.get(0).y() + rects.get(0).h()).isGreaterThan(0.4338);
    }

    @Test
    void quoteIsFoundWhateverTheReportLanguage() {
        // Alıntı belgenin dilinde kalır; Türkçe rapor da aynı yeri göstermeli.
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "the vehicle is sold \"as is\"", VEHICLE_PAGE);
        assertThat(rects).hasSize(1);
        assertThat(rects.get(0).y()).isEqualTo(0.4059);
    }

    @Test
    void curlyQuotesAndDoubleSpacesDoNotBreakTheMatch() {
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "The  vehicle is sold “as is”.", VEHICLE_PAGE);
        assertThat(rects).isNotEmpty();
    }

    @Test
    void quoteThatIsNotOnThePageFindsNothing() {
        assertThat(EvidenceLocator.locateLiteral(
                "The seller provides a twelve month warranty.", VEHICLE_PAGE)).isEmpty();
    }

    @Test
    void tooShortQuoteIsNotSearchedLiterally() {
        // Kısa parça sayfada rastgele yerlere denk gelir; birebir arama için en az 12 karakter istenir.
        assertThat(EvidenceLocator.locateLiteral("as is", VEHICLE_PAGE)).isEmpty();
    }

    /** İki sütunlu denetçi raporu: satırlar yatay okunduğu için sağ sütun sola karışıyor,
     *  cümle kesintisiz geçmiyor. Alıntı yine de kendi bloğunda bulunmalı. */
    private static final PageText TWO_COLUMN_PAGE = new PageText(3, List.of(
            line("Kilit Denetim Konusu Denetimde Konunun Nasıl Ele Alındığı", 0.2000),
            line("2.1 numaralı dipnotta açıklandığı üzere, Grup'un Uygulanan denetim prosedürleri aşağıda", 0.2200),
            line("fonksiyonel para biriminin 31 Aralık 2024 tarihi açıklanmıştır;", 0.2400),
            line("itibari ile yüksek enflasyonlu ekonomi para birimi", 0.2600),
            line("olarak değerlendirilmesi sebebi ile Grup, \"TMS 29", 0.2800),
            line("Ticari alacakların tahsil edilebilirliği ayrıca değerlendirilmiştir.", 0.3400)));

    @Test
    void quoteInterleavedWithTheOtherColumnIsStillFound() {
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "2.1 numaralı dipnotta açıklandığı üzere, Grup'un fonksiyonel para biriminin "
                        + "31 Aralık 2024 tarihi itibari ile yüksek enflasyonlu ekonomi para birimi olarak "
                        + "değerlendirilmesi sebebi ile Grup, \"TMS 29 Yüksek Enflasyonlu Ekonomilerde "
                        + "Finansal Raporlama\" standardını uygulamaya devam etmektedir.",
                TWO_COLUMN_PAGE);
        assertThat(rects).isNotEmpty();
        // Blok 0.22 ile 0.28 arasında; alakasız son satır (0.34) kapsanmamalı.
        assertThat(rects.get(0).y()).isEqualTo(0.2200);
        assertThat(rects.get(0).y() + rects.get(0).h()).isLessThan(0.3400);
    }

    @Test
    void oneAccidentalFragmentIsNotEnough() {
        // Tek parça tutturan alıntı rastlantı olabilir; en az iki satır gerekir.
        assertThat(EvidenceLocator.locateLiteral(
                "Ticari alacakların tahsil edilebilirliği hakkında ayrı bir görüş verilmemiştir.",
                TWO_COLUMN_PAGE)).isEmpty();
    }

    @Test
    void fragmentsAreTakenFromTheClusterNotFromAcrossThePage() {
        // Benzer ifade sayfanin iki ayri yerinde geciyor. Cipa, parcalarin bir arada durdugu yere
        // oturmali; eskiden her parca icin sayfadaki ilk eslesme alindigi icin arada bir yere kayiyordu.
        PageText page = new PageText(1, List.of(
                line("2.2. Tedarikci, liste fiyatlarini yilda iki kez,", 0.100),
                line("otuz gun onceden bildirmek kaydiyla degistirebilir.", 0.120),
                line("Bu bolum bos birakilmistir.", 0.300),
                line("Ek-2: Tedarikci, liste fiyatlarini ayrica ilan eder.", 0.500),
                line("otuz gun onceden bildirmek kaydiyla duyurulur.", 0.520)));
        List<AuditResponse.Rect> rects = EvidenceLocator.locateLiteral(
                "Tedarikci, liste fiyatlarini yilda iki kez, otuz gun onceden bildirmek kaydiyla degistirebilir.",
                page);
        assertThat(rects).isNotEmpty();
        assertThat(rects.get(0).y()).isLessThan(0.2000);
    }
}
