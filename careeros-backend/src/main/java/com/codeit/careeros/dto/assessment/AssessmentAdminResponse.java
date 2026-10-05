package com.codeit.careeros.dto.assessment;

import java.time.Instant;

public record AssessmentAdminResponse(
        Long id,
        Long careerId,
        String careerName,
        String title,
        String description,
        Integer durationMinutes,
        boolean published,
        long questionCount,
        Instant createdAt
) {
}
