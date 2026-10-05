package com.codeit.careeros.dto.career;

import java.time.Instant;
import java.util.List;

public record CareerResponse(
        Long id,
        String name,
        String description,
        String category,
        String difficultyLevel,
        boolean published,
        List<CareerSkillResponse> skills,
        Instant createdAt,
        Instant updatedAt
) {
}
