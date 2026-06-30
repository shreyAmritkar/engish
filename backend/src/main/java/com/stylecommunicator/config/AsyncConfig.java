package com.stylecommunicator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables @Async support for background situation replenishment,
 * and @Scheduled support for cron jobs (rival leaderboard, etc).
 *
 * Separate thread pools per job so unrelated background work
 * never competes for the same threads.
 */
@Configuration
@EnableAsync
@EnableScheduling
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

    @Bean(name = "rivalLeaderboardExecutor")
    public Executor rivalLeaderboardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500); // one task per active user
        executor.setThreadNamePrefix("rival-leaderboard-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}

