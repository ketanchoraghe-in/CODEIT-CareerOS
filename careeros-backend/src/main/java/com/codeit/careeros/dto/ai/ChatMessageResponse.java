package com.codeit.careeros.dto.ai;

import java.time.Instant;

public record ChatMessageResponse(Long id, String role, String content, Instant createdAt) {
}
