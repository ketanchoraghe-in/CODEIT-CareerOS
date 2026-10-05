package com.codeit.careeros.dto.roadmap;

import jakarta.validation.constraints.NotBlank;

public record RoadmapItemStatusRequest(
        @NotBlank(message = "Status is required (NOT_STARTED, IN_PROGRESS or COMPLETED)") String status) {
}
