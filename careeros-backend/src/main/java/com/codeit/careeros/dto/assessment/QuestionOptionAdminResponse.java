package com.codeit.careeros.dto.assessment;

public record QuestionOptionAdminResponse(
        Long id,
        String optionText,
        boolean correct,
        Integer displayOrder
) {
}
