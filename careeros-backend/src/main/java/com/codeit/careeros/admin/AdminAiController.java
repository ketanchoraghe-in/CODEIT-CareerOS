package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.admin.AdminAiStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin read-only view of AI configuration (operational settings only, never keys). */
@Tag(name = "Admin AI", description = "Admin AI configuration status")
@RestController
@RequestMapping("/api/v1/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {

    private final AdminAiService adminAiService;

    @Operation(summary = "AI configuration status (admin)")
    @GetMapping("/status")
    public ApiResponse<AdminAiStatusResponse> status() {
        return ApiResponse.success(adminAiService.status());
    }
}
