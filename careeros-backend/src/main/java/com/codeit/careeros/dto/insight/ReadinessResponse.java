package com.codeit.careeros.dto.insight;

import java.util.List;

/**
 * Career readiness of the current student for their target career
 * (Sprint 3, docs sections 16 and 17).
 * {@code readinessPercent} is the weight-aware share of required competency
 * achieved: {@code 100 * sum(weight * min(score, target)) / sum(weight * target)},
 * computed only from database values (framework weights/targets plus the
 * student's latest assessed scores). {@code readinessLevel} reuses the
 * existing {@code ResultLevel} scale.
 */
public record ReadinessResponse(
        Long targetCareerId,
        String targetCareerName,
        Boolean hasTarget,
        Integer readinessPercent,
        String readinessLevel,
        Integer totalSkills,
        Integer assessedSkills,
        Integer metSkills,
        Integer submittedAttempts,
        AttemptHistoryItem latestAttempt,
        List<SkillGapResponse> strengths,
        List<SkillGapResponse> improvements,
        List<SkillGapResponse> gaps
) {
}
