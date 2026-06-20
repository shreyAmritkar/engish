package com.stylecommunicator.service.situation;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.SituationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Generates a batch of situations via the LLM source and persists them.
 *
 * AdviceSlip and Wikipedia were dropped: their raw text had no inherent
 * power dynamic or difficulty tier, and template-substituting a phrase
 * from either source into a sentence template produced scenarios that
 * were frequently awkward or barely related to a real workplace situation.
 * The LLM source alone is the single source of truth for situation text.
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
    private final LlmSituationSource llmSource;

    public SituationSourceRouter(SituationRepository repository, LlmSituationSource llmSource) {
        this.repository = repository;
        this.llmSource  = llmSource;
    }

    /**
     * Async: generates and persists a batch of situations for the given
     * power + level combination. Does NOT block the calling thread.
     */
    @Async("situationReplenishExecutor")
    @Transactional
    public void replenishAsync(String power, String level) {
        log.info("Replenishing situations: power={} level={}", power, level);

        List<String> texts = llmSource.fetch(power, level, BATCH_SIZE);

        if (texts.isEmpty()) {
            log.warn("LLM source returned no situations for power={} level={} — nothing stored", power, level);
            return;
        }

        List<SituationEntry> entries = texts.stream()
            .map(text -> {
                SituationEntry e = new SituationEntry();
                e.setPower(power);
                e.setLevel(level);
                e.setText(text);
                e.setSource("LLM");
                return e;
            })
            .toList();

        repository.saveAll(entries);
        log.info("Stored {} new situations for power={} level={}", entries.size(), power, level);
    }
}
