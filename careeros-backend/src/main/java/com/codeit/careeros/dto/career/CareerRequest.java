package com.codeit.careeros.dto.career;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Create/update payload for the career master. Skills with weights form the
 * competency framework; weights must sum to 100 (validated in the service).
 */
public record CareerRequest(
        @NotBlank(message = "Career name is required")
        @Size(max = 140, message = "Career name must be at most 140 characters")
        String name,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description,

        @NotBlank(message = "Category is required")
        String category,

        @NotBlank(message = "Difficulty level is required")
        String difficultyLevel,

        Boolean published,

        @Valid
        List<CareerSkillRequest> skills
) {
}
