package com.audittrove.financial;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM çıkarımını belgeye karşı doğrular. Kural tek: belgede harfiyen geçmeyen sayı rapora giremez.
 * Sayfa yanlışsa düzeltilir, sayı hiçbir sayfada yoksa kalem düşer, dönem sırası tersse
 * çıkarımın tamamı reddedilir (yanlış yönlü bulgu üretmekten bulgu üretmemek iyidir).
 */
public final class StatementVerifier {
    private StatementVerifier() {}

    private static final Pattern YEAR = Pattern.compile("(?:19|20)\\d{2}");
    private static final Set<String> SCALES = Set.of("units", "thousand", "million", "billion");

    /** Doğrulanmış kalem: artık sayı, sayfa da belgede bulunduğu yer. */
    public record VerifiedItem(LineItemKey key, String label, double current, double previous, int page,
                               String currentKey, String previousKey) {
        public VerifiedItem(LineItemKey key, String label, double current, double previous, int page) {
            this(key, label, current, previous, page, "", "");
        }
    }

    public record VerifiedStatement(StatementExtraction.Unit unit, List<VerifiedItem> items) {
        public boolean isEmpty() {
            return items.isEmpty();
        }
    }

    public static VerifiedStatement verify(StatementExtraction extraction, Map<Integer, String> pages) {
        if (extraction == null || !extraction.found() || pages == null || pages.isEmpty()) {
            return new VerifiedStatement(null, List.of());
        }
        if (periodsReversed(extraction.periods())) {
            return new VerifiedStatement(null, List.of());
        }
        Map<Integer, Set<String>> keysByPage = new HashMap<>();
        for (Map.Entry<Integer, String> e : pages.entrySet()) {
            keysByPage.put(e.getKey(), NumberText.digitKeys(e.getValue()));
        }
        Set<LineItemKey> seen = EnumSet.noneOf(LineItemKey.class);
        List<VerifiedItem> out = new ArrayList<>();
        for (StatementExtraction.Item item : extraction.items()) {
            LineItemKey key = item.lineItem();
            if (key == null || !seen.add(key)) continue;
            Double cur = NumberText.parse(item.current());
            Double prev = NumberText.parse(item.previous());
            if (cur == null || prev == null) continue;
            int page = locate(item, keysByPage);
            if (page < 0) continue;
            if (!columnOrderConsistent(item, pages.get(page), extraction.periods())) continue;
            out.add(new VerifiedItem(key, item.label(), cur, prev, page,
                    NumberText.digits(item.current()), NumberText.digits(item.previous())));
        }
        StatementExtraction.Unit unit = cleanUnit(extraction.unit());
        if (unit == null) unit = declaredUnit(pages);
        return new VerifiedStatement(unit, canonicalPage(out, keysByPage));
    }

    // Tablonun birim beyanı: "1.000 TL", "Bin TL", "TL Thousand", "in thousands of USD", "Milyon TL",
    // "in millions of U.S. dollars", "'000". Çıkarım birim vermediyse buradan okunur; bulunamazsa birim yok.
    private static final Pattern DECLARED_UNIT = Pattern.compile(
            "(?i)(?:\\b(bin|thousand|thousands|'000|000s|milyon|million|millions|milyar|billion|billions)\\b[^\\n]{0,40}?\\b(TL|TRY|₺|USD|US\\$|\\$|dollars?|EUR|€|euros?|GBP|£|pounds?)\\b"
            + "|\\b(TL|TRY|USD|EUR|GBP)\\s*(bin|thousand|milyon|million|milyar|billion)\\b"
            + "|\\b1[.,]000\\s*(TL|TRY|USD|EUR|GBP)\\b)");

    static StatementExtraction.Unit declaredUnit(Map<Integer, String> pages) {
        for (String page : pages.values()) {
            Matcher m = DECLARED_UNIT.matcher(page);
            if (!m.find()) continue;
            String scaleWord = m.group(1) != null ? m.group(1) : m.group(4) != null ? m.group(4) : "thousand";
            String currencyWord = m.group(2) != null ? m.group(2) : m.group(3) != null ? m.group(3) : m.group(5);
            String scale = switch (scaleWord.toLowerCase()) {
                case "bin", "thousand", "thousands", "'000", "000s" -> "thousand";
                case "milyon", "million", "millions" -> "million";
                default -> "billion";
            };
            String currency = switch (currencyWord.toLowerCase()) {
                case "tl", "try", "₺" -> "TRY";
                case "usd", "us$", "$", "dollar", "dollars" -> "USD";
                case "eur", "€", "euro", "euros" -> "EUR";
                default -> "GBP";
            };
            return new StatementExtraction.Unit(currency, scale);
        }
        return null;
    }

    // Aynı tutar birden fazla sayfada geçebilir (bilanço + gelir tablosu). Kalemlerin çoğunluğunun
    // bulunduğu sayfa gelir tablosudur; sayıları orada da geçen her kalem o sayfaya bağlanır.
    private static List<VerifiedItem> canonicalPage(List<VerifiedItem> items, Map<Integer, Set<String>> keysByPage) {
        if (items.size() < 2) return List.copyOf(items);
        Map<Integer, Integer> votes = new HashMap<>();
        for (VerifiedItem it : items) votes.merge(it.page(), 1, Integer::sum);
        int statementPage = votes.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).orElse(-1);
        Set<String> keys = keysByPage.getOrDefault(statementPage, Set.of());
        List<VerifiedItem> out = new ArrayList<>();
        for (VerifiedItem it : items) {
            boolean there = keys.contains(it.currentKey()) && keys.contains(it.previousKey());
            out.add(it.page() != statementPage && there
                    ? new VerifiedItem(it.key(), it.label(), it.current(), it.previous(), statementPage,
                                       it.currentKey(), it.previousKey())
                    : it);
        }
        return List.copyOf(out);
    }

    // Her iki tutar da aynı sayfada geçmeli. Önce söylenen sayfaya, sonra tüm sayfalara bakılır.
    private static int locate(StatementExtraction.Item item, Map<Integer, Set<String>> keysByPage) {
        Set<String> claimed = keysByPage.get(item.page());
        if (claimed != null && bothOccur(item, claimed)) return item.page();
        for (Map.Entry<Integer, Set<String>> e : keysByPage.entrySet()) {
            if (bothOccur(item, e.getValue())) return e.getKey();
        }
        return -1;
    }

    private static boolean bothOccur(StatementExtraction.Item item, Set<String> keys) {
        return NumberText.occursIn(item.current(), keys) && NumberText.occursIn(item.previous(), keys);
    }

    // Satırdaki iki tutarın sırası, sayfa başlığındaki dönem sırasıyla aynı olmalı. Çıkarım cari ile
    // önceki değeri yer değiştirmişse yön tersine döner; bunu yakalayıp kalemi düşürürüz.
    // Yıl ya da satır bulunamıyorsa karar verilemez, kalem korunur.
    static boolean columnOrderConsistent(StatementExtraction.Item item, String pageText,
                                         StatementExtraction.Periods periods) {
        if (pageText == null || periods == null) return true;
        Integer curYear = lastYear(periods.current());
        Integer prevYear = lastYear(periods.previous());
        if (curYear == null || prevYear == null || curYear.equals(prevYear)) return true;
        int headCur = pageText.indexOf(String.valueOf(curYear));
        int headPrev = pageText.indexOf(String.valueOf(prevYear));
        if (headCur < 0 || headPrev < 0) return true;
        String curKey = NumberText.digits(item.current());
        String prevKey = NumberText.digits(item.previous());
        if (curKey.equals(prevKey)) return true;
        for (String line : pageText.split("\\R")) {
            int posCur = NumberText.positionOf(curKey, line);
            int posPrev = NumberText.positionOf(prevKey, line);
            if (posCur >= 0 && posPrev >= 0) {
                return (headCur < headPrev) == (posCur < posPrev);
            }
        }
        return true;
    }

    // Başlıklardan yıl okunabiliyorsa cari dönemin yılı öncekinden küçük olamaz.
    static boolean periodsReversed(StatementExtraction.Periods periods) {
        if (periods == null) return false;
        Integer cur = lastYear(periods.current());
        Integer prev = lastYear(periods.previous());
        return cur != null && prev != null && cur < prev;
    }

    private static Integer lastYear(String label) {
        if (label == null) return null;
        Matcher m = YEAR.matcher(label);
        Integer last = null;
        while (m.find()) last = Integer.parseInt(m.group());
        return last;
    }

    // Tanınmayan ölçek birim yazdırmaz; para birimi boşsa da birim yok sayılır.
    private static StatementExtraction.Unit cleanUnit(StatementExtraction.Unit unit) {
        if (unit == null || unit.currency() == null || unit.currency().isBlank()) return null;
        String scale = unit.scale() == null ? "units" : unit.scale().trim().toLowerCase();
        if (!SCALES.contains(scale)) return null;
        return new StatementExtraction.Unit(unit.currency().trim().toUpperCase(), scale);
    }
}