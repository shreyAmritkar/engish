package com.stylecommunicator.service.situation;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.SituationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class SituationSourceRouter {

    private static final Logger log = LoggerFactory.getLogger(SituationSourceRouter.class);
    static final int BATCH_SIZE = 12;

    private final SituationRepository repository;
    private final LlmSituationSource llmSource;

    public SituationSourceRouter(SituationRepository repository, LlmSituationSource llmSource) {
        this.repository = repository;
        this.llmSource  = llmSource;
    }

    /** Backward-compatible — defaults to PROFESSIONAL context. */
    @Async("situationReplenishExecutor")
    @Transactional
    public void replenishAsync(String power, String level) {
        replenishAsync(power, level, "PROFESSIONAL");
    }

    @Async("situationReplenishExecutor")
    @Transactional
    public void replenishAsync(String power, String level, String context) {
        log.info("Replenishing: power={} level={} context={}", power, level, context);

        List<String> texts = llmSource.fetch(power, level, context, BATCH_SIZE);

        if (texts.isEmpty()) {
            log.warn("LLM returned no situations for power={} level={} context={}", power, level, context);
            return;
        }

        List<SituationEntry> entries = texts.stream()
            .map(text -> {
                SituationEntry e = new SituationEntry();
                e.setPower(power);
                e.setLevel(level);
                e.setContext(context);
                e.setText(text);
                e.setSource("LLM");
                return e;
            })
            .toList();

        repository.saveAll(entries);
        log.info("Stored {} situations for power={} level={} context={}", entries.size(), power, level, context);
    }
}
