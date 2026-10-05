package com.codeit.careeros.dto.assessment;

import java.util.List;

/** Lightweight autosave acknowledgement returned when an answer is saved. */
public record AttemptProgressResponse(
        Long attemptId,
        Integer answeredCount,
        Integer totalQuestions,
        List<Long> answeredQuestionIds
) {
}