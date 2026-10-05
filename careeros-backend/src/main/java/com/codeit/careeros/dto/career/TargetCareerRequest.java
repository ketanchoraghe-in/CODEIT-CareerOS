package com.codeit.careeros.dto.career;

import jakarta.validation.constraints.NotNull;

public record TargetCareerRequest(
        @NotNull(message = "Career is required")
        Long careerId
) {
}
