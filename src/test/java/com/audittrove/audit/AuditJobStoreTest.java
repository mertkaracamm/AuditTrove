package com.audittrove.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Yarıda kalma kuralı: sunucu yeniden başladığında iş "devam ediyor" görünüp asılı kalmamalı. */
class AuditJobStoreTest {

    private static final long NOW = 1_800_000_000_000L;

    @Test
    void aRunningJobThatStoppedBeingUpdatedCountsAsInterrupted() {
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW - AuditJobStore.STALE_MS - 1, NOW)).isTrue();
        assertThat(AuditJobStore.isStale(AuditJob.Status.PENDING, NOW - AuditJobStore.STALE_MS - 1, NOW)).isTrue();
    }

    @Test
    void aJobStillWorkingIsLeftAlone() {
        // Uzun belge birkaç dakika sürebilir; eşiğin altındaki iş beklemeye devam eder.
        assertThat(AuditJobStore.isStale(AuditJob.Status.PROCESSING, NOW - 90_000, NOW)).isFalse();
    }

    @Test
    void finishedJobsAreNeverMarkedInterrupted() {
        long old = NOW - 10 * AuditJobStore.STALE_MS;
        assertThat(AuditJobStore.isStale(AuditJob.Status.DONE, old, NOW)).isFalse();
        assertThat(AuditJobStore.isStale(AuditJob.Status.FAILED, old, NOW)).isFalse();
        assertThat(AuditJobStore.isStale(AuditJob.Status.INTERRUPTED, old, NOW)).isFalse();
    }

    @Test
    void statusChangeRefreshesTheClock() {
        long longAgo = System.currentTimeMillis() - 10 * AuditJobStore.STALE_MS;
        AuditJob job = AuditJob.restored("job-1", "device-1", "sozlesme.pdf", "tr", longAgo);
        assertThat(AuditJobStore.isStale(job.status(), job.updatedAt(), System.currentTimeMillis())).isTrue();
        job.setStatus(AuditJob.Status.PROCESSING);
        assertThat(AuditJobStore.isStale(job.status(), job.updatedAt(), System.currentTimeMillis())).isFalse();
    }
}
