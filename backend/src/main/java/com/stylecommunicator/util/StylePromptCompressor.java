package com.stylecommunicator.util;

import com.stylecommunicator.entity.StyleProfile;

import java.util.stream.Collectors;

public final class StylePromptCompressor {

    private StylePromptCompressor() {}

    public static String compress(StyleProfile profile) {
        String power = abbrev(profile.getPowerDynamic());
        int formal = profile.getFormalityLevel() != null ? profile.getFormalityLevel() : 5;
        String structure = abbrev(profile.getSentenceStructure());
        String patterns = join(profile.getKeyPatterns());
        String avoid = join(profile.getAvoidPatterns());
        return "Style:" + power + "|F" + formal + "|" + structure
                + "|pat:" + patterns + "|avoid:" + avoid;
    }

    private static String abbrev(String value) {
        if (value == null) return "UNK";
        return switch (value.toUpperCase()) {
            case "DOMINANT" -> "DOM";
            case "EQUAL" -> "EQ";
            case "SUBMISSIVE" -> "SUB";
            case "SHORT_PUNCHY" -> "SP";
            case "LONG_COMPLEX" -> "LC";
            case "MIXED" -> "MX";
            default -> value.length() > 3 ? value.substring(0, 3).toUpperCase() : value.toUpperCase();
        };
    }

    private static String join(java.util.List<String> items) {
        if (items == null || items.isEmpty()) return "-";
        return items.stream()
                .map(s -> s.replaceAll("\\s+", ""))
                .limit(5)
                .collect(Collectors.joining(","));
    }
}
