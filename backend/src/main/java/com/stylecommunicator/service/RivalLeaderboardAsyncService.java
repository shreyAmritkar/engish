package com.stylecommunicator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RivalLeaderboardAsyncService {

    private static final Logger log = LoggerFactory.getLogger(RivalLeaderboardAsyncService.class);

    // top N candidates pulled per dimension before merging — 1-hop, so this stays small
    private static final int CANDIDATES_PER_DIMENSION = 20;

    private final StringRedisTemplate redis;

    public RivalLeaderboardAsyncService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Async("rivalLeaderboardExecutor")
    public void rebuildRivalsForUser(UUID userId, Set<String> myDimensions) {
        try {
            Map<UUID, Double> rivalBest = new HashMap<>();
            Map<UUID, String> rivalBestDimension = new HashMap<>();

            for (String dimension : myDimensions) {
                String key = "dimension:%s:leaderboard".formatted(dimension);
                Set<ZSetOperations.TypedTuple<String>> top =
                        redis.opsForZSet().reverseRangeWithScores(key, 0, CANDIDATES_PER_DIMENSION - 1);
                if (top == null) continue;

                for (ZSetOperations.TypedTuple<String> t : top) {
                    String member = t.getValue();
                    Double score = t.getScore();
                    if (member == null || score == null) continue;

                    UUID rivalId = UUID.fromString(member);
                    if (rivalId.equals(userId)) continue;

                    Double existing = rivalBest.get(rivalId);
                    if (existing == null || score > existing) {
                        rivalBest.put(rivalId, score);
                        rivalBestDimension.put(rivalId, dimension);
                    }
                }
            }

            String myKey = "user:%s:rival_leaderboard".formatted(userId);
            String dimensionKey = "user:%s:rival_dimension".formatted(userId);
            redis.delete(myKey);
            redis.delete(dimensionKey);
            if (!rivalBest.isEmpty()) {
                rivalBest.forEach((rivalId, score) ->
                        redis.opsForZSet().add(myKey, rivalId.toString(), score));
                rivalBestDimension.forEach((rivalId, dimension) ->
                        redis.opsForHash().put(dimensionKey, rivalId.toString(), dimension));
            }
            redis.expire(myKey, Duration.ofHours(30)); // safety net if a cron run fails or is delayed
            redis.expire(dimensionKey, Duration.ofHours(30));
        } catch (Exception e) {
            // one user's failure must never block/affect the rest of the fan-out
            log.warn("Failed to rebuild rival leaderboard for user {}", userId, e);
        }
    }
}
