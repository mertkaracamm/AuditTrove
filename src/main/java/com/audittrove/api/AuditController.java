package com.audittrove.api;

import com.audittrove.audit.FinancialDocumentAuditService;
import com.audittrove.security.MobileAuthFilter;
import com.audittrove.security.QuotaService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final QuotaService quotaService;

    public AuditController(FinancialDocumentAuditService auditService, QuotaService quotaService) {
        this.auditService = auditService;
        this.quotaService = quotaService;
    }

    @PostMapping(value = "/audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Finansal PDF dokümanını denetler")
    public AuditResponse audit(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "language", required = false) String language,
                               HttpServletRequest request) {
        AuditResponse response = auditService.audit(file, language);
        // sayac sadece basarili incelemede artsin
        String deviceId = (String) request.getAttribute(MobileAuthFilter.DEVICE_ID_ATTR);
        Object decision = request.getAttribute(MobileAuthFilter.QUOTA_DECISION_ATTR);
        if (deviceId != null && decision instanceof QuotaService.Decision d) {
            quotaService.recordUsage(deviceId, d);
        }
        return response;
    }
}
