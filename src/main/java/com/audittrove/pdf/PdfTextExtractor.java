package com.audittrove.pdf;

import com.audittrove.audit.InvalidDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {

    // Cok asiri belgelerde bellek/sure korumasi icin ust tavan (~3M karakter, ~450 sayfa).
    // Async akista sure baskisi olmadigindan tavan yuksek; chunking metni parcalara boler.
    // Bu tavani asan belgelerde truncated=true olur ve ozet notu dogru sekilde kismi inceleme der.
    private static final int HARD_CAP_CHARS = 3_000_000;

    /** Metin cikarma sonucu. truncated yalnizca HARD_CAP asilirsa true olur (cok nadir). */
    public record ExtractResult(String text, int totalPages, int includedPages, boolean truncated) {}

    public String extract(byte[] content) throws IOException {
        return extractDetailed(content).text();
    }

    public ExtractResult extractDetailed(byte[] content) throws IOException {
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
            if (text.length() < 40) {
                throw new InvalidDocumentException("PDF içinde analiz edilebilir metin bulunamadı");
            }
            return new ExtractResult(text, total, included, truncated);
        }
    }
}