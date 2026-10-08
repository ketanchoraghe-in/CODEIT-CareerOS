package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.service.CareerInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Sprint 3 student insights: assessment history, career gap analysis and
 * career readiness for the authenticated student's target career.
 * Read-only; Sprint 1/2 endpoints are untouched.
 */
@Tag(name = "Student Insights", description = "Assessment history, skill gaps and career readiness")
@RestController
@RequestMapping("/api/v1/students/me")
@RequiredArgsConstructor
public class StudentInsightController {

    private final CareerInsightService careerInsightService;

    @Operation(summary = "Assessment history of the current student, newest first")
    @GetMapping("/attempts")
    public ApiResponse<List<AttemptHistoryItem>> myAttempts(
            @RequestParam(required = false) Long assessmentId) {
        return ApiResponse.success(careerInsightService.myHistory(assessmentId));
    }

    @Operation(summary = "Career readiness and skill gaps for the current student's target career")
    @GetMapping("/readiness")
    public ApiResponse<ReadinessResponse> myReadiness() {
        return ApiResponse.success(careerInsightService.myReadiness());
    }

}
