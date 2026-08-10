package com.audittrove.rag;

import java.time.LocalDate;

public record RegulationChunk(
        String id,
        String source,
        String lawNumber,
        String article,
        String paragraph,
        String title,
        String text,
        String authority,
        String documentType,
        String[] topics,
        String[] applicableDocuments,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status,
        String officialUrl,
        String contentHash,
        boolean verified,
        String[] keywords) {

    public boolean activeOn(LocalDate date) {
        return (status == null || "ACTIVE".equals(status))
                && (effectiveFrom == null || !effectiveFrom.isAfter(date))
                && (effectiveTo == null || !effectiveTo.isBefore(date));
    }
}
