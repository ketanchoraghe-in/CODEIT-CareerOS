package com.codeit.careeros.dto.assessment;

import java.time.Instant;
import java.util.List;

/** Admin view of an assessment including its full question bank. */
public record AssessmentAdminDetailResponse(
        Long id,
        Long careerId,
        String careerName,
        String title,
        String description,
        Integer durationMinutes,
        boolean published,
        long questionCount,
        Instant createdAt,
        Instant updatedAt,
        List<QuestionAdminResponse> questions
) {
}