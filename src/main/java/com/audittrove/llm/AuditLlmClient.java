package com.audittrove.llm;

import com.audittrove.api.AuditResponse;
import com.audittrove.rag.RegulationChunk;

import java.util.List;

public interface AuditLlmClient {
    AuditResponse audit(String documentText, List<RegulationChunk> context, String language, String documentType,
                        boolean truncated, int totalPages, int includedPages);
}