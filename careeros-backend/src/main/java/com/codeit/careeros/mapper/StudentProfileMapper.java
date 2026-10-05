package com.codeit.careeros.mapper;

import com.codeit.careeros.dto.student.StudentProfileRequest;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.StudentProfile;

public final class StudentProfileMapper {

    private StudentProfileMapper() {
    }

    public static StudentProfileResponse toResponse(StudentProfile profile) {
        if (profile == null) {
            return null;
        }
        return new StudentProfileResponse(
                profile.getId(),
                profile.getStudentId(),
                profile.getFullName(),
                profile.getUser().getEmail(),
                profile.getMobile(),
                profile.getDateOfBirth(),
                profile.getCollege(),
                profile.getDegree(),
                profile.getBranch(),
                profile.getGraduationYear(),
                profile.getSemester(),
                profile.getLocation(),
                profile.getGithubUrl(),
                profile.getLinkedinUrl(),
                profile.getPortfolioUrl(),
                profile.getProfilePhotoUrl(),
                profile.getTargetCareer() != null ? profile.getTargetCareer().getId() : null,
                profile.getTargetCareer() != null ? profile.getTargetCareer().getName() : null,
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    public static void applyRequest(StudentProfile profile, StudentProfileRequest request) {
        profile.setFullName(request.fullName());
        profile.setMobile(request.mobile());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setCollege(request.college());
        profile.setDegree(request.degree());
        profile.setBranch(request.branch());
        profile.setGraduationYear(request.graduationYear());
        profile.setSemester(request.semester());
        profile.setLocation(request.location());
        profile.setGithubUrl(request.githubUrl());
        profile.setLinkedinUrl(request.linkedinUrl());
        profile.setPortfolioUrl(request.portfolioUrl());
    }
}