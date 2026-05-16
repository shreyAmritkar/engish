package com.stylecommunicator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String corsOrigins = "http://localhost:3000";
    private int dailySessionCap = 10;
    private int styleCacheTtlMinutes = 60;
    private double similarityThreshold = 0.85;

    public String getCorsOrigins() { return corsOrigins; }
    public void setCorsOrigins(String corsOrigins) { this.corsOrigins = corsOrigins; }
    public int getDailySessionCap() { return dailySessionCap; }
    public void setDailySessionCap(int dailySessionCap) { this.dailySessionCap = dailySessionCap; }
    public int getStyleCacheTtlMinutes() { return styleCacheTtlMinutes; }
    public void setStyleCacheTtlMinutes(int styleCacheTtlMinutes) { this.styleCacheTtlMinutes = styleCacheTtlMinutes; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
}
