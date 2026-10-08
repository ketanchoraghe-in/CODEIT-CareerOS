package com.codeit.careeros.dto.student;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record StudentProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must be at most 120 characters")
        String fullName,

        @Size(max = 20, message = "Mobile must be at most 20 characters")
        @Pattern(regexp = "^$|^[0-9+\\- ]{6,20}$", message = "Mobile number is invalid")
        String mobile,

        LocalDate dateOfBirth,

        @Size(max = 150, message = "College must be at most 150 characters")
        String college,

        @Size(max = 100, message = "Degree must be at most 100 characters")
        String degree,

        @Size(max = 100, message = "Branch must be at most 100 characters")
        String branch,

        @Min(value = 2000, message = "Graduation year must be at least 2000")
        @Max(value = 2100, message = "Graduation year must be at most 2100")
        Integer graduationYear,

        @Min(value = 1, message = "Semester must be at least 1")
        @Max(value = 12, message = "Semester must be at most 12")
        Integer semester,

        @Size(max = 150, message = "Location must be at most 150 characters")
        String location,

        @Size(max = 250, message = "GitHub URL must be at most 250 characters")
        String githubUrl,

        @Size(max = 250, message = "LinkedIn URL must be at most 250 characters")
        String linkedinUrl,

        @Size(max = 250, message = "Portfolio URL must be at most 250 characters")
        String portfolioUrl
) {
}