package com.codeit.careeros.dto.project;

import java.util.List;

public record ProjectResponse(
        Long projectId,
        String title,
        String description,
        String difficulty,
        Integer estimatedWeeks,
        Integer displayOrder,
        String status,
        List<ProjectSkillResponse> skills) {
}
