package com.codeit.careeros.admin;

import com.codeit.careeros.ai.AiProperties;
import com.codeit.careeros.dto.admin.AdminAiStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Admin read-only view of AI configuration. Exposes operational settings only —
 * API keys are never read here and can never leak through this service.
 */
@Service
@RequiredArgsConstructor
public class AdminAiService {

    private final AiProperties aiProperties;

    public AdminAiStatusResponse status() {
        boolean configured = aiProperties.isConfigured();
        return new AdminAiStatusResponse(
                configured,
                aiProperties.getProvider(),
                aiProperties.effectiveModel(),
                configured ? "llm" : "offline-smart",
                aiProperties.isOfflineFallbackEnabled(),
                aiProperties.getMaxToolIterations(),
                aiProperties.getHistoryLimit(),
                aiProperties.getRequestTimeoutSeconds(),
                aiProperties.getRateLimitPerMinute());
    }
}
