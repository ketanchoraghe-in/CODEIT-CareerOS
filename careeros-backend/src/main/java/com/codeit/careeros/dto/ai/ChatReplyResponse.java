package com.codeit.careeros.dto.ai;

import java.util.List;

public record ChatReplyResponse(Long sessionId, String reply, List<String> toolsUsed, boolean offline, String mode) {
    /** Backwards-compatible 3-arg constructor. */
    public ChatReplyResponse(Long sessionId, String reply, List<String> toolsUsed) {
        this(sessionId, reply, toolsUsed, false, "llm");
    }
}
