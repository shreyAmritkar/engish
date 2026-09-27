package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.SituationEntry;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.SituationRepository;
import com.stylecommunicator.service.situation.SituationSourceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

@Service
public class SituationBankService {

    private static final Logger log = LoggerFactory.getLogger(SituationBankService.class);
    private static final int LOW_STOCK_THRESHOLD = 40;
    private static final int CANDIDATE_POOL      = 20;
    private static final int RECENT_HISTORY_SIZE = 20;

    private static final List<String> EMOTIONAL_CONTEXTS = List.of(
        "calm but firm", "under pressure", "frustrated but professional",
        "empathetic", "confident", "cautious", "enthusiastic", "skeptical"
    );

    private static final List<String> REQUIRED_WORDS = List.of(
        "however", "therefore", "specifically", "clearly", "respectfully",
        "directly", "together", "priority", "understand", "commit",
        "align", "decision", "timeline", "support", "outcome"
    );

    // All known buckets — used only by the BACKGROUND pre-load sweep below.
    private static final List<String> ALL_POWERS   = List.of("DOMINANT", "EQUAL", "SUBMISSIVE");
    private static final List<String> ALL_LEVELS   = List.of("A1", "A2", "B1", "B2");
    private static final List<String> ALL_CONTEXTS = List.of("PROFESSIONAL", "CASUAL", "DRAMATIC");

    private final SituationRepository repository;
    private final PracticeSessionRepository practiceSessionRepository;
    private final SituationSourceRouter router;
    private final SituationLoadingService loadingService;
    private final Random rng = new Random();

    public SituationBankService(SituationRepository repository,
                                PracticeSessionRepository practiceSessionRepository,
                                SituationSourceRouter router,
                                SituationLoadingService loadingService) {
        this.repository = repository;
        this.practiceSessionRepository = practiceSessionRepository;
        this.router = router;
        this.loadingService = loadingService;
    }

    // ── Backward-compatible overload ──────────────────────────────────────

    public String pickSituation(String power, UUID userId) {
        return pickSituation(power, "B2", userId, 7, "MODERATE");
    }

    public String pickSituation(String power, String level, UUID userId) {
        return pickSituation(power, level, userId, 7, "MODERATE");
    }

    // ── Main pick — context-aware ─────────────────────────────────────────

    @Transactional
    public String pickSituation(String power, String level, UUID userId,
                                int formalityLevel, String emotionalRange) {
        String p = normalise(power, "EQUAL");
        String l = normalise(level, "B2");
        String context = resolveContext(formalityLevel, emotionalRange);

        // Trigger replenishment if this context pool is low
        if (repository.countByPowerAndLevelAndContext(p, l, context) < LOW_STOCK_THRESHOLD) {
            log.info("Stock low for power={} level={} context={} — triggering async replenishment", p, l, context);
            router.replenishAsync(p, l, context);
        }

        // Try context-specific pool first
        List<SituationEntry> candidates =
            repository.findCandidates(p, l, context, PageRequest.of(0, CANDIDATE_POOL));

        // Fallback: any context for this power+level if context pool is empty
        if (candidates.isEmpty()) {
            log.info("No {} situations for power={} level={} — falling back to any context", context, p, l);
            candidates = repository.findCandidatesAnyContext(p, l, PageRequest.of(0, CANDIDATE_POOL));
        }

        if (candidates.isEmpty()) {
            log.warn("No situations at all for power={} level={} — using inline fallback", p, l);
            return inlineFallback(p, context);
        }

        Set<String> recentlySeen = recentlySeenTexts(userId);
        List<SituationEntry> unseen = candidates.stream()
            .filter(c -> !recentlySeen.contains(c.getText()))
            .toList();
        List<SituationEntry> pool = unseen.isEmpty() ? candidates : unseen;

        int window = Math.min(pool.size(), CANDIDATE_POOL);
        SituationEntry chosen = pool.get(rng.nextInt(window));
        chosen.setUseCount(chosen.getUseCount() + 1);
        repository.save(chosen);
        return chosen.getText();
    }

    // ── BACKGROUND loading strategy ─────────────────────────────────────────
    //
    // ON_DEMAND (default): the reactive check inside pickSituation() above is
    // the only replenishment path — a bucket only gets topped up once a real
    // request actually finds it low.
    //
    // BACKGROUND: this sweep proactively tops up every bucket ahead of time,
    // so a real request is less likely to ever hit the reactive low-stock
    // path. It's a no-op unless an admin has switched the strategy via
    // SituationLoadingService — see AdminController's
    // /api/admin/settings/situation-loading endpoint.
    //
    // Runs every 15 minutes; that's slow enough that a bucket topped up by
    // one sweep won't still be low by the next one, and it stays well under
    // situationReplenishExecutor's small queue (core=1, max=2, capacity=10 —
    // see AsyncConfig) even when every one of the 36 buckets needs a refill
    // on the very first run.
    @Scheduled(fixedDelay = 15 * 60 * 1000)
    public void backgroundReplenishSweep() {
        if (!loadingService.shouldPreLoadInBackground()) {
            return;
        }

        int triggered = 0;
        for (String power : ALL_POWERS) {
            for (String level : ALL_LEVELS) {
                for (String context : ALL_CONTEXTS) {
                    if (repository.countByPowerAndLevelAndContext(power, level, context) < LOW_STOCK_THRESHOLD) {
                        try {
                            router.replenishAsync(power, level, context);
                            triggered++;
                        } catch (RejectedExecutionException e) {
                            // Executor's queue is full — the reactive path in
                            // pickSituation() will pick this bucket back up
                            // next time a real request needs it.
                            log.warn("Replenish executor busy, skipping power={} level={} context={} this sweep",
                                    power, level, context);
                        }
                    }
                }
            }
        }
        if (triggered > 0) {
            log.info("Background sweep: triggered replenishment for {} low-stock buckets", triggered);
        }
    }

    public String pickRequiredWord() {
        return REQUIRED_WORDS.get(rng.nextInt(REQUIRED_WORDS.size()));
    }

    public String pickEmotionalContext() {
        return EMOTIONAL_CONTEXTS.get(rng.nextInt(EMOTIONAL_CONTEXTS.size()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Set<String> recentlySeenTexts(UUID userId) {
        List<PracticeSession> recent =
            practiceSessionRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
        Set<String> texts = new HashSet<>();
        for (PracticeSession s : recent) {
            if (s.getSituation() != null) texts.add(s.getSituation());
        }
        return texts;
    }

    private String normalise(String value, String defaultVal) {
        return (value == null || value.isBlank()) ? defaultVal : value.trim().toUpperCase();
    }

    private String resolveContext(int formalityLevel, String emotionalRange) {
        if ("EXPRESSIVE".equalsIgnoreCase(emotionalRange)) return "DRAMATIC";
        if (formalityLevel <= 4) return "CASUAL";
        return "PROFESSIONAL";
    }

    private String inlineFallback(String power, String context) {
        if ("CASUAL".equals(context)) {
            return switch (power) {
                case "DOMINANT"   -> "Your friend is about to make a bad decision. Talk them out of it.";
                case "SUBMISSIVE" -> "You need to ask a favour from someone who might say no.";
                default           -> "You and a close friend disagree. Neither of you is backing down.";
            };
        }
        if ("DRAMATIC".equals(context)) {
            return switch (power) {
                case "DOMINANT"   -> "Everything is at risk. You have one chance to turn it around. Speak.";
                case "SUBMISSIVE" -> "You've stayed quiet for too long. It's now or never.";
                default           -> "Your closest ally is making a terrible mistake. Stop them.";
            };
        }
        return switch (power) {
            case "DOMINANT"   -> "A team member challenges your decision. Respond as the team lead.";
            case "SUBMISSIVE" -> "You need to ask your manager for more time on a task.";
            default           -> "You and a colleague disagree on the best way to solve a problem.";
        };
    }
}
