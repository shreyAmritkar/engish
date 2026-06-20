package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.StyleProfileRepository;
import com.stylecommunicator.repository.UserProgressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class SessionService {

    private final PracticeSessionRepository practiceSessionRepository;
    private final StyleProfileRepository styleProfileRepository;
    private final SituationBankService situationBankService;
    private final AnalysisRouter analysisRouter;
    private final ProgressTracker progressTracker;
    private final IntentValidationService intentValidationService;
    private final UserProgressRepository userProgressRepository;

    public SessionService(
            PracticeSessionRepository practiceSessionRepository,
            StyleProfileRepository styleProfileRepository,
            SituationBankService situationBankService,
            AnalysisRouter analysisRouter,
            ProgressTracker progressTracker,
            IntentValidationService intentValidationService,
            UserProgressRepository userProgressRepository) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.styleProfileRepository    = styleProfileRepository;
        this.situationBankService      = situationBankService;
        this.analysisRouter            = analysisRouter;
        this.progressTracker           = progressTracker;
        this.intentValidationService   = intentValidationService;
        this.userProgressRepository    = userProgressRepository;
    }

    @Transactional
    public PracticeSession startSession(UUID userId, UUID styleProfileId) {
        StyleProfile style = styleProfileRepository.findById(styleProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));

        String cefrLevel = resolveLevel(userId, style.getVocabularyTier());
        int formality = style.getFormalityLevel() != null ? style.getFormalityLevel() : 7;
        String emotionalRange = style.getEmotionalRange() != null ? style.getEmotionalRange() : "MODERATE";

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setStyleProfileId(styleProfileId);
        session.setSituation(situationBankService.pickSituation(
                style.getPowerDynamic(), cefrLevel, userId, formality, emotionalRange));
        session.setRequiredWord(situationBankService.pickRequiredWord());
        session.setEmotionalContext(situationBankService.pickEmotionalContext());
        return practiceSessionRepository.save(session);
    }

    /**
     * Resolves the CEFR difficulty level for a new session.
     *
     * Logic:
     *  - The style's vocabularyTier gives a BASE level (the difficulty the
     *    user signed up for by picking that style).
     *  - The user's currentLevel (1–5, tracked by ProgressTracker) acts as
     *    an OFFSET: if they're consistently performing well (+2 levels above
     *    base) we nudge them up one CEFR step; if they're struggling (-2 below
     *    base) we nudge them down one step.
     *  - New users (no progress record yet) get exactly the style's base level.
     *
     * CEFR ladder: A1 → A2 → B1 → B2
     * currentLevel ladder: 1 (weakest) → 5 (strongest)
     * Base currentLevel for each style tier:
     *   SIMPLE=1, INTERMEDIATE=2-3, ADVANCED/TECHNICAL=4-5
     */
    /**
     * Resolves CEFR difficulty purely from the user's performance level.
     * Style profile determines WHAT they practice (power dynamic, topic domain),
     * not HOW HARD — so every user starts at A1 and earns harder situations
     * through consistent good scores, regardless of which style they picked.
     *
     * currentLevel → CEFR:
     *   1 (new / struggling) → A1
     *   2                    → A2
     *   3                    → B1
     *   4–5 (excelling)      → B2
     */
    private String resolveLevel(UUID userId, String vocabularyTier) {
        UserProgress progress = userProgressRepository.findById(userId).orElse(null);
        if (progress == null) return "A1"; // brand new user always starts easy

        return switch (progress.getCurrentLevel()) {
            case 1  -> "A1";
            case 2  -> "A2";
            case 3  -> "B1";
            default -> "B2"; // level 4 and 5
        };
    }

    @Transactional
    public PracticeSession submitResponse(UUID sessionId, UUID userId, String userResponse) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to user");
        }
        StyleProfile style = styleProfileRepository.findById(session.getStyleProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));

        IntentValidationService.ValidationResult validation =
                intentValidationService.validate(userResponse, session.getSituation());
        if (!validation.valid()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, validation.reason());
        }

        session.setUserResponse(userResponse);
        Map<String, Object> feedback = analysisRouter.analyze(session, style, userResponse);
        session.setFeedback(feedback);
        practiceSessionRepository.save(session);

        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) feedback.get("scores");
        progressTracker.update(userId, scores);
        return session;
    }

    @Transactional
    public Map<String, Object> fetchRewrites(UUID sessionId, UUID userId) {
        PracticeSession session = getSessionForUser(sessionId, userId);
        if (session.getUserResponse() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit a response first");
        }
        StyleProfile style = styleProfileRepository.findById(session.getStyleProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));

        Map<String, Object> rewrites = analysisRouter.generateRewrites(session, style, session.getUserResponse());
        Map<String, Object> feedback = session.getFeedback() != null
                ? new java.util.LinkedHashMap<>(session.getFeedback())
                : new java.util.LinkedHashMap<>();
        feedback.put("rewrites", rewrites);
        session.setFeedback(feedback);
        practiceSessionRepository.save(session);
        return rewrites;
    }

    @Transactional
    public String fetchCoachingTip(UUID sessionId, UUID userId) {
        PracticeSession session = getSessionForUser(sessionId, userId);
        Map<String, Object> feedback = session.getFeedback();
        if (feedback == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submit a response first");
        }
        if (feedback.get("coaching_tip") != null) {
            return String.valueOf(feedback.get("coaching_tip"));
        }
        StyleProfile style = styleProfileRepository.findById(session.getStyleProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));
        String tip = analysisRouter.generateCoachingTip(session, style, feedback);
        Map<String, Object> updated = new java.util.LinkedHashMap<>(feedback);
        updated.put("coaching_tip", tip);
        session.setFeedback(updated);
        practiceSessionRepository.save(session);
        return tip;
    }

    public PracticeSession getSession(UUID sessionId) {
        return practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private PracticeSession getSessionForUser(UUID sessionId, UUID userId) {
        PracticeSession session = getSession(sessionId);
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to user");
        }
        return session;
    }
}
