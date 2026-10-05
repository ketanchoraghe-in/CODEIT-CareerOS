package com.codeit.careeros.dto.admin;

import java.util.List;

/** Admin read-only view of one career's roadmap template (phases + items). */
public record AdminRoadmapTemplateResponse(
        Long careerId,
        String careerName,
        boolean careerPublished,
        int phaseCount,
        int itemCount,
        int totalEstimatedHours,
        List<TemplatePhase> phases) {

    public record TemplatePhase(
            Long id,
            String title,
            String description,
            int displayOrder,
            int durationDays,
            List<TemplateItem> items) {
    }

    public record TemplateItem(
            Long id,
            String title,
            String description,
            String learningGoal,
            int displayOrder,
            int estimatedHours,
            Long skillId,
            String skillName) {
    }
}
