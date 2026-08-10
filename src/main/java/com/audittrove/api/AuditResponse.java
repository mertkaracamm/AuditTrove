package com.audittrove.api;

import java.util.List;

public record AuditResponse(
        int riskScore,
        String summary,
        List<Risk> risks,
        List<String> recommendations,
        List<Reference> references) {
    public AuditResponse {
        riskScore = Math.max(0, Math.min(100, riskScore));
        risks = risks == null ? List.of() : List.copyOf(risks);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public record Risk(String title, String severity, String finding, String evidence) {
    }

    public record Reference(String source, String article, String title) {
    }
}
