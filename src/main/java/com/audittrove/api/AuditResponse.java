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
        int pageCount) {
    /** language: raporun dili ("tr"/"en"); arayüz buna kilitlenir. pageCount: belgenin sayfa sayısı ("Sayfa 13 / 18"). */
    @JsonCreator
    public AuditResponse {
        riskScore = Math.max(0, Math.min(100, riskScore));
        risks = risks == null ? List.of() : List.copyOf(risks);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        keyMetrics = keyMetrics == null ? List.of() : List.copyOf(keyMetrics);
        advisorQuestions = advisorQuestions == null ? List.of() : List.copyOf(advisorQuestions);
        references = references == null ? List.of() : List.copyOf(references);
        language = language == null ? "" : language;
    }

    public AuditResponse(int riskScore, String scoreRationale, String summary, List<Risk> risks,
                         List<String> recommendations, List<KeyMetric> keyMetrics,
                         List<String> advisorQuestions, List<Reference> references) {
        this(riskScore, scoreRationale, summary, risks, recommendations, keyMetrics, advisorQuestions, references, null, 0);
    }

    /**
     * pages: bulgunun dayandığı gerçek sayfalar; metin içinde sayfa atfı taşınmaz, arayüz bu alandan basar.
     * source: "engine" (sayılardan kodla), "rubric" (kontrol listesi, sabit başlık/önem), "model" (LLM'in
     * serbest gözlemi; skora girmez, arayüz "ek gözlem" olarak ayırır).
     */
    public record Risk(String title, String severity, String finding, String evidence, List<Integer> pages, String source,
                       List<Anchor> anchors) {
        public static final String ENGINE = "engine";
        public static final String RUBRIC = "rubric";
        public static final String MODEL = "model";

        @JsonCreator
        public Risk {
            pages = pages == null ? List.of() : List.copyOf(pages);
            source = source == null || source.isBlank() ? MODEL : source;
            anchors = anchors == null ? List.of() : List.copyOf(anchors);
        }

        public Risk(String title, String severity, String finding, String evidence, List<Integer> pages, String source) {
            this(title, severity, finding, evidence, pages, source, List.of());
        }

        public Risk(String title, String severity, String finding, String evidence) {
            this(title, severity, finding, evidence, List.of(), MODEL, List.of());
        }

        public Risk(String title, String severity, String finding, String evidence, List<Integer> pages) {
            this(title, severity, finding, evidence, pages, MODEL, List.of());
        }

        public boolean isModel() {
            return MODEL.equals(source);
        }

        public Risk withAnchors(List<Anchor> anchors) {
            return new Risk(title, severity, finding, evidence, pages, source, anchors);
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