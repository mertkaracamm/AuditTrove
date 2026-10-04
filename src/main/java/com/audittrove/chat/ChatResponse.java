package com.audittrove.chat;

import java.util.List;

/**
 * answer: cevap, telefon dilinde. pages: cevabın dayandığı sayfalar (arayüz görüntüleyiciye bağlar).
 * grounded: cevap gönderilen rapor ve sayfalara dayanıyor mu; false ise arayüz "belgede bulunamadı" tonunda gösterir.
 */
public record ChatResponse(String answer, List<Integer> pages, boolean grounded) {
    public ChatResponse {
        answer = answer == null ? "" : answer;
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}
