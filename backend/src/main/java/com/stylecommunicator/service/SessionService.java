package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.StyleProfileRepository;
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

    public SessionService(
            PracticeSessionRepository practiceSessionRepository,
            StyleProfileRepository styleProfileRepository,
            SituationBankService situationBankService,
            AnalysisRouter analysisRouter,
            ProgressTracker progressTracker,
            IntentValidationService intentValidationService) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.situationBankService = situationBankService;
        this.analysisRouter = analysisRouter;
        this.progressTracker = progressTracker;
        this.intentValidationService = intentValidationService;
    }

    @Transactional
    public PracticeSession startSession(UUID userId, UUID styleProfileId) {
        StyleProfile style = styleProfileRepository.findById(styleProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setStyleProfileId(styleProfileId);
        session.setSituation(situationBankService.pickSituation(
                style.getPowerDynamic(), toCefrLevel(style.getVocabularyTier()), userId));
        session.setRequiredWord(situationBankService.pickRequiredWord());
        session.setEmotionalContext(situationBankService.pickEmotionalContext());
        return practiceSessionRepository.save(session);
    }

    /**
     * Maps the style profile's vocabulary tier (SIMPLE / INTERMEDIATE /
     * ADVANCED / TECHNICAL — produced by the LLM style-extraction prompt)
     * to the CEFR level codes the situation_bank table actually stores
     * (A1 / A2 / B1 / B2). These are two different vocabularies for two
     * different purposes and must never be passed through unmapped —
     * doing so violates the situation_bank_level_check DB constraint.
     */
    private String toCefrLevel(String vocabularyTier) {
        if (vocabularyTier == null) return "B2";
        return switch (vocabularyTier.trim().toUpperCase()) {
            case "SIMPLE"       -> "A1";
            case "INTERMEDIATE" -> "B1";
            case "ADVANCED"     -> "B2";
            case "TECHNICAL"    -> "B2";
            default             -> "B2";
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
