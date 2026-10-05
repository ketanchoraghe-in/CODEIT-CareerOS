package com.codeit.careeros.dto.linkedin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Student-supplied LinkedIn data: the public profile URL plus the visible
 * sections copied from the profile (LinkedIn has no supported personal
 * profile API, so nothing is fetched automatically).
 */
public record LinkedInProfileRequest(
        @NotBlank(message = "LinkedIn profile URL is required")
        @Size(max = 500, message = "LinkedIn profile URL must be at most 500 characters")
        String profileUrl,

        @Size(max = 220, message = "Headline must be at most 220 characters")
        String headline,

        @Size(max = 3000, message = "About must be at most 3000 characters")
        String about,

        @Size(max = 180, message = "Current role must be at most 180 characters")
        String currentRole,

        @Size(max = 5000, message = "Experience must be at most 5000 characters")
        String experienceText,

        @Size(max = 2000, message = "Skills must be at most 2000 characters")
        String skillsText,

        @Size(max = 2000, message = "Education must be at most 2000 characters")
        String educationText) {
}
