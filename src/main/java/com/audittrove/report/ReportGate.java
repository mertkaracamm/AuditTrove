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

    // Sayı: binlik ayracı nokta/virgül ya da boşluk olabilir ("28.984.491", "18 420,6"); ikisi de tek sayıdır.
    // Boşluklu biçim ayrı yazılır: tek kalıpta boşluk ayracı "402.5 612.1" gibi yan yana iki sayıyı
    // birleştirip tek sayı sayıyordu, ham tablo satırı kapısı da bu yüzden açılmıyordu.
    private static final Pattern BIG_NUMBER = Pattern.compile(
            "\\d{1,3}(?:[ \\u00a0]\\d{3})+(?:[.,]\\d+)?|\\d[\\d.,]{3,}\\d");
    private static final Pattern WORD = Pattern.compile("\\p{L}{2,}");
    // "594,995,138 thousand TL", "%80", "80 %", "EUR 84,000", "£1,250", "12 ay" → sayı kısmı + birim.
    // Önde para birimi kodu/simgesi ya da yüzde, arkada birim metni olabilir; ikisi birden de gelir ("EUR 84,000 gross").
    private static final Pattern VALUE_WITH_UNIT = Pattern.compile(
            "^\\s*(%|[\\p{Lu}]{2,4}|[$€£₺¥])?\\s*([-(−]?\\d[\\d.,\\s]*\\d\\)?|\\d)\\s*(%|[\\p{L}][\\p{L}\\s./']*)?\\s*$");
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
        return new AuditResponse.Risk(risk.title(), risk.severity(), finding, evidence, risk.pages(), risk.source(), risk.quote());
    }

    /**
     * Gösterge değerinde birim gömülüyse ayırır ("594,995,138 thousand TL" → value + unit).
     * Ne sayı ne kısa metin olan değer (uzun cümle) düşer (null).
     */
    // Tarihin parçası gösterge olamaz: "Ruhsat veriliş günü: 19", "ayı: mar" gibi kartlar düşer; tarih tek kartta kalır.
    private static final Pattern DATE_PART_LABEL = Pattern.compile("(?iU)\\b(gün|günü|ay|ayı|yıl|yılı|day|month|year)\\b");
    private static final Pattern MONTH_ABBR = Pattern.compile("(?i)^(oca|şub|sub|mar|nis|may|haz|tem|ağu|agu|eyl|eki|kas|ara|jan|feb|apr|jun|jul|aug|sep|oct|nov|dec)[a-zçğıöşü]*\\.?$");

    // Kimlik ve numara gösterge değildir: "Vergi numarası: 18.902.730.010", "Sözleşme No: 4936" gibi kartlar düşer.
    private static final Pattern IDENTIFIER_LABEL = Pattern.compile(
            "(?iU)\\b(numarası|numara|no|nr|sicil|kimlik|tckn|tc|vkn|iban|barkod|kodu|kod|id|number|code|ref|referans)\\b");
    private static final Pattern COUNT_LABEL = Pattern.compile("(?iU)\\b(number of|count|sayısı|adedi)\\b");

    public static AuditResponse.KeyMetric gateMetric(AuditResponse.KeyMetric m) {
        if (m == null || m.value() == null) return null;
        String value = m.value().trim();
        String unit = m.unit() == null ? "" : m.unit().trim();
        if (value.isEmpty()) return null;
        String label = m.label() == null ? "" : m.label();
        if (MONTH_ABBR.matcher(value).matches()) return null;
        if (unit.isEmpty() && value.matches("\\d{1,2}") && DATE_PART_LABEL.matcher(label).find()) return null;
        // "Number of employees: 120" bir sayımdır, kimlik değil; o yüzden ayrı tutulur.
        if (unit.isEmpty() && IDENTIFIER_LABEL.matcher(label).find() && !COUNT_LABEL.matcher(label).find()) return null;
        Matcher v = VALUE_WITH_UNIT.matcher(value);
        if (v.matches()) {
            String number = v.group(2).trim();
            String lead = v.group(1) == null ? "" : v.group(1).trim();
            String tail = v.group(3) == null ? "" : v.group(3).trim();
            String embedded = (lead + " " + tail).trim();
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