package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.llm.AuditLlmClient;
import com.audittrove.pdf.OcrPageReader;
import com.audittrove.pdf.PageText;
import com.audittrove.pdf.PdfTextExtractor;
import com.audittrove.rag.RegulationChunk;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpOcrPolicyTest {

    /** Metin katmani olmayan belge: bos sayfa da taranmis sayfa gibi metin cikariciya bos doner. */
    private static byte[] pdfWithNoTextLayer() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static class RecordingOcr extends OcrPageReader {
        boolean called;

        RecordingOcr() {
            super(true, 200, 20, 40, "tur+eng");
        }

        @Override
        public Map<Integer, PageText> read(byte[] content) {
            called = true;
            return Map.of(1, new PageText(1, List.of(new PageText.Line(
                    "Taranmis belgeden okunan deneme satiri",
                    new AuditResponse.Rect(0.1, 0.1, 0.5, 0.02)))));
        }
    }

    private static class FakeLlm implements AuditLlmClient {
        boolean called;

        @Override
        public AuditResponse audit(String documentText, List<RegulationChunk> context, String language,
                                   String documentType, boolean truncated, int totalPages, int includedPages) {
            called = true;
            return new AuditResponse(50, "", "", List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    private static FinancialDocumentAuditService service(RecordingOcr ocr, FakeLlm llm) {
        return new FinancialDocumentAuditService(new PdfTextExtractor(), ocr, null, llm, 15_728_640L);
    }

    @Test
    void theMcpPathDoesNotRunOcrAndTellsTheClientToSendText() throws Exception {
        RecordingOcr ocr = new RecordingOcr();
        FakeLlm llm = new FakeLlm();

        assertThatThrownBy(() -> service(ocr, llm)
                .auditWithoutOcr("taranmis.pdf", pdfWithNoTextLayer(), "tr", "general"))
                .isInstanceOf(InvalidDocumentException.class);

        // Sure sinirini asacak is hic baslamamali: ne OCR calisir ne de model bedeli odenir.
        assertThat(ocr.called).isFalse();
        assertThat(llm.called).isFalse();
    }

    @Test
    void theAppPathStillReadsScannedDocumentsWithOcr() throws Exception {
        RecordingOcr ocr = new RecordingOcr();
        FakeLlm llm = new FakeLlm();

        service(ocr, llm).audit("taranmis.pdf", pdfWithNoTextLayer(), "tr", "general");

        assertThat(ocr.called).isTrue();
        assertThat(llm.called).isTrue();
    }
}
