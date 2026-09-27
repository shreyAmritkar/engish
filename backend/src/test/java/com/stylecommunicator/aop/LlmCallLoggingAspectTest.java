package com.stylecommunicator.aop;

import com.stylecommunicator.llm.LlmTier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmCallLoggingAspectTest {

    @Mock private ProceedingJoinPoint pjp;

    private LlmCallLoggingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new LlmCallLoggingAspect();
    }

    @Test
    void logLlmCall_success_returnsUnderlyingResultUnchanged() throws Throwable {
        Map<String, Object> expected = Map.of("score", 8);
        when(pjp.proceed()).thenReturn(expected);

        Object result = aspect.logLlmCall(pjp, "some prompt", LlmTier.FAST);

        assertSame(expected, result);
        verify(pjp, times(1)).proceed();
    }

    @Test
    void logLlmCall_failure_rethrowsOriginalException() throws Throwable {
        RuntimeException boom = new RuntimeException("all models exhausted");
        when(pjp.proceed()).thenThrow(boom);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.logLlmCall(pjp, "some prompt", LlmTier.QUALITY));

        assertSame(boom, thrown);
    }

    @Test
    void logLlmCall_nullPrompt_doesNotThrowOnLengthCalculation() throws Throwable {
        when(pjp.proceed()).thenReturn(Map.of());

        assertDoesNotThrow(() -> aspect.logLlmCall(pjp, null, LlmTier.FAST));
    }
}
