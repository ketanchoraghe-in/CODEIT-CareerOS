package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.assessment.AnswerRequest;
import com.codeit.careeros.dto.assessment.AssessmentOverviewResponse;
import com.codeit.careeros.dto.assessment.AttemptProgressResponse;
import com.codeit.careeros.dto.assessment.AttemptResultResponse;
import com.codeit.careeros.dto.assessment.AttemptStartResponse;
import com.codeit.careeros.dto.assessment.AttemptStateResponse;
import com.codeit.careeros.security.SecurityUtils;
import com.codeit.careeros.service.AssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/** Student assessment runtime (Sprint 2 assessment engine). */
@Tag(name = "Assessments", description = "Student assessment runtime")
@RestController
@RequestMapping("/api/v1/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @Operation(summary = "List published assessments for a career")
    @GetMapping("/{careerId}/available")
    public ApiResponse<List<AssessmentOverviewResponse>> listForCareer(@PathVariable Long careerId) {
        return ApiResponse.success(assessmentService.listPublishedForCareer(careerId));
    }

    @Operation(summary = "Start or resume an assessment attempt")
    @PostMapping("/{assessmentId}/start")
    public ApiResponse<AttemptStartResponse> start(@PathVariable Long assessmentId) {
        return ApiResponse.success(assessmentService.start(assessmentId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Get the state of an attempt")
    @GetMapping("/attempts/{attemptId}")
    public ApiResponse<AttemptStateResponse> getState(@PathVariable Long attemptId) {
        return ApiResponse.success(assessmentService.getState(attemptId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Autosave an answer for an in-progress attempt")
    @PutMapping("/attempts/{attemptId}/answers")
    public ApiResponse<AttemptProgressResponse> saveAnswer(
            @PathVariable Long attemptId, @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.success(
                assessmentService.saveAnswer(attemptId, SecurityUtils.currentUserId(), request));
    }

    @Operation(summary = "Submit an attempt for grading")
    @PostMapping("/attempts/{attemptId}/submit")
    public ApiResponse<AttemptResultResponse> submit(@PathVariable Long attemptId) {
        return ApiResponse.success(assessmentService.submit(attemptId, SecurityUtils.currentUserId()));
    }

    @Operation(summary = "Get the latest attempt of the current student for an assessment")
    @GetMapping("/attempts/my")
    public ApiResponse<Optional<AttemptStateResponse>> myLatestAttempt(
            @RequestParam Long assessmentId) {
        return ApiResponse.success(assessmentService.myLatestAttempt(assessmentId, SecurityUtils.currentUserId()));
    }
}