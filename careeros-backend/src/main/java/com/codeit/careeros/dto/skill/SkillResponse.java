package com.codeit.careeros.dto.skill;

import java.time.Instant;

public record SkillResponse(
        Long id,
        String name,
        String category,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
