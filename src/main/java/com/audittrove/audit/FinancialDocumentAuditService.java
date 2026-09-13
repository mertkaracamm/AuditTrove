package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.llm.AuditLlmClient;
import com.audittrove.pdf.EvidenceLocator;
import com.audittrove.pdf.PageText;
import com.audittrove.pdf.PdfGeometry;
import com.audittrove.pdf.PdfTextExtractor;
import com.audittrove.rag.RegulationChunk;
import com.audittrove.rag.RegulationRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;

@Service
public class FinancialDocumentAuditService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FinancialDocumentAuditService.class);
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
            AuditResponse response = llmClient.audit(extracted.text(), context, language, documentType,
                    extracted.truncated(), extracted.totalPages(), extracted.includedPages());
            return anchorEvidence(response, content);
        } catch (IOException exception) {
            throw new InvalidDocumentException("PDF okunamadı", exception);
        }
    }

    // Bulgu kanıtlarının sayfa üzerindeki yerleri: görüntüleyici bunları boyar. Konum bulunamazsa rapor
    // aynen döner; bu adım hiçbir zaman incelemeyi düşürmez.
    private AuditResponse anchorEvidence(AuditResponse response, byte[] content) {
        try {
            Map<Integer, PageText> pages = PdfGeometry.read(content);
            AuditResponse anchored = EvidenceLocator.annotate(response, pages);
            // Sayfa metinleri de yanıtla iner: soru-cevap için cihaz bunları saklar, sunucu belge tutmaz.
            List<AuditResponse.PageContent> texts = new ArrayList<>();
            for (Map.Entry<Integer, PageText> e : new TreeMap<>(pages).entrySet()) {
                StringBuilder sb = new StringBuilder();
                for (PageText.Line line : e.getValue().lines()) sb.append(line.text()).append('\n');
                texts.add(new AuditResponse.PageContent(e.getKey(), sb.toString().strip()));
            }
            return anchored.withPageTexts(texts);
        } catch (Exception e) {
            log.warn("Kanit konumlari cikarilamadi, rapor konumsuz donuyor: {}", e.toString());
            return response;
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