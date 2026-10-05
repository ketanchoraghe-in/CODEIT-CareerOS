package com.codeit.careeros.dto.career;

public record CareerSkillResponse(
        Long skillId,
        String skillName,
        String skillCategory,
        int weightPercent,
        String requiredLevel,
        int targetPercent
) {
}
