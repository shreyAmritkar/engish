package com.stylecommunicator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simplified coaching tip cache using in-memory storage.
 * 
 * Caches coaching tips keyed on (power_dynamic, weakest_dimension, situation).
 * Uses LRU (Least Recently Used) eviction with max 1000 entries.
 * 
 * Safe because none of the three inputs are free-typed user text — situations
 * are drawn from a bounded, reused DB pool, so the same triple recurs across
 * many different users and sessions.
 * 
 * Note: Not suitable for distributed systems. For multi-instance deployments,
 * consider external cache (Redis/Memcached) or database cache.
 */
@Component
public class CoachingTipCache {

    private static final Logger log = LoggerFactory.getLogger(CoachingTipCache.class);
    private static final int MAX_ENTRIES = 1000;

    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final LinkedList<String> accessOrder = new LinkedList<>();

    public Optional<String> get(String powerDynamic, String weakestDimension, String situation) {
        String key = buildKey(powerDynamic, weakestDimension, situation);
        String value = cache.get(key);
        
        if (value != null) {
            // Update access order for LRU
            accessOrder.remove(key);
            accessOrder.addLast(key);
        }
        
        return Optional.ofNullable(value);
    }

    public void put(String powerDynamic, String weakestDimension, String situation, String tip) {
        String key = buildKey(powerDynamic, weakestDimension, situation);
        
        // Evict LRU entry if at capacity
        if (cache.size() >= MAX_ENTRIES && !cache.containsKey(key)) {
            String lruKey = accessOrder.removeFirst();
            cache.remove(lruKey);
            log.debug("Evicted LRU coaching tip cache entry");
        }
        
        cache.put(key, tip);
        accessOrder.remove(key);
        accessOrder.addLast(key);
    }

    private String buildKey(String powerDynamic, String weakestDimension, String situation) {
        return powerDynamic + "|" + weakestDimension + "|" + situation.trim().toLowerCase();
    }

    public int getCacheSize() {
        return cache.size();
    }

    public void clear() {
        cache.clear();
        accessOrder.clear();
        log.info("Coaching tip cache cleared");
    }
}