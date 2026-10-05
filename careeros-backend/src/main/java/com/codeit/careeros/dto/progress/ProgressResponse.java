package com.codeit.careeros.dto.progress;

import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.linkedin.LinkedInAnalysisResponse;
import com.codeit.careeros.dto.project.ProjectListResponse;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;

import java.util.List;

/**
 * Consolidated student progress: aggregates the existing Sprint 1-7
 * read services (readiness, attempts, roadmap, projects, CV, LinkedIn)
 * into one payload so the Progress page needs a single request.
 * No business logic is duplicated here; all computation lives in the
 * reused services.
 */
public record ProgressResponse(
        ReadinessResponse readiness,
        List<AttemptHistoryItem> attempts,
        Integer completedAssessments,
        AttemptHistoryItem latestAttempt,
        AttemptHistoryItem previousAttempt,
        RoadmapResponse roadmap,
        ProjectListResponse projects,
        CvAnalysisResponse cv,
        LinkedInAnalysisResponse linkedIn,
        List<ActivityItem> recentActivity) {
}
