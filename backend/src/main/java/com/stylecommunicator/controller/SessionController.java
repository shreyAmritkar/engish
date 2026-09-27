package com.stylecommunicator.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.stylecommunicator.dto.StartSessionRequest;
import com.stylecommunicator.dto.SubmitSessionRequest;
import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Tag(name = "Sessions", description = "Practice session lifecycle: start, submit, rewrite, coaching tip.")
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    private UUID userId(HttpServletRequest request) {
        return (UUID) request.getAttribute("authenticatedUserId");
    }

    @PostMapping("/start")
    public SessionResponse start(@Valid @RequestBody StartSessionRequest body,
                                 HttpServletRequest request) {
        PracticeSession session = sessionService.startSession(userId(request), body.styleProfileId());
        return SessionResponse.from(session, false);
    }

    @PostMapping("/{id}/submit")
    public SessionResponse submit(@PathVariable UUID id,
                                  @Valid @RequestBody SubmitSessionRequest body,
                                  HttpServletRequest request) {
        PracticeSession session = sessionService.submitResponse(id, userId(request), body.userResponse());
        return SessionResponse.from(session, true);
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable UUID id) {
        PracticeSession session = sessionService.getSession(id);
        return SessionResponse.from(session, session.getFeedback() != null);
    }

    @PostMapping("/{id}/rewrites")
    public Map<String, Object> rewrites(@PathVariable UUID id, HttpServletRequest request) {
        return sessionService.fetchRewrites(id, userId(request));
    }

    @PostMapping("/{id}/coaching-tip")
    public Map<String, String> coachingTip(@PathVariable UUID id, HttpServletRequest request) {
        String tip = sessionService.fetchCoachingTip(id, userId(request));
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
