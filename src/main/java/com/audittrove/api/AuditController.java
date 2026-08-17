package com.audittrove.api;

import com.audittrove.audit.AuditJob;
import com.audittrove.audit.AuditJobService;
import com.audittrove.audit.AuditJobStore;
import com.audittrove.audit.FinancialDocumentAuditService;
import com.audittrove.security.MobileAuthFilter;
import com.audittrove.security.QuotaService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Financial Document Audit")
public class AuditController {
    private final FinancialDocumentAuditService auditService;
    private final QuotaService quotaService;
    private final AuditJobService jobService;
    private final AuditJobStore jobStore;

    public AuditController(FinancialDocumentAuditService auditService,
                           QuotaService quotaService,
                           AuditJobService jobService,
                           AuditJobStore jobStore) {
        this.auditService = auditService;
        this.quotaService = quotaService;
        this.jobService = jobService;
        this.jobStore = jobStore;
    }

    // --- Senkron (kucuk belgeler + geriye uyumluluk; eski istemciler bunu kullanir) ---
    @PostMapping(value = "/audit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Finansal PDF dokümanını denetler (senkron)")
    public AuditResponse audit(@RequestParam("file") MultipartFile file,
                               @RequestParam(value = "language", required = false) String language,
                               @RequestParam(value = "documentType", required = false) String documentType,
                               HttpServletRequest request) {
        AuditResponse response = auditService.audit(file, language, documentType);
        String deviceId = (String) request.getAttribute(MobileAuthFilter.DEVICE_ID_ATTR);
        Object decision = request.getAttribute(MobileAuthFilter.QUOTA_DECISION_ATTR);
        if (deviceId != null && decision instanceof QuotaService.Decision d) {
            quotaService.recordUsage(deviceId, d);
        }
        return response;
    }

    // --- Async (buyuk/uzun belgeler): is olustur, hemen jobId don, arka planda isle ---
    @PostMapping(value = "/audit/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uzun belgeler için async inceleme başlatır, jobId döner")
    public ResponseEntity<AuditJobResponse> auditAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "documentType", required = false) String documentType,
            HttpServletRequest request) throws IOException {
        String deviceId = (String) request.getAttribute(MobileAuthFilter.DEVICE_ID_ATTR);
        Object decisionObj = request.getAttribute(MobileAuthFilter.QUOTA_DECISION_ATTR);
        QuotaService.Decision decision =
                decisionObj instanceof QuotaService.Decision d ? d : null;

        String jobId = UUID.randomUUID().toString();
        AuditJob job = new AuditJob(jobId, deviceId, file.getOriginalFilename(), language);
        jobStore.put(job);

        // Multipart istek cagri bitince kapanir; byte'lari SIMDI al, sonra async'e devret
        jobService.process(job, file.getOriginalFilename(), file.getBytes(),
                language, documentType, decision);

        return ResponseEntity.accepted().body(AuditJobResponse.of(job)); // 202 PENDING
    }

    // --- Async durum sorgusu (mobil polling). Sadece token dogrulanir; kota/rate-limit YOK ---
    @GetMapping("/audit/jobs/{id}")
    @Operation(summary = "Async inceleme durumunu/sonucunu sorgular")
    public ResponseEntity<AuditJobResponse> jobStatus(@PathVariable String id,
                                                      HttpServletRequest request) {
        AuditJob job = jobStore.get(id);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        // Sahiplik: yalnizca isi baslatan cihaz sorgulayabilir
        String deviceId = (String) request.getAttribute(MobileAuthFilter.DEVICE_ID_ATTR);
        if (job.deviceId() != null && !job.deviceId().equals(deviceId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(AuditJobResponse.of(job));
    }
}