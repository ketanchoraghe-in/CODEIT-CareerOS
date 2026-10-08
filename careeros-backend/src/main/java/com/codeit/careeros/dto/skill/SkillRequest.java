package com.codeit.careeros.dto.skill;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRequest(
        @NotBlank(message = "Skill name is required")
        @Size(max = 120, message = "Skill name must be at most 120 characters")
        String name,

        @NotBlank(message = "Category is required")
        String category,

        @Size(max = 300, message = "Description must be at most 300 characters")
        String description,

        Boolean active
) {
}
