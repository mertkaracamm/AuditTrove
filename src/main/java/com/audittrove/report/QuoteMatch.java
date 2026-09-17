package com.audittrove.report;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Belgeden kelimesi kelimesine alınan alıntının metinde aranması. Tek yerde durmasının sebebi:
 * aynı kural hem sayfa bulmada hem çıpa yerleştirmede gerekiyor, iki kopya zamanla ayrışıyor.
 *
 * İki aşama var. Önce alıntı olduğu gibi aranır. Bulunamazsa beşer kelimelik parçalara bölünüp
 * aranır; iki sütunlu sayfalarda satırlar birbirine karıştığı için cümle kesintisiz geçmez ama
 * parçaları kendi satırlarında durur.
 */
public final class QuoteMatch {
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final int MIN_CHARS = 12;
    private static final int WINDOW_WORDS = 5;
    private static final int WINDOW_STEP = 2;
    private static final int WINDOW_MIN_CHARS = 20;
    private static final int MIN_WINDOW_HITS = 2;

    private QuoteMatch() {
    }

    /**
     * Karşılaştırma için sadeleştirme: bitişik harfler ayrılır, boşluklar teke iner, tırnak çeşitleri
     * düzleşir, küçük harfe inilir.
     *
     * Bitişik harf (ligatür) adımı şart: PDF metni "tek taraﬂı" gibi tek karakterlik ﬂ taşıyabiliyor,
     * aynı cümlenin başka bir kopyası ise düz "taraflı" yazıyor. İkisi farklı string olduğu için aynı
     * madde iki ayrı bulgu olarak rapora giriyordu.
     */
    public static String flatten(String text) {
        if (text == null) return "";
        String flat = Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-')
                .replace(' ', ' ');
        return flat.replaceAll("\\s+", " ").trim().toLowerCase(TR);
    }

    /** Birebir arama için yeterince uzun mu. */
    public static boolean searchable(String flatQuote) {
        return flatQuote != null && flatQuote.length() >= MIN_CHARS;
    }

    /** Bu alıntı üzerinden karar verilebilir mi: boş ya da çok kısa alıntı belgede aranamaz. */
    public static boolean verifiable(String quote) {
        return searchable(flatten(quote));
    }

    /** Alıntının beşer kelimelik parçaları; kısa olanlar ayırt edici değil, atılır. */
    public static List<String> windows(String flatQuote) {
        List<String> out = new ArrayList<>();
        if (flatQuote == null) return out;
        String[] words = flatQuote.split(" ");
        for (int start = 0; start + WINDOW_WORDS <= words.length; start += WINDOW_STEP) {
            StringBuilder window = new StringBuilder(words[start]);
            for (int k = 1; k < WINDOW_WORDS; k++) window.append(' ').append(words[start + k]);
            if (window.length() >= WINDOW_MIN_CHARS) out.add(window.toString());
        }
        return out;
    }

    /** Alıntı bu metinde geçiyor mu: ya birebir ya da en az iki parçasıyla. */
    public static boolean occursIn(String quote, String text) {
        String needle = flatten(quote);
        if (!searchable(needle)) return false;
        String haystack = flatten(text);
        if (haystack.contains(needle)) return true;
        int hits = 0;
        for (String window : windows(needle)) {
            if (haystack.contains(window) && ++hits >= MIN_WINDOW_HITS) return true;
        }
        return false;
    }

    public static int minWindowHits() {
        return MIN_WINDOW_HITS;
    }
}
