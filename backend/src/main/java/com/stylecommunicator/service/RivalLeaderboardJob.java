package com.stylecommunicator.service;

import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.UserProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rival leaderboard, 1-hop.
 *
 * "Here are users who score on the same dimensions as you, ranked by their
 * score on those dimensions." Dimensions are the fixed set already tracked
 * in UserProgress.averageScores (confidence, tone, persuasion,
 * emotional_control, professionalism, style_match) — see ProgressTracker.
 *
 * Two Redis layers:
 *  - dimension:{dim}:leaderboard      ZSET  member=userId  score=averageScores.get(dim)
 *  - user:{userId}:rival_leaderboard  ZSET  member=rivalId  score=rival's best shared-dimension score
 *
 * Step 1 (this class, cron thread) rebuilds the small per-dimension ZSETs —
 * only 6 of them, cheap, done inline.
 * Step 2 fans out one async task per user to rebuild their personalized
 * rival list, so the cron thread returns immediately and users are
 * processed in parallel on a dedicated pool.
 */
@Component
public class RivalLeaderboardJob {

    private static final Logger log = LoggerFactory.getLogger(RivalLeaderboardJob.class);

    private final UserProgressRepository userProgressRepository;
    private final StringRedisTemplate redis;
    private final RivalLeaderboardAsyncService asyncService;

    public RivalLeaderboardJob(UserProgressRepository userProgressRepository,
                                StringRedisTemplate redis,
                                RivalLeaderboardAsyncService asyncService) {
        this.userProgressRepository = userProgressRepository;
        this.redis = redis;
        this.asyncService = asyncService;
    }

    @Scheduled(cron = "0 0 3 * * *") // 3 AM daily
    public void rebuild() {
        List<UserProgress> all = userProgressRepository.findAll();
        log.info("Rival leaderboard rebuild starting for {} users", all.size());

        // Step 1 — rebuild the 6 per-dimension leaderboards inline (cheap: <=6 * users ZADDs)
        for (UserProgress p : all) {
            Map<String, Double> averages = p.getAverageScores();
            if (averages == null || averages.isEmpty()) continue;

            for (Map.Entry<String, Double> entry : averages.entrySet()) {
                String key = "dimension:%s:leaderboard".formatted(entry.getKey());
                redis.opsForZSet().add(key, p.getUserId().toString(), entry.getValue());
            }
        }

        // Step 2 — fan out the per-user rival rebuild, async, off the cron thread
        for (UserProgress p : all) {
            Map<String, Double> averages = p.getAverageScores();
            if (averages == null || averages.isEmpty()) continue;
            asyncService.rebuildRivalsForUser(p.getUserId(), averages.keySet());
        }

        log.info("Rival leaderboard rebuild dispatched for {} users", all.size());
    }
}
