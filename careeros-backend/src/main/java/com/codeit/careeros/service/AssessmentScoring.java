package com.codeit.careeros.service;

import java.util.List;

/**
 * Pure scoring functions for the assessment engine (docs sections 13 and 67).
 * Kept free of Spring dependencies so it is trivially unit-testable.
 */
public final class AssessmentScoring {

    private AssessmentScoring() {
    }

    /** Skill score = share of that skill's questions answered correctly. */
    public static int skillScore(int correctCount, int questionCount) {
        if (questionCount <= 0) {
            return 0;
        }
        return Math.round((correctCount * 100.0f) / questionCount);
    }

    /**
     * Overall score = weighted mean of the per-skill scores, using the
     * competency weights of the career. Weights are normalized across the
     * skills present in the assessment; if all weights are zero, skills are
     * weighted equally.
     */
    public static int overallScore(List<WeightedScore> scores) {
        if (scores == null || scores.isEmpty()) {
            return 0;
        }
        int totalWeight = scores.stream().mapToInt(WeightedScore::weightPercent).sum();
        if (totalWeight <= 0) {
            return Math.round((float) scores.stream().mapToInt(WeightedScore::scorePercent).average().orElse(0));
        }
        double weighted = scores.stream()
                .mapToDouble(s -> s.weightPercent() * (double) s.scorePercent())
                .sum();
        return Math.round((float) (weighted / totalWeight));
    }

    /** Importance + score pair used by {@link #overallScore(List)}. */
    public record WeightedScore(int weightPercent, int scorePercent) {
    }
}
