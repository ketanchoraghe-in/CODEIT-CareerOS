package com.codeit.careeros.dto.settings;

import com.codeit.careeros.dto.ai.AiStatusResponse;
import com.codeit.careeros.dto.auth.UserResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;

/**
 * Settings snapshot for the Settings page: account + profile (reused
 * mappers, no duplication), AI status (no secrets), session counts and
 * data counts. Notification preferences are reported honestly: the
 * platform has no notification infrastructure yet.
 */
public record SettingsResponse(
        UserResponse account,
        StudentProfileResponse profile,
        AiStatusResponse aiStatus,
        Integer activeSessions,
        Integer aiConversations,
        Boolean hasCv,
        Boolean hasLinkedIn,
        Boolean notificationsConnected) {
}
