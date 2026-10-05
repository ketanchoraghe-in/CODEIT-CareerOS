package com.codeit.careeros.dto.report;

import java.time.Instant;

/**
 * One available CareerOS report: name, description, live-data status and
 * the actions the frontend can offer (view JSON + download PDF).
 */
public record ReportSummary(
        String type,
        String title,
        String description,
        Instant generatedAt,
        Boolean available,
        String statusDetail) {
}
