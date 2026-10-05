package com.codeit.careeros.dto.assessment;

import java.time.Instant;
import java.util.List;

/**
 * Payload returned when an attempt starts or resumes. Questions are sent
 * WITHOUT correct-answer flags and WITHOUT explanations.
 */
public record AttemptStartResponse(
        Long attemptId,
        String attemptCode,
        Long assessmentId,
        String assessmentTitle,
        Long careerId,
        String careerName,
        int durationMinutes,
        int totalQuestions,
        Instant startedAt,
        Instant expiresAt,
        List<Long> answeredQuestionIds,
        List<AttemptQuestionResponse> questions
) {
}
