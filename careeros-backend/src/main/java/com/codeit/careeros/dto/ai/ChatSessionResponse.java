package com.codeit.careeros.dto.ai;

import java.time.Instant;

public record ChatSessionResponse(Long id, String title, Instant createdAt, Instant updatedAt) {
}
