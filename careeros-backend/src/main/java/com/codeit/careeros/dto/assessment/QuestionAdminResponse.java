package com.codeit.careeros.dto.assessment;

import java.time.Instant;
import java.util.List;

public record QuestionAdminResponse(
        Long id,
        Long assessmentId,
        Long skillId,
        String skillName,
        String questionText,
        String questionType,
        String difficulty,
        String explanation,
        Integer displayOrder,
        boolean active,
        Instant createdAt,
        List<QuestionOptionAdminResponse> options
) {
}
