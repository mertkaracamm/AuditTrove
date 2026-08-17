package com.audittrove.api;

import com.audittrove.audit.AuditJob;

/**
 * Async is durum yaniti. result yalnizca DONE'da, error yalnizca FAILED'da doldurulur.
 */
public record AuditJobResponse(String id, String status, AuditResponse result, String error) {
    public static AuditJobResponse of(AuditJob job) {
        return new AuditJobResponse(
                job.id(),
                job.status().name(),
                job.status() == AuditJob.Status.DONE ? job.result() : null,
                job.status() == AuditJob.Status.FAILED ? job.error() : null
        );
    }
}