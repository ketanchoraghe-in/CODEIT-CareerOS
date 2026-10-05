package com.codeit.careeros.dto.career;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CareerSkillRequest(
        @NotNull(message = "Skill is required")
        Long skillId,

        @NotNull(message = "Weight percent is required")
        @Min(value = 1, message = "Weight percent must be at least 1")
        @Max(value = 100, message = "Weight percent must be at most 100")
        Integer weightPercent,

        @NotBlank(message = "Required level is required")
        String requiredLevel,

        @Min(value = 1, message = "Target percent must be at least 1")
        @Max(value = 100, message = "Target percent must be at most 100")
        Integer targetPercent
) {
}
