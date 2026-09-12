package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.NumberText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bulgu kanıtını sayfanın satırlarına bağlar. Kural, sayfa gerekçelendirmesiyle aynı: önce sayılar
 * (biçimden bağımsız rakam anahtarı), sayı yoksa ayırt edici kelimeler ve özel adlar. Kanıt raporun dilinde,
 * belge başka dilde olsa bile sayılar ve özel adlar değişmediği için eşleşme çoğu zaman tutar.
 * Eşleşme bulunamazsa dikdörtgen verilmez; arayüz sayfa kenarında şerit gösterir, yanlış yer boyamaz.
 */
public final class EvidenceLocator {
    private EvidenceLocator() {}

    private static final Pattern ANCHOR_TOKEN = Pattern.compile("\\d[\\d.,]*\\d|\\d");
    private static final Pattern YEAR_LIKE = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern WORD_TOKEN = Pattern.compile("\\p{L}{5,}");
    private static final Pattern PROPER_NOUN = Pattern.compile("\\b\\p{Lu}\\p{Ll}{3,}\\b");
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final int MAX_LINES = 6;
    private static final int WINDOW = 3;

    /** Her bulguya, sayfalarındaki kanıt yerlerini ekler. Sayfa metni yoksa bulgu olduğu gibi kalır. */
    public static AuditResponse annotate(AuditResponse response, Map<Integer, PageText> pages) {
        if (response == null || pages == null || pages.isEmpty()) return response;
        List<AuditResponse.Risk> out = new ArrayList<>();
        for (AuditResponse.Risk risk : response.risks()) {
            List<AuditResponse.Anchor> anchors = new ArrayList<>();
            for (Integer p : risk.pages()) {
                PageText page = pages.get(p);
                if (page == null) continue;
                List<AuditResponse.Rect> rects = locate(risk.evidence(), page);
                if (rects.isEmpty()) rects = locate(risk.finding(), page);
                anchors.add(new AuditResponse.Anchor(p, rects));
            }
            out.add(anchors.isEmpty() ? risk : risk.withAnchors(anchors));
        }
        return new AuditResponse(response.riskScore(), response.scoreRationale(), response.summary(), out,
                response.recommendations(), response.keyMetrics(), response.advisorQuestions(),
                response.references(), response.language(), response.pageCount());
    }

    /** Kanıt metninin sayfada geçtiği satırların dikdörtgenleri; ardışık satırlar tek dikdörtgende birleşir. */
    public static List<AuditResponse.Rect> locate(String evidence, PageText page) {
        if (evidence == null || evidence.isBlank() || page == null || page.lines().isEmpty()) return List.of();
        Set<String> numbers = numberKeys(evidence);
        Set<String> words = wordKeys(evidence);
        List<PageText.Line> lines = page.lines();
        int n = lines.size();
        int[] numberHits = new int[n];
        int[] wordHits = new int[n];
        List<Set<String>> wordsPerLine = new ArrayList<>(n);
        boolean anyNumber = false;
        for (int i = 0; i < n; i++) {
            String text = lines.get(i).text();
            if (!numbers.isEmpty()) {
                Set<String> keys = new HashSet<>(NumberText.digitKeys(text));
                keys.addAll(NumberText.percentKeys(text));
                for (String k : numbers) if (keys.contains(k)) numberHits[i]++;
                if (numberHits[i] > 0) anyNumber = true;
            }
            Set<String> hit = new HashSet<>();
            String lower = text.toLowerCase(TR);
            for (String w : words) if (lower.contains(w)) hit.add(w);
            wordsPerLine.add(hit);
            wordHits[i] = hit.size();
        }
        List<Integer> chosen = anyNumber ? byNumbers(numberHits, wordHits, lines) : byWords(wordsPerLine, words.size());
        return merge(chosen, lines);
    }

    // Sayı eşleşen satırlar: en çok sayı, sonra en çok kelime eşleşeni önde; en fazla MAX_LINES satır.
    // Sayının geçtiği satırın komşusu aynı cümleyi sürdürüyorsa (en az iki kelime eşleşmesi) o da boyanır;
    // "Madde 5 - Gecikme ..." başlığı ile "%0,5 oranında" satırı tek paragraf olarak görünür.
    // Tablo satırları paragraf değildir: komşu satır kendisi de sayı dolu bir satırsa (başka bir kalem) alınmaz.
    private static List<Integer> byNumbers(int[] numberHits, int[] wordHits, List<PageText.Line> lines) {
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < numberHits.length; i++) if (numberHits[i] > 0) idx.add(i);
        idx.sort((a, b) -> numberHits[b] != numberHits[a] ? numberHits[b] - numberHits[a] : wordHits[b] - wordHits[a]);
        if (idx.size() > MAX_LINES) idx = new ArrayList<>(idx.subList(0, MAX_LINES));
        Set<Integer> out = new LinkedHashSet<>(idx);
        for (int i : idx) {
            if (out.size() >= MAX_LINES) break;
            if (i > 0 && continuesSentence(wordHits[i - 1], lines.get(i - 1).text())) out.add(i - 1);
            if (i + 1 < numberHits.length && continuesSentence(wordHits[i + 1], lines.get(i + 1).text())) out.add(i + 1);
        }
        return new ArrayList<>(out);
    }

    private static boolean continuesSentence(int wordHits, String text) {
        return wordHits >= 2 && !com.audittrove.report.ReportGate.looksLikeRawRow(text);
    }

    // Sayı yoksa: ardışık üç satırlık pencerede en çok farklı kelime eşleşen yer. Kısa kanıtta iki, uzun kanıtta
    // üç farklı kelime eşleşmesi gerekir; azı rastlantıdır, boyanmaz. Pencerenin eşleşmesiz kenarları kırpılır.
    private static List<Integer> byWords(List<Set<String>> wordsPerLine, int evidenceWords) {
        int n = wordsPerLine.size();
        if (n == 0 || evidenceWords == 0) return List.of();
        int minHits = evidenceWords < 6 ? 2 : 3;
        int bestStart = -1, best = 0;
        for (int s = 0; s < n; s++) {
            Set<String> distinct = new HashSet<>();
            for (int i = s; i < Math.min(n, s + WINDOW); i++) distinct.addAll(wordsPerLine.get(i));
            if (distinct.size() > best) { best = distinct.size(); bestStart = s; }
        }
        if (bestStart < 0 || best < minHits) return List.of();
        int from = bestStart, to = Math.min(n, bestStart + WINDOW) - 1;
        // Paragraf pencereden uzunsa devamı da alınır: komşu satır en az iki kelime eşliyorsa dahil.
        while (to + 1 < n && to - from + 1 < MAX_LINES && wordsPerLine.get(to + 1).size() >= 2) to++;
        while (from - 1 >= 0 && to - from + 1 < MAX_LINES && wordsPerLine.get(from - 1).size() >= 2) from--;
        // Kenarlarda tek kelimeyle tutunan satır komşu paragrafa aittir; kalan hâlâ yeterliyse atılır.
        while (from < to && wordsPerLine.get(from).size() <= 1 && distinct(wordsPerLine, from + 1, to) >= minHits) from++;
        while (to > from && wordsPerLine.get(to).size() <= 1 && distinct(wordsPerLine, from, to - 1) >= minHits) to--;
        List<Integer> idx = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            if (!wordsPerLine.get(i).isEmpty()) idx.add(i);
        }
        return idx;
    }

    private static int distinct(List<Set<String>> wordsPerLine, int from, int to) {
        Set<String> all = new HashSet<>();
        for (int i = from; i <= to; i++) all.addAll(wordsPerLine.get(i));
        return all.size();
    }

    private static List<AuditResponse.Rect> merge(List<Integer> chosen, List<PageText.Line> lines) {
        if (chosen.isEmpty()) return List.of();
        List<Integer> sorted = new ArrayList<>(new LinkedHashSet<>(chosen));
        sorted.sort(Integer::compareTo);
        List<AuditResponse.Rect> out = new ArrayList<>();
        AuditResponse.Rect current = lines.get(sorted.get(0)).rect();
        int last = sorted.get(0);
        for (int k = 1; k < sorted.size(); k++) {
            int i = sorted.get(k);
            AuditResponse.Rect r = lines.get(i).rect();
            if (i == last + 1) {
                current = union(current, r);
            } else {
                out.add(current);
                current = r;
            }
            last = i;
        }
        out.add(current);
        return out;
    }

    private static AuditResponse.Rect union(AuditResponse.Rect a, AuditResponse.Rect b) {
        double x = Math.min(a.x(), b.x());
        double y = Math.min(a.y(), b.y());
        double right = Math.max(a.x() + a.w(), b.x() + b.w());
        double bottom = Math.max(a.y() + a.h(), b.y() + b.h());
        return new AuditResponse.Rect(x, y, right - x, bottom - y);
    }

    // Kanıttaki sayı çıpaları: en az üç rakam ya da ondalıklı; düz yıl çıpa değil. Yüzdeler ayrı anahtar.
    static Set<String> numberKeys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        Matcher m = ANCHOR_TOKEN.matcher(text);
        while (m.find()) {
            String tok = m.group();
            if (YEAR_LIKE.matcher(tok).matches()) continue;
            String key = NumberText.digits(tok);
            boolean decimal = tok.matches(".*[.,]\\d{1,2}$") && key.length() >= 2;
            if (key.length() >= 3 || decimal) keys.add(key);
        }
        keys.addAll(NumberText.percentKeys(text));
        return keys;
    }

    static Set<String> wordKeys(String text) {
        Set<String> words = new LinkedHashSet<>();
        Matcher m = WORD_TOKEN.matcher(text.toLowerCase(TR));
        while (m.find()) words.add(m.group());
        Matcher pn = PROPER_NOUN.matcher(text);
        while (pn.find()) words.add(pn.group().toLowerCase(TR));
        return words;
    }
}