package com.audittrove.audit;

import com.audittrove.api.AuditResponse;
import com.audittrove.security.ExpoPushClient;
import com.audittrove.security.PushTokenStore;
import com.audittrove.security.QuotaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async inceleme calistiricisi. Isi ayri bir thread'de yurutur; bitince durumu gunceller,
 * kotayi (yalnizca basarili incelemede) artirir ve cihaza push bildirimi gonderir.
 */
@Service
public class AuditJobService {
    private static final Logger log = LoggerFactory.getLogger(AuditJobService.class);

    private final FinancialDocumentAuditService auditService;
    private final AuditJobStore store;
    private final QuotaService quotaService;
    private final PushTokenStore pushTokenStore;
    private final ExpoPushClient expoPushClient;

    public AuditJobService(FinancialDocumentAuditService auditService,
                           AuditJobStore store,
                           QuotaService quotaService,
                           PushTokenStore pushTokenStore,
                           ExpoPushClient expoPushClient) {
        this.auditService = auditService;
        this.store = store;
        this.quotaService = quotaService;
        this.pushTokenStore = pushTokenStore;
        this.expoPushClient = expoPushClient;
    }

    @Async("auditJobExecutor")
    public void process(AuditJob job, String filename, byte[] content,
                        String language, String documentType,
                        QuotaService.Decision decision) {
        job.setStatus(AuditJob.Status.PROCESSING);
        try {
            AuditResponse response = auditService.audit(filename, content, language, documentType);
            // Kullanici bu arada iptal ettiyse: sonuc/kota/push YOK.
            if (job.isCancelled()) {
                job.setStatus(AuditJob.Status.FAILED);
                job.setError("İnceleme iptal edildi");
                return;
            }
            job.setResult(response);
            job.setStatus(AuditJob.Status.DONE);
            if (job.deviceId() != null && decision != null) {
                quotaService.recordUsage(job.deviceId(), decision);
            }
            sendReadyPush(job);
        } catch (Exception ex) {
            log.warn("Async inceleme basarisiz (job {}): {}", job.id(), ex.getMessage());
            job.setError(ex.getMessage());
            job.setStatus(AuditJob.Status.FAILED);
        }
    }

    // Is bitince cihaza "rapor hazir" push'u. Uygulama kapali/arka planda olsa da gelir.
    private void sendReadyPush(AuditJob job) {
        try {
            String token = pushTokenStore.get(job.deviceId());
            if (token == null) {
                return;
            }
            boolean turkish = !"en".equalsIgnoreCase(job.language());
            // Dosya adi push metninde KULLANILMIYOR: multipart'tan gelen ad Turkce karakterlerde
            // bozuk/percent-encoded olabiliyor (or. "%C3%87"). Genel, encoding-guvenli mesaj veriyoruz.
            String title = turkish ? "İncelemeniz hazır" : "Your review is ready";
            String body = turkish ? "Raporunuz görüntülenmeye hazır." : "Your report is ready to view.";
            expoPushClient.send(token, title, body);
        } catch (Exception e) {
            log.warn("Push bildirimi gonderilemedi (job {}): {}", job.id(), e.getMessage());
        }
    }
}