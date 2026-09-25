package com.yagay.ypower.model;

public enum DetectionHitState {
    CHECKED,
    HIT,
    NOT_HIT,
    UNKNOWN;

    public static DetectionHitState from(String value, boolean legacyMatched) {
        if (value != null && !value.isBlank()) {
            try {
                return valueOf(value);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return legacyMatched ? HIT : UNKNOWN;
    }
}
