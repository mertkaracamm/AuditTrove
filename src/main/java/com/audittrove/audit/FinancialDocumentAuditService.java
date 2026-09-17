package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.llm.AuditLlmClient;
import com.audittrove.pdf.EvidenceLocator;
import com.audittrove.pdf.PageText;
import com.audittrove.pdf.PdfGeometry;
import com.audittrove.pdf.OcrPageReader;
import com.audittrove.pdf.PdfTextExtractor;
import com.audittrove.rag.RegulationChunk;
import com.audittrove.rag.RegulationRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.TreeMap;
import java.util.Map;

@Service
public class FinancialDocumentAuditService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FinancialDocumentAuditService.class);
    private final OcrPageReader ocrPageReader;
    private final RegulationRetriever regulationRetriever;
    private final AuditLlmClient llmClient;
    private final long maxPdfBytes;

    public FinancialDocumentAuditService(OcrPageReader ocrPageReader,
                                         RegulationRetriever regulationRetriever,
                                         AuditLlmClient llmClient,
                                         @Value("${audittrove.max-pdf-bytes:15728640}") long maxPdfBytes) {
        this.ocrPageReader = ocrPageReader;
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

    /** Sayfa numarasi ve o sayfanin metni. PDF baytlarini gonderemeyen istemciler icin.
     *  pdf.PageText ile karistirilmasin: bu disaridan gelen ham girdi, o cikarilmis geometri. */
    public record PageInput(int page, String text) {}

    // Metin girisinde PDF boyut tavani anlamsiz; yerine karakter tavani koyuyoruz.
    private static final int MAX_TEXT_CHARS = 400_000;
    private static final Pattern PAGE_MARKER = Pattern.compile("\\[REPORT PAGE (\\d+)]");

    /** ChatGPT gibi istemciler dosyanin baytlarini degil, okunmus metnini gonderebiliyor. Metin
     *  PDF'ten cikarilmis gibi ayni sayfa isaretleriyle kuruluyor ki sayfa atiflari bozulmasin.
     *  Bayt olmadigi icin kanit konumu (anchors) ve sayfa metni uretilmez. */
    public AuditResponse auditText(String documentText, List<PageInput> pages, String language, String documentType) {
        String text = pages == null || pages.isEmpty() ? withMarkers(documentText) : join(pages);
        if (text == null || text.trim().length() < 40) {
            throw new InvalidDocumentException("Belge metni bulunamadı");
        }
        if (text.length() > MAX_TEXT_CHARS) {
            throw new InvalidDocumentException("Belge metni izin verilen boyutu aşıyor");
        }
        int total = countPages(text);
        return llmClient.audit(text, List.of(), language, documentType, false, total, total);
    }

    /** Sayfa isareti yoksa metnin tamami tek sayfa sayilir; atif uretimi yine calisir. */
    private String withMarkers(String documentText) {
        if (documentText == null || documentText.isBlank()) {
            return null;
        }
        return PAGE_MARKER.matcher(documentText).find()
                ? documentText.trim()
                : "[REPORT PAGE 1]\n" + documentText.trim();
    }

    private String join(List<PageInput> pages) {
        StringBuilder builder = new StringBuilder();
        pages.stream()
                .filter(page -> page != null && page.text() != null && !page.text().isBlank())
                .sorted(Comparator.comparingInt(PageInput::page))
                .forEach(page -> builder.append("\n\n[REPORT PAGE ")
                        .append(Math.max(1, page.page()))
                        .append("]\n")
                        .append(page.text().trim()));
        return builder.toString().trim();
    }

    private int countPages(String text) {
        Matcher matcher = PAGE_MARKER.matcher(text);
        int max = 0;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return Math.max(1, max);
    }

    public AuditResponse audit(String filename, byte[] content) {
        return audit(filename, content, null, null);
    }

    public AuditResponse audit(String filename, byte[] content, String language) {
        return audit(filename, content, language, null);
    }

    public AuditResponse audit(String filename, byte[] content, String language, String documentType) {
        return audit(filename, content, language, documentType, () -> false);
    }

    /** cancelled: kullanıcı incelemeyi bıraktı mı. Bayrak model çağrılarına kadar iner, kalanlar yapılmaz. */
    public AuditResponse audit(String filename, byte[] content, String language, String documentType,
                               java.util.function.BooleanSupplier cancelled) {
        return CancelScope.run(cancelled, () -> runAudit(filename, content, language, documentType));
    }

    private AuditResponse runAudit(String filename, byte[] content, String language, String documentType) {
        validate(filename, content);
        try {
            // Metin de satir konumlari da TEK kaynaktan okunur. Once iki ayri cikarim vardi
            // (inceleme PDFTextStripper'i, cipalar PdfGeometry'yi) ve tablo basliklari ikisinde farkli
            // siraya giriyordu: modelin gordugu cumle cipa tarafinda bulunamayip bulgu sayfada
            // isaretlenemiyordu. Tek kaynak bu sinifi tamamen ortadan kaldiriyor.
            Map<Integer, PageText> pages = PdfGeometry.read(content);
            PdfTextExtractor.ExtractResult extracted = PdfTextExtractor.fromPages(pages, pages.size());
            if (extracted == null) {
                // Metin katmani yok: taranmis ya da telefonla cekilmis belge. Metin de satir konumlari da
                // OCR'dan gelir; ikisi ayni kaynak oldugu icin bulgular sayfada yine isaretlenebilir.
                pages = ocrPageReader.read(content);
                extracted = PdfTextExtractor.fromPages(pages, pageCount(content));
            }
            if (extracted == null) {
                throw new InvalidDocumentException("PDF içinde analiz edilebilir metin bulunamadı");
            }
            // Belgeler kendi iceriklerine gore degerlendirilir; RAG korpusu aktif degil.
            List<RegulationChunk> context = List.of();
            AuditResponse response = llmClient.audit(extracted.text(), context, language, documentType,
                    extracted.truncated(), extracted.totalPages(), extracted.includedPages());
            return anchorEvidence(response, pages);
        } catch (IOException exception) {
            throw new InvalidDocumentException("PDF okunamadı", exception);
        }
    }

    // Bulgu kanıtlarının sayfa üzerindeki yerleri: görüntüleyici bunları boyar. Konum bulunamazsa rapor
    // aynen döner; bu adım hiçbir zaman incelemeyi düşürmez.
    private static int pageCount(byte[] content) throws IOException {
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(content)) {
            return document.getNumberOfPages();
        }
    }

    private AuditResponse anchorEvidence(AuditResponse response, Map<Integer, PageText> pages) {
        try {
            if (pages == null || pages.isEmpty()) return response;
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