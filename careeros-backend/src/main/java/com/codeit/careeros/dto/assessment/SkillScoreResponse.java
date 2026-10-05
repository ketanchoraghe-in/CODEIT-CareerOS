package com.codeit.careeros.dto.assessment;

public record SkillScoreResponse(
        Long skillId,
        String skillName,
        String skillCategory,
        Integer weightPercent,
        Integer targetPercent,
        Integer questionCount,
        Integer correctCount,
        Integer scorePercent,
        String level
) {
}
