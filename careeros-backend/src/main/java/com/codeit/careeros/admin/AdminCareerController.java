package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.career.CareerRequest;
import com.codeit.careeros.dto.career.CareerResponse;
import com.codeit.careeros.dto.career.CareerSkillRequest;
import com.codeit.careeros.dto.career.CareerSummaryResponse;
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

/** Admin management of the career master and competency framework. */
@Tag(name = "Admin Careers", description = "Admin career master and competency framework")
@RestController
@RequestMapping("/api/v1/admin/careers")
@RequiredArgsConstructor
public class AdminCareerController {

    private final AdminCareerService adminCareerService;

    @Operation(summary = "List all careers (admin)")
    @GetMapping
    public ApiResponse<List<CareerSummaryResponse>> list() {
        return ApiResponse.success(adminCareerService.listAll());
    }

    @Operation(summary = "Get a career with its competency framework (admin)")
    @GetMapping("/{id}")
    public ApiResponse<CareerResponse> get(@PathVariable Long id) {
        return ApiResponse.success(adminCareerService.get(id));
    }

    @Operation(summary = "Create a career with its competency framework (admin)")
    @PostMapping
    public ApiResponse<CareerResponse> create(@Valid @RequestBody CareerRequest request) {
        return ApiResponse.success("Career created", adminCareerService.create(request));
    }

    @Operation(summary = "Update a career and optionally its competency framework (admin)")
    @PutMapping("/{id}")
    public ApiResponse<CareerResponse> update(@PathVariable Long id, @Valid @RequestBody CareerRequest request) {
        return ApiResponse.success("Career updated", adminCareerService.update(id, request));
    }

    @Operation(summary = "Replace the competency framework of a career (admin)")
    @PutMapping("/{id}/skills")
    public ApiResponse<CareerResponse> replaceSkills(
            @PathVariable Long id, @Valid @RequestBody List<CareerSkillRequest> skills) {
        return ApiResponse.success("Competency framework updated", adminCareerService.replaceSkills(id, skills));
    }

    @Operation(summary = "Delete a career (admin)")
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        adminCareerService.delete(id);
        return ApiResponse.success("Career deleted", Map.of("deleted", true));
    }
}