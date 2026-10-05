package com.codeit.careeros.dto.cv;

/** One skill detected in the CV text, mapped to the CareerOS skill master. */
public record DetectedSkillResponse(
        Long skillId,
        String skillName,
        String skillCategory) {
}
