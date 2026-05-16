package com.stylecommunicator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SituationBankService {

    private static final List<String> EMOTIONAL_CONTEXTS = List.of(
            "calm but firm", "under pressure", "frustrated but professional",
            "empathetic", "confident", "cautious", "enthusiastic", "skeptical"
    );

    private static final List<String> REQUIRED_WORDS = List.of(
            "however", "therefore", "specifically", "clearly", "respectfully",
            "directly", "together", "priority", "understand", "commit",
            "align", "decision", "timeline", "support", "outcome"
    );

    private final ObjectMapper objectMapper;
    private final Map<String, List<String>> situationsByPower = new HashMap<>();
    private final Map<UUID, Deque<String>> recentByUser = new ConcurrentHashMap<>();

    public SituationBankService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws Exception {
        try (InputStream in = new ClassPathResource("data/situations.json").getInputStream()) {
            Map<String, List<String>> loaded = objectMapper.readValue(in, new TypeReference<>() {});
            situationsByPower.putAll(loaded);
        }
    }

    public String pickSituation(String powerDynamic, UUID userId) {
        String key = powerDynamic != null ? powerDynamic.toUpperCase() : "EQUAL";
        List<String> bank = situationsByPower.getOrDefault(key, situationsByPower.get("EQUAL"));
        Deque<String> recent = recentByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        List<String> candidates = new ArrayList<>(bank);
        candidates.removeAll(recent);
        if (candidates.isEmpty()) {
            candidates = new ArrayList<>(bank);
        }
        String chosen = candidates.get(new Random().nextInt(candidates.size()));
        recent.addLast(chosen);
        while (recent.size() > 5) {
            recent.removeFirst();
        }
        return chosen;
    }

    public String pickRequiredWord() {
        return REQUIRED_WORDS.get(new Random().nextInt(REQUIRED_WORDS.size()));
    }

    public String pickEmotionalContext() {
        return EMOTIONAL_CONTEXTS.get(new Random().nextInt(EMOTIONAL_CONTEXTS.size()));
    }
}
