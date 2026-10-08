package com.codeit.careeros.dto.roadmap;

import java.util.List;

public record RoadmapPhaseResponse(
        Long phaseId,
        String title,
        String description,
        Integer displayOrder,
        Integer durationDays,
        Integer totalItems,
        Integer completedItems,
        List<RoadmapItemResponse> items) {
}
