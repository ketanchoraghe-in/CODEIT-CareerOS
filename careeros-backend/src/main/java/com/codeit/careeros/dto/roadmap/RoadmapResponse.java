package com.codeit.careeros.dto.roadmap;

import java.util.List;

/**
 * Personalized roadmap of the current student for their target career.
 * Without a target career this is an empty payload ({@code hasTarget=false})
 * so the UI can render its career-selection empty state.
 */
public record RoadmapResponse(
        Long careerId,
        String careerName,
        Boolean hasTarget,
        Integer progressPercent,
        Integer totalItems,
        Integer completedItems,
        Integer inProgressItems,
        List<RoadmapPhaseResponse> phases) {
}
