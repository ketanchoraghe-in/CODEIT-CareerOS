package com.codeit.careeros.dto.project;

/** One required skill of a project, enriched with the student's live gap. */
public record ProjectSkillResponse(
        Long skillId,
        String skillName,
        String skillCategory,
        Integer targetPercent,
        Integer scorePercent,
        Integer gapPercent,
        Boolean assessed) {
}
