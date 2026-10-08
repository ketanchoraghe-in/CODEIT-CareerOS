package com.codeit.careeros.dto.linkedin;

import com.codeit.careeros.dto.cv.DetectedSkillResponse;
import com.codeit.careeros.dto.insight.SkillGapResponse;

import java.util.List;

/**
 * LinkedIn analysis for the current student. Skill gaps are the Sprint 3
 * {@link SkillGapResponse} rows (same calculation, no duplicate rules);
 * detected skills reuse the skill-master matching; CV comparison joins the
 * Sprint 5 CV analysis. Empty payload when no LinkedIn data was saved yet.
 */
public record LinkedInAnalysisResponse(
        Boolean hasProfile,
        Boolean hasTarget,
        Long careerId,
        String careerName,
        LinkedInProfileResponse profile,
        Integer alignmentPercent,
        List<DetectedSkillResponse> detectedSkills,
        List<SkillGapResponse> matchedSkills,
        List<SkillGapResponse> missingSkills,
        List<DetectedSkillResponse> alsoOnCv,
        List<DetectedSkillResponse> onlyOnLinkedIn,
        List<DetectedSkillResponse> onlyOnCv,
        Integer completenessPercent,
        List<String> strengths,
        List<String> suggestions) {
}
