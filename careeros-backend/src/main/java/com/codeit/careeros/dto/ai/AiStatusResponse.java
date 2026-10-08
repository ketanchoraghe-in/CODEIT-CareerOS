package com.codeit.careeros.dto.ai;

public record AiStatusResponse(
        boolean configured,
        String provider,
        String model,
        boolean offlineFallback,
        String mode) {
    /** Backwards-compatible 3-arg constructor used by older callers/tests. */
    public AiStatusResponse(boolean configured, String provider, String model) {
        this(configured, provider, model, true, configured ? "llm" : "offline-smart");
    }
}
