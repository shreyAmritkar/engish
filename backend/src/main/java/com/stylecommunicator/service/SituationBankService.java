package com.stylecommunicator.service;

import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.SituationRepository;
import com.stylecommunicator.service.situation.SituationSourceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Drop-in replacement for the original SituationBankService.
 *
 * Public API is identical: pickSituation(power, userId) still works.
 * Added:  pickSituation(power, level, userId) for level-aware callers.
 *
 * Internally:
 *  - reads from situation_bank table (DB)
 *  - triggers async replenishment via SituationSourceRouter when stock
 *    drops below LOW_STOCK_THRESHOLD
 *  - the router tries AdviceSlip → Wikipedia → LLM in that order
 */
@Service
public class SituationBankService {

    private static final Logger log = LoggerFactory.getLogger(SituationBankService.class);

    /** Trigger async replenishment when fewer than this many situations exist. */
    private static final int LOW_STOCK_THRESHOLD = 6;

    /** Fetch this many candidates from DB when picking (least-used first). */
    private static final int CANDIDATE_POOL = 20;

    // ── Hardcoded lists (unchanged — these are tiny and never scale) ──────

    private static final List<String> EMOTIONAL_CONTEXTS = List.of(
        "calm but firm", "under pressure", "frustrated but professional",
        "empathetic", "confident", "cautious", "enthusiastic", "skeptical"
    );

    private static final List<String> REQUIRED_WORDS = List.of(
        "however", "therefore", "specifically", "clearly", "respectfully",
        "directly", "together", "priority", "understand", "commit",
        "align", "decision", "timeline", "support", "outcome"
    );

    private final SituationRepository repository;
    private final SituationSourceRouter router;
    private final Random rng = new Random();

    public SituationBankService(SituationRepository repository, SituationSourceRouter router) {
        this.repository = repository;
        this.router     = router;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Backward-compatible overload used by SessionService.
     * Defaults level to B2 to match the original behaviour.
     */
    public String pickSituation(String power, UUID userId) {
        return pickSituation(power, "B2", userId);
    }

    /**
     * Level-aware pick. Used when the session carries an explicit level.
     */
    @Transactional
    public String pickSituation(String power, String level, UUID userId) {
        String p = normalise(power,  "EQUAL");
        String l = normalise(level,  "B2");

        // Trigger async top-up if running low (non-blocking)
        if (repository.countByPowerAndLevel(p, l) < LOW_STOCK_THRESHOLD) {
            log.info("Stock low for power={} level={} — triggering async replenishment", p, l);
            router.replenishAsync(p, l);
        }

        List<SituationEntry> candidates =
            repository.findCandidates(p, l, PageRequest.of(0, CANDIDATE_POOL));

        if (candidates.isEmpty()) {
            // replenishment is running but nothing in DB yet — use a safe hardcoded fallback
            log.warn("No situations in DB for power={} level={} — using inline fallback", p, l);
            return inlineFallback(p);
        }

        // Pick least-used, with a small random offset so it doesn't always repeat
        int pick = rng.nextInt(Math.min(candidates.size(), 5));
        SituationEntry chosen = candidates.get(pick);
        chosen.setUseCount(chosen.getUseCount() + 1);
        repository.save(chosen);
        return chosen.getText();
    }

    public String pickRequiredWord() {
        return REQUIRED_WORDS.get(rng.nextInt(REQUIRED_WORDS.size()));
    }

    public String pickEmotionalContext() {
        return EMOTIONAL_CONTEXTS.get(rng.nextInt(EMOTIONAL_CONTEXTS.size()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private String normalise(String value, String defaultVal) {
        return (value == null || value.isBlank()) ? defaultVal : value.trim().toUpperCase();
    }

    private String inlineFallback(String power) {
        return switch (power) {
            case "DOMINANT"   -> "A team member challenges your decision. Respond as the team lead.";
            case "SUBMISSIVE" -> "You need to ask your manager for more time on a task.";
            default           -> "You and a colleague disagree on the best way to solve a problem.";
        };
    }
}
