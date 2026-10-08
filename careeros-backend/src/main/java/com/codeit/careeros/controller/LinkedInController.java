package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.linkedin.LinkedInAnalysisResponse;
import com.codeit.careeros.dto.linkedin.LinkedInProfileRequest;
import com.codeit.careeros.dto.linkedin.LinkedInProfileResponse;
import com.codeit.careeros.service.LinkedInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 6 student LinkedIn: URL plus manually imported profile sections
 * (no scraping — LinkedIn forbids it and offers no personal-profile API),
 * plus analysis against the target career, assessments and CV.
 * Everything is scoped to the authenticated student.
 */
@Tag(name = "LinkedIn", description = "LinkedIn profile import and analysis")
@RestController
@RequestMapping("/api/v1/linkedin")
@RequiredArgsConstructor
public class LinkedInController {

    private final LinkedInService linkedInService;

    @Operation(summary = "Save or update the current student's LinkedIn data")
    @PutMapping
    public ApiResponse<LinkedInProfileResponse> save(@Valid @RequestBody LinkedInProfileRequest request) {
        return ApiResponse.success("LinkedIn profile saved", linkedInService.save(request));
    }

    @Operation(summary = "Current student's LinkedIn data, or null when none was saved yet")
    @GetMapping("/me")
    public ApiResponse<LinkedInProfileResponse> myProfile() {
        return ApiResponse.success(linkedInService.myProfile());
    }

    @Operation(summary = "Analysis of the current student's LinkedIn presence")
    @GetMapping("/analysis")
    public ApiResponse<LinkedInAnalysisResponse> analyze() {
        return ApiResponse.success(linkedInService.analyze());
    }
}
