package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.roadmap.RoadmapItemResponse;
import com.codeit.careeros.dto.roadmap.RoadmapItemStatusRequest;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;
import com.codeit.careeros.service.RoadmapService;
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
 * Sprint 4 student roadmap: personalized phases/steps for the authenticated
 * student's target career plus per-item progress tracking.
 * Read structure from the database; only progress rows are written.
 */
@Tag(name = "Roadmap", description = "Personalized career roadmap and item progress")
@RestController
@RequestMapping("/api/v1/roadmaps")
@RequiredArgsConstructor
public class RoadmapController {

    private final RoadmapService roadmapService;

    @Operation(summary = "Personalized roadmap of the current student for their target career")
    @GetMapping("/my")
    public ApiResponse<RoadmapResponse> myRoadmap() {
        return ApiResponse.success(roadmapService.myRoadmap());
    }

    @Operation(summary = "Mark a roadmap item NOT_STARTED, IN_PROGRESS or COMPLETED")
    @PutMapping("/items/{itemId}/status")
    public ApiResponse<RoadmapItemResponse> updateItemStatus(
            @PathVariable Long itemId, @Valid @RequestBody RoadmapItemStatusRequest request) {
        return ApiResponse.success(roadmapService.updateItemStatus(itemId, request.status()));
    }
}
