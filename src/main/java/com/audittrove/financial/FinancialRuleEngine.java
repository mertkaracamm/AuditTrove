package com.audittrove.financial;

import com.audittrove.api.AuditResponse;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Doğrulanmış tutarlardan bulgu üretir. Yön her zaman iki sayıdan hesaplanır, metinden değil;
 * eşik ve şablon sabittir. Aynı sayılar her dilde ve her çalıştırmada aynı bulguyu verir.
 */
public final class FinancialRuleEngine {
    private FinancialRuleEngine() {}

    /** Kâr kaleminde en az bu oranda düşüş, gider kaleminde en az bu oranda artış bulgu sayılır. */
    public static final double THRESHOLD_PCT = 20.0;
    private static final String SEVERITY = "MEDIUM";

    /** Motorun ürettiği bulgular ve kodun cevap verdiği kalemler (bulgu çıkmasa bile). */
    public record Result(List<AuditResponse.Risk> findings, Set<LineItemKey> covered) {
        public static Result empty() {
            return new Result(List.of(), EnumSet.noneOf(LineItemKey.class));
        }
    }

    public static Result evaluate(StatementVerifier.VerifiedStatement statement, Lang lang) {
        if (statement == null || statement.isEmpty()) return Result.empty();
        List<AuditResponse.Risk> findings = new ArrayList<>();
        Set<LineItemKey> covered = EnumSet.noneOf(LineItemKey.class);
        for (StatementVerifier.VerifiedItem item : statement.items()) {
            covered.add(item.key());
            double prev = Math.abs(item.previous());
            double cur = Math.abs(item.current());
            if (prev == 0) continue;
            double changePct = (cur - prev) / prev * 100.0;
            boolean profitDrop = item.key().isProfit() && changePct <= -THRESHOLD_PCT;
            boolean expenseRise = !item.key().isProfit() && changePct >= THRESHOLD_PCT;
            if (profitDrop || expenseRise) {
                findings.add(finding(item, prev, cur, Math.abs(changePct), statement.unit(), lang));
            }
        }
        return new Result(List.copyOf(findings), covered);
    }

    /** LLM'in aynı kalem hakkında yazdığı bulgu kodun cevabıyla çelişebilir; kodun cevabı geçerlidir. */
    public static boolean coveredByRules(AuditResponse.Risk risk, Set<LineItemKey> covered) {
        if (risk == null || covered.isEmpty()) return false;
        String text = (risk.title() == null ? "" : risk.title()) + " " + (risk.evidence() == null ? "" : risk.evidence());
        for (LineItemKey key : covered) {
            if (key.mentionedIn(text)) return true;
        }
        return false;
    }

    private static AuditResponse.Risk finding(StatementVerifier.VerifiedItem item, double prev, double cur,
                                              double pct, StatementExtraction.Unit unit, Lang lang) {
        String name = item.key().displayName(lang);
        String unitText = unitText(unit, lang);
        String prevText = amount(prev, unitText, lang);
        String curText = amount(cur, unitText, lang);
        String pctText = percent(pct, lang);
        boolean down = item.key().isProfit();
        String title, evidence;
        if (lang.isTurkish()) {
            title = name + " " + pctText + " oranında " + (down ? "düştü" : "arttı");
            evidence = name + " " + prevText + " seviyesinden " + curText + " seviyesine "
                    + pctText + " oranında " + (down ? "düşmüştür." : "artmıştır.");
        } else {
            title = name + (down ? " declined by " : " increased by ") + pctText;
            evidence = name + (down ? " fell from " : " increased from ") + prevText + " to " + curText
                    + ", a " + pctText + (down ? " decrease." : " increase.");
        }
        return new AuditResponse.Risk(title, SEVERITY, evidence, evidence, List.of(item.page()));
    }

    // Belgenin beyan ettiği ölçek ve para birimi; beyan yoksa birim yazılmaz, uydurulmaz.
    static String unitText(StatementExtraction.Unit unit, Lang lang) {
        if (unit == null) return "";
        String currency = "TRY".equals(unit.currency()) ? "TL" : unit.currency();
        String scale = switch (unit.scale()) {
            case "thousand" -> lang.isTurkish() ? "bin" : "thousand";
            case "million" -> lang.isTurkish() ? "milyon" : "million";
            case "billion" -> lang.isTurkish() ? "milyar" : "billion";
            default -> "";
        };
        return (scale.isEmpty() ? "" : scale + " ") + currency;
    }

    static String amount(double v, String unitText, Lang lang) {
        NumberFormat nf = NumberFormat.getNumberInstance(lang.locale());
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(0);
        String num = nf.format(v);
        return unitText.isEmpty() ? num : num + " " + unitText;
    }

    static String percent(double pct, Lang lang) {
        NumberFormat nf = NumberFormat.getNumberInstance(lang.locale());
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(1);
        String num = nf.format(pct);
        return lang.isTurkish() ? "%" + num : num + "%";
    }
}