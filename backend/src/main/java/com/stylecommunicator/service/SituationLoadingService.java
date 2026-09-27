package com.stylecommunicator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Controls situation loading strategy for the application.
 * 
 * Prevents delays when users practice by allowing admin to control
 * when situations are loaded (cache warm-up, batch loading, etc.)
 * 
 * Two modes:
 * 1. ON_DEMAND: Load situations when user requests (fast for user, may have delay)
 * 2. BACKGROUND: Load situations in background, available instantly (better UX)
 */
@Service
public class SituationLoadingService {

    private static final Logger log = LoggerFactory.getLogger(SituationLoadingService.class);

    public enum LoadingStrategy {
        ON_DEMAND,      // Load when user needs it (may cause delay)
        BACKGROUND      // Pre-load in background (better UX, uses resources)
    }

    // Admin can change this
    private volatile LoadingStrategy strategy = LoadingStrategy.ON_DEMAND;

    /**
     * Get current loading strategy.
     */
    public LoadingStrategy getStrategy() {
        return strategy;
    }

    /**
     * Update loading strategy (ADMIN ONLY).
     */
    public void setStrategy(LoadingStrategy newStrategy) {
        LoadingStrategy old = this.strategy;
        this.strategy = newStrategy;
        log.info("Situation loading strategy changed from {} to {}", old, newStrategy);
    }

    /**
     * Check if we should pre-load situations in background.
     */
    public boolean shouldPreLoadInBackground() {
        return strategy == LoadingStrategy.BACKGROUND;
    }

    /**
     * Check if we should delay situation loading until user requests.
     */
    public boolean shouldLoadOnDemand() {
        return strategy == LoadingStrategy.ON_DEMAND;
    }

    public String getStrategyInfo() {
        return switch (strategy) {
            case ON_DEMAND -> "Situations loaded on-demand when user practices (may have short delay)";
            case BACKGROUND -> "Situations pre-loaded in background (instant, uses more resources)";
        };
    }
}
