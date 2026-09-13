package com.audittrove.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async inceleme + TTL sürümü için. Aynı anda kaç inceleme koşacağı ortamdan gelir; LLM tarafındaki
 * kota koruması ayrı bir sınırla (OPENAI_MAX_CONCURRENT) yapılır, burada değil.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    // Çekirdek ve tavan aynı: ThreadPoolTaskExecutor kuyruk dolmadan çekirdeğin üstüne çıkmaz,
    // tavan ayrı verilirse fiilen çekirdek kadar iş koşar. Kuyruk geniş: yığılma reddedilmez, bekler.
    @Bean(name = "auditJobExecutor")
    public Executor auditJobExecutor(@Value("${AUDIT_JOB_CONCURRENCY:8}") int concurrency,
                                     @Value("${AUDIT_SHUTDOWN_WAIT_SECONDS:120}") int shutdownWaitSeconds) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("audit-job-");
        // Kapanma sırasında süren inceleme yarıda kesilmez: yeni iş alınmaz, eldekinin bitmesi beklenir.
        // Railway yeni sürümü ayağa kaldırırken eski kopya bu sürede işini bitirip sonucu kaydeder.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(shutdownWaitSeconds);
        executor.initialize();
        return executor;
    }
}