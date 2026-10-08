package com.codeit.careeros.dto.admin;

import java.time.Instant;

/** Admin read-only view of one project in the catalog (published or not). */
public record AdminProjectResponse(
        Long id,
        String title,
        String description,
        Long careerId,
        String careerName,
        String difficulty,
        int estimatedWeeks,
        int displayOrder,
        boolean published,
        Instant createdAt) {
}
