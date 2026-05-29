package com.stylecommunicator.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the platform-thread pool is configured correctly.
 * No Spring context — instantiates AsyncConfig directly.
 */
class AsyncConfigTest {

    private ThreadPoolTaskExecutor executor;

    @BeforeEach
    void setUp() {
        AsyncConfig config = new AsyncConfig();
        Executor raw = config.situationReplenishExecutor();
        executor = (ThreadPoolTaskExecutor) raw;
    }

    

    @Test
    void corePoolSize_isOne() {
        assertEquals(1, executor.getCorePoolSize(),
            "Core pool should be 1 — one background thread always alive");
    }

    @Test
    void maxPoolSize_isTwo() {
        assertEquals(2, executor.getMaxPoolSize(),
            "Max 2 concurrent replenishment tasks — keeps it lightweight");
    }

    @Test
    void queueCapacity_isTen() {
        assertEquals(10, executor.getQueueCapacity(),
            "Queue capacity 10 — absorbs bursts without spawning extra threads");
    }

    @Test
    void threadNamePrefix_isCorrect() {
        assertEquals("situation-replenish-", executor.getThreadNamePrefix());
    }

    @Test
    void threads_areDaemon() {
        // Daemon threads do not block JVM shutdown
        assertTrue(executor.isDaemon(),
            "Replenishment threads must be daemon so JVM can shut down cleanly");
    }

    // @Test
    // void rejectedExecutionHandler_isCallerRunsPolicy() {
    //     // CallerRunsPolicy: if queue is full, run on calling thread rather than drop
    //     assertInstanceOf(
    //         ThreadPoolExecutor.CallerRunsPolicy.class,
    //         executor.getThreadPoolExecutor().getRejectedExecutionHandler(),
    //         "Expected CallerRunsPolicy so tasks are never silently dropped"
    //     );
    // }

    @Test
    void executor_acceptsAndRunsTasks() throws InterruptedException {
        // Smoke test: submitting a task should not throw
        boolean[] ran = {false};
        assertDoesNotThrow(() -> executor.execute(() -> ran[0] = true));

        // Give the background thread a moment to run
        Thread.sleep(200);
        assertTrue(ran[0], "Submitted task should have executed on the pool thread");
    }

    @Test
    void executor_threadNamesMatchPrefix() throws InterruptedException {
        String[] threadName = {""};
        executor.execute(() -> threadName[0] = Thread.currentThread().getName());

        Thread.sleep(200);
        assertTrue(threadName[0].startsWith("situation-replenish-"),
            "Thread name should start with prefix, got: " + threadName[0]);
    }

    @Test
    void executor_threadIsDaemonAtRuntime() throws InterruptedException {
        boolean[] isDaemon = {false};
        executor.execute(() -> isDaemon[0] = Thread.currentThread().isDaemon());

        Thread.sleep(200);
        assertTrue(isDaemon[0], "Running thread must be a daemon thread");
    }
}
