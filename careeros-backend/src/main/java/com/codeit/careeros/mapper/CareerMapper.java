package com.codeit.careeros.mapper;

import com.codeit.careeros.career.Career;
import com.codeit.careeros.career.CareerSkill;
import com.codeit.careeros.common.enums.CareerCategory;
import com.codeit.careeros.common.enums.DifficultyLevel;
import com.codeit.careeros.common.enums.SkillLevel;
import com.codeit.careeros.dto.career.CareerRequest;
import com.codeit.careeros.dto.career.CareerResponse;
import com.codeit.careeros.dto.career.CareerSkillResponse;
import com.codeit.careeros.dto.career.CareerSummaryResponse;
import com.codeit.careeros.exception.BusinessException;

import java.util.List;

public final class CareerMapper {

    private CareerMapper() {
    }

    public static CareerSummaryResponse toSummary(Career career, long skillCount) {
        return new CareerSummaryResponse(
                career.getId(),
                career.getName(),
                career.getDescription(),
                career.getCategory().name(),
                career.getDifficultyLevel().name(),
                career.isPublished(),
                skillCount);
    }

    public static CareerSkillResponse toSkillResponse(CareerSkill careerSkill) {
        return new CareerSkillResponse(
                careerSkill.getSkill().getId(),
                careerSkill.getSkill().getName(),
                careerSkill.getSkill().getCategory().name(),
                careerSkill.getWeightPercent(),
                careerSkill.getRequiredLevel().name(),
                careerSkill.getTargetPercent());
    }

    public static CareerResponse toResponse(Career career, List<CareerSkill> skills) {
        return new CareerResponse(
                career.getId(),
                career.getName(),
                career.getDescription(),
                career.getCategory().name(),
                career.getDifficultyLevel().name(),
                career.isPublished(),
                skills.stream().map(CareerMapper::toSkillResponse).toList(),
                career.getCreatedAt(),
                career.getUpdatedAt());
    }

    public static void applyRequest(Career career, CareerRequest request) {
        career.setName(request.name().trim());
        career.setDescription(request.description());
        career.setCategory(parseEnum(CareerCategory.class, request.category(), "category"));
        career.setDifficultyLevel(parseEnum(DifficultyLevel.class, request.difficultyLevel(), "difficultyLevel"));
        if (request.published() != null) {
            career.setPublished(request.published());
        }
    }

    public static <T extends Enum<T>> T parseEnum(Class<T> type, String value, String field) {
        if (value == null || value.isBlank()) {
            throw BusinessException.badRequest(field + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException ex) {
            throw BusinessException.badRequest(
                    "Invalid " + field + " '" + value + "'. Allowed values: " + String.join(", ", namesOf(type)));
        }
    }

    private static <T extends Enum<T>> String[] namesOf(Class<T> type) {
        T[] constants = type.getEnumConstants();
        String[] names = new String[constants.length];
        for (int i = 0; i < constants.length; i++) {
            names[i] = constants[i].name();
        }
        return names;
    }

    /** Parses a skill level with a friendly error message. */
    public static SkillLevel parseSkillLevel(String value, String field) {
        return parseEnum(SkillLevel.class, value, field);
    }
}
