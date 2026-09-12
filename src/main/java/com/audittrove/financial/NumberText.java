package com.audittrove.financial;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Belgede yazılı tutarları biçimden bağımsız okur ve karşılaştırır.
 * TR "19.917,1", EN "12,345.6", parantezli ya da eksili negatifler hepsi tek yerden geçer.
 */
public final class NumberText {
    private NumberText() {}

    // Metindeki sayı adayları: rakamla başlar, ayraç ve rakamla devam eder.
    private static final Pattern TOKEN = Pattern.compile("\\d[\\d.,]*\\d|\\d");

    // İki ayraç türü varsa sağdaki ondalıktır. Tek tür ayraç birden fazla geçiyorsa ya da
    // tam üç hane izliyorsa binliktir ("12,345" / "1.234"), aksi halde ondalıktır ("74,7").
    public static Double parse(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        boolean negative = t.startsWith("(") && t.endsWith(")") || t.startsWith("-") || t.startsWith("−");
        t = t.replaceAll("[()\\-\\u2212\\s]", "");
        if (t.isEmpty()) return null;
        boolean hasDot = t.indexOf('.') >= 0, hasComma = t.indexOf(',') >= 0;
        try {
            double v;
            if (hasDot && hasComma) {
                boolean dotDecimal = t.lastIndexOf('.') > t.lastIndexOf(',');
                v = Double.parseDouble(dotDecimal ? t.replace(",", "") : t.replace(".", "").replace(',', '.'));
            } else if (hasDot || hasComma) {
                char sep = hasDot ? '.' : ',';
                int first = t.indexOf(sep), last = t.lastIndexOf(sep);
                boolean thousands = first != last || t.length() - last - 1 == 3;
                v = thousands ? Double.parseDouble(t.replace(String.valueOf(sep), ""))
                              : Double.parseDouble(t.replace(sep, '.'));
            } else {
                v = Double.parseDouble(t);
            }
            return negative ? -v : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ayraçlardan arınmış rakam dizisi: "28.984.491" ve "28,984,491" aynı anahtara iner. */
    public static String digits(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /** Sayfa metnindeki tüm sayıların rakam anahtarları; aynı sayı hangi biçimde yazılsa da bulunur. */
    public static Set<String> digitKeys(String text) {
        Set<String> keys = new HashSet<>();
        if (text == null) return keys;
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            keys.add(digits(m.group()));
        }
        return keys;
    }

    /** Rakam anahtarı satırda hangi konumda geçiyor; yoksa -1. Sütun sırası kontrolü için. */
    public static int positionOf(String digitKey, String line) {
        if (digitKey == null || digitKey.isEmpty() || line == null) return -1;
        Matcher m = TOKEN.matcher(line);
        while (m.find()) {
            if (digitKey.equals(digits(m.group()))) return m.start();
        }
        return -1;
    }

    /** Yazılı tutar sayfa metninde geçiyor mu — biçim farkı (nokta/virgül) eşleşmeyi bozmaz. */
    public static boolean occursIn(String printed, Set<String> pageKeys) {
        String key = digits(printed);
        return !key.isEmpty() && pageKeys.contains(key);
    }
}
