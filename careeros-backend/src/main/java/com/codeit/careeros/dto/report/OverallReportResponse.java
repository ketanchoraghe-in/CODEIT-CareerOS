package com.codeit.careeros.dto.report;

import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.linkedin.LinkedInAnalysisResponse;
import com.codeit.careeros.dto.project.ProjectListResponse;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;

import java.time.Instant;
import java.util.List;

/**
 * Overall CareerOS report: the major career-development information in
 * one payload, all from live database/application data.
 */
public record OverallReportResponse(
        StudentProfileResponse student,
        ReadinessResponse readiness,
        RoadmapResponse roadmap,
        ProjectListResponse projects,
        CvAnalysisResponse cv,
        LinkedInAnalysisResponse linkedIn,
        List<String> recommendedNextSteps,
        Instant generatedAt) {
}
