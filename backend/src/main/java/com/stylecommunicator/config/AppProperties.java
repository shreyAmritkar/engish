package com.stylecommunicator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String corsOrigins = "http://localhost:3000";
    private int dailySessionCap = 10;
    private int styleCacheTtlMinutes = 60;
    private double similarityThreshold = 1.0;
    // false locally (HTTP), true on production (HTTPS)
    private boolean cookieSecure = false;

    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
    public int getDailySessionCap() { return dailySessionCap; }
    public void setDailySessionCap(int dailySessionCap) { this.dailySessionCap = dailySessionCap; }
    public int getStyleCacheTtlMinutes() { return styleCacheTtlMinutes; }
    public void setStyleCacheTtlMinutes(int styleCacheTtlMinutes) { this.styleCacheTtlMinutes = styleCacheTtlMinutes; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public boolean isCookieSecure() { return cookieSecure; }
    public void setCookieSecure(boolean cookieSecure) { this.cookieSecure = cookieSecure; }
}

