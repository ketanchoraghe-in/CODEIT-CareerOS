package com.codeit.careeros.dto.assessment;

import java.time.Instant;
import java.util.List;

/** Graded result of a submitted attempt (docs sections 13 and 67). */
public record AttemptResultResponse(
        Long attemptId,
        Long assessmentId,
        String assessmentTitle,
        Long careerId,
        String careerName,
        String status,
        Instant startedAt,
        Instant submittedAt,
        Integer totalQuestions,
        Integer answeredCount,
        Integer overallScore,
        List<SkillScoreResponse> skills
) {
}
