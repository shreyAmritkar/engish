package com.stylecommunicator.util;

import com.stylecommunicator.entity.StyleProfile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Hybrid style similarity: structural enums (coarse) + pattern/phrase tokens + lexical fingerprint.
 * Five enum dimensions alone cannot separate e.g. "CEO" vs "meme streamer" — both may be DOMINANT/formal.
 */
public final class CosineSimilarityUtil {

    private static final double WEIGHT_STRUCTURAL = 0.30;
    private static final double WEIGHT_PATTERN_JACCARD = 0.45;
    private static final double WEIGHT_LEXICAL = 0.25;

    /** Discriminator terms — counts become a sparse vector for cosine. */
    private static final String[] STYLE_LEXICON = {
            // Corporate / executive
            "executive", "ceo", "board", "stakeholder", "quarterly", "strategic", "leadership",
            "revenue", "deliverable", "accountable", "initiative", "prioritize", "roi",
            // Meme / informal / internet
            "meme", "viral", "ironic", "slang", "bro", "vibe", "based", "cringe", "literally",
            "lowkey", "highkey", "sus", "cap", "ratio", "stream", "chat",
            // Negotiation / direct
            "decisive", "direct", "boundary", "terms", "negotiate", "commit", "deadline",
            // Empathetic
            "empathy", "understand", "feel", "validate", "listen", "support", "together",
            // Hedging / weak (avoid patterns often)
            "sorry", "apologize", "maybe", "perhaps", "might", "just", "kind of", "sort of",
            // Formal register
            "pursuant", "hereby", "accordingly", "respectfully", "sincerely", "regards",
            // Aggressive / dominant tone
            "must", "will", "need", "expect", "require", "nonnegotiable", "final"
    };

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "that", "with", "this", "from", "your", "you", "are", "was",
            "have", "has", "not", "but", "can", "will", "our", "their", "they", "about", "into"
    );

    private CosineSimilarityUtil() {}

    /** Combined similarity in [0, 1]. Used for deduplication threshold (e.g. 0.85). */
    public static double cosineSimilarity(StyleProfile a, StyleProfile b) {
        double structural = structuralCosine(a, b);
        double patternJaccard = jaccard(tokenSet(a), tokenSet(b));
        double lexical = cosineVectors(lexiconVector(a), lexiconVector(b));
        return WEIGHT_STRUCTURAL * structural
                + WEIGHT_PATTERN_JACCARD * patternJaccard
                + WEIGHT_LEXICAL * lexical;
    }

    /** Structural enums only (legacy / tests). */
    static double structuralCosine(StyleProfile a, StyleProfile b) {
        double[] vecA = structuralVector(a);
        double[] vecB = structuralVector(b);
        return cosine(vecA, vecB);
    }

    private static double[] structuralVector(StyleProfile profile) {
        int formality = profile.getFormalityLevel() != null ? profile.getFormalityLevel() : 5;
        int patternCount = sizeOf(profile.getKeyPatterns()) + sizeOf(profile.getAvoidPatterns());
        double patternDensity = Math.min(1.0, patternCount / 10.0);
        double phraseDensity = Math.min(1.0, sizeOf(profile.getSamplePhrases()) / 8.0);
        return new double[]{
                mapVocabularyTier(profile.getVocabularyTier()),
                mapSentenceStructure(profile.getSentenceStructure()),
                mapEmotionalRange(profile.getEmotionalRange()),
                mapPowerDynamic(profile.getPowerDynamic()),
                formality / 10.0,
                patternDensity,
                phraseDensity
        };
    }

    private static Set<String> tokenSet(StyleProfile profile) {
        String blob = Stream.of(
                        profile.getName(),
                        profile.getRawDescription(),
                        joinList(profile.getKeyPatterns()),
                        joinList(profile.getAvoidPatterns()),
                        joinList(profile.getSamplePhrases()))
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        return tokenize(blob);
    }

    private static double[] lexiconVector(StyleProfile profile) {
        String blob = tokenSet(profile).stream().collect(Collectors.joining(" "));
        String lower = blob.toLowerCase(Locale.ROOT);
        double[] vec = new double[STYLE_LEXICON.length];
        for (int i = 0; i < STYLE_LEXICON.length; i++) {
            String term = STYLE_LEXICON[i];
            int count = countOccurrences(lower, term);
            vec[i] = count;
        }
        return normalize(vec);
    }

    private static int countOccurrences(String text, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) >= 0) {
            count++;
            idx += term.length();
        }
        return count;
    }

    private static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String[] parts = text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        Set<String> tokens = new HashSet<>();
        for (String part : parts) {
            if (part.length() >= 3 && !STOPWORDS.contains(part)) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static double cosine(double[] a, double[] b) {
        double dot = 0, magA = 0, magB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            magA += a[i] * a[i];
            magB += b[i] * b[i];
        }
        if (magA == 0 || magB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(magA) * Math.sqrt(magB));
    }

    private static double[] normalize(double[] vec) {
        double mag = 0;
        for (double v : vec) {
            mag += v * v;
        }
        mag = Math.sqrt(mag);
        if (mag == 0) {
            return vec;
        }
        double[] out = new double[vec.length];
        for (int i = 0; i < vec.length; i++) {
            out[i] = vec[i] / mag;
        }
        return out;
    }

    private static double cosineVectors(double[] a, double[] b) {
        return cosine(a, b);
    }

    private static int sizeOf(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private static String joinList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(" ", list);
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
