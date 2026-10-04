package com.audittrove.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Yarıda kalma kuralı: sunucu yeniden başladığında iş "devam ediyor" görünüp asılı kalmamalı. */
class AuditJobStoreTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void aRunningJobThatStoppedBeingUpdatedCountsAsInterrupted() {
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW - AuditJobStore.DEFAULT_STALE_MS - 1, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isTrue();
        assertThat(AuditJobStore.isStale(AuditJob.Status.PENDING, NOW - AuditJobStore.DEFAULT_STALE_MS - 1, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isTrue();
    }

    @Test
    void aJobStillWorkingIsLeftAlone() {
        // Uzun belge birkaç dakika sürebilir; eşiğin altındaki iş beklemeye devam eder.
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW - 90_000, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
    }

    @Test
    void aJobStampedAtShutdownIsInterruptedQuickly() {
        // Kapanma damgası var ve iş kısa sürede bitmedi: o kopyayla birlikte gitti.
        long stamp = NOW - AuditJobStore.DRAIN_GRACE_MS - 1;
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW, stamp, NOW, AuditJobStore.DEFAULT_STALE_MS)).isTrue();
        // Damga yeni: iş nazik kapanma penceresinde hâlâ bitiyor olabilir, beklenir.
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW, NOW - 5_000, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
        // Damgalı ama bitmiş iş: sonuç geçerli.
        assertThat(AuditJobStore.isStale(AuditJob.Status.DONE, NOW, stamp, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
    }

    @Test
    void finishedJobsAreNeverMarkedInterrupted() {
        long old = NOW - 10 * AuditJobStore.DEFAULT_STALE_MS;
        assertThat(AuditJobStore.isStale(AuditJob.Status.DONE, old, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
        assertThat(AuditJobStore.isStale(AuditJob.Status.FAILED, old, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
        assertThat(AuditJobStore.isStale(AuditJob.Status.INTERRUPTED, old, 0L, NOW, AuditJobStore.DEFAULT_STALE_MS)).isFalse();
    }

    @Test
    void statusChangeRefreshesTheClock() {
        long longAgo = System.currentTimeMillis() - 10 * AuditJobStore.DEFAULT_STALE_MS;
        AuditJob job = AuditJob.restored("job-1", "device-1", "sozlesme.pdf", "tr", longAgo);
        assertThat(AuditJobStore.isStale(job.status(), job.updatedAt(), 0L, System.currentTimeMillis(), AuditJobStore.DEFAULT_STALE_MS)).isTrue();
        job.setStatus(AuditJob.Status.PROCESSING);
        assertThat(AuditJobStore.isStale(job.status(), job.updatedAt(), 0L, System.currentTimeMillis(), AuditJobStore.DEFAULT_STALE_MS)).isFalse();
    }
}
