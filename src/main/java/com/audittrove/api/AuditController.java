package com.audittrove.api;

import com.audittrove.audit.FinancialDocumentAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Financial Document Audit")
public class AuditController {
    private final FinancialDocumentAuditService auditService;

    public AuditController(FinancialDocumentAuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping(value = "/audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Finansal PDF dokümanını denetler")
    public AuditResponse audit(@RequestParam("file") MultipartFile file) {
        return auditService.audit(file);
    }
}
