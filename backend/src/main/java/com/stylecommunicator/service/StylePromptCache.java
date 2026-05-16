package com.stylecommunicator.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StylePromptCache {

    private record CacheEntry(String compressedPrompt, Instant expiresAt) {}

    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final com.stylecommunicator.config.AppProperties appProperties;

    public StylePromptCache(com.stylecommunicator.config.AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    public Optional<String> get(UUID profileId) {
        CacheEntry entry = cache.get(profileId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(profileId);
            return Optional.empty();
        }
        return Optional.of(entry.compressedPrompt());
    }

    public void put(UUID profileId, String compressedPrompt) {
        cache.put(profileId, new CacheEntry(
                compressedPrompt,
                Instant.now().plusSeconds(appProperties.getStyleCacheTtlMinutes() * 60L)
        ));
    }

    public void invalidate(UUID profileId) {
        cache.remove(profileId);
    }
}
