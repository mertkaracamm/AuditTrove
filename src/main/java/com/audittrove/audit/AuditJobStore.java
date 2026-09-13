package com.audittrove.audit;

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
    static final long STALE_MS = 5 * 60 * 1000L;          // bu kadar güncellenmeyen iş yarıda kalmış sayılır

    private final Map<String, AuditJob> jobs = new ConcurrentHashMap<>();
    private final AuditJobRecords records;

    public AuditJobStore(AuditJobRecords records) {
        this.records = records;
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
        if (job != null && isStale(job.status(), job.updatedAt(), System.currentTimeMillis())) {
            job.setStatus(AuditJob.Status.INTERRUPTED);
        }
        return job;
    }

    public void remove(String id) {
        jobs.remove(id);
    }

    /** Devam ediyor görünen ama uzun süredir kımıldamayan iş: sunucu yeniden başlamış demektir. */
    static boolean isStale(AuditJob.Status status, long updatedAt, long now) {
        if (status != AuditJob.Status.PENDING && status != AuditJob.Status.PROCESSING) return false;
        return now - updatedAt > STALE_MS;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void sweep() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
        records.deleteOlderThan(now - TTL_MS);
    }
}
