package com.codeit.careeros.ai;

import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.insight.SkillGapResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.service.CareerInsightService;
import com.codeit.careeros.service.CvAnalysisService;
import com.codeit.careeros.service.LinkedInService;
import com.codeit.careeros.service.ProjectService;
import com.codeit.careeros.service.RoadmapService;
import com.codeit.careeros.service.StudentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sprint 7 controlled tool-calling registry.
 *
 * <p>Security contract (enforced, not advisory):
 * <ul>
 *   <li>Tools NEVER touch MySQL directly and NEVER generate SQL — they only
 *       call the existing Sprint 1–6 services.</li>
 *   <li>Every underlying service resolves the student itself via
 *       {@code SecurityUtils.currentUserId()}, so the model can neither
 *       supply nor override identity — cross-student access is impossible.</li>
 *   <li>Only the eight registered tools below are callable; unknown tool
 *       names and out-of-range arguments are rejected before execution.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CareerAssistantTools {

    private final StudentProfileService studentProfileService;
    private final CareerInsightService careerInsightService;
    private final RoadmapService roadmapService;
    private final ProjectService projectService;
    private final CvAnalysisService cvAnalysisService;
    private final LinkedInService linkedInService;

    /** Allowlisted tool names, in the order advertised to the model. */
    public List<String> toolNames() {
        return List.of(
                "getStudentProfile",
                "getReadiness",
                "getSkillGaps",
                "getAssessmentHistory",
                "getRoadmap",
                "getProjects",
                "getCvAnalysis",
                "getLinkedInAnalysis");
    }

    /** Compact, token-friendly description of every tool for the system prompt. */
    public String describeTools() {
        return """
                CareerOS tools available to you (call them when the question needs
                THIS student's own data — never for general knowledge questions):
                - getStudentProfile -> profile: name, college, degree, branch, target career
                - getReadiness -> readiness percent, level, skill counts, latest attempt
                - getSkillGaps (topN 1-20) -> largest skill gaps vs target career
                - getAssessmentHistory -> submitted attempts, newest first (max 10)
                - getRoadmap -> phases, items, progress, current focus
                - getProjects -> recommended projects with status
                - getCvAnalysis -> CV status, detected skills, completeness
                - getLinkedInAnalysis -> LinkedIn completeness, skills, alignment
                Never invent data. If a tool returns no data (e.g. no target career
                or nothing uploaded), say so plainly and guide the next step.
                """;
    }

    // ------------------------------------------------- native LLM tools ----
    //
    // The methods below expose the same allowlisted capabilities to the LLM
    // through Spring AI native function calling. The model itself decides
    // whether (and which) tools to call — there is no keyword routing here.
    // Each underlying service resolves the student from the security context,
    // so the model can neither supply nor override identity.

    @Tool(name = "getStudentProfile",
            description = "Get the current student's profile: name, college, degree, branch and target career. "
                    + "Use when the question is about who they are or their target career.")
    public Map<String, Object> getStudentProfile() {
        return profile();
    }

    @Tool(name = "getReadiness",
            description = "Get the student's career readiness: readiness percent, level, skill counts and latest "
                    + "assessment attempt. Use for 'how ready am I' style questions.")
    public Map<String, Object> getReadiness() {
        return readiness();
    }

    @Tool(name = "getSkillGaps",
            description = "Get the student's largest skill gaps versus their target career, biggest gap first. "
                    + "Use for skill gaps, weak skills, 'what should I learn/improve' questions.")
    public List<Map<String, Object>> getSkillGaps(
            @ToolParam(description = "Maximum number of gaps to return (1-20).", required = false)
            Integer topN) {
        return skillGaps(topN(topN, 8));
    }

    @Tool(name = "getAssessmentHistory",
            description = "Get the student's submitted assessment attempts, newest first (max 10). "
                    + "Use for questions about assessment results, scores or test history.")
    public List<Map<String, Object>> getAssessmentHistory() {
        return history();
    }

    @Tool(name = "getRoadmap",
            description = "Get the student's roadmap: phases, items, progress and current focus. "
                    + "Use for 'what should I learn next', roadmap progress or study-plan questions.")
    public Map<String, Object> getRoadmap() {
        return roadmap();
    }

    @Tool(name = "getProjects",
            description = "Get project recommendations for the student's target career with completion status. "
                    + "Use for 'which project should I build' questions.")
    public Map<String, Object> getProjects() {
        return projects();
    }

    @Tool(name = "getCvAnalysis",
            description = "Get the student's CV analysis: upload status, detected skills and completeness. "
                    + "Use for CV/resume questions.")
    public Map<String, Object> getCvAnalysis() {
        return cv();
    }

    @Tool(name = "getLinkedInAnalysis",
            description = "Get the student's LinkedIn analysis: profile completeness, skills and alignment. "
                    + "Use for LinkedIn questions.")
    public Map<String, Object> getLinkedInAnalysis() {
        return linkedIn();
    }

    /**
     * Executes one allowlisted tool. Arguments come from the model and are
     * validated here; student identity always comes from the security context.
     */
    public Object execute(String tool, Map<String, Object> args) {
        Map<String, Object> arguments = args == null ? Map.of() : args;
        return switch (tool) {
            case "getStudentProfile" -> profile();
            case "getReadiness" -> readiness();
            case "getSkillGaps" -> skillGaps(topN(arguments, 8));
            case "getAssessmentHistory" -> history();
            case "getRoadmap" -> roadmap();
            case "getProjects" -> projects();
            case "getCvAnalysis" -> cv();
            case "getLinkedInAnalysis" -> linkedIn();
            default -> throw new IllegalArgumentException("Unknown tool: " + tool);
        };
    }

    private int topN(Map<String, Object> args, int fallback) {
        Object raw = args.get("topN");
        if (raw instanceof Number number) {
            return clampTopN(number.intValue());
        }
        return fallback;
    }

    private int topN(Integer value, int fallback) {
        if (value == null) {
            return fallback;
        }
        return clampTopN(value);
    }

    private int clampTopN(int value) {
        return Math.min(20, Math.max(1, value));
    }

    private Map<String, Object> profile() {
        StudentProfileResponse profile = studentProfileService.getCurrentProfile();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fullName", profile.fullName());
        out.put("college", profile.college());
        out.put("degree", profile.degree());
        out.put("branch", profile.branch());
        out.put("graduationYear", profile.graduationYear());
        out.put("targetCareerId", profile.targetCareerId());
        out.put("targetCareerName", profile.targetCareerName());
        return out;
    }

    private Map<String, Object> readiness() {
        ReadinessResponse readiness = careerInsightService.myReadiness();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasTarget", readiness.hasTarget());
        out.put("targetCareerName", readiness.targetCareerName());
        out.put("readinessPercent", readiness.readinessPercent());
        out.put("readinessLevel", readiness.readinessLevel());
        out.put("totalSkills", readiness.totalSkills());
        out.put("assessedSkills", readiness.assessedSkills());
        out.put("metSkills", readiness.metSkills());
        out.put("submittedAttempts", readiness.submittedAttempts());
        if (readiness.latestAttempt() != null) {
            AttemptHistoryItem latest = readiness.latestAttempt();
            Map<String, Object> attempt = new LinkedHashMap<>();
            attempt.put("assessmentTitle", latest.assessmentTitle());
            attempt.put("overallScore", latest.overallScore());
            attempt.put("status", latest.status());
            out.put("latestAttempt", attempt);
        }
        return out;
    }

    private List<Map<String, Object>> skillGaps(int topN) {
        ReadinessResponse readiness = careerInsightService.myReadiness();
        List<Map<String, Object>> out = new ArrayList<>();
        if (readiness.gaps() == null) {
            return out;
        }
        for (SkillGapResponse gap : readiness.gaps()) {
            if (out.size() >= topN) {
                break;
            }
            if (Boolean.TRUE.equals(gap.metTarget())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("skillName", gap.skillName());
            row.put("category", gap.skillCategory());
            row.put("targetPercent", gap.targetPercent());
            row.put("scorePercent", gap.scorePercent());
            row.put("gapPercent", gap.gapPercent());
            row.put("assessed", gap.assessed());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> history() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttemptHistoryItem item : careerInsightService.myHistory(null)) {
            if (out.size() >= 10) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assessmentTitle", item.assessmentTitle());
            row.put("careerName", item.careerName());
            row.put("status", item.status());
            row.put("overallScore", item.overallScore());
            row.put("submittedAt", String.valueOf(item.submittedAt()));
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> roadmap() {
        var roadmap = roadmapService.myRoadmap();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasTarget", roadmap.hasTarget());
        out.put("careerName", roadmap.careerName());
        out.put("progressPercent", roadmap.progressPercent());
        out.put("completedItems", roadmap.completedItems());
        out.put("totalItems", roadmap.totalItems());
        List<Map<String, Object>> phases = new ArrayList<>();
        if (roadmap.phases() != null) {
            for (var phase : roadmap.phases()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", phase.title());
                row.put("completedItems", phase.completedItems());
                row.put("totalItems", phase.totalItems());
                List<String> pending = new ArrayList<>();
                if (phase.items() != null) {
                    for (var item : phase.items()) {
                        if (!"COMPLETED".equalsIgnoreCase(String.valueOf(item.status()))) {
                            pending.add(String.valueOf(item.title()));
                        }
                        if (pending.size() >= 5) {
                            break;
                        }
                    }
                }
                row.put("pendingItems", pending);
                phases.add(row);
            }
        }
        out.put("phases", phases);
        return out;
    }

    private Map<String, Object> projects() {
        var list = projectService.recommended();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasTarget", list.hasTarget());
        out.put("careerName", list.careerName());
        out.put("completedProjects", list.completedProjects());
        out.put("totalProjects", list.totalProjects());
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list.projects() != null) {
            for (var project : list.projects()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", project.title());
                row.put("status", String.valueOf(project.status()));
                rows.add(row);
            }
        }
        out.put("projects", rows);
        return out;
    }

    private Map<String, Object> cv() {
        var analysis = cvAnalysisService.analyze();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasDocument", analysis.hasCv());
        List<String> skills = new ArrayList<>();
        if (analysis.detectedSkills() != null) {
            analysis.detectedSkills().forEach(skill -> skills.add(skill.skillName()));
        }
        out.put("parsed", analysis.hasCv() && !skills.isEmpty());
        out.put("detectedSkills", skills);
        if (analysis.completeness() != null) {
            out.put("completenessPercent", analysis.completeness().scorePercent());
            out.put("suggestions", analysis.completeness().suggestions());
        }
        return out;
    }

    private Map<String, Object> linkedIn() {
        var analysis = linkedInService.analyze();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("hasProfile", analysis.hasProfile());
        out.put("profileUrl", analysis.profile() == null ? null : analysis.profile().profileUrl());
        out.put("completenessPercent", analysis.completenessPercent());
        List<String> skills = new ArrayList<>();
        if (analysis.detectedSkills() != null) {
            analysis.detectedSkills().forEach(skill -> skills.add(skill.skillName()));
        }
        out.put("detectedSkills", skills);
        out.put("alignmentPercent", analysis.alignmentPercent());
        out.put("suggestions", analysis.suggestions());
        return out;
    }

}
