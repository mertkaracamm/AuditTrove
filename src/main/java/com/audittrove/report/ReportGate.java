package com.audittrove.report;

import com.audittrove.api.AuditResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ekrana çıkacak alanların biçim kapısı. Geçemeyen alan düşer ya da düzeltilir; olduğu gibi çıkmaz.
 * Kurallar belgeden bağımsızdır: "kanıt cümle olmalı", "gösterge değeri sayı, birimi ayrı".
 */
public final class ReportGate {
    private ReportGate() {}

    private static final Pattern BIG_NUMBER = Pattern.compile("\\d[\\d.,]{3,}\\d");
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");
    // "594,995,138 thousand TL", "%80", "80 %", "12 ay", "31.12.2024" → sayı kısmı + kalan
    private static final Pattern VALUE_WITH_UNIT = Pattern.compile(
            "^\\s*(%\\s*)?([-(−]?\\d[\\d.,\\s]*\\d\\)?|\\d)\\s*(%|[\\p{L}][\\p{L}\\s./']*)?\\s*$");
    private static final int MAX_TEXT_VALUE = 40;

    /** Ham tablo satırı görünümü: sayılar çok, kelimeler az ("Esas Faaliyet KARI 28.984.491 63.551.075"). */
    public static boolean looksLikeRawRow(String text) {
        if (text == null || text.isBlank()) return false;
        int numbers = 0, words = 0;
        Matcher n = BIG_NUMBER.matcher(text);
        while (n.find()) numbers++;
        Matcher w = WORD.matcher(text);
        while (w.find()) words++;
        return numbers >= 2 && words < 3 * numbers;
    }

    /** Kanıt cümle değilse LLM'in bulgu metnine düşülür; o da yoksa bulgu düşer (null). */
    public static AuditResponse.Risk gateRisk(AuditResponse.Risk risk) {
        if (risk == null) return null;
        String evidence = risk.evidence() == null ? "" : risk.evidence().trim();
        String finding = risk.finding() == null ? "" : risk.finding().trim();
        if (looksLikeRawRow(evidence)) {
            if (finding.isEmpty() || looksLikeRawRow(finding)) return null;
            evidence = finding;
        }
        if (evidence.isEmpty()) {
            if (finding.isEmpty()) return null;
            evidence = finding;
        }
        return new AuditResponse.Risk(risk.title(), risk.severity(), finding, evidence, risk.pages());
    }

    /**
     * Gösterge değerinde birim gömülüyse ayırır ("594,995,138 thousand TL" → value + unit).
     * Ne sayı ne kısa metin olan değer (uzun cümle) düşer (null).
     */
    public static AuditResponse.KeyMetric gateMetric(AuditResponse.KeyMetric m) {
        if (m == null || m.value() == null) return null;
        String value = m.value().trim();
        String unit = m.unit() == null ? "" : m.unit().trim();
        if (value.isEmpty()) return null;
        Matcher v = VALUE_WITH_UNIT.matcher(value);
        if (v.matches()) {
            String number = v.group(2).trim();
            String lead = v.group(1) == null ? "" : "%";
            String tail = v.group(3) == null ? "" : v.group(3).trim();
            String embedded = !lead.isEmpty() ? "%" : tail;
            if (!embedded.isEmpty() && unit.isEmpty()) unit = embedded;
            return new AuditResponse.KeyMetric(m.label(), number, unit, m.note());
        }
        // Sayı değil: "Olumlu görüş", "31 Aralık 2024" gibi kısa metinler kalır, cümleler düşer.
        if (value.length() > MAX_TEXT_VALUE || value.endsWith(".")) return null;
        return new AuditResponse.KeyMetric(m.label(), value, unit, m.note());
    }

    public static List<AuditResponse.KeyMetric> gateMetrics(List<AuditResponse.KeyMetric> metrics) {
        List<AuditResponse.KeyMetric> out = new ArrayList<>();
        if (metrics == null) return out;
        for (AuditResponse.KeyMetric m : metrics) {
            AuditResponse.KeyMetric g = gateMetric(m);
            if (g != null) out.add(g);
        }
        return out;
    }
}