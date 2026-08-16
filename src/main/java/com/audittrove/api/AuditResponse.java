package com.audittrove.api;

import java.util.List;

public record AuditResponse(
        int riskScore,
        String scoreRationale,
        String summary,
        List<Risk> risks,
        List<String> recommendations,
        List<KeyMetric> keyMetrics,
        List<String> advisorQuestions,
        List<Reference> references) {
    public AuditResponse {
        riskScore = Math.max(0, Math.min(100, riskScore));
        risks = risks == null ? List.of() : List.copyOf(risks);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        keyMetrics = keyMetrics == null ? List.of() : List.copyOf(keyMetrics);
        advisorQuestions = advisorQuestions == null ? List.of() : List.copyOf(advisorQuestions);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public record Risk(String title, String severity, String finding, String evidence) {
    }

    public record KeyMetric(String label, String value, String note) {
    }

    public record Reference(String source, String article, String title) {
    }
}