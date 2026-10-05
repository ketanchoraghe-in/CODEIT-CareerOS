package com.codeit.careeros.dto.cv;

import java.util.List;

public record CvCompletenessResponse(
        Integer scorePercent,
        Integer totalChecks,
        Integer passedChecks,
        List<String> strengths,
        List<String> suggestions) {
}
