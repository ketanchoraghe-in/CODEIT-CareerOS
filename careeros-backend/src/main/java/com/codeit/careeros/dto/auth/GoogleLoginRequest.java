package com.codeit.careeros.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken
) {
}
