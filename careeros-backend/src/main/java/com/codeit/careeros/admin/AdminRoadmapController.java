package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.admin.AdminRoadmapTemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin read-only browser for roadmap templates. */
@Tag(name = "Admin Roadmaps", description = "Admin roadmap template browser")
@RestController
@RequestMapping("/api/v1/admin/roadmaps")
@RequiredArgsConstructor
public class AdminRoadmapController {

    private final AdminRoadmapService adminRoadmapService;

    @Operation(summary = "List roadmap templates per career (admin)")
    @GetMapping
    public ApiResponse<List<AdminRoadmapTemplateResponse>> list() {
        return ApiResponse.success(adminRoadmapService.listTemplates());
    }
}
