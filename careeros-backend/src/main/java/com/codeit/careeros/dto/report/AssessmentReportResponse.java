package com.codeit.careeros.dto.report;

import com.codeit.careeros.dto.assessment.SkillScoreResponse;
import com.codeit.careeros.dto.insight.AttemptHistoryItem;

import java.util.List;

/**
 * Assessment report: every submitted attempt plus skill-wise scores of
 * the latest attempt (from live skill_scores rows). Empty when the
 * student has not submitted anything yet — never invented.
 */
public record AssessmentReportResponse(
        List<AttemptHistoryItem> attempts,
        AttemptHistoryItem latestAttempt,
        List<SkillScoreResponse> latestSkillScores) {
}
