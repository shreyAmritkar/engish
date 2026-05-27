package com.stylecommunicator.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.StyleProfile;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.StyleProfileRepository;

@Service
public class ConversationService {

    /** Maximum user turns before the conversation must be finished. */
    private static final int MAX_USER_TURNS = 5;

    private final PracticeSessionRepository practiceSessionRepository;
    private final StyleProfileRepository styleProfileRepository;
    private final AnalysisRouter analysisRouter;
    private final ProgressTracker progressTracker;

    public ConversationService(
            PracticeSessionRepository practiceSessionRepository,
            StyleProfileRepository styleProfileRepository,
            AnalysisRouter analysisRouter,
            ProgressTracker progressTracker) {
        this.practiceSessionRepository = practiceSessionRepository;
        this.styleProfileRepository = styleProfileRepository;
        this.analysisRouter = analysisRouter;
        this.progressTracker = progressTracker;
    }

    /**
     * Appends a user turn, generates the character reply, persists both, and
     * returns a result containing the reply, the updated history, and whether
     * the user has hit the turn limit.
     */
    @Transactional
    public TurnResult addTurn(UUID sessionId, UUID userId, String userMessage) {
        PracticeSession session = getSessionForUser(sessionId, userId);

        if (session.getFeedback() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This conversation is already scored. Start a new session to practice again.");
        }

        List<Map<String, Object>> history = mutableHistory(session);

        long userTurnCount = history.stream()
                .filter(t -> "user".equals(t.get("role")))
                .count();

        if (userTurnCount >= MAX_USER_TURNS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Max turns reached — click 'Finish & score' to see your results.");
        }

        StyleProfile style = requireStyle(session);

        // Append the user's turn
        history.add(turn("user", userMessage));

        // Generate and append the character's reply
        String characterReply = analysisRouter.generateCharacterReply(
                session.getSituation(),
                session.getEmotionalContext(),
                history,   // pass full history INCLUDING the user turn so the LLM sees it
                userMessage,
                style.getPowerDynamic()
        );
        history.add(turn("character", characterReply));

        session.setConversationHistory(history);
        session.setMultiTurn(true);
        practiceSessionRepository.save(session);

        long newUserTurnCount = userTurnCount + 1;
        return new TurnResult(characterReply, List.copyOf(history), newUserTurnCount >= MAX_USER_TURNS);
    }

    /**
     * Scores the complete conversation and persists the feedback.
     * After this call the session behaves like a normal finished session —
     * the existing rewrites and coaching-tip endpoints work unchanged.
     */
    @Transactional
    public PracticeSession finishConversation(UUID sessionId, UUID userId) {
        PracticeSession session = getSessionForUser(sessionId, userId);

        if (session.getFeedback() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already scored.");
        }

        List<Map<String, Object>> history = session.getConversationHistory();
        if (history == null || history.stream().noneMatch(t -> "user".equals(t.get("role")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Send at least one message before finishing.");
        }

        StyleProfile style = requireStyle(session);

        // Combine all user messages into userResponse for backward compat
        // (ProgressTracker and rewrites endpoints still read this field)
        String combinedUserText = history.stream()
                .filter(t -> "user".equals(t.get("role")))
                .map(t -> String.valueOf(t.get("text")))
                .collect(Collectors.joining(" "));
        session.setUserResponse(combinedUserText);

        Map<String, Object> feedback = analysisRouter.analyzeConversation(session, style, history);
        session.setFeedback(feedback);
        practiceSessionRepository.save(session);

        @SuppressWarnings("unchecked")
        Map<String, Integer> scores = (Map<String, Integer>) feedback.get("scores");
        progressTracker.update(userId, scores);

        return session;
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public record TurnResult(
            String characterReply,
            List<Map<String, Object>> history,
            boolean maxReached
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private PracticeSession getSessionForUser(UUID sessionId, UUID userId) {
        PracticeSession session = practiceSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Session does not belong to user");
        }
        return session;
    }

    private StyleProfile requireStyle(PracticeSession session) {
        return styleProfileRepository.findById(session.getStyleProfileId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Style not found"));
    }

    /**
     * Returns a mutable copy of the session's conversation history, or an
     * empty list if none exists yet.
     */
    private List<Map<String, Object>> mutableHistory(PracticeSession session) {
        return session.getConversationHistory() != null
                ? new ArrayList<>(session.getConversationHistory())
                : new ArrayList<>();
    }

    private Map<String, Object> turn(String role, String text) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("role", role);
        t.put("text", text);
        t.put("timestamp", Instant.now().toString());
        return t;
    }
}