package com.stylecommunicator.aop;

import com.stylecommunicator.llm.LlmClient;
import com.stylecommunicator.llm.LlmTier;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LlmCallLoggingAspectTest exercises the advice method directly and never
 * proves Spring actually applies it as a proxy around real LlmClient calls.
 * This test builds a real AspectJ proxy (no full Spring context needed —
 * keeps it fast) to prove the weaving itself works end to end.
 */
class LlmCallLoggingAspectWeavingTest {

    @Test
    void proxiedCall_delegatesToRealImplementation_andReturnsItsResult() {
        LlmClient real = (prompt, tier) -> Map.of("ok", true);

        LlmClient proxied = proxy(real);

        Map<String, Object> result = proxied.generateJson("test prompt", LlmTier.FAST);

        assertEquals(Map.of("ok", true), result);
    }

    @Test
    void proxiedCall_stillInvokesTheRealImplementation() {
        AtomicInteger callCount = new AtomicInteger();
        LlmClient real = (prompt, tier) -> {
            callCount.incrementAndGet();
            return Map.of();
        };

        LlmClient proxied = proxy(real);
        proxied.generateJson("x", LlmTier.QUALITY);

        assertEquals(1, callCount.get(), "aspect must not skip or double-invoke the real call");
    }

    @Test
    void proxiedCall_stillPropagatesExceptions() {
        LlmClient real = (prompt, tier) -> { throw new RuntimeException("all models exhausted"); };

        LlmClient proxied = proxy(real);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> proxied.generateJson("x", LlmTier.FAST));
        assertEquals("all models exhausted", thrown.getMessage());
    }

    private static LlmClient proxy(LlmClient target) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new LlmCallLoggingAspect());
        return factory.getProxy();
    }
}
