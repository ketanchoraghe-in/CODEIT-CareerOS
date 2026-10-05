package com.codeit.careeros.dto.assessment;

import jakarta.validation.constraints.NotNull;

public record AnswerRequest(
        @NotNull(message = "Question is required")
        Long questionId,

        @NotNull(message = "Selected option is required")
        Long optionId
) {
}
