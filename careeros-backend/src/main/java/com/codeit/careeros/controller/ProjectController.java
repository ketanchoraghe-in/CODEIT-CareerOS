package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.project.ProjectListResponse;
import com.codeit.careeros.dto.project.ProjectResponse;
import com.codeit.careeros.dto.project.ProjectStatusRequest;
import com.codeit.careeros.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 4 student projects: recommendations for the authenticated student's
 * target career plus per-project progress tracking.
 */
@Tag(name = "Projects", description = "Recommended projects and project progress")
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @Operation(summary = "Recommended projects of the current student for their target career")
    @GetMapping("/recommended")
    public ApiResponse<ProjectListResponse> recommended() {
        return ApiResponse.success(projectService.recommended());
    }

    @Operation(summary = "Mark a project NOT_STARTED, IN_PROGRESS or COMPLETED")
    @PutMapping("/{projectId}/status")
    public ApiResponse<ProjectResponse> updateStatus(
            @PathVariable Long projectId, @Valid @RequestBody ProjectStatusRequest request) {
        return ApiResponse.success(projectService.updateStatus(projectId, request.status()));
    }
}
