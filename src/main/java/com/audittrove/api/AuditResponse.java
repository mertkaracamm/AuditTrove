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

    /** pages: bulgunun dayandığı gerçek sayfalar; metin içinde sayfa atfı taşınmaz, arayüz bu alandan basar. */
    public record Risk(String title, String severity, String finding, String evidence, List<Integer> pages) {
        @JsonCreator
        public Risk {
            pages = pages == null ? List.of() : List.copyOf(pages);
        }

        public Risk(String title, String severity, String finding, String evidence) {
            this(title, severity, finding, evidence, List.of());
        }

        public Risk withPagesAndEvidence(List<Integer> newPages, String newEvidence) {
            return new Risk(title, severity, finding, newEvidence, newPages);
        }
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