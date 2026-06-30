package com.stylecommunicator.controller;

import com.stylecommunicator.entity.AppUser;
import com.stylecommunicator.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final StringRedisTemplate redis;
    private final AppUserRepository userRepository;

    public LeaderboardController(StringRedisTemplate redis, AppUserRepository userRepository) {
        this.redis = redis;
        this.userRepository = userRepository;
    }

    /**
     * Top 10 rivals: users who score on the same dimension(s) as you, ranked by
     * their best shared score, enriched with email and which dimension matched
     * so the result is actually readable in the UI (not just a UUID + number).
     */
    @GetMapping("/rivals")
    public List<RivalEntry> getRivals(HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute("authenticatedUserId");
        String scoreKey = "user:%s:rival_leaderboard".formatted(userId);
        String dimensionKey = "user:%s:rival_dimension".formatted(userId);

        Set<ZSetOperations.TypedTuple<String>> top = redis.opsForZSet().reverseRangeWithScores(scoreKey, 0, 9);
        if (top == null || top.isEmpty()) {
            return List.of();
        }

        // ordered list of rival IDs, preserving rank order from the ZSET
        List<UUID> rivalIds = top.stream()
                .map(t -> UUID.fromString(t.getValue()))
                .toList();

        // one batch lookup for emails instead of N queries
        Map<UUID, String> emailsById = userRepository.findAllById(rivalIds).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getEmail));

        // dimension labels are a Redis HASH keyed by rival id, fetched in one batched call
        List<String> rivalIdStrings = rivalIds.stream().map(UUID::toString).toList();
        List<Object> dimensions = redis.opsForHash().multiGet(dimensionKey, List.copyOf(rivalIdStrings));

        Map<UUID, String> dimensionById = new LinkedHashMap<>();
        for (int i = 0; i < rivalIds.size(); i++) {
            Object dim = dimensions.get(i);
            dimensionById.put(rivalIds.get(i), dim != null ? dim.toString() : "unknown");
        }

        return top.stream()
                .map(t -> {
                    UUID rivalId = UUID.fromString(t.getValue());
                    return new RivalEntry(
                            rivalId,
                            emailsById.getOrDefault(rivalId, "unknown user"),
                            dimensionById.getOrDefault(rivalId, "unknown"),
                            t.getScore()
                    );
                })
                .toList();
    }

    public record RivalEntry(UUID rivalUserId, String email, String dimension, double sharedBestScore) {}
}
