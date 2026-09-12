package com.audittrove.report;

import com.audittrove.financial.Lang;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Kontrol listesi: belge TİPİNE göre sabit sorular. LLM her soruya yalnızca var/yok + kanıt verir;
 * başlık ve önem derecesi burada sabittir. Aynı belgeye aynı sorular gider, aynı evet/hayır çıkar.
 * Bu liste alan bilgisidir (sözleşme ve finansal tablo okuma pratiği), belge bilgisi değil.
 */
public enum RubricItem {
    // ---- Finansal tablo / faaliyet raporu ----
    FIN_AUDIT_OPINION_MODIFIED(Kind.FINANCIAL, "HIGH",
            "Denetçi görüşü şartlı, olumsuz ya da görüş bildirmekten kaçınılmış",
            "Auditor's opinion is qualified, adverse or a disclaimer",
            "Is the independent auditor's opinion qualified, adverse, or a disclaimer of opinion (anything other than unqualified/unmodified)?"),
    FIN_GOING_CONCERN(Kind.FINANCIAL, "HIGH",
            "İşletmenin sürekliliğine ilişkin önemli belirsizlik",
            "Material uncertainty about going concern",
            "Does the auditor or management disclose a material uncertainty related to going concern, or do current liabilities exceed current assets with reliance on external support?"),
    FIN_EMPHASIS_OF_MATTER(Kind.FINANCIAL, "MEDIUM",
            "Denetçi raporunda dikkat çekilen husus",
            "Emphasis of matter in the auditor's report",
            "Does the auditor's report contain an emphasis-of-matter or other-matter paragraph?"),
    FIN_RELATED_PARTY_CONCENTRATION(Kind.FINANCIAL, "MEDIUM",
            "Satış veya alacaklarda ilişkili taraf yoğunlaşması",
            "Related-party concentration in sales or receivables",
            "Do related parties account for a large share of sales or receivables (roughly a third or more), or are related-party balances described as significant?"),
    FIN_CUSTOMER_CONCENTRATION(Kind.FINANCIAL, "MEDIUM",
            "Az sayıda müşteriye bağımlılık",
            "Dependence on a small number of customers",
            "Do one or a few unrelated customers account for a large share of revenue (roughly a quarter or more)?"),
    FIN_LEVERAGE_OR_COVENANT(Kind.FINANCIAL, "MEDIUM",
            "Borçluluk artışı ya da kredi taahhüdü riski",
            "Rising indebtedness or covenant risk",
            "Did total borrowings increase materially, or is a covenant, leverage limit or refinancing risk disclosed?"),
    FIN_LITIGATION(Kind.FINANCIAL, "MEDIUM",
            "Önemli dava veya şarta bağlı yükümlülük",
            "Significant litigation or contingent liability",
            "Is a significant lawsuit, claim, tax dispute or other contingent liability disclosed, especially one without a provision?"),
    FIN_FX_OR_HEDGE_EXPOSURE(Kind.FINANCIAL, "MEDIUM",
            "Kur riski veya riskten korunma kayıpları",
            "Foreign-currency exposure or hedge losses",
            "Is there material foreign-currency exposure, foreign-currency debt, or hedge accounting with significant losses?"),
    FIN_SIGNIFICANT_ESTIMATES(Kind.FINANCIAL, "MEDIUM",
            "Yönetim tahminine bağlı önemli kalemler",
            "Material items dependent on management estimates",
            "Are there material balances that depend on management judgement, such as deferred tax assets, impairment, provisions, or recoverability of receivables?"),
    FIN_INFLATION_ACCOUNTING(Kind.FINANCIAL, "LOW",
            "Enflasyon muhasebesi uygulanıyor",
            "Inflation accounting applied",
            "Are the financial statements restated for hyperinflation (e.g. TMS 29 / IAS 29)?"),

    // ---- Kira ----
    RENT_DEPOSIT_TERMS(Kind.RENTAL, "MEDIUM",
            "Depozito iade koşulları kiracı aleyhine veya belirsiz",
            "Deposit refund terms unclear or unfavourable to the tenant",
            "Is the deposit large (two months or more), or are its refund conditions unclear, discretionary or allowing broad deductions?"),
    RENT_AUTO_RENEWAL(Kind.RENTAL, "MEDIUM",
            "Otomatik yenileme ve uzun bildirim süresi",
            "Automatic renewal with a long notice period",
            "Does the lease renew automatically unless notice is given, especially with a notice period of two months or more?"),
    RENT_INCREASE_CLAUSE(Kind.RENTAL, "MEDIUM",
            "Kira artış kuralı belirsiz veya endeks üstü",
            "Rent increase rule unclear or above the index",
            "Is the rent increase rule missing, discretionary, or set above the usual statutory/index cap?"),
    RENT_UNILATERAL_TERMINATION(Kind.RENTAL, "HIGH",
            "Kiraya verene tek taraflı fesih hakkı",
            "Landlord's unilateral termination right",
            "Can the landlord terminate unilaterally on grounds beyond serious breach (e.g. short payment delay, without court)?"),
    RENT_PENALTIES(Kind.RENTAL, "MEDIUM",
            "Cezai şart veya yüksek gecikme bedeli",
            "Penalty clause or high late-payment charge",
            "Are there penalty clauses, early-exit penalties or late fees beyond a token amount?"),
    RENT_EXTRA_CHARGES(Kind.RENTAL, "LOW",
            "Kira dışı yükümlülükler kiracıda",
            "Additional charges borne by the tenant",
            "Are dues, maintenance, repairs or other charges beyond rent placed on the tenant?"),
    RENT_USE_RESTRICTIONS(Kind.RENTAL, "LOW",
            "Kullanım kısıtları (alt kira, devir, evcil hayvan)",
            "Use restrictions (subletting, assignment, pets)",
            "Does the lease prohibit or restrict subletting, assignment or pets?"),

    // ---- İş sözleşmesi ----
    EMP_NON_COMPETE(Kind.EMPLOYMENT, "HIGH",
            "Rekabet yasağı geniş veya karşılıksız",
            "Broad or uncompensated non-compete",
            "Is there a post-employment non-compete of six months or longer, wide in scope, or with little or no compensation?"),
    EMP_ASYMMETRIC_NOTICE(Kind.EMPLOYMENT, "MEDIUM",
            "İhbar süreleri çalışan aleyhine asimetrik",
            "Notice periods asymmetric against the employee",
            "Must the employee give a longer notice period than the employer?"),
    EMP_UNPAID_OVERTIME(Kind.EMPLOYMENT, "MEDIUM",
            "Fazla mesai ücrete dahil sayılıyor",
            "Overtime deemed included in salary",
            "Is overtime stated to be included in the base salary or otherwise uncompensated?"),
    EMP_BROAD_IP(Kind.EMPLOYMENT, "MEDIUM",
            "Fikri mülkiyet devri iş dışını da kapsıyor",
            "IP assignment extends beyond work duties",
            "Does the IP clause assign inventions or works created outside working hours or unrelated to duties?"),
    EMP_DISCRETIONARY_PAY(Kind.EMPLOYMENT, "LOW",
            "Bonus tamamen işverenin takdirinde",
            "Bonus entirely at employer's discretion",
            "Is bonus or variable pay described as discretionary with no guaranteed component?"),
    EMP_LIQUIDATED_DAMAGES(Kind.EMPLOYMENT, "MEDIUM",
            "Çalışana yönelik cezai şart",
            "Liquidated damages against the employee",
            "Does the contract impose fixed penalties or liquidated damages on the employee?"),
    EMP_RELOCATION_OR_CHANGE(Kind.EMPLOYMENT, "LOW",
            "İşverene tek taraflı yer/görev değişikliği hakkı",
            "Employer may change location or duties unilaterally",
            "Can the employer relocate the employee or change duties unilaterally?"),

    // ---- Abonelik / üyelik ----
    SUB_AUTO_RENEWAL(Kind.SUBSCRIPTION, "MEDIUM",
            "Otomatik yenileme",
            "Automatic renewal",
            "Does the agreement renew automatically unless cancelled in advance?"),
    SUB_EARLY_TERMINATION_FEE(Kind.SUBSCRIPTION, "HIGH",
            "Erken iptal bedeli yüksek",
            "High early-termination fee",
            "Does early cancellation require paying a substantial part of the remaining fees or a fixed penalty?"),
    SUB_PRICE_INCREASE(Kind.SUBSCRIPTION, "MEDIUM",
            "Fiyat artış hakkı",
            "Right to increase prices",
            "May the provider increase fees during the term?"),
    SUB_CANCELLATION_CHANNEL(Kind.SUBSCRIPTION, "MEDIUM",
            "İptal yolu kısıtlı",
            "Restricted cancellation channel",
            "Is cancellation limited to specific channels (registered post, in person) or subject to other hurdles?"),
    SUB_FEES_WHILE_SUSPENDED(Kind.SUBSCRIPTION, "MEDIUM",
            "Askıdayken ücret işlemeye devam ediyor",
            "Fees continue while access is suspended",
            "Do fees keep accruing while the service is suspended or frozen?"),
    SUB_NON_REFUNDABLE(Kind.SUBSCRIPTION, "LOW",
            "İade edilmeyen ücretler",
            "Non-refundable fees",
            "Are joining fees or prepayments non-refundable?"),

    // ---- Sigorta ----
    INS_EXCLUSIONS(Kind.INSURANCE, "MEDIUM",
            "Geniş teminat dışı haller",
            "Broad exclusions",
            "Are there broad or unusual exclusions that materially narrow the coverage?"),
    INS_WAITING_PERIOD(Kind.INSURANCE, "MEDIUM",
            "Bekleme süresi",
            "Waiting period",
            "Is there a waiting period before coverage applies?"),
    INS_DEDUCTIBLE_OR_LIMITS(Kind.INSURANCE, "MEDIUM",
            "Yüksek muafiyet veya düşük teminat limiti",
            "High deductible or low coverage limits",
            "Is the deductible high or are coverage limits low relative to the insured risk?"),
    INS_CLAIM_DEADLINES(Kind.INSURANCE, "MEDIUM",
            "Kısa hasar bildirim süresi veya ağır yükümlülükler",
            "Short claim deadlines or heavy obligations",
            "Are claim notification deadlines short or the insured's obligations onerous?"),
    INS_CANCELLATION_REFUND(Kind.INSURANCE, "LOW",
            "İptal ve iade koşulları kısıtlı",
            "Restricted cancellation or refund",
            "Are cancellation or premium refund terms restrictive?"),

    // ---- Araç alım-satım ----
    VEH_AS_IS(Kind.VEHICLE, "HIGH",
            "Araç 'olduğu gibi' satılıyor, garanti yok",
            "Vehicle sold as-is without warranty",
            "Is the vehicle sold as-is or with a disclaimer of warranties?"),
    VEH_PAYMENT_BEFORE_TRANSFER(Kind.VEHICLE, "HIGH",
            "Ödeme devirden önce",
            "Payment due before transfer of ownership",
            "Is full payment required before the transfer of ownership is completed?"),
    VEH_LIABILITY_DISCLAIMER(Kind.VEHICLE, "MEDIUM",
            "Satıcı sorumluluğunu sınırlıyor",
            "Seller limits liability",
            "Does the seller disclaim liability for hidden defects, mileage or damage history?"),
    VEH_DECLARED_HISTORY(Kind.VEHICLE, "LOW",
            "Hasar veya kilometre beyanı",
            "Declared damage or mileage record",
            "Does the document declare accident damage, mileage or prior use that the buyer should verify?"),

    // ---- Genel sözleşme / diğer ----
    GEN_UNILATERAL_TERMINATION(Kind.GENERAL, "HIGH",
            "Karşı tarafa tek taraflı fesih hakkı",
            "Counterparty's unilateral termination right",
            "Can the other party terminate unilaterally without a comparable right for the reader?"),
    GEN_PENALTIES(Kind.GENERAL, "MEDIUM",
            "Cezai şart",
            "Penalty clause",
            "Are there penalty or liquidated-damages clauses binding the reader?"),
    GEN_AUTO_RENEWAL(Kind.GENERAL, "MEDIUM",
            "Otomatik yenileme",
            "Automatic renewal",
            "Does the agreement renew automatically?"),
    GEN_LIABILITY_WAIVER(Kind.GENERAL, "MEDIUM",
            "Sorumluluk sınırlaması veya feragat",
            "Liability limitation or waiver",
            "Does the reader waive rights or accept a limitation of the other party's liability?"),
    GEN_BINDING_DEADLINES(Kind.GENERAL, "LOW",
            "Bağlayıcı süreler ve bildirim şartları",
            "Binding deadlines and notice requirements",
            "Are there deadlines or notice requirements whose lapse costs the reader money or rights?"),
    GEN_UNCLEAR_PAYMENT(Kind.GENERAL, "MEDIUM",
            "Ödeme koşulları belirsiz veya değişken",
            "Payment terms unclear or variable",
            "Are the amounts, due dates or price-change rules unclear or one-sided?");

    /** Belge türü. Çıkarım belgeyi sınıflar; kullanıcının tip seçimine bağlı değil. */
    public enum Kind { FINANCIAL, RENTAL, EMPLOYMENT, SUBSCRIPTION, INSURANCE, VEHICLE, GENERAL, OTHER }

    private final Kind kind;
    private final String severity;
    private final String trTitle;
    private final String enTitle;
    private final String question;

    RubricItem(Kind kind, String severity, String trTitle, String enTitle, String question) {
        this.kind = kind;
        this.severity = severity;
        this.trTitle = trTitle;
        this.enTitle = enTitle;
        this.question = question;
    }

    public Kind kind() { return kind; }
    public String severity() { return severity; }
    public String question() { return question; }
    public String title(Lang lang) { return lang.isTurkish() ? trTitle : enTitle; }
    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static RubricItem fromId(String id) {
        if (id == null) return null;
        for (RubricItem r : values()) if (r.id().equals(id.trim().toLowerCase(Locale.ROOT))) return r;
        return null;
    }

    public static List<RubricItem> forKind(Kind kind) {
        return Arrays.stream(values()).filter(r -> r.kind == kind).toList();
    }

    public static Kind kindOf(String raw) {
        if (raw == null) return Kind.OTHER;
        try { return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return Kind.OTHER; }
    }
}