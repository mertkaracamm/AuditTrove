package com.audittrove.audit;

import com.audittrove.llm.AuditLlmClient;
import com.audittrove.pdf.PdfTextExtractor;
import com.audittrove.rag.RegulationRetriever;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FinancialDocumentAuditServiceTest {
    private final FinancialDocumentAuditService service = new FinancialDocumentAuditService(
            mock(PdfTextExtractor.class), mock(RegulationRetriever.class), mock(AuditLlmClient.class), 1024);

    @Test
    void rejectsNonPdfFiles() {
        assertThatThrownBy(() -> service.audit("statement.txt", "hello".getBytes()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Yalnızca PDF dosyaları desteklenir");
    }

    @Test
    void rejectsSpoofedPdfExtension() {
        assertThatThrownBy(() -> service.audit("statement.pdf", "hello".getBytes()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessage("Dosya geçerli bir PDF değil");
    }
}
