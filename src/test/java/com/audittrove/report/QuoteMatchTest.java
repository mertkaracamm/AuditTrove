package com.audittrove.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Alıntı arama: bulgunun hangi sayfaya bağlanacağı ve belge üzerinde nereye çizileceği buna dayanır. */
class QuoteMatchTest {

    private static final String SAYFA_5 = """
            Dipnot 9 - Finansman Gelir ve Giderleri
            Finansman giderlerinin 1 840,6 milyon TL'si kredi faiz gideri, 1 054,2 milyon TL'si kur farkı
            gideri, 121,7 milyon TL'si kiralama faiz giderinden oluşmaktadır.
            """;

    // İki sütunlu denetçi raporu: satırlar yatay okunduğu için sağ sütun sola karışır.
    private static final String IKI_SUTUN = """
            2.1 numaralı dipnotta açıklandığı üzere, Grup'un uygulanan denetim prosedürleri aşağıda
            fonksiyonel para biriminin 31 Aralık 2024 tarihi açıklanmıştır;
            itibari ile yüksek enflasyonlu ekonomi para birimi
            olarak değerlendirilmesi sebebi ile Grup, "TMS 29
            """;

    @Test
    void verbatimQuoteIsFound() {
        assertThat(QuoteMatch.occursIn(
                "1 054,2 milyon TL'si kur farkı gideri", SAYFA_5)).isTrue();
    }

    @Test
    void quoteBrokenByLineEndIsFound() {
        // "kur farkı / gideri" iki satıra bölünmüş; satırlar birleştirilince bulunur.
        assertThat(QuoteMatch.occursIn(
                "1 054,2 milyon TL'si kur farkı gideri, 121,7 milyon TL'si kiralama faiz giderinden",
                SAYFA_5)).isTrue();
    }

    @Test
    void quoteInterleavedWithAnotherColumnIsFound() {
        String quote = "2.1 numaralı dipnotta açıklandığı üzere, Grup'un fonksiyonel para biriminin "
                + "31 Aralık 2024 tarihi itibari ile yüksek enflasyonlu ekonomi para birimi olarak "
                + "değerlendirilmesi sebebi ile Grup, \"TMS 29 Yüksek Enflasyonlu Ekonomilerde "
                + "Finansal Raporlama\" standardını uygulamaya devam etmektedir.";
        assertThat(QuoteMatch.occursIn(quote, IKI_SUTUN)).isTrue();
    }

    @Test
    void quoteFromAnotherPageIsNotFound() {
        assertThat(QuoteMatch.occursIn(
                "Grup aleyhine açılmış davaların toplam tutarı 412,6 milyon TL'dir.", SAYFA_5)).isFalse();
    }

    @Test
    void aSingleMatchingFragmentIsNotEnough() {
        // Tek parça rastlantı olabilir; en az iki parça aranır.
        assertThat(QuoteMatch.occursIn(
                "Finansman giderlerinin 1 840,6 milyon TL'si tamamen faiz swap işlemlerinden doğmuştur",
                SAYFA_5)).isFalse();
    }

    @Test
    void tooShortQuoteIsNotSearched() {
        assertThat(QuoteMatch.occursIn("kur farkı", SAYFA_5)).isFalse();
    }

    @Test
    void emptyOrShortQuoteIsNotVerifiable() {
        // Doğrulanamayan alıntı yüzünden bulgu düşürülmemeli; kural sadece aranabilir alıntılar için işler.
        assertThat(QuoteMatch.verifiable(null)).isFalse();
        assertThat(QuoteMatch.verifiable("   ")).isFalse();
        assertThat(QuoteMatch.verifiable("kur farkı")).isFalse();
    }

    @Test
    void aFullSentenceIsVerifiable() {
        assertThat(QuoteMatch.verifiable("Grup aleyhine açılmış davaların toplam tutarı 412,6 milyon TL'dir.")).isTrue();
    }

    @Test
    void curlyQuotesAndDoubleSpacesAreNormalised() {
        assertThat(QuoteMatch.flatten("  Grup’un  “kur” farkı ")).isEqualTo("grup'un \"kur\" farkı");
    }
}
