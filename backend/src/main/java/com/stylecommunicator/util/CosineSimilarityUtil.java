package com.stylecommunicator.util;

import com.stylecommunicator.entity.StyleProfile;

public final class CosineSimilarityUtil {

    private CosineSimilarityUtil() {}

    public static double cosineSimilarity(StyleProfile a, StyleProfile b) {
        double[] vecA = toVector(a);
        double[] vecB = toVector(b);
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < vecA.length; i++) {
            dot += vecA[i] * vecB[i];
            magA += vecA[i] * vecA[i];
            magB += vecB[i] * vecB[i];
        }
        if (magA == 0 || magB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private static double[] toVector(StyleProfile profile) {
        int formality = profile.getFormalityLevel() != null ? profile.getFormalityLevel() : 5;
        return new double[]{
                mapVocabularyTier(profile.getVocabularyTier()),
                mapSentenceStructure(profile.getSentenceStructure()),
                mapEmotionalRange(profile.getEmotionalRange()),
                mapPowerDynamic(profile.getPowerDynamic()),
                formality / 10.0
        };
    }

    static double mapVocabularyTier(String tier) {
        if (tier == null) return 0.5;
        return switch (tier.toUpperCase()) {
            case "SIMPLE" -> 0.25;
            case "INTERMEDIATE" -> 0.5;
            case "ADVANCED" -> 0.75;
            case "TECHNICAL" -> 1.0;
            default -> 0.5;
        };
    }

    static double mapSentenceStructure(String structure) {
        if (structure == null) return 0.5;
        return switch (structure.toUpperCase()) {
            case "SHORT_PUNCHY" -> 0.25;
            case "MIXED" -> 0.5;
            case "LONG_COMPLEX" -> 0.75;
            default -> 0.5;
        };
    }

    static double mapEmotionalRange(String range) {
        if (range == null) return 0.5;
        return switch (range.toUpperCase()) {
            case "FLAT" -> 0.25;
            case "MODERATE" -> 0.5;
            case "EXPRESSIVE" -> 0.75;
            default -> 0.5;
        };
    }

    static double mapPowerDynamic(String dynamic) {
        if (dynamic == null) return 0.5;
        return switch (dynamic.toUpperCase()) {
            case "SUBMISSIVE" -> 0.25;
            case "EQUAL" -> 0.5;
            case "DOMINANT" -> 0.75;
            default -> 0.5;
        };
    }
}
