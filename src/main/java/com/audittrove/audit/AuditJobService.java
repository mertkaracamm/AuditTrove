package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.security.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async inceleme calistiricisi. Isi ayri bir thread'de yurutur; bitince is durumunu gunceller.
 * Sayac YALNIZCA basarili incelemede artar (senkron akistaki davranisla ayni).
 */
@Service
public class AuditJobService {
    private static final Logger log = LoggerFactory.getLogger(AuditJobService.class);

    private final FinancialDocumentAuditService auditService;
    private final AuditJobStore store;
    private final QuotaService quotaService;

    public AuditJobService(FinancialDocumentAuditService auditService,
                           AuditJobStore store,
                           QuotaService quotaService) {
        this.auditService = auditService;
        this.store = store;
        this.quotaService = quotaService;
    }

    @Async("auditJobExecutor")
    public void process(AuditJob job, String filename, byte[] content,
                        String language, String documentType,
                        QuotaService.Decision decision) {
        job.setStatus(AuditJob.Status.PROCESSING);
        try {
            AuditResponse response = auditService.audit(filename, content, language, documentType);
            job.setResult(response);
            job.setStatus(AuditJob.Status.DONE);
            if (job.deviceId() != null && decision != null) {
                quotaService.recordUsage(job.deviceId(), decision);
            }
        } catch (Exception ex) {
            log.warn("Async inceleme basarisiz (job {}): {}", job.id(), ex.getMessage());
            job.setError(ex.getMessage());
            job.setStatus(AuditJob.Status.FAILED);
        }
    }
}