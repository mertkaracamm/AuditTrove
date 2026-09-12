package com.audittrove.financial;

import java.util.List;
import java.util.Locale;

/**
 * Kural motorunun izlediği gelir tablosu kalemleri. Bu liste alan bilgisidir (muhasebe),
 * belge bilgisi değil: buraya yeni bir belgenin kelimesi değil, yeni bir kalem eklenir.
 */
public enum LineItemKey {
    OPERATING_PROFIT(true, "Esas faaliyet kârı", "Operating profit",
            List.of("faaliyet kar", "faaliyet kâr", "operating profit", "operating income")),
    NET_PROFIT(true, "Dönem kârı", "Net income",
            List.of("dönem kar", "dönem kâr", "net kar", "net kâr", "net income", "net profit", "profit for the period")),
    MARKETING_EXPENSES(false, "Pazarlama giderleri", "Marketing expenses",
            List.of("pazarlama gider", "marketing expense", "selling expense", "marketing and sales")),
    ADMIN_EXPENSES(false, "Genel yönetim giderleri", "General administrative expenses",
            List.of("genel yönetim gider", "administrative expense")),
    FINANCE_COSTS(false, "Finansman giderleri", "Finance costs",
            List.of("finansman gider", "finance cost", "finance expense", "financial expense", "financing cost"));

    private final boolean profit;
    private final String trName;
    private final String enName;
    private final List<String> keywords;

    LineItemKey(boolean profit, String trName, String enName, List<String> keywords) {
        this.profit = profit;
        this.trName = trName;
        this.enName = enName;
        this.keywords = keywords;
    }

    /** Kâr kalemi düşerse, gider kalemi artarsa bulgu olur. */
    public boolean isProfit() {
        return profit;
    }

    public String displayName(Lang lang) {
        return lang.isTurkish() ? trName : enName;
    }

    /** LLM'in aynı kalem hakkında yazdığı serbest bulguyu tanımak için (kod cevabı önceliklidir). */
    public boolean mentionedIn(String text) {
        if (text == null) return false;
        String s = text.toLowerCase(Locale.forLanguageTag("tr"));
        for (String k : keywords) {
            if (s.contains(k)) return true;
        }
        return false;
    }

    /** JSON anahtarı (LLM şemasındaki enum değeri). */
    public String jsonKey() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static LineItemKey fromJsonKey(String key) {
        if (key == null) return null;
        for (LineItemKey k : values()) {
            if (k.jsonKey().equals(key.trim().toLowerCase(Locale.ROOT))) return k;
        }
        return null;
    }
}
