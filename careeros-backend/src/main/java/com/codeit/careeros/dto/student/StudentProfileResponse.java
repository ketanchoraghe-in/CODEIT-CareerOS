package com.codeit.careeros.dto.student;

import java.time.Instant;
import java.time.LocalDate;

public record StudentProfileResponse(
        Long id,
        String studentId,
        String fullName,
        String email,
        String mobile,
        LocalDate dateOfBirth,
        String college,
        String degree,
        String branch,
        Integer graduationYear,
        Integer semester,
        String location,
        String githubUrl,
        String linkedinUrl,
        String portfolioUrl,
        String profilePhotoUrl,
        Long targetCareerId,
        String targetCareerName,
        Instant createdAt,
        Instant updatedAt
) {
}