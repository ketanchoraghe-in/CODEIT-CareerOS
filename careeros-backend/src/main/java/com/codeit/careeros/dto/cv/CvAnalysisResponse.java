package com.codeit.careeros.dto.cv;

import com.codeit.careeros.dto.insight.SkillGapResponse;

import java.util.List;

/**
 * CV analysis for the current student. Skill gaps are the Sprint 3
 * {@link SkillGapResponse} rows (same calculation, no second system):
 * {@code matchedSkills} are framework skills found in the CV,
 * {@code missingSkills} are framework skills absent from the CV.
 */
public record CvAnalysisResponse(
        Boolean hasCv,
        Boolean hasTarget,
        Long careerId,
        String careerName,
        CvDocumentResponse document,
        List<DetectedSkillResponse> detectedSkills,
        List<SkillGapResponse> matchedSkills,
        List<SkillGapResponse> missingSkills,
        CvCompletenessResponse completeness,
        /**
         * Per-section breakdown (contact, summary, skills, experience,
         * education, projects, certifications, structure) derived from the
         * extracted text. Empty when no CV was uploaded.
         */
        List<CvSectionResponse> sections,
        /**
         * Machine-readability score (0-100): share of ATS checks passed
         * (contact email/phone as text, standard skill/experience/education
         * headings, extractable text layer). All inputs are real parse
         * signals; see {@code CvAnalysisService.atsScoreOf}.
         */
        Integer atsScore,
        /**
         * Overall CV score (0-100): with a target career it blends profile
         * quality, career match and ATS readability
         * (50/30/20); without one it blends quality and readability
         * (60/40). Every input is a real measured signal.
         */
        Integer overallScore) {
}
