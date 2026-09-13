package com.audittrove.api;

import com.audittrove.audit.AuditJob;

/**
 * Async iş durum yanıtı. result yalnız DONE'da; error FAILED ve INTERRUPTED'da doldurulur.
 * INTERRUPTED: sunucu yeniden başladığı için iş yarıda kaldı; arayüz kendi metnini gösterir.
 */
public record AuditJobResponse(String id, String status, AuditResponse result, String error) {
    public static AuditJobResponse of(AuditJob job) {
        return new AuditJobResponse(
                job.id(),
                job.status().name(),
                job.status() == AuditJob.Status.DONE ? job.result() : null,
                job.status() == AuditJob.Status.FAILED || job.status() == AuditJob.Status.INTERRUPTED ? job.error() : null
        );
    }
}