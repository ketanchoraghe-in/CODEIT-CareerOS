package com.codeit.careeros.service;

import com.codeit.careeros.assessment.SkillScore;
import com.codeit.careeros.dto.assessment.SkillScoreResponse;
import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.insight.SkillGapResponse;
import com.codeit.careeros.dto.linkedin.LinkedInAnalysisResponse;
import com.codeit.careeros.dto.project.ProjectListResponse;
import com.codeit.careeros.dto.report.AssessmentReportResponse;
import com.codeit.careeros.dto.report.OverallReportResponse;
import com.codeit.careeros.dto.report.ReportSummary;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.repository.SkillScoreRepository;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CareerOS reports: JSON views plus professional PDF downloads, all
 * generated from live database/application data via the existing
 * services. No fake rows: when data does not exist the report states
 * that clearly instead of inventing information.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final CareerInsightService careerInsightService;
    private final RoadmapService roadmapService;
    private final ProjectService projectService;
    private final CvAnalysisService cvAnalysisService;
    private final LinkedInService linkedInService;
    private final StudentProfileService studentProfileService;
    private final SkillScoreRepository skillScoreRepository;

    @Transactional(readOnly = true)
    public List<ReportSummary> listReports() {
        Instant now = Instant.now();
        ReadinessResponse readiness = careerInsightService.myReadiness();
        List<AttemptHistoryItem> attempts = careerInsightService.myHistory(null);
        RoadmapResponse roadmap = roadmapService.myRoadmap();
        ProjectListResponse projects = projectService.recommended();

        boolean hasTarget = Boolean.TRUE.equals(readiness.hasTarget());
        boolean hasAttempts = !attempts.isEmpty();
        boolean hasRoadmap = roadmap.totalItems() != null && roadmap.totalItems() > 0;
        boolean hasProjects = projects.totalProjects() != null && projects.totalProjects() > 0;

        return List.of(
                new ReportSummary("readiness", "Career Readiness Report",
                        "Overall readiness, strengths, improvement areas and next steps for your target career.",
                        now, hasTarget && hasAttempts,
                        hasTarget ? (hasAttempts ? readiness.readinessPercent() + "% ready" : "No assessment submitted yet")
                                : "No target career selected"),
                new ReportSummary("assessment", "Skill Assessment Report",
                        "Submitted attempts with skill-wise scores and correct/incorrect summary.",
                        now, hasAttempts,
                        hasAttempts ? attempts.size() + " submission(s)" : "No assessment submitted yet"),
                new ReportSummary("gap-analysis", "Career Gap Analysis Report",
                        "Required vs current levels, gaps, priority areas and recommendations.",
                        now, hasTarget,
                        hasTarget ? readiness.totalSkills() + " required skills" : "No target career selected"),
                new ReportSummary("roadmap", "Learning / Roadmap Progress Report",
                        "Phases, completed / in-progress / pending items and completion percentage.",
                        now, hasTarget && hasRoadmap,
                        hasRoadmap ? roadmap.completedItems() + "/" + roadmap.totalItems() + " steps completed"
                                : "No roadmap published for your career yet"),
                new ReportSummary("overall", "Overall CareerOS Progress Report",
                        "Combined career-development snapshot: readiness, gaps, roadmap, projects, CV and LinkedIn.",
                        now, hasTarget,
                        hasTarget ? "Covers " + readiness.targetCareerName() : "No target career selected"
                                + (hasProjects ? "" : "")));
    }

    @Transactional(readOnly = true)
    public ReadinessResponse readinessReport() {
        ReadinessResponse readiness = careerInsightService.myReadiness();
        if (!Boolean.TRUE.equals(readiness.hasTarget())) {
            throw BusinessException.badRequest("Choose a target career first to generate the readiness report");
        }
        return readiness;
    }

    @Transactional(readOnly = true)
    public AssessmentReportResponse assessmentReport() {
        List<AttemptHistoryItem> attempts = careerInsightService.myHistory(null);
        if (attempts.isEmpty()) {
            throw BusinessException.badRequest("No submitted assessment found for the assessment report");
        }
        AttemptHistoryItem latest = attempts.get(0);
        List<SkillScoreResponse> scores = skillScoreRepository
                .findByAttemptIdOrderByWeightPercentDesc(latest.attemptId()).stream()
                .map(ReportService::toSkillScore)
                .toList();
        return new AssessmentReportResponse(attempts, latest, scores);
    }

    @Transactional(readOnly = true)
    public ReadinessResponse gapReport() {
        ReadinessResponse readiness = careerInsightService.myReadiness();
        if (!Boolean.TRUE.equals(readiness.hasTarget())) {
            throw BusinessException.badRequest("Choose a target career first to generate the gap report");
        }
        return readiness;
    }

    @Transactional(readOnly = true)
    public RoadmapResponse roadmapReport() {
        RoadmapResponse roadmap = roadmapService.myRoadmap();
        if (!Boolean.TRUE.equals(roadmap.hasTarget())) {
            throw BusinessException.badRequest("Choose a target career first to generate the roadmap report");
        }
        return roadmap;
    }

    @Transactional(readOnly = true)
    public OverallReportResponse overallReport() {
        StudentProfileResponse student = studentProfileService.getCurrentProfile();
        ReadinessResponse readiness = careerInsightService.myReadiness();
        RoadmapResponse roadmap = roadmapService.myRoadmap();
        ProjectListResponse projects = projectService.recommended();
        CvAnalysisResponse cv = cvAnalysisService.analyze();
        LinkedInAnalysisResponse linkedIn = linkedInService.analyze();
        return new OverallReportResponse(student, readiness, roadmap, projects, cv, linkedIn,
                nextSteps(readiness, roadmap, projects, cv, linkedIn), Instant.now());
    }

    // ---- PDF ----

    @Transactional(readOnly = true)
    public byte[] generatePdf(String type) {
        String normalized = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "readiness" -> readinessPdf();
            case "assessment" -> assessmentPdf();
            case "gap-analysis", "gap" -> gapPdf();
            case "roadmap" -> roadmapPdf();
            case "overall" -> overallPdf();
            default -> throw BusinessException.badRequest(
                    "Unknown report type: " + type + " (expected readiness, assessment, gap-analysis, roadmap or overall)");
        };
    }

    public String pdfFilename(String type) {
        String normalized = type == null ? "report" : type.trim().toLowerCase(Locale.ROOT);
        return "careeros-" + normalized + "-report.pdf";
    }

    private byte[] readinessPdf() {
        StudentProfileResponse student = studentProfileService.getCurrentProfile();
        ReadinessResponse readiness = readinessReport();
        PdfDoc doc = newDoc("Career Readiness Report");
        doc.addStudentHeader(student, readiness.targetCareerName());
        doc.addKeyValue("Overall readiness", readiness.readinessPercent() + "% (" + pretty(readiness.readinessLevel()) + ")");
        doc.addKeyValue("Skills assessed", readiness.assessedSkills() + " of " + readiness.totalSkills()
                + " (" + readiness.metSkills() + " on target, " + readiness.submittedAttempts() + " submissions)");
        doc.addSection("Strengths");
        if (readiness.strengths().isEmpty()) {
            doc.addBody("No met skills yet — your first wins will show up here.");
        } else {
            doc.addGapTable(readiness.strengths());
        }
        doc.addSection("Improvement areas");
        if (readiness.improvements().isEmpty()) {
            doc.addBody("Everything assessed is on target. Nice work.");
        } else {
            doc.addGapTable(readiness.improvements());
        }
        doc.addSection("Recommended next steps");
        nextSteps(readiness, roadmapService.myRoadmap(), projectService.recommended(),
                cvAnalysisService.analyze(), linkedInService.analyze())
                .forEach(step -> doc.addBody("• " + step));
        return doc.bytes();
    }

    private byte[] assessmentPdf() {
        StudentProfileResponse student = studentProfileService.getCurrentProfile();
        AssessmentReportResponse report = assessmentReport();
        PdfDoc doc = newDoc("Skill Assessment Report");
        doc.addStudentHeader(student, report.latestAttempt().careerName());
        for (AttemptHistoryItem attempt : report.attempts()) {
            doc.addSection(attempt.assessmentTitle() + " — " + formatDate(attempt.submittedAt()));
            doc.addKeyValue("Score", String.valueOf(attempt.overallScore()));
            doc.addKeyValue("Questions", attempt.answeredCount() + " answered of " + attempt.totalQuestions());
        }
        if (!report.latestSkillScores().isEmpty()) {
            doc.addSection("Skill-wise scores (latest attempt)");
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            doc.addHeaderRow(table, List.of("Skill", "Score", "Target", "Level"));
            for (SkillScoreResponse s : report.latestSkillScores()) {
                doc.addRow(table, List.of(
                        s.skillName(),
                        s.scorePercent() + "% (" + s.correctCount() + "/" + s.questionCount() + ")",
                        s.targetPercent() + "%",
                        pretty(s.level())));
            }
            doc.addTable(table);
        }
        return doc.bytes();
    }

    private byte[] gapPdf() {
        StudentProfileResponse student = studentProfileService.getCurrentProfile();
        ReadinessResponse readiness = gapReport();
        PdfDoc doc = newDoc("Career Gap Analysis Report");
        doc.addStudentHeader(student, readiness.targetCareerName());
        doc.addKeyValue("Skills on target", readiness.metSkills() + " of " + readiness.totalSkills());
        doc.addSection("Required skills — current vs target (largest gaps first)");
        List<SkillGapResponse> sorted = new ArrayList<>(readiness.gaps());
        sorted.sort((a, b) -> {
            int gapA = a.gapPercent() == null ? Integer.MIN_VALUE : a.gapPercent();
            int gapB = b.gapPercent() == null ? Integer.MIN_VALUE : b.gapPercent();
            return Integer.compare(gapA, gapB);
        });
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        doc.addHeaderRow(table, List.of("Skill", "Current", "Required", "Gap"));
        for (SkillGapResponse gap : sorted) {
            doc.addRow(table, List.of(
                    gap.skillName(),
                    gap.assessed() ? gap.scorePercent() + "%" : "Not assessed",
                    gap.targetPercent() + "%",
                    gap.assessed() ? (gap.metTarget() ? "On target" : gap.gapPercent() + "%") : "—"));
        }
        doc.addTable(table);
        doc.addSection("Priority areas");
        readiness.improvements().forEach(gap -> doc.addBody(
                "• " + gap.skillName() + ": " + (gap.assessed()
                        ? gap.scorePercent() + "% vs required " + gap.targetPercent() + "%"
                        : "not assessed yet, required " + gap.targetPercent() + "%")));
        return doc.bytes();
    }

    private byte[] roadmapPdf() {
        StudentProfileResponse student = studentProfileService.getCurrentProfile();
        RoadmapResponse roadmap = roadmapReport();
        ProjectListResponse projects = projectService.recommended();
        PdfDoc doc = newDoc("Learning / Roadmap Progress Report");
        doc.addStudentHeader(student, roadmap.careerName());
        doc.addKeyValue("Completion",
                roadmap.completedItems() + " of " + roadmap.totalItems() + " steps (" + roadmap.progressPercent() + "%)");
        doc.addKeyValue("In progress", String.valueOf(roadmap.inProgressItems()));
        roadmap.phases().forEach(phase -> {
            doc.addSection(phase.title() + " (" + phase.completedItems() + "/" + phase.totalItems() + " done)");
            phase.items().forEach(item -> doc.addBody(
                    "• [" + pretty(item.status()) + "] " + item.title()));
        });
        doc.addSection("Projects");
        doc.addBody(projects.completedProjects() + " of " + projects.totalProjects() + " recommended projects completed"
                + " (" + projects.inProgressProjects() + " in progress).");
        projects.projects().forEach(p -> doc.addBody("• [" + pretty(p.status()) + "] " + p.title()));
        return doc.bytes();
    }

    private byte[] overallPdf() {
        OverallReportResponse overall = overallReport();
        PdfDoc doc = newDoc("Overall CareerOS Progress Report");
        doc.addStudentHeader(overall.student(),
                overall.readiness().hasTarget() ? overall.readiness().targetCareerName() : null);
        if (overall.readiness().hasTarget()) {
            doc.addSection("Career readiness");
            doc.addKeyValue("Readiness",
                    overall.readiness().readinessPercent() + "% (" + pretty(overall.readiness().readinessLevel()) + ")");
            doc.addKeyValue("Gaps", overall.readiness().metSkills() + " of "
                    + overall.readiness().totalSkills() + " skills on target");
        } else {
            doc.addSection("Career readiness");
            doc.addBody("No target career selected yet — choose one to unlock readiness, gaps and roadmap.");
        }
        doc.addSection("Learning roadmap");
        if (Boolean.TRUE.equals(overall.roadmap().hasTarget())) {
            doc.addBody(overall.roadmap().completedItems() + " of " + overall.roadmap().totalItems()
                    + " steps completed (" + overall.roadmap().progressPercent() + "%).");
        } else {
            doc.addBody("No roadmap available yet.");
        }
        doc.addSection("Projects");
        doc.addBody(overall.projects().completedProjects() + " of " + overall.projects().totalProjects()
                + " recommended projects completed.");
        doc.addSection("CV analysis");
        doc.addBody(Boolean.TRUE.equals(overall.cv().hasCv())
                ? overall.cv().detectedSkills().size() + " skills detected on CV; "
                        + overall.cv().matchedSkills().size() + " framework skills matched, "
                        + overall.cv().missingSkills().size() + " missing."
                : "No CV uploaded yet.");
        doc.addSection("LinkedIn analysis");
        doc.addBody(Boolean.TRUE.equals(overall.linkedIn().hasProfile())
                ? "Profile completeness " + overall.linkedIn().completenessPercent() + "%, alignment "
                        + overall.linkedIn().alignmentPercent() + "%."
                : "No LinkedIn profile connected yet.");
        doc.addSection("Recommended next steps");
        overall.recommendedNextSteps().forEach(step -> doc.addBody("• " + step));
        return doc.bytes();
    }

    private List<String> nextSteps(ReadinessResponse readiness, RoadmapResponse roadmap,
                                  ProjectListResponse projects, CvAnalysisResponse cv,
                                  LinkedInAnalysisResponse linkedIn) {
        List<String> steps = new ArrayList<>();
        if (!Boolean.TRUE.equals(readiness.hasTarget())) {
            steps.add("Choose your target career to unlock assessments, gaps and roadmap.");
            return steps;
        }
        readiness.improvements().stream().limit(3).forEach(gap -> steps.add(
                "Improve " + gap.skillName() + " (" + (gap.assessed()
                        ? gap.scorePercent() + "% vs required " + gap.targetPercent() + "%"
                        : "not assessed yet, required " + gap.targetPercent() + "%") + ")."));
        if (roadmap.totalItems() != null && roadmap.totalItems() > 0
                && roadmap.completedItems() < roadmap.totalItems()) {
            steps.add("Continue your roadmap: " + roadmap.completedItems() + " of "
                    + roadmap.totalItems() + " steps completed.");
        }
        if (projects.totalProjects() != null && projects.totalProjects() > 0
                && projects.completedProjects() < projects.totalProjects()) {
            steps.add("Complete a recommended project to turn a gap into proof.");
        }
        if (!Boolean.TRUE.equals(cv.hasCv())) {
            steps.add("Upload your CV to detect skills and match them against your career.");
        }
        if (!Boolean.TRUE.equals(linkedIn.hasProfile())) {
            steps.add("Connect your LinkedIn profile to check completeness and alignment.");
        }
        if (steps.isEmpty()) {
            steps.add("You are on track — keep your profile, CV and LinkedIn fresh.");
        }
        return steps;
    }

    private static SkillScoreResponse toSkillScore(SkillScore score) {
        return new SkillScoreResponse(
                score.getSkill().getId(),
                score.getSkill().getName(),
                score.getSkill().getCategory().name(),
                score.getWeightPercent(),
                score.getTargetPercent(),
                score.getQuestionCount(),
                score.getCorrectCount(),
                score.getScorePercent(),
                score.getLevel() == null ? null : score.getLevel().name());
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatDate(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
                .withZone(ZoneId.systemDefault()).format(instant);
    }

    private PdfDoc newDoc(String title) {
        return new PdfDoc(title);
    }

    /** Minimal OpenPDF helper: warm, professional CareerOS report layout. */
    private static class PdfDoc {
        private final Document document;
        private final ByteArrayOutputStream out;
        private final Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        private final Font h1Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
        private final Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        private final Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        PdfDoc(String title) {
            try {
                document = new Document(PageSize.A4, 48, 48, 56, 48);
                out = new ByteArrayOutputStream();
                PdfWriter.getInstance(document, out);
                document.open();
                Paragraph brand = new Paragraph("CODEIT CareerOS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
                brand.setAlignment(Element.ALIGN_LEFT);
                document.add(brand);
                Paragraph heading = new Paragraph(title, titleFont);
                heading.setSpacingBefore(4);
                heading.setSpacingAfter(4);
                document.add(heading);
                Paragraph sub = new Paragraph("Career Development Report  •  Generated "
                        + formatDate(Instant.now()), smallFont);
                sub.setSpacingAfter(12);
                document.add(sub);
            } catch (Exception e) {
                throw BusinessException.badRequest("Could not start the PDF report: " + e.getMessage());
            }
        }

        void addStudentHeader(StudentProfileResponse student, String careerName) {
            addSection("Student information");
            addKeyValue("Name", student.fullName());
            addKeyValue("Student ID", student.studentId());
            addKeyValue("Target career", careerName == null ? "Not selected yet" : careerName);
        }

        void addSection(String heading) {
            try {
                Paragraph p = new Paragraph(heading, h1Font);
                p.setSpacingBefore(14);
                p.setSpacingAfter(6);
                document.add(p);
            } catch (Exception e) {
                throw BusinessException.badRequest("Could not write the PDF report: " + e.getMessage());
            }
        }

        void addKeyValue(String key, String value) {
            addBody(key + ": " + (value == null ? "—" : value));
        }

        void addBody(String text) {
            try {
                Paragraph p = new Paragraph(text == null ? "—" : text, normalFont);
                p.setSpacingAfter(3);
                document.add(p);
            } catch (Exception e) {
                throw BusinessException.badRequest("Could not write the PDF report: " + e.getMessage());
            }
        }

        void addGapTable(List<SkillGapResponse> gaps) {
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            addHeaderRow(table, List.of("Skill", "Score", "Target", "Gap"));
            for (SkillGapResponse gap : gaps) {
                addRow(table, List.of(
                        gap.skillName(),
                        gap.assessed() ? gap.scorePercent() + "%" : "Not assessed",
                        gap.targetPercent() + "%",
                        gap.assessed() ? (gap.metTarget() ? "On target" : gap.gapPercent() + "%") : "—"));
            }
            addTable(table);
        }

        void addHeaderRow(PdfPTable table, List<String> headers) {
            headers.forEach(h -> {
                com.lowagie.text.pdf.PdfPCell cell =
                        new com.lowagie.text.pdf.PdfPCell(new Phrase(h, smallFont));
                cell.setBackgroundColor(new java.awt.Color(255, 122, 26));
                cell.setPadding(6);
                table.addCell(cell);
            });
        }

        void addRow(PdfPTable table, List<String> values) {
            values.forEach(v -> {
                com.lowagie.text.pdf.PdfPCell cell =
                        new com.lowagie.text.pdf.PdfPCell(
                                new Phrase(v == null ? "—" : v, smallFont));
                cell.setPadding(5);
                table.addCell(cell);
            });
        }

        void addTable(PdfPTable table) {
            try {
                table.setSpacingBefore(6);
                table.setSpacingAfter(6);
                document.add(table);
                document.add(new Paragraph(Chunk.NEWLINE));
            } catch (Exception e) {
                throw BusinessException.badRequest("Could not write the PDF report: " + e.getMessage());
            }
        }

        byte[] bytes() {
            try {
                document.close();
                return out.toByteArray();
            } catch (Exception e) {
                throw BusinessException.badRequest("Could not finish the PDF report: " + e.getMessage());
            }
        }
    }
}
