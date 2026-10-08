package com.codeit.careeros.dto.insight;

/**
 * One submitted attempt of the current student, newest first.
 * Sprint 3 assessment history (docs section 15): every retake creates a new
 * attempt, so history is the list of SUBMITTED attempts, never overwritten.
 */
public record AttemptHistoryItem(
        Long attemptId,
        Long assessmentId,
        String assessmentTitle,
        Long careerId,
        String careerName,
        String status,
        Integer overallScore,
        Integer totalQuestions,
        Integer answeredCount,
        java.time.Instant startedAt,
        java.time.Instant submittedAt
) {
}
