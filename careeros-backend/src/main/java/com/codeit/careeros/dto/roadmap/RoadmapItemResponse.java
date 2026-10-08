package com.codeit.careeros.dto.roadmap;

/**
 * One roadmap step enriched with the student's live skill gap (when the step
 * trains a framework skill) and the student's own progress status.
 */
public record RoadmapItemResponse(
        Long itemId,
        String title,
        String description,
        String learningGoal,
        Integer estimatedHours,
        Integer displayOrder,
        Long skillId,
        String skillName,
        String skillCategory,
        Integer targetPercent,
        Integer scorePercent,
        Integer gapPercent,
        Boolean assessed,
        String status) {
}
