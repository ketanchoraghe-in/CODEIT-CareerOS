package com.codeit.careeros.dto.project;

import jakarta.validation.constraints.NotBlank;

public record ProjectStatusRequest(
        @NotBlank(message = "Status is required (NOT_STARTED, IN_PROGRESS or COMPLETED)") String status) {
}
