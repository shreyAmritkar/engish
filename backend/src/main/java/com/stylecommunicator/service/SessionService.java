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

    public SessionService(
            PracticeSessionRepository practiceSessionRepository,
            StyleProfileRepository styleProfileRepository,
            SituationBankService situationBankService,
            AnalysisRouter analysisRouter,
            ProgressTracker progressTracker) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.situationBankService = situationBankService;
        this.analysisRouter = analysisRouter;
        this.progressTracker = progressTracker;
    }

    @Transactional
    public PracticeSession startSession(UUID userId, UUID styleProfileId) {
        StyleProfile style = styleProfileRepository.findById(styleProfileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));

        PracticeSession session = new PracticeSession();
        session.setUserId(userId);
        session.setStyleProfileId(styleProfileId);
        session.setSituation(situationBankService.pickSituation(style.getPowerDynamic(), userId));
        session.setRequiredWord(situationBankService.pickRequiredWord());
        session.setEmotionalContext(situationBankService.pickEmotionalContext());
        return practiceSessionRepository.save(session);
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
