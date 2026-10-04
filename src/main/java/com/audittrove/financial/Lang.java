package com.audittrove.financial;

import java.util.Locale;

/** Rapor dili. İstekten bir kez çözülür, her katman aynı değeri kullanır. */
public enum Lang {
    TR("tr", Locale.forLanguageTag("tr-TR")),
    EN("en", Locale.ENGLISH);

    private final String code;
    private final Locale locale;

    Lang(String code, Locale locale) {
        this.code = code;
        this.locale = locale;
    }

    public String code() {
        return code;
    }

    public Locale locale() {
        return locale;
    }

    public boolean isTurkish() {
        return this == TR;
    }

    // "tr", "TR", "tr-TR" hepsi Türkçe; tanınmayan ya da boş değer İngilizce sayılır.
    public static Lang of(String raw) {
        if (raw == null) return EN;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.equals("tr") || s.startsWith("tr-") || s.startsWith("tr_") ? TR : EN;
    }
}
