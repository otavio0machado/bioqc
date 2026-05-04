package com.bioqc.util;

public final class NumericUtils {

    private NumericUtils() {
    }

    public static double calculateCv(Double value, Double targetValue) {
        if (value == null || targetValue == null || targetValue == 0D) {
            return 0D;
        }
        return (Math.abs(value - targetValue) / Math.abs(targetValue)) * 100D;
    }

    public static double calculateZScore(Double value, Double targetValue, Double targetSd) {
        if (value == null || targetValue == null || targetSd == null || targetSd == 0D) {
            return 0D;
        }
        return (value - targetValue) / targetSd;
    }

    public static double defaultIfNull(Double value) {
        return value == null ? 0D : value;
    }
}
