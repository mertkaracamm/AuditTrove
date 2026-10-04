package com.audittrove.api;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

public record AuditResponse(
        int riskScore,
        String scoreRationale,
        String summary,
        List<Risk> risks,
        List<String> recommendations,
        List<KeyMetric> keyMetrics,
        List<String> advisorQuestions,
        List<Reference> references,
        String language,
        int pageCount,
        List<PageContent> pageTexts) {
    /**
     * language: raporun dili ("tr"/"en"); arayüz buna kilitlenir. pageCount: belgenin sayfa sayısı ("Sayfa 13 / 18").
     * pageTexts: sayfa metinleri; cihaz saklar, "rapora soru sor" her soruda ilgili sayfaları geri gönderir.
     * Sunucu belge tutmaz, bu yüzden metin yanıtla birlikte cihaza iner.
     */
    @JsonCreator
    public AuditResponse {
        riskScore = Math.max(0, Math.min(100, riskScore));
        risks = risks == null ? List.of() : List.copyOf(risks);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        keyMetrics = keyMetrics == null ? List.of() : List.copyOf(keyMetrics);
        advisorQuestions = advisorQuestions == null ? List.of() : List.copyOf(advisorQuestions);
        references = references == null ? List.of() : List.copyOf(references);
        language = language == null ? "" : language;
        pageTexts = pageTexts == null ? List.of() : List.copyOf(pageTexts);
    }

    public AuditResponse(int riskScore, String scoreRationale, String summary, List<Risk> risks,
                         List<String> recommendations, List<KeyMetric> keyMetrics,
                         List<String> advisorQuestions, List<Reference> references) {
        this(riskScore, scoreRationale, summary, risks, recommendations, keyMetrics, advisorQuestions, references, null, 0, List.of());
    }

    public AuditResponse(int riskScore, String scoreRationale, String summary, List<Risk> risks,
                         List<String> recommendations, List<KeyMetric> keyMetrics,
                         List<String> advisorQuestions, List<Reference> references, String language, int pageCount) {
        this(riskScore, scoreRationale, summary, risks, recommendations, keyMetrics, advisorQuestions, references, language, pageCount, List.of());
    }

    public AuditResponse withPageTexts(List<PageContent> pageTexts) {
        return new AuditResponse(riskScore, scoreRationale, summary, risks, recommendations, keyMetrics, advisorQuestions, references, language, pageCount, pageTexts);
    }

    /** Sayfa metni olmadan aynı rapor (MCP yanıtı gibi metnin gereksiz olduğu yerler için). */
    public AuditResponse withoutPageTexts() {
        return pageTexts.isEmpty() ? this : withPageTexts(List.of());
    }

    /** Bir sayfanın düz metni; satırlar yeni satırla ayrılmış. */
    public record PageContent(int page, String text) {
        @JsonCreator
        public PageContent {
            text = text == null ? "" : text;
        }
    }

    /**
     * pages: bulgunun dayandığı gerçek sayfalar; metin içinde sayfa atfı taşınmaz, arayüz bu alandan basar.
     * source: "engine" (sayılardan kodla), "rubric" (kontrol listesi, sabit başlık/önem), "model" (LLM'in
     * serbest gözlemi; skora girmez, arayüz "ek gözlem" olarak ayırır).
     */
    public record Risk(String title, String severity, String finding, String evidence, List<Integer> pages, String source,
                       String quote, List<Anchor> anchors) {
        public static final String ENGINE = "engine";
        public static final String RUBRIC = "rubric";
        public static final String MODEL = "model";

        /** quote: kanıtın dayandığı, belgeden kelimesi kelimesine alınmış kısa parça (belgenin kendi dilinde). Sayfa üzerinde yer bulmak için kullanılır. */
        @JsonCreator
        public Risk {
            pages = pages == null ? List.of() : List.copyOf(pages);
            source = source == null || source.isBlank() ? MODEL : source;
            quote = quote == null ? "" : quote;
            anchors = anchors == null ? List.of() : List.copyOf(anchors);
        }

        public Risk(String title, String severity, String finding, String evidence, List<Integer> pages, String source, String quote) {
            this(title, severity, finding, evidence, pages, source, quote, List.of());
        }

        public Risk(String title, String severity, String finding, String evidence, List<Integer> pages, String source) {
            this(title, severity, finding, evidence, pages, source, "", List.of());
        }

        public Risk(String title, String severity, String finding, String evidence) {
            this(title, severity, finding, evidence, List.of(), MODEL, "", List.of());
        }

        public Risk(String title, String severity, String finding, String evidence, List<Integer> pages) {
            this(title, severity, finding, evidence, pages, MODEL, "", List.of());
        }

        public boolean isModel() {
            return MODEL.equals(source);
        }

        public boolean isRubric() {
            return RUBRIC.equals(source);
        }

        public Risk withAnchors(List<Anchor> anchors) {
            return new Risk(title, severity, finding, evidence, pages, source, quote, anchors);
        }
    }

    /**
     * Bulgu kanıtının sayfa üzerindeki yeri: görüntüleyici bu dikdörtgenleri boyar. Koordinatlar sayfa
     * genişliği/yüksekliğine oranlı (0..1), sol üst köşe orijin; böylece arayüz PDF birimlerini bilmek zorunda kalmaz.
     * rects boşsa arayüz sayfa kenarında şerit gösterir (kanıt sayfada bulundu ama satırı eşleşmedi).
     */
    public record Anchor(int page, List<Rect> rects) {
        @JsonCreator
        public Anchor {
            rects = rects == null ? List.of() : List.copyOf(rects);
        }
    }

    public record Rect(double x, double y, double w, double h) {
    }

    /** value yalnızca sayı/oran, unit ayrı ("thousand TL", "%"); biçimlendirme arayüzde. */
    public record KeyMetric(String label, String value, String unit, String note) {
        @JsonCreator
        public KeyMetric {
            unit = unit == null ? "" : unit;
        }

        public KeyMetric(String label, String value, String note) {
            this(label, value, "", note);
        }
    }

    public record Reference(String source, String article, String title) {
    }
}