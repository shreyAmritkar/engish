package com.stylecommunicator.util;

import java.util.List;

public final class StatsUtil {

    private StatsUtil() {}

    public static double linearRegressionSlope(List<Double> values) {
        if (values == null || values.size() < 2) {
            return 0;
        }
        int n = values.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = values.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denom = n * sumX2 - sumX * sumX;
        if (denom == 0) {
            return 0;
        }
        return (n * sumXY - sumX * sumY) / denom;
    }

    public static double variance(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream()
                .mapToDouble(v -> (v - mean) * (v - mean))
                .average()
                .orElse(0);
    }

    public static double stdDev(List<Double> values) {
        return Math.sqrt(variance(values));
    }

    public static double weightedAverage(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double weightedSum = 0;
        double weightTotal = 0;
        for (int i = 0; i < values.size(); i++) {
            double weight = i + 1;
            weightedSum += values.get(i) * weight;
            weightTotal += weight;
        }
        return weightTotal == 0 ? 0 : weightedSum / weightTotal;
    }
}
