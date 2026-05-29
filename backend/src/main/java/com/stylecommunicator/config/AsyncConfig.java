package com.stylecommunicator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async support for background situation replenishment.
 *
 * A small dedicated thread pool so replenishment never competes with
 * request-handling threads.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    //  executor_name

    @Bean(name = "situationReplenishExecutor")
    public Executor situationReplenishExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("situation-replenish-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
