package com.stylecommunicator.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class GrammarCheckerService {

    private static final Pattern COMMA_SPLICE = Pattern.compile("[a-z]{3,}\\s*,\\s*[a-z]{3,}\\s+(is|are|was|were|have|has)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOWERCASE_START = Pattern.compile("(^|[.!?]\\s+)[a-z]");
    private static final Pattern DOUBLE_SPACE = Pattern.compile("  +");
    private static final Pattern EXCESSIVE_EXCLAMATION = Pattern.compile("!{2,}");

    public List<String> check(String text) {
        List<String> notes = new ArrayList<>();
        if (text == null || text.isBlank()) {
            notes.add("Response is empty.");
            return notes;
        }
        if (Character.isLowerCase(text.trim().charAt(0))) {
            notes.add("Start sentences with a capital letter.");
        }
        if (COMMA_SPLICE.matcher(text).find()) {
            notes.add("Possible comma splice — consider splitting into two sentences.");
        }
        if (DOUBLE_SPACE.matcher(text).find()) {
            notes.add("Remove extra spaces between words.");
        }
        if (EXCESSIVE_EXCLAMATION.matcher(text).find()) {
            notes.add("Multiple exclamation marks can reduce professionalism.");
        }
        if (!text.trim().matches(".*[.!?]$")) {
            notes.add("End your response with proper punctuation.");
        }
        if (text.length() > 20 && !text.contains(",")) {
            notes.add("Long responses often benefit from a comma for clarity.");
        }
        return notes;
    }
}
