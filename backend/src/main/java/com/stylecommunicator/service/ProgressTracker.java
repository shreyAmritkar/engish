package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.UserProgressRepository;
import com.stylecommunicator.util.StatsUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProgressTracker {

    private static final List<String> DIMENSIONS = List.of(
            "confidence", "tone", "persuasion", "emotional_control", "professionalism", "style_match");

    private static final double EMA_OLD = 0.7;
    private static final double EMA_NEW = 0.3;

    private final UserProgressRepository userProgressRepository;
    private final PracticeSessionRepository practiceSessionRepository;

    public ProgressTracker(
            UserProgressRepository userProgressRepository,
            PracticeSessionRepository practiceSessionRepository) {
        this.userProgressRepository = userProgressRepository;
        this.practiceSessionRepository = practiceSessionRepository;
    }

    @Transactional
    public UserProgress update(UUID userId, Map<String, Integer> sessionScores) {
        UserProgress progress = userProgressRepository.findById(userId)
                .orElseGet(() -> {
                    UserProgress p = new UserProgress();
                    p.setUserId(userId);
                    p.setAverageScores(new HashMap<>());
                    p.setWeakAreas(new ArrayList<>());
                    p.setStrongAreas(new ArrayList<>());
                    p.setHabitFlags(new ArrayList<>());
                    return p;
                });

        Map<String, Double> averages = progress.getAverageScores() != null
                ? new HashMap<>(progress.getAverageScores())
                : new HashMap<>();

        for (String dim : DIMENSIONS) {
            int score = sessionScores.getOrDefault(dim, 50);
            double old = averages.getOrDefault(dim, (double) score);
            averages.put(dim, old * EMA_OLD + score * EMA_NEW);
        }

        progress.setAverageScores(averages);
        progress.setTotalSessions(progress.getTotalSessions() + 1);
        progress.setWeakAreas(findWeakAreas(averages));
        progress.setStrongAreas(findStrongAreas(averages));

        List<PracticeSession> recentSessions = practiceSessionRepository
                .findTop20ByUserIdOrderByCreatedAtDesc(userId);

        if (progress.getTotalSessions() % 5 == 0) {
            progress.setHabitFlags(
                    detectHabits(recentSessions, averages));
        }

        progress.setCurrentLevel(
                calculateLevel(recentSessions));
        userProgressRepository.save(progress);
        return progress;
    }

    private List<String> findWeakAreas(Map<String, Double> averages) {
        return averages.entrySet().stream()
                .filter(e -> e.getValue() < 50)
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> findStrongAreas(Map<String, Double> averages) {
        return averages.entrySet().stream()
                .filter(e -> e.getValue() >= 70)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<String> detectHabits(List<PracticeSession> sessions, Map<String, Double> averages) {
        if (sessions.size() < 5) {
            return List.of();
        }
        Collections.reverse(sessions);
        List<String> flags = new ArrayList<>();

        for (String dim : List.of("persuasion", "confidence", "tone")) {
            List<Double> series = sessions.stream()
                    .map(s -> scoreFromFeedback(s, dim))
                    .filter(Objects::nonNull)
                    .toList();
            if (series.size() < 5)
                continue;

            double trend = StatsUtil.linearRegressionSlope(series);
            double variance = StatsUtil.variance(series);
            double avg = series.stream().mapToDouble(Double::doubleValue).average().orElse(50);

            if ("persuasion".equals(dim) && trend < -0.5) {
                flags.add("Persuasion declining — practice stronger calls to action.");
            }
            if ("confidence".equals(dim) && variance > 30 && avg < 50) {
                flags.add("Emotionally inconsistent — aim for steadier tone.");
            }
            if ("tone".equals(dim) && avg < 40) {
                flags.add("Tone is too flat — add warmth or energy as appropriate.");
            }
        }
        return flags.stream().distinct().toList();
    }

    private int calculateLevel(List<PracticeSession> recentSessions) {
        List<PracticeSession> lastTen = recentSessions.stream().limit(10).toList();
        if (lastTen.isEmpty())
            return 1;

        List<Double> compositeScores = new ArrayList<>();
        for (PracticeSession session : lastTen) {
            compositeScores.add(averageSessionScore(session));
        }
        Collections.reverse(compositeScores);

        double weighted = StatsUtil.weightedAverage(compositeScores);
        double consistencyBonus = 1.0 - Math.min(1.0, StatsUtil.stdDev(compositeScores) / 100.0);
        double finalScore = weighted * (0.9 + 0.1 * consistencyBonus);

        if (finalScore >= 85)
            return 5;
        if (finalScore >= 75)
            return 4;
        if (finalScore >= 60)
            return 3;
        if (finalScore >= 40)
            return 2;
        return 1;
    }

    private double averageSessionScore(PracticeSession session) {
        Map<String, Object> feedback = session.getFeedback();
        if (feedback == null || !(feedback.get("scores") instanceof Map<?, ?> scores)) {
            return 50;
        }
        return scores.values().stream()
                .filter(Number.class::isInstance)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .average()
                .orElse(50);
    }

    private Double scoreFromFeedback(PracticeSession session, String dimension) {
        Map<String, Object> feedback = session.getFeedback();
        if (feedback == null || !(feedback.get("scores") instanceof Map<?, ?> scores)) {
            return null;
        }
        Object val = scores.get(dimension);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return null;
    }

    public UserProgress currentLevelFor(UUID userId) {
        return userProgressRepository.findById(userId).orElse(null);
    }
}
