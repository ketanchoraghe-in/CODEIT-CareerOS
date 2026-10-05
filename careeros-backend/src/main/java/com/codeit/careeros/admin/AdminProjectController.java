package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.admin.AdminProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin read-only browser for the project catalog. */
@Tag(name = "Admin Projects", description = "Admin project catalog browser")
@RestController
@RequestMapping("/api/v1/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final AdminProjectService adminProjectService;

    @Operation(summary = "List all projects in the catalog (admin)")
    @GetMapping
    public ApiResponse<List<AdminProjectResponse>> list() {
        return ApiResponse.success(adminProjectService.listAll());
    }
}
