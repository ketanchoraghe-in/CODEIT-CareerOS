package com.codeit.careeros.dto.career;

public record CareerSummaryResponse(
        Long id,
        String name,
        String description,
        String category,
        String difficultyLevel,
        boolean published,
        long skillCount
) {
}
