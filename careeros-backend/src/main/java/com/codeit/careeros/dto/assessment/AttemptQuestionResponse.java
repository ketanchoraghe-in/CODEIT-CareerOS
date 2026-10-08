package com.codeit.careeros.dto.assessment;

import java.util.List;

public record AttemptQuestionResponse(
        Long questionId,
        int displayOrder,
        String skillName,
        String skillCategory,
        String questionText,
        String difficulty,
        /** Previously autosaved selection, so resumes restore the student's picks. */
        Long selectedOptionId,
        List<OptionResponse> options
) {
}
