package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.skill.SkillResponse;
import com.codeit.careeros.skill.Skill;
import com.codeit.careeros.repository.SkillRepository;
import com.codeit.careeros.mapper.SkillMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Skill catalog read endpoint (used by admin forms and student tooling). */
@Tag(name = "Skills", description = "Skill catalog")
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRepository skillRepository;

    @Operation(summary = "List all active skills")
    @GetMapping
    public ApiResponse<List<SkillResponse>> listSkills() {
        List<Skill> skills = skillRepository.findAllByOrderByNameAsc().stream()
                .filter(Skill::isActive)
                .toList();
        return ApiResponse.success(skills.stream().map(SkillMapper::toResponse).toList());
    }
}
