package com.audittrove.audit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isler yalnizca bellekte tutulur. Railway tek replica oldugu icin yeterli;
 * restart'ta bekleyen is kaybolur (kullanici yeniden dener). Olceklenince Redis'e tasinabilir.
 */
@Component
public class AuditJobStore {
    private static final long TTL_MS = 30 * 60 * 1000L; // 30 dk sonra sil
    private final Map<String, AuditJob> jobs = new ConcurrentHashMap<>();

    public void put(AuditJob job) { jobs.put(job.id(), job); }
    public AuditJob get(String id) { return jobs.get(id); }
    public void remove(String id) { jobs.remove(id); }

    @Scheduled(fixedDelay = 5 * 60 * 1000L) // her 5 dk suru
    public void sweep() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
    }
}