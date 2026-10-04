package com.audittrove.pdf;

import com.audittrove.api.AuditResponse;

import java.util.List;

/**
 * Bir sayfanın satırları ve her satırın sayfa üzerindeki yeri. Koordinatlar sayfa boyutuna oranlı (0..1),
 * orijin sol üst. PDF'ten okunur; eşleştirme bu yapının üstünde, PDFBox'tan bağımsız çalışır.
 */
public record PageText(int page, List<Line> lines) {

    public record Line(String text, AuditResponse.Rect rect) {
    }
}