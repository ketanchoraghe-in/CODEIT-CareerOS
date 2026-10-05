package com.codeit.careeros.common.enums;

/**
 * Per-skill assessment output levels (docs section 13):
 * STRONG, GOOD, DEVELOPING, NEEDS_IMPROVEMENT, PRIORITY_GAP.
 */
public enum ResultLevel {
    STRONG,
    GOOD,
    DEVELOPING,
    NEEDS_IMPROVEMENT,
    PRIORITY_GAP;

    /** Maps a 0-100 score percent to the standardized output level. */
    public static ResultLevel fromScore(int scorePercent) {
        if (scorePercent >= 80) {
            return STRONG;
        }
        if (scorePercent >= 65) {
            return GOOD;
        }
        if (scorePercent >= 50) {
            return DEVELOPING;
        }
        if (scorePercent >= 35) {
            return NEEDS_IMPROVEMENT;
        }
        return PRIORITY_GAP;
    }
}
