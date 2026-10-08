package com.codeit.careeros.dto.settings;

import com.codeit.careeros.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/**
 * Secure password change: current password is always required and
 * verified with BCrypt; the new password follows the same strength
 * rules as registration. Passwords are never logged.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @StrongPassword
        String newPassword) {
}
