package com.audittrove.report;

import com.audittrove.financial.Lang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Kontrol listesi: belge TİPİNE göre sabit sorular. LLM her soruya yalnızca var/yok + kanıt verir;
 * başlık ve önem derecesi burada sabittir. Sorular OLGU sorar ("fesih hakkı var mı"), yorum değil
 * ("ağır mı"); yorum gerektiren soru koşudan koşuya farklı cevap alır, olgu almaz.
 * Bu liste alan bilgisidir (sözleşme ve finansal tablo okuma pratiği), belge bilgisi değil.
 */
public enum RubricItem {
    // ---- Finansal tablo / faaliyet raporu ----
    FIN_AUDIT_OPINION_MODIFIED(Kind.FINANCIAL, "HIGH",
            "Denetçi görüşü şartlı, olumsuz ya da görüş bildirmekten kaçınılmış",
            "Auditor's opinion is qualified, adverse or a disclaimer",
            "Does the auditor's report state an opinion other than unqualified/unmodified (qualified, adverse, or disclaimer)?"),
    FIN_GOING_CONCERN(Kind.FINANCIAL, "HIGH",
            "İşletmenin sürekliliğine ilişkin önemli belirsizlik",
            "Material uncertainty about going concern",
            "Does the document contain a going-concern material-uncertainty paragraph, or state that current liabilities exceed current assets?"),
    FIN_EMPHASIS_OF_MATTER(Kind.FINANCIAL, "MEDIUM",
            "Denetçi raporunda dikkat çekilen husus / kilit denetim konuları",
            "Emphasis of matter / key audit matters in the auditor's report",
            "Does the auditor's report contain a paragraph headed emphasis of matter / other matter / key audit matters?"),
    FIN_RELATED_PARTY_CONCENTRATION(Kind.FINANCIAL, "MEDIUM",
            "Satış veya alacaklarda ilişkili taraf payı",
            "Related-party share of sales or receivables",
            "Does the document state a percentage share of sales or receivables with related parties?"),
    FIN_CUSTOMER_CONCENTRATION(Kind.FINANCIAL, "MEDIUM",
            "Az sayıda müşteriye bağımlılık",
            "Dependence on a small number of customers",
            "Does the document state that one or a few customers account for a specified share of revenue?"),
    FIN_LEVERAGE_OR_COVENANT(Kind.FINANCIAL, "MEDIUM",
            "Borçluluk artışı veya kredi taahhüdü",
            "Increase in borrowings or covenant terms",
            "Does the document report an increase in total borrowings, or mention a covenant, leverage ratio limit or refinancing?"),
    FIN_LITIGATION(Kind.FINANCIAL, "MEDIUM",
            "Dava veya şarta bağlı yükümlülük",
            "Litigation or contingent liability",
            "Does the document disclose a lawsuit, legal claim, tax dispute or other contingent liability?"),
    FIN_FX_OR_HEDGE_EXPOSURE(Kind.FINANCIAL, "MEDIUM",
            "Kur riski veya riskten korunma kayıpları",
            "Foreign-currency exposure or hedge losses",
            "Does the document mention foreign-currency borrowings, hedge accounting, or foreign-exchange or hedge losses?"),
    FIN_SIGNIFICANT_ESTIMATES(Kind.FINANCIAL, "MEDIUM",
            "Yönetim tahminine bağlı önemli kalemler",
            "Material items dependent on management estimates",
            "Does the document describe deferred tax assets, impairment, provisions or recoverability of receivables as depending on management estimates or judgement?"),
    FIN_INFLATION_ACCOUNTING(Kind.FINANCIAL, "LOW",
            "Enflasyon muhasebesi uygulanıyor",
            "Inflation accounting applied",
            "Does the document state that the financial statements are restated for inflation (e.g. TMS 29 / IAS 29)?"),

    // ---- Kira ----
    RENT_DEPOSIT_TERMS(Kind.RENTAL, "LOW",
            "Depozito şartı ve iade koşulları",
            "Deposit requirement and refund terms",
            "Does the lease require a deposit?"),
    RENT_AUTO_RENEWAL(Kind.RENTAL, "MEDIUM", Topic.AUTO_RENEWAL,
            "Otomatik yenileme ve uzun bildirim süresi",
            "Automatic renewal with a long notice period",
            "Does the lease state that it renews or extends automatically unless notice is given?"),
    RENT_INCREASE_CLAUSE(Kind.RENTAL, "LOW",
            "Kira artış kuralı",
            "Rent increase rule",
            "Does the lease contain a rent increase rule?"),
    RENT_UNILATERAL_TERMINATION(Kind.RENTAL, "MEDIUM", Topic.TERMINATION,
            "Kiraya verenin süre bitiminden önce fesih hakkı",
            "Landlord may terminate before the end of the term",
            "Does the lease give the landlord a right to terminate before the end of the term under stated conditions?"),
    RENT_PENALTIES(Kind.RENTAL, "MEDIUM", Topic.PENALTY,
            "Cezai şart, gecikme veya erken çıkış bedeli",
            "Penalty, late-payment or early-exit charge",
            "Does the lease contain a penalty, late-payment charge or early-exit charge?"),
    RENT_EXTRA_CHARGES(Kind.RENTAL, "LOW",
            "Kira dışı yükümlülükler kiracıda",
            "Additional charges borne by the tenant",
            "Does the lease place dues, utilities, maintenance or repair costs on the tenant?"),
    RENT_USE_RESTRICTIONS(Kind.RENTAL, "LOW",
            "Kullanım kısıtları (alt kira, devir, evcil hayvan)",
            "Use restrictions (subletting, assignment, pets)",
            "Does the lease prohibit or condition subletting, assignment or pets?"),

    // ---- İş sözleşmesi ----
    EMP_NON_COMPETE(Kind.EMPLOYMENT, "HIGH",
            "İşten ayrılma sonrası rekabet yasağı",
            "Post-employment non-compete",
            "Does the contract contain a post-employment non-compete clause?"),
    EMP_ASYMMETRIC_NOTICE(Kind.EMPLOYMENT, "MEDIUM",
            "İhbar süreleri çalışan aleyhine asimetrik",
            "Notice periods asymmetric against the employee",
            "Is the employee's notice period stated as longer than the employer's?"),
    EMP_UNPAID_OVERTIME(Kind.EMPLOYMENT, "MEDIUM",
            "Fazla mesai ücrete dahil sayılıyor",
            "Overtime deemed included in salary",
            "Does the contract state that overtime is included in the salary or not separately paid?"),
    EMP_BROAD_IP(Kind.EMPLOYMENT, "MEDIUM",
            "Fikri mülkiyet devri iş dışını da kapsıyor",
            "IP assignment extends beyond work duties",
            "Does the IP clause cover work created outside working hours or unrelated to the employee's duties?"),
    EMP_DISCRETIONARY_PAY(Kind.EMPLOYMENT, "LOW",
            "Bonus tamamen işverenin takdirinde",
            "Bonus entirely at employer's discretion",
            "Is bonus or variable pay described as discretionary or not guaranteed?"),
    EMP_LIQUIDATED_DAMAGES(Kind.EMPLOYMENT, "MEDIUM", Topic.PENALTY,
            "Çalışana yönelik cezai şart",
            "Liquidated damages against the employee",
            "Does the contract specify a fixed penalty or liquidated-damages amount payable by the employee?"),
    EMP_RELOCATION_OR_CHANGE(Kind.EMPLOYMENT, "LOW",
            "İşverene tek taraflı yer/görev değişikliği hakkı",
            "Employer may change location or duties unilaterally",
            "Can the employer change the place of work or duties by giving notice?"),

    // ---- Abonelik / üyelik ----
    SUB_AUTO_RENEWAL(Kind.SUBSCRIPTION, "MEDIUM", Topic.AUTO_RENEWAL,
            "Otomatik yenileme",
            "Automatic renewal",
            "Does the agreement state that it renews automatically unless cancelled?"),
    SUB_EARLY_TERMINATION_FEE(Kind.SUBSCRIPTION, "HIGH", Topic.PENALTY,
            "Erken iptal bedeli",
            "Early-termination fee",
            "Does early cancellation require a payment (remaining fees or a fixed amount)?"),
    SUB_PRICE_INCREASE(Kind.SUBSCRIPTION, "MEDIUM",
            "Fiyat artış hakkı",
            "Right to increase prices",
            "Does the agreement allow the provider to increase fees during the term?"),
    SUB_CANCELLATION_CHANNEL(Kind.SUBSCRIPTION, "MEDIUM",
            "İptal yolu kısıtlı",
            "Restricted cancellation channel",
            "Does the agreement restrict cancellation to specific channels or forms?"),
    SUB_FEES_WHILE_SUSPENDED(Kind.SUBSCRIPTION, "MEDIUM",
            "Askıdayken ücret işlemeye devam ediyor",
            "Fees continue while access is suspended",
            "Does the agreement state that fees continue while access is suspended or frozen?"),
    SUB_NON_REFUNDABLE(Kind.SUBSCRIPTION, "LOW",
            "İade edilmeyen ücretler",
            "Non-refundable fees",
            "Does the agreement describe any fee as non-refundable?"),

    // ---- Sigorta ----
    INS_EXCLUSIONS(Kind.INSURANCE, "LOW",
            "Teminat dışı haller",
            "Coverage exclusions",
            "Does the policy list exclusions from coverage?"),
    INS_WAITING_PERIOD(Kind.INSURANCE, "MEDIUM",
            "Bekleme süresi",
            "Waiting period",
            "Does the policy state a waiting period before coverage starts?"),
    INS_DEDUCTIBLE_OR_LIMITS(Kind.INSURANCE, "LOW",
            "Muafiyet ve teminat limitleri",
            "Deductible and coverage limits",
            "Does the policy state a deductible or coverage limit amount?"),
    INS_CLAIM_DEADLINES(Kind.INSURANCE, "MEDIUM", Topic.DEADLINES,
            "Kısa hasar bildirim süresi veya ağır yükümlülükler",
            "Short claim deadlines or heavy obligations",
            "Does the policy state a deadline for notifying claims?"),
    INS_CANCELLATION_REFUND(Kind.INSURANCE, "LOW",
            "İptal ve iade koşulları kısıtlı",
            "Restricted cancellation or refund",
            "Does the policy state conditions for cancellation or premium refund?"),

    // ---- Araç alım-satım ----
    VEH_AS_IS(Kind.VEHICLE, "HIGH",
            "Araç 'olduğu gibi' satılıyor, garanti yok",
            "Vehicle sold as-is without warranty",
            "Does the document state that the vehicle is sold as-is or without warranty?"),
    VEH_PAYMENT_BEFORE_TRANSFER(Kind.VEHICLE, "HIGH",
            "Ödeme devirden önce",
            "Payment due before transfer of ownership",
            "Does the document require full payment before the ownership transfer is completed?"),
    VEH_LIABILITY_DISCLAIMER(Kind.VEHICLE, "MEDIUM", Topic.LIABILITY,
            "Satıcı sorumluluğunu sınırlıyor",
            "Seller limits liability",
            "Does the seller disclaim liability for defects, mileage or damage history?"),
    VEH_DECLARED_HISTORY(Kind.VEHICLE, "LOW",
            "Hasar veya kilometre beyanı",
            "Declared damage or mileage record",
            "Does the document declare accident damage, mileage or prior use?"),

    // ---- Genel sözleşme / diğer ----
    GEN_UNILATERAL_TERMINATION(Kind.GENERAL, "HIGH", Topic.TERMINATION,
            "Karşı tarafa tek taraflı fesih hakkı",
            "Counterparty's unilateral termination right",
            "Does the document give the other party a right to terminate unilaterally?"),
    GEN_PENALTIES(Kind.GENERAL, "MEDIUM", Topic.PENALTY,
            "Cezai şart",
            "Penalty clause",
            "Does the document contain a penalty or liquidated-damages clause binding the reader?"),
    GEN_AUTO_RENEWAL(Kind.GENERAL, "MEDIUM", Topic.AUTO_RENEWAL,
            "Otomatik yenileme",
            "Automatic renewal",
            "Does the document state that it renews automatically?"),
    GEN_LIABILITY_WAIVER(Kind.GENERAL, "MEDIUM", Topic.LIABILITY,
            "Sorumluluk sınırlaması veya feragat",
            "Liability limitation or waiver",
            "Does the document contain a waiver of the reader's rights or a limitation of the other party's liability?"),
    GEN_BINDING_DEADLINES(Kind.GENERAL, "LOW", Topic.DEADLINES,
            "Bağlayıcı süreler ve bildirim şartları",
            "Binding deadlines and notice requirements",
            "Does the document set deadlines or notice periods binding the reader?"),
    GEN_UNCLEAR_PAYMENT(Kind.GENERAL, "MEDIUM",
            "Ödeme koşulları belirsiz veya değişken",
            "Payment terms unclear or variable",
            "Are payment amounts or due dates left variable, open or at the other party's discretion?");

    /** Belge türü. Çıkarım belgeyi sınıflar; kullanıcının tip seçimine bağlı değil. */
    public enum Kind { FINANCIAL, RENTAL, EMPLOYMENT, SUBSCRIPTION, INSURANCE, VEHICLE, GENERAL, OTHER }

    /**
     * Genel soruyla türe özel sorunun aynı maddeyi sorduğu durumlar. Kira sözleşmesinde hem
     * "kiraya verenin fesih hakkı" hem "karşı tarafa tek taraflı fesih hakkı" sorulursa aynı
     * madde rapora iki başlıkla giriyor; konusu tutulan genel soru sorulmaz.
     */
    public enum Topic { NONE, TERMINATION, PENALTY, AUTO_RENEWAL, LIABILITY, DEADLINES }

    private final Kind kind;
    private final String severity;
    private final Topic topic;
    private final String trTitle;
    private final String enTitle;
    private final String question;

    RubricItem(Kind kind, String severity, String trTitle, String enTitle, String question) {
        this(kind, severity, Topic.NONE, trTitle, enTitle, question);
    }

    RubricItem(Kind kind, String severity, Topic topic, String trTitle, String enTitle, String question) {
        this.kind = kind;
        this.severity = severity;
        this.topic = topic;
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

    public Topic topic() { return topic; }

    /**
     * Verilen soruların üzerine genel soruları ekler. Tek taraflı fesih, cezai şart, otomatik yenileme
     * gibi maddeler belgenin türünden bağımsız geçerli; önceden yalnızca sınıflandırma tam "general"
     * dediğinde soruluyordu. Danışmanlık sözleşmesi "hizmet sözleşmesi" sayılıp iş sözleşmesi
     * sorularını aldığı için içindeki tek taraflı fesih hakkı hiç sorulmadan rapor temiz çıkıyordu.
     */
    public static List<RubricItem> withGeneral(Kind kind, List<RubricItem> items) {
        // Hiçbir tür bu sorulardan muaf değil, finansal tablo dahil. Bir ara "finansal tabloya
        // sözleşme sorusu sorulmasın" istisnası vardı; rakam yoğun bir kredi formu finansal
        // sanılınca belgeye ne finansal ne sözleşme sorusu tutuyor, rapor sıfır bulguyla "temiz"
        // çıkıyordu. Yanlış yere düşen bulgu kullanıcıya görünür, hiç sorulmayan soru görünmez.
        List<RubricItem> out = new ArrayList<>();
        EnumSet<Topic> covered = EnumSet.noneOf(Topic.class);
        if (items != null) {
            for (RubricItem r : items) {
                if (out.contains(r)) continue;
                out.add(r);
                if (r.topic != Topic.NONE) covered.add(r.topic);
            }
        }
        for (RubricItem g : forKind(Kind.GENERAL)) {
            if (out.contains(g)) continue;
            if (g.topic != Topic.NONE && covered.contains(g.topic)) continue;
            out.add(g);
        }
        return out;
    }

    public static List<RubricItem> forKind(Kind kind) {
        return Arrays.stream(values()).filter(r -> r.kind == kind).toList();
    }

    public static Kind kindOf(String raw) {
        if (raw == null) return Kind.OTHER;
        try { return Kind.valueOf(raw.trim().toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return Kind.OTHER; }
    }
}