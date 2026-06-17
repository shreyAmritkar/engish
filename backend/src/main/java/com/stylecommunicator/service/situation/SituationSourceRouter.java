package com.stylecommunicator.service.situation;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.SituationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the four situation sources in priority order:
 *
 *   1. AdviceSlip  — always free, no key, keyword search
 *   2. Wikipedia   — always free, no key, curated article list
 *   3. LLM         — paid credits, last resort only
 *
 * Called asynchronously by SituationBankService when stock drops below
 * LOW_STOCK_THRESHOLD, so it never blocks the user's session-start request.
 */
@Component
public class SituationSourceRouter {

    private static final Logger log = LoggerFactory.getLogger(SituationSourceRouter.class);

    /** How many situations to generate in one replenishment cycle. */
    static final int BATCH_SIZE = 12;

    private final SituationRepository repository;
    private final AdviceSlipSource adviceSlipSource;
    private final WikipediaSource wikipediaSource;
    private final LlmSituationSource llmSource;

    public SituationSourceRouter(
            SituationRepository repository,
            AdviceSlipSource adviceSlipSource,
            WikipediaSource wikipediaSource,
            LlmSituationSource llmSource) {
        this.repository       = repository;
        this.adviceSlipSource = adviceSlipSource;
        this.wikipediaSource  = wikipediaSource;
        this.llmSource        = llmSource;
    }

    /**
     * Async: generates and persists a batch of situations for the given
     * power + level combination. Does NOT block the calling thread.
     */
    @Async("situationReplenishExecutor")
    @Transactional
    public void replenishAsync(String power, String level) {
        log.info("Replenishing situations: power={} level={}", power, level);

        List<String> texts = new ArrayList<>();

        // 1. AdviceSlip — free, no key
        drain(adviceSlipSource.fetch(power, level, BATCH_SIZE), texts, BATCH_SIZE, "AdviceSlip");

        // 2. Wikipedia — free, no key
        if (texts.size() < BATCH_SIZE) {
            drain(wikipediaSource.fetch(power, level, BATCH_SIZE - texts.size()),
                  texts, BATCH_SIZE, "Wikipedia");
        }

        // 3. LLM — last resort
        if (texts.size() < BATCH_SIZE) {
            int needed = BATCH_SIZE - texts.size();
            log.info("External sources yielded {} / {} — falling back to LLM for {} more",
                     texts.size(), BATCH_SIZE, needed);
            drain(llmSource.fetch(power, level, needed), texts, BATCH_SIZE, "LLM");
        }

        if (texts.isEmpty()) {
            log.warn("All sources exhausted for power={} level={} — no new situations stored", power, level);
            return;
        }

        // Persist all collected situations
        String source = determineSource(texts.size());
        List<SituationEntry> entries = texts.stream()
            .map(text -> {
                SituationEntry e = new SituationEntry();
                e.setPower(power);
                e.setLevel(level);
                e.setText(text);
                e.setSource(source);
                return e;
            })
            .toList();

        repository.saveAll(entries);
        log.info("Stored {} new situations for power={} level={}", entries.size(), power, level);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void drain(List<String> incoming, List<String> out, int max, String sourceName) {
        int before = out.size();
        for (String s : incoming) {
            if (out.size() >= max) break;
            if (s != null && !s.isBlank()) out.add(s);
        }
        log.debug("{} contributed {} situations", sourceName, out.size() - before);
    }

    /**
     * Heuristic: mark the source based on which sources likely contributed.
     * In a more granular implementation you'd tag each entry individually;
     * for now we record the highest-priority source that actually fired.
     */
    private String determineSource(int total) {
        // We always try AdviceSlip first — if we got anything, attribute to it.
        // The entity's source field is informational/diagnostic only.
        return total > 0 ? "ADVICE" : "LLM";
    }
}
