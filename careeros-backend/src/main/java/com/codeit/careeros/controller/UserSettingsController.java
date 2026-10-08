package com.codeit.careeros.controller;

import com.codeit.careeros.common.ApiResponse;
import com.codeit.careeros.dto.settings.ChangePasswordRequest;
import com.codeit.careeros.dto.settings.SettingsResponse;
import com.codeit.careeros.service.UserSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Student settings: settings snapshot, secure password change, session
 * revocation and AI history clearing. All endpoints are strictly
 * owner-scoped to the authenticated user.
 */
@Tag(name = "Settings", description = "Student account settings and security")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;

    @Operation(summary = "Settings snapshot of the authenticated user (account, profile, AI status, sessions)")
    @GetMapping("/settings/me")
    public ApiResponse<SettingsResponse> mySettings() {
        return ApiResponse.success(userSettingsService.mySettings());
    }

    @Operation(summary = "Change the password of the authenticated user (current password required)")
    @PutMapping("/users/me/password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userSettingsService.changePassword(request);
        return ApiResponse.success("Password changed successfully. Please log in again on other devices.", null);
    }

    @Operation(summary = "Log out from all other sessions (revoke every refresh token)")
    @PostMapping("/users/me/sessions/revoke-all")
    public ApiResponse<Map<String, Integer>> revokeAllSessions() {
        int revoked = userSettingsService.revokeAllSessions();
        return ApiResponse.success("All sessions revoked", Map.of("revokedSessions", revoked));
    }

    @Operation(summary = "Clear all AI conversation history of the authenticated student")
    @DeleteMapping("/settings/me/ai-history")
    public ApiResponse<Map<String, Integer>> clearAiHistory() {
        int deleted = userSettingsService.clearAiHistory();
        return ApiResponse.success("AI conversation history cleared", Map.of("deletedSessions", deleted));
    }
}
