package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;
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
 * Özet ve skor gerekçesi de bulgular gibi dayanak ister. Özetteki her cümle ya belgede geçen bir
 * sayıya ya da özel ada dayanır ya da rapordaki bir bulguyu anlatır; ikisi de değilse cümle çıkar.
 * Skor gerekçesi LLM'den alınmaz, skoru üreten bulgulardan kodda yazılır.
 */
public final class SummaryGate {
    private SummaryGate() {}

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+(?=\\p{Lu}|\\d|\"|“)");
    private static final Pattern ANCHOR_TOKEN = Pattern.compile("\\d[\\d.,]*\\d|\\d");
    private static final Pattern YEAR_LIKE = Pattern.compile("(?:19|20)\\d{2}");
    private static final Pattern PROPER_NOUN = Pattern.compile("\\b\\p{Lu}\\p{Ll}{3,}\\b");
    private static final Pattern WORD_TOKEN = Pattern.compile("\\p{L}{5,}");
    private static final int MIN_FINDING_OVERLAP = 2;
    private static final Locale TR = Locale.forLanguageTag("tr");

    /** Dayanaksız cümleler çıkarılmış özet; hiçbir cümle kalmazsa boş string. */
    public static String ground(String summary, Map<Integer, String> pages, List<AuditResponse.Risk> risks) {
        if (summary == null || summary.isBlank()) return "";
        Set<String> docNumbers = new HashSet<>();
        Set<String> docProperNouns = new HashSet<>();
        for (String page : pages.values()) {
            docNumbers.addAll(NumberText.digitKeys(page));
            docNumbers.addAll(NumberText.percentKeys(page));
            Matcher pn = PROPER_NOUN.matcher(page);
            while (pn.find()) docProperNouns.add(pn.group().toLowerCase(TR));
        }
        List<Set<String>> findingWords = new ArrayList<>();
        for (AuditResponse.Risk r : risks) {
            findingWords.add(words(r.title() + " " + r.finding() + " " + r.evidence()));
        }
        List<String> kept = new ArrayList<>();
        for (String sentence : SENTENCE_END.split(summary.trim())) {
            String s = sentence.trim();
            if (s.isEmpty()) continue;
            if (grounded(s, docNumbers, docProperNouns, findingWords)) kept.add(s);
        }
        return String.join(" ", kept);
    }

    /** Cümlenin dayanağı var mı: belgedeki sayı, belgedeki özel ad ya da bir bulguyla kelime örtüşmesi. */
    static boolean grounded(String sentence, Set<String> docNumbers, Set<String> docProperNouns,
                            List<Set<String>> findingWords) {
        Matcher m = ANCHOR_TOKEN.matcher(sentence);
        while (m.find()) {
            String tok = m.group();
            if (YEAR_LIKE.matcher(tok).matches()) continue;
            String key = NumberText.digits(tok);
            boolean decimal = tok.matches(".*[.,]\\d{1,2}$") && key.length() >= 2;
            if ((key.length() >= 3 || decimal) && docNumbers.contains(key)) return true;
        }
        for (String p : NumberText.percentKeys(sentence)) {
            if (docNumbers.contains(p)) return true;
        }
        Matcher pn = PROPER_NOUN.matcher(sentence);
        while (pn.find()) {
            if (docProperNouns.contains(pn.group().toLowerCase(TR))) return true;
        }
        Set<String> mine = words(sentence);
        for (Set<String> fw : findingWords) {
            int hits = 0;
            for (String w : mine) if (fw.contains(w)) hits++;
            if (hits >= MIN_FINDING_OVERLAP) return true;
        }
        return false;
    }

    private static Set<String> words(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) return out;
        Matcher m = WORD_TOKEN.matcher(text.toLowerCase(TR));
        while (m.find()) out.add(m.group());
        return out;
    }

    /** Özet tamamen düştüyse bulgulardan yazılan yedek özet; aynı bulgular her seferinde aynı cümleyi verir. */
    public static String fallbackSummary(List<AuditResponse.Risk> risks, Lang lang, int pageCount) {
        Counts c = Counts.of(risks);
        if (c.total == 0) {
            return lang.isTurkish()
                    ? pageCount + " sayfalık belgede dikkat gerektiren bir bulguya rastlanmadı."
                    : "No finding requiring attention was identified in the " + pageCount + "-page document.";
        }
        String head = lang.isTurkish()
                ? pageCount + " sayfalık belgede " + c.total + " bulgu tespit edildi (" + c.breakdown(lang) + ")."
                : c.total + (c.total == 1 ? " finding was" : " findings were") + " identified in the "
                        + pageCount + "-page document (" + c.breakdown(lang) + ").";
        String top = c.topTitle == null ? "" : lang.isTurkish()
                ? " En önemlisi: " + c.topTitle + "."
                : " The most significant: " + c.topTitle + ".";
        return head + top;
    }

    /** Skor gerekçesi skoru üreten şeyden yazılır: en ağır bulgunun seviyesi ve bulgu sayısı. */
    public static String rationale(List<AuditResponse.Risk> risks, Lang lang) {
        Counts c = Counts.of(risks);
        if (c.total == 0) {
            return lang.isTurkish()
                    ? "Skora giren bulgu yok; belge bu incelemede temiz görünüyor."
                    : "No finding counts toward the score; the document looks clean in this review.";
        }
        String level = severityName(c.topRank, lang);
        if (lang.isTurkish()) {
            return "Skoru " + level + " seviyedeki en ağır bulgu belirledi; toplam " + c.total + " bulgu ("
                    + c.breakdown(lang) + ") skora dahil edildi.";
        }
        return "The score is set by the most severe finding (" + level + "); " + c.total
                + (c.total == 1 ? " finding" : " findings") + " (" + c.breakdown(lang) + ") count toward it.";
    }

    private static String severityName(int rank, Lang lang) {
        return switch (rank) {
            case 4 -> lang.isTurkish() ? "kritik" : "critical";
            case 3 -> lang.isTurkish() ? "yüksek" : "high";
            case 2 -> lang.isTurkish() ? "orta" : "medium";
            default -> lang.isTurkish() ? "düşük" : "low";
        };
    }

    static int rank(String severity) {
        if (severity == null) return 0;
        return switch (severity.trim().toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 4;
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    // Skora giren bulguların seviye dağılımı; ek gözlemler (model) sayılmaz.
    private static final class Counts {
        int total, topRank;
        String topTitle;
        final int[] byRank = new int[5];

        static Counts of(List<AuditResponse.Risk> risks) {
            Counts c = new Counts();
            if (risks == null) return c;
            for (AuditResponse.Risk r : risks) {
                if (r.isModel()) continue;
                int rank = rank(r.severity());
                if (rank == 0) continue;
                c.total++;
                c.byRank[rank]++;
                if (rank > c.topRank) {
                    c.topRank = rank;
                    c.topTitle = r.title();
                }
            }
            return c;
        }

        String breakdown(Lang lang) {
            List<String> parts = new ArrayList<>();
            for (int rank = 4; rank >= 1; rank--) {
                if (byRank[rank] > 0) parts.add(byRank[rank] + " " + severityName(rank, lang));
            }
            return String.join(", ", parts);
        }
    }
}