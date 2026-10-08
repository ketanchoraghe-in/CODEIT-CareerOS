package com.codeit.careeros.dto.assessment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record QuestionRequest(
        @NotNull(message = "Assessment is required")
        Long assessmentId,

        @NotNull(message = "Skill is required")
        Long skillId,

        @NotBlank(message = "Question text is required")
        @Size(max = 1000, message = "Question text must be at most 1000 characters")
        String questionText,

        @NotBlank(message = "Difficulty is required")
        String difficulty,

        @Size(max = 600, message = "Explanation must be at most 600 characters")
        String explanation,

        String questionType,

        Boolean active,

        Integer displayOrder,

        @Valid
        @NotNull(message = "Options are required")
        List<QuestionOptionRequest> options
) {
}
