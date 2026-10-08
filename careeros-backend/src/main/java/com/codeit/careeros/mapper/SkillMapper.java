package com.codeit.careeros.mapper;

import com.codeit.careeros.common.enums.SkillCategory;
import com.codeit.careeros.dto.skill.SkillRequest;
import com.codeit.careeros.dto.skill.SkillResponse;
import com.codeit.careeros.skill.Skill;

public final class SkillMapper {

    private SkillMapper() {
    }

    public static SkillResponse toResponse(Skill skill) {
        return new SkillResponse(
                skill.getId(),
                skill.getName(),
                skill.getCategory().name(),
                skill.getDescription(),
                skill.isActive(),
                skill.getCreatedAt(),
                skill.getUpdatedAt());
    }

    public static void applyRequest(Skill skill, SkillRequest request) {
        skill.setName(request.name().trim());
        skill.setCategory(CareerMapper.parseEnum(SkillCategory.class, request.category(), "category"));
        skill.setDescription(request.description());
        if (request.active() != null) {
            skill.setActive(request.active());
        }
    }
}
