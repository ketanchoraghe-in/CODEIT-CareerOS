package com.codeit.careeros.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Message must not be blank")
        @Size(max = 4000, message = "Message must be at most 4000 characters") String message) {
}
