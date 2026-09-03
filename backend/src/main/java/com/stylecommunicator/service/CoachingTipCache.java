package com.stylecommunicator.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;

/**
 * Caches coaching tips keyed on (power_dynamic, weakest_dimension, situation).
 * Safe because none of those three inputs are free-typed user text — situations
 * are drawn from a bounded, reused DB pool, so the same triple recurs across
 * many different users and sessions.
 *
 * Do NOT reuse this pattern for scoring or rewrites: those prompts embed the
 * user's own response verbatim and essentially never repeat.
 */
@Component
public class CoachingTipCache {

    private static final String PREFIX = "llm:coach:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    public CoachingTipCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Optional<String> get(String powerDynamic, String weakestDimension, String situation) {
        return Optional.ofNullable(redis.opsForValue().get(key(powerDynamic, weakestDimension, situation)));
    }

    public void put(String powerDynamic, String weakestDimension, String situation, String tip) {
        redis.opsForValue().set(key(powerDynamic, weakestDimension, situation), tip, TTL);
    }

    private String key(String powerDynamic, String weakestDimension, String situation) {
        String norm = (powerDynamic + "|" + weakestDimension + "|" + situation.trim().toLowerCase());
        return PREFIX + hash(norm);
    }

    private String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(text.hashCode()); // never let hashing itself break caching
        }
    }
}