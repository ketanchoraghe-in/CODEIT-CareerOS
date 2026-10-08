package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.assessment.AssessmentAdminDetailResponse;
import com.codeit.careeros.dto.assessment.AssessmentAdminResponse;
import com.codeit.careeros.dto.assessment.AssessmentRequest;
import com.codeit.careeros.dto.assessment.QuestionAdminResponse;
import com.codeit.careeros.dto.assessment.QuestionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Admin management of assessments and the question bank. */
@Tag(name = "Admin Assessments", description = "Admin assessment composition and question bank")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAssessmentController {

    private final AdminAssessmentService adminAssessmentService;

    @Operation(summary = "List all assessments (admin)")
    @GetMapping("/assessments")
    public ApiResponse<List<AssessmentAdminResponse>> list() {
        return ApiResponse.success(adminAssessmentService.listAll());
    }

    @Operation(summary = "Get an assessment with its questions (admin)")
    @GetMapping("/assessments/{id}")
    public ApiResponse<AssessmentAdminDetailResponse> get(@PathVariable Long id) {
        return ApiResponse.success(adminAssessmentService.get(id));
    }

    @Operation(summary = "Create an assessment (admin)")
    @PostMapping("/assessments")
    public ApiResponse<AssessmentAdminResponse> create(@Valid @RequestBody AssessmentRequest request) {
        return ApiResponse.success("Assessment created", adminAssessmentService.create(request));
    }

    @Operation(summary = "Update an assessment (admin)")
    @PutMapping("/assessments/{id}")
    public ApiResponse<AssessmentAdminResponse> update(@PathVariable Long id, @Valid @RequestBody AssessmentRequest request) {
        return ApiResponse.success("Assessment updated", adminAssessmentService.update(id, request));
    }

    @Operation(summary = "Delete an assessment (admin)")
    @DeleteMapping("/assessments/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        adminAssessmentService.delete(id);
        return ApiResponse.success("Assessment deleted", Map.of("deleted", true));
    }

    @Operation(summary = "List the questions of an assessment (admin)")
    @GetMapping("/assessments/{id}/questions")
    public ApiResponse<List<QuestionAdminResponse>> listQuestions(@PathVariable Long id) {
        return ApiResponse.success(adminAssessmentService.listQuestions(id));
    }

    @Operation(summary = "Add a question to an assessment (admin)")
    @PostMapping("/assessments/{id}/questions")
    public ApiResponse<QuestionAdminResponse> createQuestion(
            @PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success("Question added", adminAssessmentService.createQuestion(id, request));
    }

    @Operation(summary = "Update a question (admin)")
    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionAdminResponse> updateQuestion(
            @PathVariable Long questionId, @Valid @RequestBody QuestionRequest request) {
        return ApiResponse.success("Question updated", adminAssessmentService.updateQuestion(questionId, request));
    }

    @Operation(summary = "Delete a question (admin)")
    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<Map<String, Boolean>> deleteQuestion(@PathVariable Long questionId) {
        adminAssessmentService.deleteQuestion(questionId);
        return ApiResponse.success("Question deleted", Map.of("deleted", true));
    }
}