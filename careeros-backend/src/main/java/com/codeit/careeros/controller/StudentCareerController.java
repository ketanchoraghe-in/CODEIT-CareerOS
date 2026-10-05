package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.career.TargetCareerRequest;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 2: career selection for the authenticated student. Kept in its own
 * controller so the existing StudentController contract stays untouched.
 */
@Tag(name = "Student", description = "Student profile endpoints")
@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentCareerController {

    private final CareerService careerService;

    @Operation(summary = "Set (or change) the target career of the authenticated student")
    @PutMapping("/me/career")
    public ApiResponse<StudentProfileResponse> setTargetCareer(@Valid @RequestBody TargetCareerRequest request) {
        return ApiResponse.success("Target career updated", careerService.setTargetCareer(request.careerId()));
    }
}
