package com.codeit.careeros.dto.admin;

/**
 * Admin view of the AI assistant configuration. Never contains secrets —
 * API keys are always backend-only and are never serialized here.
 */
public record AdminAiStatusResponse(
        boolean configured,
        String provider,
        String model,
        String mode,
        boolean offlineFallbackEnabled,
        int maxToolIterations,
        int historyLimit,
        int requestTimeoutSeconds,
        int rateLimitPerMinute) {
}
