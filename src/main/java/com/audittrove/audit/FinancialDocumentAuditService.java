package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.llm.AuditLlmClient;
import com.audittrove.pdf.PdfTextExtractor;
import com.audittrove.rag.RegulationChunk;
import com.audittrove.rag.RegulationRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class FinancialDocumentAuditService {
    private final PdfTextExtractor pdfTextExtractor;
    private final RegulationRetriever regulationRetriever;
    private final AuditLlmClient llmClient;
    private final long maxPdfBytes;

    public FinancialDocumentAuditService(PdfTextExtractor pdfTextExtractor,
                                         RegulationRetriever regulationRetriever,
                                         AuditLlmClient llmClient,
                                         @Value("${audittrove.max-pdf-bytes:15728640}") long maxPdfBytes) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.regulationRetriever = regulationRetriever;
        this.llmClient = llmClient;
        this.maxPdfBytes = maxPdfBytes;
    }

    public AuditResponse audit(MultipartFile file) {
        return audit(file, null);
    }

    public AuditResponse audit(MultipartFile file, String language) {
        return audit(file, language, null);
    }

    public AuditResponse audit(MultipartFile file, String language, String documentType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("PDF dosyası zorunludur");
        }
        try {
            return audit(file.getOriginalFilename(), file.getBytes(), language, documentType);
        } catch (IOException exception) {
            throw new InvalidDocumentException("Dosya okunamadı", exception);
        }
    }

    public AuditResponse audit(String filename, byte[] content) {
        return audit(filename, content, null, null);
    }

    public AuditResponse audit(String filename, byte[] content, String language) {
        return audit(filename, content, language, null);
    }

    public AuditResponse audit(String filename, byte[] content, String language, String documentType) {
        validate(filename, content);
        try {
            PdfTextExtractor.ExtractResult extracted = pdfTextExtractor.extractDetailed(content);
            // Belgeler kendi iceriklerine gore degerlendirilir; RAG korpusu aktif degil.
            List<RegulationChunk> context = List.of();
            return llmClient.audit(extracted.text(), context, language, documentType,
                    extracted.truncated(), extracted.totalPages(), extracted.includedPages());
        } catch (IOException exception) {
            throw new InvalidDocumentException("PDF okunamadı", exception);
        }
    }

    private void validate(String filename, byte[] content) {
        if (content == null || content.length < 5) {
            throw new InvalidDocumentException("PDF dosyası boş veya geçersiz");
        }
        if (content.length > maxPdfBytes) {
            throw new InvalidDocumentException("PDF izin verilen boyutu aşıyor");
        }
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new InvalidDocumentException("Yalnızca PDF dosyaları desteklenir");
        }
        if (content[0] != '%' || content[1] != 'P' || content[2] != 'D'
                || content[3] != 'F' || content[4] != '-') {
            throw new InvalidDocumentException("Dosya geçerli bir PDF değil");
        }
    }
}