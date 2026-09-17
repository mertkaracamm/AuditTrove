package com.audittrove.pdf;

import com.audittrove.audit.InvalidDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

@Component
public class PdfTextExtractor {

    // Cok asiri belgelerde bellek/sure korumasi icin ust tavan (~3M karakter, ~450 sayfa).
    // Async akista sure baskisi olmadigindan tavan yuksek; chunking metni parcalara boler.
    // Bu tavani asan belgelerde truncated=true olur ve ozet notu dogru sekilde kismi inceleme der.
    private static final int HARD_CAP_CHARS = 3_000_000;
    // Bu kadar metin bile cikmiyorsa belgenin metin katmani yok demektir.
    private static final int MIN_CHARS = 40;

    /** Metin cikarma sonucu. truncated yalnizca HARD_CAP asilirsa true olur (cok nadir). */
    public record ExtractResult(String text, int totalPages, int includedPages, boolean truncated) {}

    public String extract(byte[] content) throws IOException {
        return extractDetailed(content).text();
    }

    /** Metin katmanı yoksa hata verir. OCR'a düşebilen çağıranlar extractOrNull kullanır. */
    public ExtractResult extractDetailed(byte[] content) throws IOException {
        ExtractResult result = extractOrNull(content);
        if (result == null) {
            throw new InvalidDocumentException("PDF içinde analiz edilebilir metin bulunamadı");
        }
        return result;
    }

    /** Sayfa satırlarından metin kurar: OCR'dan gelen belge de aynı [REPORT PAGE n] biçimini alır. */
    public static ExtractResult fromPages(Map<Integer, PageText> pages, int totalPages) {
        if (pages == null || pages.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, PageText> entry : new TreeMap<>(pages).entrySet()) {
            sb.append("\n\n[REPORT PAGE ").append(entry.getKey()).append("]\n");
            for (PageText.Line line : entry.getValue().lines()) sb.append(line.text()).append('\n');
        }
        String text = sb.toString().trim();
        if (text.length() < MIN_CHARS) return null;
        return new ExtractResult(text, totalPages, pages.size(), pages.size() < totalPages);
    }

    /** Metin katmanı yoksa null döner. */
    public ExtractResult extractOrNull(byte[] content) throws IOException {
        PDDocument document;
        try {
            document = Loader.loadPDF(content);
        } catch (InvalidPasswordException e) {
            // Yalnizca ACILIS parolasi (user-password) olan PDF'ler burada patlar.
            throw new InvalidDocumentException("Açılış parolası olan PDF dosyaları desteklenmiyor", e);
        }
        try (document) {
            // Owner-password'lu (acilabilen ama kopyalama/yazdirma kisitli) belgeler resmi
            // evraklarda yaygin — bunlardan metin cikarilabilir. Guvenlik bayraklarini kaldirip
            // metin cikarmayi garanti ediyoruz; isEncrypted reddi kaldirildi.
            document.setAllSecurityToBeRemoved(true);
            int total = document.getNumberOfPages();
            StringBuilder sb = new StringBuilder();
            int included = 0;
            boolean truncated = false;
            for (int page = 1; page <= total; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String block = "\n\n[REPORT PAGE " + page + "]\n" + stripper.getText(document);
                if (sb.length() + block.length() > HARD_CAP_CHARS && included > 0) {
                    truncated = true;
                    break;
                }
                sb.append(block);
                included++;
            }
            String text = sb.toString().trim();
            if (text.length() < MIN_CHARS) {
                // Metin katmanı yok: taranmış belge olabilir, çağıran OCR'a düşer.
                return null;
            }
            return new ExtractResult(text, total, included, truncated);
        }
    }
}