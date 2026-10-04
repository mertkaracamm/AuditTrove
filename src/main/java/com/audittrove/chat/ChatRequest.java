package com.audittrove.chat;

import com.audittrove.api.AuditResponse;

import java.util.List;

/**
 * Rapora soru sor isteği. Sunucu belge ya da rapor tutmaz; cihaz her soruda raporu ve sayfa
 * metinlerini kendisi gönderir. pages: cihazın incelemeden sakladığı sayfa metinleri (tümü ya da bir kısmı).
 */
public record ChatRequest(String question, String language, AuditResponse report, List<AuditResponse.PageContent> pages) {
    public ChatRequest {
        question = question == null ? "" : question.strip();
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
