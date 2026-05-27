package com.stylecommunicator.controller;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.service.ConversationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the multi-turn conversation loop.
 *
 * POST /api/sessions/{id}/turns   — add a user turn, get character reply
 * POST /api/sessions/{id}/finish  — score the full conversation
 *
 * The existing SessionController endpoints (/start, /submit, /rewrites,
 * /coaching-tip) are unchanged and still work for single-response sessions.
 */
@RestController
@RequestMapping("/api/sessions")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * Accepts a user message, generates the character's reply, and returns
     * the full conversation history plus a flag when the turn limit is reached.
     */
    @PostMapping("/{id}/turns")
    public TurnResponse addTurn(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody TurnRequest request) {

        ConversationService.TurnResult result =
                conversationService.addTurn(id, userId, request.message());

        return new TurnResponse(
                result.characterReply(),
                result.history(),
                result.maxReached()
        );
    }

    /**
     * Finalises the conversation, runs the full-conversation scorer, and
     * returns the session with feedback — identical shape to the existing
     * SessionController.SessionResponse so the frontend can route to /feedback.
     */
    @PostMapping("/{id}/finish")
    public FinishResponse finishConversation(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID userId) {

        PracticeSession session = conversationService.finishConversation(id, userId);

        return new FinishResponse(
                session.getId(),
                session.getUserId(),
                session.getStyleProfileId(),
                session.getSituation(),
                session.getRequiredWord(),
                session.getEmotionalContext(),
                session.getFeedback(),
                session.getConversationHistory()
        );
    }

    // ── Request / response records ────────────────────────────────────────────

    public record TurnRequest(
            @NotBlank @Size(min = 1, max = 2000) String message
    ) {}

    public record TurnResponse(
            String characterReply,
            List<Map<String, Object>> history,
            boolean maxReached
    ) {}

    public record FinishResponse(
            UUID id,
            UUID userId,
            UUID styleProfileId,
            String situation,
            String requiredWord,
            String emotionalContext,
            Map<String, Object> feedback,
            List<Map<String, Object>> conversationHistory
    ) {}
}