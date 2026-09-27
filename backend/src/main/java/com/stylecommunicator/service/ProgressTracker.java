package com.stylecommunicator.service;

import com.stylecommunicator.entity.PracticeSession;
import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.PracticeSessionRepository;
import com.stylecommunicator.repository.UserProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simplified progress tracking service.
 * 
 * Tracks user progress across 6 key communication dimensions:
 * confidence, tone, persuasion, emotional_control, professionalism, style_match
 * 
 * Uses Exponential Moving Average (EMA) for dimension scores:
 * - Old weight: 70%, New weight: 30%
 * - Identifies weak areas (score < 50) and strong areas (score >= 70)
 * - Calculates skill level based on recent performance (1-5 scale)
 */
@Service
public class ProgressTracker {

    private static final java.util.List<String> DIMENSIONS = java.util.List.of(
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

    /**
     * Update user progress after a practice session.
     * Calculates EMA for all dimensions, identifies weak/strong areas, and updates level.
     */
    @Transactional
    public UserProgress update(UUID userId, java.util.Map<String, Integer> sessionScores) {
        UserProgress progress = userProgressRepository.findById(userId)
                .orElseGet(() -> {
                    UserProgress p = new UserProgress();
                    p.setUserId(userId);
                    p.setAverageScores(new java.util.HashMap<>());
                    p.setWeakAreas(new java.util.ArrayList<>());
                    p.setStrongAreas(new java.util.ArrayList<>());
                    p.setHabitFlags(new java.util.ArrayList<>());
                    p.setTotalSessions(0);
                    p.setCurrentLevel(1);
                    return p;
                });

        java.util.Map<String, Double> averages = progress.getAverageScores() != null
                ? new java.util.HashMap<>(progress.getAverageScores())
                : new java.util.HashMap<>();

        // Update EMA for each dimension
        for (String dim : DIMENSIONS) {
            int score = sessionScores.getOrDefault(dim, 50);
            double old = averages.getOrDefault(dim, (double) score);
            averages.put(dim, old * EMA_OLD + score * EMA_NEW);
        }

        progress.setAverageScores(averages);
        progress.setTotalSessions(progress.getTotalSessions() + 1);
        progress.setWeakAreas(findWeakAreas(averages));
        progress.setStrongAreas(findStrongAreas(averages));

        // Update level based on recent sessions (last 10)
        java.util.List<PracticeSession> recentSessions = practiceSessionRepository
                .findTop20ByUserIdOrderByCreatedAtDesc(userId);
        progress.setCurrentLevel(calculateLevel(recentSessions));

        userProgressRepository.save(progress);
        return progress;
    }

    /**
     * Find weak areas: dimensions where average score < 50
     * Returns up to 3 weakest dimensions sorted by score (ascending)
     */
    private java.util.List<String> findWeakAreas(java.util.Map<String, Double> averages) {
        return averages.entrySet().stream()
                .filter(e -> e.getValue() < 50)
                .sorted(java.util.Comparator.comparingDouble(java.util.Map.Entry::getValue))
                .map(java.util.Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Find strong areas: dimensions where average score >= 70
     * Returns up to 3 strongest dimensions sorted by score (descending)
     */
    private java.util.List<String> findStrongAreas(java.util.Map<String, Double> averages) {
        return averages.entrySet().stream()
                .filter(e -> e.getValue() >= 70)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(java.util.Map.Entry::getKey)
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Calculate skill level (1-5) based on average score from recent 10 sessions.
     * 
     * Level calculation:
     * - 85+ = Level 5 (Expert)
     * - 75+ = Level 4 (Advanced)
     * - 60+ = Level 3 (Intermediate)
     * - 40+ = Level 2 (Beginner)
     * - <40 = Level 1 (Novice)
     */
    private int calculateLevel(java.util.List<PracticeSession> recentSessions) {
        if (recentSessions.isEmpty()) return 1;

        // Take last 10 sessions
        double avgScore = recentSessions.stream()
                .limit(10)
                .mapToDouble(this::averageSessionScore)
                .average()
                .orElse(50);

        if (avgScore >= 85) return 5;
        if (avgScore >= 75) return 4;
        if (avgScore >= 60) return 3;
        if (avgScore >= 40) return 2;
        return 1;
    }

    /**
     * Calculate average score from session feedback
     */
    private double averageSessionScore(PracticeSession session) {
        java.util.Map<String, Object> feedback = session.getFeedback();
        if (feedback == null || !(feedback.get("scores") instanceof java.util.Map<?, ?> scores)) {
            return 50;
        }
        return scores.values().stream()
                .filter(Number.class::isInstance)
                .mapToDouble(v -> ((Number) v).doubleValue())
                .average()
                .orElse(50);
    }

    public UserProgress currentLevelFor(UUID userId) {
        return userProgressRepository.findById(userId).orElse(null);
    }
}
