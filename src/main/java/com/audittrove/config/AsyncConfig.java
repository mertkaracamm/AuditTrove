package com.audittrove.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async inceleme + TTL surume icin. Es zamanli is sayisi bilincli olarak dusuk tutulur:
 * her is ~10-18 OpenAI cagrisi yaptigindan, rate limit'i korumak icin tavan 3.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    @Bean(name = "auditJobExecutor")
    public Executor auditJobExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("audit-job-");
        executor.initialize();
        return executor;
    }
}