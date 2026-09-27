package com.stylecommunicator.aop;

import com.stylecommunicator.llm.LlmTier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Cross-cutting logging for every outbound LLM call.
 *
 * Wraps {@link com.stylecommunicator.llm.LlmClient#generateJson} so that
 * AnalysisRouter, IntentValidationService, and LlmSituationSource all get
 * consistent timing/outcome logs without each caller writing its own —
 * previously this was either duplicated per-caller or missing entirely.
 *
 * Deliberately logs prompt LENGTH, not prompt CONTENT — several callers
 * (scoring, rewrites) embed the user's own written response verbatim, and
 * that's not something that belongs in application logs.
 */
@Aspect
@Component
public class LlmCallLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LlmCallLoggingAspect.class);

    @Around("execution(* com.stylecommunicator.llm.LlmClient.generateJson(..)) && args(prompt, tier)")
    public Object logLlmCall(ProceedingJoinPoint pjp, String prompt, LlmTier tier) throws Throwable {
        int promptLength = prompt != null ? prompt.length() : 0;
        long start = System.currentTimeMillis();

        try {
            Object result = pjp.proceed();
            long durationMs = System.currentTimeMillis() - start;
            log.info("LLM call succeeded tier={} promptChars={} durationMs={}",
                    tier, promptLength, durationMs);
            return result;
        } catch (Throwable t) {
            long durationMs = System.currentTimeMillis() - start;
            log.warn("LLM call failed tier={} promptChars={} durationMs={} error={}: {}",
                    tier, promptLength, durationMs, t.getClass().getSimpleName(), t.getMessage());
            throw t;
        }
    }
}
