package com.codeit.careeros.dto.assessment;

import java.time.Instant;
import java.util.List;

/**
 * Current state of an attempt. While IN_PROGRESS it carries questions and
 * answered ids; once SUBMITTED it carries the result instead.
 */
public record AttemptStateResponse(
        Long attemptId,
        String attemptCode,
        Long assessmentId,
        String assessmentTitle,
        Long careerId,
        String careerName,
        String status,
        Integer durationMinutes,
        Integer totalQuestions,
        Integer answeredCount,
        Integer overallScore,
        Instant startedAt,
        Instant expiresAt,
        Instant submittedAt,
        List<Long> answeredQuestionIds,
        List<AttemptQuestionResponse> questions,
        List<SkillScoreResponse> skills
) {
}
