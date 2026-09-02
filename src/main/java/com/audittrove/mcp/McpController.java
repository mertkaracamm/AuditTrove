package com.audittrove.mcp;

import com.audittrove.audit.FinancialDocumentAuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@RestController
public class McpController {
    private final FinancialDocumentAuditService auditService;
    private final ObjectMapper objectMapper;

    public McpController(FinancialDocumentAuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    /** Tarayici/GET istekleri icin bilgi cevabi — MCP istemcileri POST kullanir. */
    @GetMapping(value = "/mcp", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> info() {
        return Map.of(
                "name", "audittrove",
                "version", "1.0.1",
                "protocol", "MCP (JSON-RPC over HTTP POST)",
                "hint", "Send JSON-RPC requests via POST to this endpoint.");
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(@RequestBody JsonNode request) {
        Object id = request.has("id") ? objectMapper.convertValue(request.get("id"), Object.class) : null;
        try {
            return switch (request.path("method").asText()) {
                case "initialize" -> success(id, Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of("tools", Map.of()),
                        "serverInfo", Map.of("name", "audittrove", "version", "1.0.0")));
                case "ping" -> success(id, Map.of());
                case "tools/list" -> success(id, Map.of("tools", List.of(auditTool())));
                case "tools/call" -> callTool(id, request.path("params"));
                default -> error(id, -32601, "Method not found");
            };
        } catch (IllegalArgumentException exception) {
            return error(id, -32602, exception.getMessage());
        } catch (Exception exception) {
            return error(id, -32603, "Audit could not be completed");
        }
    }

    private Map<String, Object> callTool(Object id, JsonNode params) throws Exception {
        if (!"audit_document".equals(params.path("name").asText())) {
            return error(id, -32602, "Unknown tool");
        }
        JsonNode arguments = params.path("arguments");
        String filename = arguments.path("filename").asText();
        byte[] pdf = decodePdf(arguments.path("pdfBase64").asText());
        String language = arguments.hasNonNull("language") ? arguments.path("language").asText() : "en";
        String documentType = arguments.hasNonNull("documentType") ? arguments.path("documentType").asText() : "general";
        String result = objectMapper.writeValueAsString(auditService.audit(filename, pdf, language, documentType));
        return success(id, Map.of(
                "content", List.of(Map.of("type", "text", "text", result)),
                "structuredContent", objectMapper.readTree(result)));
    }

    private Map<String, Object> auditTool() {
        return Map.of(
                "name", "audit_document",
                "title", "AI Document Review",
                "description", "Reviews a user-provided PDF document (contracts, lease agreements, insurance policies, "
                        + "financial reports and other documents) and returns a structured, page-referenced review with "
                        + "a score and findings. Extracted text is sent to AI providers (OpenAI, Anthropic, Google) for "
                        + "analysis. Results are decision-support information only and do not determine lawfulness or "
                        + "compliance, and are not professional financial, legal or investment advice.",
                "annotations", Map.of(
                        "readOnlyHint", true,
                        "openWorldHint", true,
                        "destructiveHint", false),
                "inputSchema", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "required", List.of("filename", "pdfBase64"),
                        "properties", Map.of(
                                "filename", Map.of("type", "string", "description", "PDF file name"),
                                "pdfBase64", Map.of("type", "string", "description", "Base64-encoded PDF content"),
                                "language", Map.of("type", "string", "description",
                                        "Report language: 'tr' or 'en'. Defaults to 'en'."),
                                "documentType", Map.of("type", "string", "description",
                                        "Document type hint: general, financial, rental or subscription. Defaults to 'general'."))));
    }

    /** Accept standard and URL-safe Base64 payloads sent by MCP clients. */
    private byte[] decodePdf(String encodedPdf) {
        if (encodedPdf == null || encodedPdf.isBlank()) {
            throw new IllegalArgumentException("PDF içeriği eksik");
        }

        String normalized = encodedPdf.trim();
        int dataSeparator = normalized.indexOf(',');
        if (normalized.regionMatches(true, 0, "data:", 0, 5) && dataSeparator >= 0) {
            normalized = normalized.substring(dataSeparator + 1);
        }
        normalized = normalized.replaceAll("\\s", "");

        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException standardEncodingFailure) {
            try {
                return Base64.getUrlDecoder().decode(normalized);
            } catch (IllegalArgumentException urlEncodingFailure) {
                throw new IllegalArgumentException("PDF Base64 içeriği geçersiz");
            }
        }
    }

    private Map<String, Object> success(Object id, Object result) {
        return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "result", result);
    }

    private Map<String, Object> error(Object id, int code, String message) {
        return Map.of("jsonrpc", "2.0", "id", id == null ? "" : id,
                "error", Map.of("code", code, "message", message));
    }
}