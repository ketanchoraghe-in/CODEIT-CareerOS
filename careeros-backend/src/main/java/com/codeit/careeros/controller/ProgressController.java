package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.progress.ProgressResponse;
import com.codeit.careeros.service.ProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consolidated student progress: one authenticated, owner-scoped endpoint
 * aggregating the existing readiness/roadmap/project/CV/LinkedIn services.
 */
@Tag(name = "Progress", description = "Consolidated student progress dashboard")
@RestController
@RequestMapping("/api/v1/progress")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class ProgressController {

    private final ProgressService progressService;

    @Operation(summary = "Consolidated progress of the current student")
    @GetMapping("/me")
    public ApiResponse<ProgressResponse> myProgress() {
        return ApiResponse.success(progressService.myProgress());
    }
}
