package com.stylecommunicator.controller;

import com.stylecommunicator.dto.StartSessionRequest;
import com.stylecommunicator.dto.SubmitSessionRequest;
import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public SessionResponse start(@Valid @RequestBody StartSessionRequest request) {
        PracticeSession session = sessionService.startSession(request.userId(), request.styleProfileId());
        return SessionResponse.from(session, false);
    }

    @PostMapping("/{id}/submit")
    public SessionResponse submit(@PathVariable UUID id, @Valid @RequestBody SubmitSessionRequest request) {
        PracticeSession session = sessionService.submitResponse(id, request.userId(), request.userResponse());
        return SessionResponse.from(session, true);
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable UUID id) {
        PracticeSession session = sessionService.getSession(id);
        return SessionResponse.from(session, session.getFeedback() != null);
    }

    @PostMapping("/{id}/rewrites")
    public Map<String, Object> rewrites(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID userId) {
        return sessionService.fetchRewrites(id, userId);
    }

    @PostMapping("/{id}/coaching-tip")
    public Map<String, String> coachingTip(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID userId) {
        String tip = sessionService.fetchCoachingTip(id, userId);
        return Map.of("coaching_tip", tip);
    }

    public record SessionResponse(
            UUID id,
            UUID userId,
            UUID styleProfileId,
            String situation,
            String requiredWord,
            String emotionalContext,
            String userResponse,
            Map<String, Object> feedback
    ) {
        static SessionResponse from(PracticeSession session, boolean includeFeedback) {
            return new SessionResponse(
                    session.getId(),
                    session.getUserId(),
                    session.getStyleProfileId(),
                    session.getSituation(),
                    session.getRequiredWord(),
                    session.getEmotionalContext(),
                    session.getUserResponse(),
                    includeFeedback ? session.getFeedback() : null
            );
        }
    }
}
