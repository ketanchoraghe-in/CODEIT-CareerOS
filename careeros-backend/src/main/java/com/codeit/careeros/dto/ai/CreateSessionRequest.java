package com.codeit.careeros.dto.ai;

import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @Size(max = 190, message = "Title must be at most 190 characters") String title) {
}
