package com.stylecommunicator.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import com.stylecommunicator.service.RivalLeaderboardService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Tag(name = "Leaderboard", description = "Rival leaderboard by shared strongest dimension.")
@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final RivalLeaderboardService rivalService;
    private final AppUserRepository userRepository;

    public LeaderboardController(RivalLeaderboardService rivalService, AppUserRepository userRepository) {
        this.rivalService = rivalService;
        this.userRepository = userRepository;
    }

    /**
     * Top 10 rivals: users who score on the same dimension(s) as you, ranked by
     * similarity score. Uses algorithm-based calculation (no Redis).
     * 
     * Similarity is based on shared top-performing dimensions (score >= 70).
     */
    @GetMapping("/rivals")
    public List<RivalEntryResponse> getRivals(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        
        // Get rivals using similarity-based algorithm
        List<RivalLeaderboardService.RivalEntry> rivals = rivalService.getRivalsForUser(userId);
        
        if (rivals.isEmpty()) {
            return List.of();
        }

        // Batch lookup emails for all rivals
        List<UUID> rivalIds = rivals.stream()
                .map(RivalLeaderboardService.RivalEntry::rivalUserId)
                .toList();
        
        Map<UUID, String> emailsById = userRepository.findAllById(rivalIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));

        // Enrich with emails
        return rivals.stream()
                .map(rival -> new RivalEntryResponse(
                        rival.rivalUserId(),
                        emailsById.getOrDefault(rival.rivalUserId(), "unknown user"),
                        rival.matchingDimension(),
                        rival.similarityScore()
                ))
                .toList();
    }

    public record RivalEntryResponse(UUID rivalUserId, String email, String dimension, double similarityScore) {}
}
