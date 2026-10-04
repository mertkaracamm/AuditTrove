package com.audittrove.audit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** İptal bayrağı: model çağrısını durdurur ve dağıtım havuzlarındaki alt işlere de geçer. */
class CancelScopeTest {

    @Test
    void checkThrowsOnlyWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancelScope.run(cancelled::get, () -> { CancelScope.check(); return null; });   // iptal yok, sorun yok
        cancelled.set(true);
        boolean thrown = false;
        try {
            CancelScope.run(cancelled::get, () -> { CancelScope.check(); return null; });
        } catch (AuditCancelledException e) {
            thrown = true;
        }
        assertThat(thrown).isTrue();
    }

    @Test
    void outsideAnyScopeNothingIsCancelled() {
        assertThat(CancelScope.cancelled()).isFalse();
        CancelScope.check();
    }

    @Test
    void theFlagReachesTasksRunningInAnotherPool() throws Exception {
        ExecutorService pool = CancelScope.inherit(Executors.newCachedThreadPool());
        AtomicBoolean cancelled = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        try {
            Boolean sawCancel = CancelScope.run(cancelled::get, () -> {
                try {
                    return pool.submit(() -> {
                        try {
                            CancelScope.check();          // alt iş de iptali görmeli
                            calls.incrementAndGet();
                            return false;
                        } catch (AuditCancelledException e) {
                            return true;
                        }
                    }).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            assertThat(sawCancel).isTrue();
            assertThat(calls.get()).isEqualTo(0);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void scopeIsClearedAfterTheAuditSoOtherJobsAreNotAffected() {
        CancelScope.run(() -> true, () -> null);
        assertThat(CancelScope.cancelled()).isFalse();
    }
}
