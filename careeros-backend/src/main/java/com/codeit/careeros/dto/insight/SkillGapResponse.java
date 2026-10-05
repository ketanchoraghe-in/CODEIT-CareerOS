package com.codeit.careeros.dto.insight;

/**
 * One row of the career gap analysis (Sprint 3, docs section 16).
 * Compares the student's latest assessed score for a skill against the live
 * competency framework of the target career (weight, required level, target).
 * All thresholds come from the database; {@code level} reuses the existing
 * {@code ResultLevel} scale and {@code metTarget} is a direct score-vs-target
 * comparison, so no business rules are hardcoded here.
 */
public record SkillGapResponse(
        Long skillId,
        String skillName,
        String skillCategory,
        Integer weightPercent,
        String requiredLevel,
        Integer targetPercent,
        Integer scorePercent,
        Integer correctCount,
        Integer questionCount,
        Integer gapPercent,
        Boolean metTarget,
        Boolean assessed,
        String level,
        Long sourceAttemptId
) {
}
