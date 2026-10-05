package com.codeit.careeros.dto.progress;

import java.time.Instant;

/**
 * One recent CareerOS activity derived from existing timestamps only
 * (assessment submissions, roadmap/project progress updates, CV/LinkedIn updates).
 * Nothing is invented: entries exist only when the underlying row exists.
 */
public record ActivityItem(
        String type,
        String title,
        String detail,
        Instant occurredAt) {
}
