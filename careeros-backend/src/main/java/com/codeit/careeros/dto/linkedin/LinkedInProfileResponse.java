package com.codeit.careeros.dto.linkedin;

import java.time.Instant;

public record LinkedInProfileResponse(
        Long profileId,
        String profileUrl,
        String headline,
        String about,
        String currentRole,
        String experienceText,
        String skillsText,
        String educationText,
        Instant updatedAt) {
}
