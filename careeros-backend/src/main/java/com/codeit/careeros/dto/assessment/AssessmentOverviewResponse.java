package com.codeit.careeros.dto.assessment;

public record AssessmentOverviewResponse(
        Long assessmentId,
        Long careerId,
        String careerName,
        String title,
        String description,
        Integer durationMinutes,
        long questionCount
) {
}
