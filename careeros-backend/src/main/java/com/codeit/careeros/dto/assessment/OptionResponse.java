package com.codeit.careeros.dto.assessment;

public record OptionResponse(
        Long optionId,
        String optionText,
        int displayOrder
) {
}
