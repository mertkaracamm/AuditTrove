package com.audittrove.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * İşler bellekte tutulur; aynı anda veritabanına da yazılır (adres verilmişse). Bellek hızlı yol,
 * veritabanı sunucu yeniden başladığında ve birden fazla kopya çalıştığında ortak hafıza.
 *
 * Yarıda kalma: sunucu bir işi işlerken ölürse kayıt PROCESSING'de kalır. Açılışta toplu işaretleme
 * yapılmaz — nazik kapanma sırasında eski kopya hâlâ çalışıyor olabilir. Bunun yerine okuma anında
 * bakılır: uzun süredir güncellenmemiş bir iş yarıda kalmıştır.
 */
@Component
public class AuditJobStore {
    private static final long TTL_MS = 30 * 60 * 1000L;   // kayıt 30 dk sonra silinir
    static final long DEFAULT_STALE_MS = 3 * 60 * 1000L;  // bu kadar güncellenmeyen iş yarıda kalmış sayılır
    private static final long HEARTBEAT_MS = 60 * 1000L;  // süren işin kaydına "yaşıyorum" damgası

    private final Map<String, AuditJob> jobs = new ConcurrentHashMap<>();
    private final AuditJobRecords records;
    private final long staleMs;

    public AuditJobStore(AuditJobRecords records,
                         @Value("${audittrove.jobs.stale-seconds:180}") long staleSeconds) {
        this.records = records;
        this.staleMs = staleSeconds * 1000L;
    }

    public void put(AuditJob job) {
        jobs.put(job.id(), job);
        records.save(job);
    }

    /** Durum değiştikten sonra çağrılır; kaydı günceller. */
    public void update(AuditJob job) {
        if (job == null) return;
        jobs.put(job.id(), job);
        records.save(job);
    }

    public AuditJob get(String id) {
        AuditJob job = jobs.get(id);
        if (job == null) job = records.find(id);   // başka kopyanın ya da önceki sürümün işi
        if (job != null && isStale(job.status(), job.updatedAt(), System.currentTimeMillis(), staleMs)) {
            job.setStatus(AuditJob.Status.INTERRUPTED);
        }
        return job;
    }

    public void remove(String id) {
        jobs.remove(id);
    }

    /** Devam ediyor görünen ama uzun süredir kımıldamayan iş: sunucu yeniden başlamış demektir. */
    static boolean isStale(AuditJob.Status status, long updatedAt, long now, long staleMs) {
        if (status != AuditJob.Status.PENDING && status != AuditJob.Status.PROCESSING) return false;
        return now - updatedAt > staleMs;
    }

    /** Süren işler kaydına dokunur; uzun bir inceleme yanlışlıkla yarıda kalmış sayılmasın. */
    @Scheduled(fixedDelay = HEARTBEAT_MS)
    public void heartbeat() {
        if (!records.isEnabled()) return;
        for (AuditJob job : jobs.values()) {
            if (job.status() == AuditJob.Status.PENDING || job.status() == AuditJob.Status.PROCESSING) {
                records.save(job);
            }
        }
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void sweep() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
        records.deleteOlderThan(now - TTL_MS);
    }
}
