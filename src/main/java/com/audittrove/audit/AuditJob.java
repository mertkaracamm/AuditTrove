package com.audittrove.audit;

import com.audittrove.api.AuditResponse;

/**
 * Bellekte tutulan async inceleme isi. Sonuc diske/DB'ye kalici yazilmaz (gizlilik kurali);
 * yalnizca teslim/TTL suresince RAM'de durur. fileName + language bildirim metni icin tutulur.
 */
public class AuditJob {
    public enum Status { PENDING, PROCESSING, DONE, FAILED }

    private final String id;
    private final String deviceId;      // isi baslatan cihaz (sahiplik + push hedefi)
    private final String fileName;      // bildirim metni icin
    private final String language;      // bildirim dili icin
    private final long createdAt;
    private volatile Status status;
    private volatile AuditResponse result;
    private volatile String error;
    private volatile boolean cancelled = false;

    public AuditJob(String id, String deviceId, String fileName, String language) {
        this.id = id;
        this.deviceId = deviceId;
        this.fileName = fileName;
        this.language = language;
        this.createdAt = System.currentTimeMillis();
        this.status = Status.PENDING;
    }

    public String id() { return id; }
    public String deviceId() { return deviceId; }
    public String fileName() { return fileName; }
    public String language() { return language; }
    public long createdAt() { return createdAt; }
    public Status status() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public AuditResponse result() { return result; }
    public void setResult(AuditResponse result) { this.result = result; }
    public String error() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}