package com.audittrove.mcp;

import com.audittrove.api.AuditResponse;
import com.audittrove.audit.FinancialDocumentAuditService;
import com.audittrove.audit.InvalidDocumentException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MCP sozlesmesi: ChatGPT App review'una gonderilen senaryolarin makine karsiligi.
 *  Model cagrilmaz, inceleme servisi taklit edilir; burada sinanan sey protokol ve girdi yollari. */
class McpContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FinancialDocumentAuditService auditService;
    private McpController controller;

    @BeforeEach
    void setUp() {
        auditService = mock(FinancialDocumentAuditService.class);
        controller = new McpController(auditService, MAPPER);
        AuditResponse sample = new AuditResponse(
                42, "Örnek gerekçe", "Örnek özet",
                List.of(new AuditResponse.Risk("Rekabet yasağı", "high", "İki yıl sürüyor", "Madde 5")),
                List.of("Süreyi pazarlık edin"),
                List.of(new AuditResponse.KeyMetric("Brüt ücret", "142.000 TL", null)),
                List.of("Yasak coğrafi olarak sınırlanabilir mi?"),
                List.of());
        when(auditService.auditText(any(), any(), anyString(), anyString())).thenReturn(sample);
        when(auditService.audit(anyString(), any(byte[].class), anyString(), anyString())).thenReturn(sample);
    }

    private JsonNode call(String json) throws Exception {
        return MAPPER.valueToTree(controller.handle(MAPPER.readTree(json)));
    }

    @Test
    void toolsListAdvertisesTextInputsFirst() throws Exception {
        JsonNode response = call("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        JsonNode tool = response.path("result").path("tools").get(0);
        assertThat(tool.path("name").asText()).isEqualTo("audit_document");
        JsonNode properties = tool.path("inputSchema").path("properties");
        assertThat(properties.has("documentText")).isTrue();
        assertThat(properties.has("pageTexts")).isTrue();
        assertThat(properties.has("pdfBase64")).isTrue();
        // Hicbir alan zorunlu degil: istemci ucunden hangisini gonderebiliyorsa onu gonderiyor.
        assertThat(tool.path("inputSchema").has("required")).isFalse();
    }

    @Test
    void plainTextIsAccepted() throws Exception {
        JsonNode response = call("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"documentText":"KİRA SÖZLEŞMESİ\\nMadde 1 - Kira bedeli aylık 42.500 TL.",
                 "language":"tr","documentType":"rental"}}}""");
        assertThat(response.path("result").path("structuredContent").path("riskScore").asInt()).isEqualTo(42);
        verify(auditService).auditText(anyString(), eq(List.of()), eq("tr"), eq("rental"));
        verify(auditService, never()).audit(anyString(), any(byte[].class), anyString(), anyString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pagedTextKeepsPageNumbers() throws Exception {
        call("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"pageTexts":[{"page":1,"text":"Birinci sayfa"},{"page":2,"text":"İkinci sayfa"}],
                 "language":"en","documentType":"general"}}}""");
        ArgumentCaptor<List<FinancialDocumentAuditService.PageInput>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditService).auditText(any(), captor.capture(), eq("en"), eq("general"));
        assertThat(captor.getValue()).extracting(FinancialDocumentAuditService.PageInput::page)
                .containsExactly(1, 2);
    }

    @Test
    void base64StillWorksForClientsThatSendBytes() throws Exception {
        String pdf = java.util.Base64.getEncoder().encodeToString("%PDF-1.4 sahte".getBytes());
        call("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"audit_document\","
                + "\"arguments\":{\"filename\":\"a.pdf\",\"pdfBase64\":\"" + pdf + "\"}}}");
        verify(auditService).audit(eq("a.pdf"), any(byte[].class), eq("en"), eq("general"));
    }

    @Test
    void missingDocumentIsAClearError() throws Exception {
        JsonNode response = call("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"language":"en"}}}""");
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(response.path("error").path("message").asText()).contains("documentText");
    }

    @Test
    void brokenBase64IsAClearError() throws Exception {
        JsonNode response = call("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"filename":"a.pdf","pdfBase64":"!!!not base64!!!"}}}""");
        assertThat(response.path("error").path("code").asInt()).isEqualTo(-32602);
    }

    @Test
    void invalidDocumentDoesNotCrashTheServer() throws Exception {
        when(auditService.auditText(any(), any(), anyString(), anyString()))
                .thenThrow(new InvalidDocumentException("Belge metni bulunamadı"));
        JsonNode response = call("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"documentText":"kısa"}}}""");
        assertThat(response.path("error").path("code").asInt()).isIn(-32602, -32603);
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
    }

    @Test
    void unknownToolAndMethodAreRejected() throws Exception {
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":"
                + "{\"name\":\"delete_everything\",\"arguments\":{}}}")
                .path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(call("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/list\"}")
                .path("error").path("code").asInt()).isEqualTo(-32601);
    }

    @Test
    void initializeAnnouncesToolsCapability() throws Exception {
        JsonNode response = call("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"initialize\"}");
        assertThat(response.path("result").path("capabilities").has("tools")).isTrue();
        assertThat(response.path("result").path("serverInfo").path("name").asText()).isEqualTo("audittrove");
    }

    /** Sayfa metinleri kullanicinin belgesinin kendisi; MCP yanitiyla disari cikmamali. */
    @Test
    void responseNeverCarriesPageTexts() throws Exception {
        JsonNode response = call("""
                {"jsonrpc":"2.0","id":11,"method":"tools/call","params":{"name":"audit_document",
                 "arguments":{"documentText":"Madde 1 - Kira bedeli aylık 42.500 TL olarak belirlenmiştir."}}}""");
        JsonNode structured = response.path("result").path("structuredContent");
        assertThat(structured.path("pageTexts").isArray()).isTrue();
        assertThat(structured.path("pageTexts")).isEmpty();
    }
}
