package com.stylecommunicator.controller;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.UserProgressRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    private final UserProgressRepository userProgressRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    public ProgressController(UserProgressRepository userProgressRepository,
                               PracticeSessionRepository practiceSessionRepository) {
        this.userProgressRepository = userProgressRepository;
        this.practiceSessionRepository = practiceSessionRepository;
    }

    /** Get the authenticated user's own progress */
    @GetMapping("/me")
    public ProgressResponse getMyProgress(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        return buildProgress(userId);
    }

    /** Admin / legacy path — users can only access their own data */
    @GetMapping("/{userId}")
    public ProgressResponse getProgress(@PathVariable UUID userId,
                                        HttpServletRequest request) {
        UUID caller = (UUID) request.getAttribute("authenticatedUserId");
        // Only allow if caller is the same user (admin bypass not needed here,
        // but SecurityConfig already gates /api/admin/** separately)
        if (!caller.equals(userId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Access denied");
        }
        return buildProgress(userId);
    }

    private ProgressResponse buildProgress(UUID userId) {
        UserProgress progress = userProgressRepository.findById(userId)
                .orElseGet(() -> {
                    UserProgress p = new UserProgress();
                    p.setUserId(userId);
                    p.setAverageScores(Map.of());
                    p.setWeakAreas(List.of());
                    p.setStrongAreas(List.of());
                    p.setHabitFlags(List.of());
                    return p;
                });

        List<SessionSummary> history = practiceSessionRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(20)
                .map(SessionSummary::from)
                .toList();

        return new ProgressResponse(
                progress.getUserId(),
                progress.getCurrentLevel(),
                progress.getTotalSessions(),
                progress.getAverageScores(),
                progress.getWeakAreas(),
                progress.getStrongAreas(),
                progress.getHabitFlags(),
                history
        );
    }

    public record ProgressResponse(
            UUID userId,
            int currentLevel,
            int totalSessions,
            Map<String, Double> averageScores,
            List<String> weakAreas,
            List<String> strongAreas,
            List<String> habitFlags,
            List<SessionSummary> recentSessions
    ) {}

    public record SessionSummary(
            UUID id,
            UUID styleProfileId,
            String situation,
            Map<String, Object> feedback,
            String createdAt
    ) {
        static SessionSummary from(PracticeSession session) {
            return new SessionSummary(
                    session.getId(),
                    session.getStyleProfileId(),
                    session.getSituation(),
                    session.getFeedback(),
                    session.getCreatedAt() != null ? session.getCreatedAt().toString() : null
            );
        }
    }
}
