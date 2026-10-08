package com.codeit.careeros.admin;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.skill.SkillRequest;
import com.codeit.careeros.dto.skill.SkillResponse;
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

/** Admin management of the skill catalog. */
@Tag(name = "Admin Skills", description = "Admin skill master")
@RestController
@RequestMapping("/api/v1/admin/skills")
@RequiredArgsConstructor
public class AdminSkillController {

    private final AdminSkillService adminSkillService;

    @Operation(summary = "List all skills (admin)")
    @GetMapping
    public ApiResponse<List<SkillResponse>> list() {
        return ApiResponse.success(adminSkillService.listAll());
    }

    @Operation(summary = "Get a skill (admin)")
    @GetMapping("/{id}")
    public ApiResponse<SkillResponse> get(@PathVariable Long id) {
        return ApiResponse.success(adminSkillService.get(id));
    }

    @Operation(summary = "Create a skill (admin)")
    @PostMapping
    public ApiResponse<SkillResponse> create(@Valid @RequestBody SkillRequest request) {
        return ApiResponse.success("Skill created", adminSkillService.create(request));
    }

    @Operation(summary = "Update a skill (admin)")
    @PutMapping("/{id}")
    public ApiResponse<SkillResponse> update(@PathVariable Long id, @Valid @RequestBody SkillRequest request) {
        return ApiResponse.success("Skill updated", adminSkillService.update(id, request));
    }

    @Operation(summary = "Delete a skill (admin)")
    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable Long id) {
        adminSkillService.delete(id);
        return ApiResponse.success("Skill deleted", Map.of("deleted", true));
    }
}