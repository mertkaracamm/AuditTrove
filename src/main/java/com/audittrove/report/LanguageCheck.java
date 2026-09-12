package com.audittrove.report;

import com.audittrove.api.AuditResponse;
import com.audittrove.financial.Lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Raporun her metin alanı istenen dilde mi diye kaba bir kontrol. Karar vermez, yalnızca
 * karışıklığı adıyla loglar ki dil sızıntısı bir daha ekran görüntüsünden fark edilmesin.
 */
public final class LanguageCheck {
    private LanguageCheck() {}

    private static final Set<String> TR = Set.of("ve", "ile", "için", "bir", "bu", "olarak", "olan", "olup",
            "tarihi", "itibarıyla", "itibariyle", "göre", "ancak", "veya", "ise", "kadar", "üzere", "gibi", "daha");
    private static final Set<String> EN = Set.of("the", "and", "of", "to", "in", "for", "with", "is", "are",
            "by", "from", "that", "as", "on", "which", "this", "at", "be", "or");
    private static final int MIN_WORDS = 6;

    /** Metin belirgin şekilde diğer dildeyse o dili, aksi halde null döner (emin değilse susar). */
    public static Lang detect(String text) {
        if (text == null) return null;
        int tr = 0, en = 0, words = 0;
        for (String w : text.toLowerCase(Locale.forLanguageTag("tr")).split("[^\\p{L}]+")) {
            if (w.isEmpty()) continue;
            words++;
            if (TR.contains(w)) tr++;
            if (EN.contains(w)) en++;
        }
        if (words < MIN_WORDS || tr == en) return null;
        return tr > en ? Lang.TR : Lang.EN;
    }

    /** İstenen dilden sapan alanların adları; boşsa rapor tek dilde. */
    public static List<String> mismatches(AuditResponse r, Lang expected) {
        List<String> out = new ArrayList<>();
        check("summary", r.summary(), expected, out);
        check("scoreRationale", r.scoreRationale(), expected, out);
        int i = 0;
        for (AuditResponse.Risk risk : r.risks()) {
            i++;
            check("risk[" + i + "].title", risk.title(), expected, out);
            check("risk[" + i + "].evidence", risk.evidence(), expected, out);
        }
        i = 0;
        for (String rec : r.recommendations()) check("recommendation[" + (++i) + "]", rec, expected, out);
        i = 0;
        for (String q : r.advisorQuestions()) check("question[" + (++i) + "]", q, expected, out);
        return out;
    }

    private static void check(String field, String text, Lang expected, List<String> out) {
        Lang found = detect(text);
        if (found != null && found != expected) out.add(field);
    }
}