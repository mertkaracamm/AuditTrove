package com.audittrove.audit;

import com.audittrove.api.AuditResponse;

/**
 * Async inceleme işi. Bellekte tutulur; sunucu yeniden başlarsa telefon işi kaybetmesin diye durumu ve
 * raporu kısa süreli olarak veritabanına da yazılır (belge ve sayfa metni asla yazılmaz).
 * fileName + language bildirim metni için tutulur.
 *
 * INTERRUPTED: iş yarıda kaldı (sunucu yeniden başladı). Kullanıcının hakkı yanmaz, yeniden başlatabilir.
 */
public class AuditJob {
    public enum Status { PENDING, PROCESSING, DONE, FAILED, INTERRUPTED }

    private final String id;
    private final String deviceId;      // isi baslatan cihaz (sahiplik + push hedefi)
    private final String fileName;      // bildirim metni icin
    private final String language;      // bildirim dili icin
    private final long createdAt;
    private volatile long updatedAt;
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
        this.updatedAt = this.createdAt;
        this.status = Status.PENDING;
    }

    private AuditJob(String id, String deviceId, String fileName, String language, long createdAt) {
        this.id = id;
        this.deviceId = deviceId;
        this.fileName = fileName;
        this.language = language;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.status = Status.PENDING;
    }

    /** Veritabanı kaydından geri kurulan iş; bu kopyada işlem yürümez, yalnız durum taşınır. */
    public static AuditJob restored(String id, String deviceId, String fileName, String language, long createdAt) {
        return new AuditJob(id, deviceId, fileName, language, createdAt);
    }

    public String id() { return id; }
    public String deviceId() { return deviceId; }
    public String fileName() { return fileName; }
    public String language() { return language; }
    public long createdAt() { return createdAt; }
    public long updatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public Status status() { return status; }
    public void setStatus(Status status) { this.status = status; this.updatedAt = System.currentTimeMillis(); }
    public AuditResponse result() { return result; }
    public void setResult(AuditResponse result) { this.result = result; }
    public String error() { return error; }
    public void setError(String error) { this.error = error; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}