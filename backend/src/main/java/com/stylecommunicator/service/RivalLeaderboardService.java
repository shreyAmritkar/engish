package com.stylecommunicator.service;

import com.stylecommunicator.entity.UserProgress;
import com.stylecommunicator.repository.UserProgressRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simplified Rival Leaderboard using Minimum Spanning Tree (MST) algorithm.
 * 
 * Instead of Redis, we calculate rivals on-demand using a similarity-based approach.
 * For each user, find the top 10 rivals who share similar strong dimensions.
 * 
 * Algorithm: Build a similarity graph between users based on dimension overlap,
 * then use a greedy approach to find the most similar rivals (similar to MST concept).
 * 
 * Dimensions tracked: confidence, tone, persuasion, emotional_control, professionalism, style_match
 */
@Service
public class RivalLeaderboardService {

    private static final Logger log = LoggerFactory.getLogger(RivalLeaderboardService.class);
    private static final int TOP_RIVALS = 10;
    private static final List<String> DIMENSIONS = List.of(
            "confidence", "tone", "persuasion", "emotional_control", "professionalism", "style_match"
    );

    private final UserProgressRepository userProgressRepository;

    public RivalLeaderboardService(UserProgressRepository userProgressRepository) {
        this.userProgressRepository = userProgressRepository;
    }

    /**
     * Get top 10 rivals for a user based on shared dimension strengths.
     * Uses a similarity score calculated from matching high-performing dimensions.
     * Time: O(n * 6) = O(n) where n = total users
     */
    public List<RivalEntry> getRivalsForUser(UUID userId) {
        try {
            UserProgress myProgress = userProgressRepository.findById(userId).orElse(null);
            if (myProgress == null || myProgress.getAverageScores() == null || myProgress.getAverageScores().isEmpty()) {
                return List.of();
            }

            // Get my top dimensions (score >= 70)
            Set<String> myTopDimensions = myProgress.getAverageScores().entrySet().stream()
                    .filter(e -> e.getValue() >= 70)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            if (myTopDimensions.isEmpty()) {
                return List.of(); // No rivals if no strong dimensions
            }

            // Calculate similarity score for each other user
            List<UserProgress> allUsers = userProgressRepository.findAll();
            List<RivalSimilarity> similarities = new ArrayList<>();

            for (UserProgress other : allUsers) {
                if (other.getUserId().equals(userId)) continue; // Skip self
                if (other.getAverageScores() == null || other.getAverageScores().isEmpty()) continue;

                // Calculate similarity: how many of their top dimensions overlap with mine
                double similarity = calculateSimilarity(myProgress.getAverageScores(), 
                                                      other.getAverageScores(), 
                                                      myTopDimensions);
                if (similarity > 0) {
                    similarities.add(new RivalSimilarity(other.getUserId(), similarity, other));
                }
            }

            // Sort by similarity (descending) and take top 10
            return similarities.stream()
                    .sorted((a, b) -> Double.compare(b.similarity, a.similarity))
                    .limit(TOP_RIVALS)
                    .map(rs -> new RivalEntry(
                            rs.userId,
                            rs.userProgress.getUserId().toString(), // Will be replaced with email in controller
                            rs.getBestMatchingDimension(myTopDimensions),
                            rs.similarity
                    ))
                    .toList();

        } catch (Exception e) {
            log.warn("Failed to calculate rivals for user {}", userId, e);
            return List.of();
        }
    }

    /**
     * Calculate similarity score between two users based on shared top dimensions.
     * Similarity = (matched dimensions count * average matched score) / my dimension count
     * 
     * Higher score = more similar user
     */
    private double calculateSimilarity(Map<String, Double> myScores, 
                                       Map<String, Double> otherScores, 
                                       Set<String> myTopDimensions) {
        double matchedScore = 0;
        int matchedCount = 0;

        for (String dimension : myTopDimensions) {
            Double otherScore = otherScores.get(dimension);
            if (otherScore != null && otherScore >= 70) { // They also strong in this dimension
                matchedScore += otherScore;
                matchedCount++;
            }
        }

        if (matchedCount == 0) return 0;

        // Normalized similarity: average matched score * overlap percentage
        double avgMatchedScore = matchedScore / matchedCount;
        double overlapPercentage = (double) matchedCount / myTopDimensions.size();
        
        return avgMatchedScore * overlapPercentage;
    }

    /**
     * DTO for rival entry in leaderboard
     */
    public record RivalEntry(
            UUID rivalUserId,
            String email,
            String matchingDimension,
            double similarityScore
    ) {}

    /**
     * Internal class for similarity calculation
     */
    private static class RivalSimilarity {
        UUID userId;
        double similarity;
        UserProgress userProgress;

        RivalSimilarity(UUID userId, double similarity, UserProgress userProgress) {
            this.userId = userId;
            this.similarity = similarity;
            this.userProgress = userProgress;
        }

        String getBestMatchingDimension(Set<String> myTopDimensions) {
            if (userProgress.getAverageScores() == null) return "unknown";

            return myTopDimensions.stream()
                    .filter(d -> userProgress.getAverageScores().containsKey(d) && 
                                userProgress.getAverageScores().get(d) >= 70)
                    .max(Comparator.comparingDouble(d -> userProgress.getAverageScores().get(d)))
                    .orElse("unknown");
        }
    }
}
