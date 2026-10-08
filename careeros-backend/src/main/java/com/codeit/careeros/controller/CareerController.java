package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.career.CareerResponse;
import com.codeit.careeros.dto.career.CareerSkillResponse;
import com.codeit.careeros.dto.career.CareerSummaryResponse;
import com.codeit.careeros.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Public career catalog (permitted in SecurityConfig). */
@Tag(name = "Careers", description = "Career catalog and competency framework")
@RestController
@RequestMapping("/api/v1/careers")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    @Operation(summary = "List published careers")
    @GetMapping
    public ApiResponse<List<CareerSummaryResponse>> listCareers() {
        return ApiResponse.success(careerService.listPublishedCareers());
    }

    @Operation(summary = "Get a published career with its competency framework")
    @GetMapping("/{id}")
    public ApiResponse<CareerResponse> getCareer(@PathVariable Long id) {
        return ApiResponse.success(careerService.getPublishedCareer(id));
    }

    @Operation(summary = "List the required skills and weights of a career")
    @GetMapping("/{id}/skills")
    public ApiResponse<List<CareerSkillResponse>> getCareerSkills(@PathVariable Long id) {
        return ApiResponse.success(careerService.getCareerSkills(id));
    }
}
