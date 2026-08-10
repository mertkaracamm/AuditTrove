package com.audittrove.pdf;

import com.audittrove.audit.InvalidDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {
    public String extract(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new InvalidDocumentException("Şifreli PDF dosyaları desteklenmiyor");
            }
            String text = new PDFTextStripper().getText(document).trim();
            if (text.length() < 40) {
                throw new InvalidDocumentException("PDF içinde analiz edilebilir metin bulunamadı");
            }
            return text;
        }
    }
}
