package com.codeit.careeros.service;

import com.codeit.careeros.common.enums.AttemptStatus;
import com.codeit.careeros.cv.CvDocument;
import com.codeit.careeros.dto.cv.CvAnalysisResponse;
import com.codeit.careeros.dto.insight.AttemptHistoryItem;
import com.codeit.careeros.dto.insight.ReadinessResponse;
import com.codeit.careeros.dto.linkedin.LinkedInAnalysisResponse;
import com.codeit.careeros.dto.progress.ActivityItem;
import com.codeit.careeros.dto.progress.ProgressResponse;
import com.codeit.careeros.dto.project.ProjectListResponse;
import com.codeit.careeros.dto.roadmap.RoadmapResponse;
import com.codeit.careeros.linkedin.LinkedInProfile;
import com.codeit.careeros.project.ProjectProgress;
import com.codeit.careeros.repository.AssessmentAttemptRepository;
import com.codeit.careeros.repository.CvDocumentRepository;
import com.codeit.careeros.repository.LinkedInProfileRepository;
import com.codeit.careeros.repository.ProjectProgressRepository;
import com.codeit.careeros.repository.RoadmapItemProgressRepository;
import com.codeit.careeros.roadmap.RoadmapItemProgress;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Student progress aggregation. Reuses the existing Sprint 1-7 read
 * services (no duplicated business logic): readiness/gaps, attempts,
 * roadmap, projects, CV and LinkedIn. The activity timeline is derived
 * only from existing timestamps; nothing is invented.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final CareerInsightService careerInsightService;
    private final RoadmapService roadmapService;
    private final ProjectService projectService;
    private final CvAnalysisService cvAnalysisService;
    private final LinkedInService linkedInService;
    private final AssessmentAttemptRepository attemptRepository;
    private final RoadmapItemProgressRepository roadmapProgressRepository;
    private final ProjectProgressRepository projectProgressRepository;
    private final CvDocumentRepository cvDocumentRepository;
    private final LinkedInProfileRepository linkedInProfileRepository;

    @Transactional(readOnly = true)
    public ProgressResponse myProgress() {
        Long userId = SecurityUtils.currentUserId();

        ReadinessResponse readiness = careerInsightService.myReadiness();
        List<AttemptHistoryItem> attempts = careerInsightService.myHistory(null);
        RoadmapResponse roadmap = roadmapService.myRoadmap();
        ProjectListResponse projects = projectService.recommended();
        CvAnalysisResponse cv = cvAnalysisService.analyze();
        LinkedInAnalysisResponse linkedIn = linkedInService.analyze();

        AttemptHistoryItem latest = attempts.isEmpty() ? null : attempts.get(0);
        AttemptHistoryItem previous = attempts.size() > 1 ? attempts.get(1) : null;

        List<ActivityItem> activity = buildActivity(userId, attempts);

        log.info("Progress for student {}: readiness {}%, {} attempts, roadmap {}/{}",
                userId,
                readiness.readinessPercent(),
                attempts.size(),
                roadmap.completedItems(), roadmap.totalItems());
        return new ProgressResponse(
                readiness,
                attempts,
                attempts.size(),
                latest,
                previous,
                roadmap,
                projects,
                cv,
                linkedIn,
                activity);
    }

    private List<ActivityItem> buildActivity(Long userId, List<AttemptHistoryItem> attempts) {
        List<ActivityItem> items = new ArrayList<>();

        for (AttemptHistoryItem attempt : attempts.stream().limit(5).toList()) {
            if (attempt.submittedAt() != null) {
                items.add(new ActivityItem(
                        "ASSESSMENT",
                        "Assessment submitted: " + attempt.assessmentTitle(),
                        attempt.overallScore() == null
                                ? "Submitted"
                                : "Score " + attempt.overallScore(),
                        attempt.submittedAt()));
            }
        }

        try {
            Long careerId = roadmapCareerIdSafe();
            if (careerId != null) {
                List<RoadmapItemProgress> roadmapProgress = roadmapProgressRepository
                        .findByUserIdAndRoadmapItemPhaseCareerId(userId, careerId);
                roadmapProgress.stream()
                        .filter(p -> p.getUpdatedAt() != null)
                        .sorted(Comparator.comparing(RoadmapItemProgress::getUpdatedAt).reversed())
                        .limit(5)
                        .forEach(p -> items.add(new ActivityItem(
                                "ROADMAP",
                                "Roadmap item " + p.getStatus().name().toLowerCase().replace('_', ' ')
                                        + ": " + safeItemTitle(p),
                                null,
                                p.getUpdatedAt())));
            }
        } catch (Exception e) {
            log.debug("Roadmap activity skipped: {}", e.getMessage());
        }

        try {
            Long careerId = roadmapCareerIdSafe();
            if (careerId != null) {
                List<ProjectProgress> projectProgress = projectProgressRepository
                        .findByUserIdAndProjectCareerId(userId, careerId);
                projectProgress.stream()
                        .filter(p -> p.getUpdatedAt() != null)
                        .sorted(Comparator.comparing(ProjectProgress::getUpdatedAt).reversed())
                        .limit(5)
                        .forEach(p -> items.add(new ActivityItem(
                                "PROJECT",
                                "Project " + p.getStatus().name().toLowerCase().replace('_', ' ')
                                        + ": " + safeProjectTitle(p),
                                null,
                                p.getUpdatedAt())));
            }
        } catch (Exception e) {
            log.debug("Project activity skipped: {}", e.getMessage());
        }

        CvDocument cv = cvDocumentRepository.findByUserId(userId).orElse(null);
        if (cv != null && cv.getUpdatedAt() != null) {
            items.add(new ActivityItem("CV", "CV updated: " + cv.getOriginalFilename(), null, cv.getUpdatedAt()));
        }

        LinkedInProfile linkedIn = linkedInProfileRepository.findByUserId(userId).orElse(null);
        if (linkedIn != null && linkedIn.getUpdatedAt() != null) {
            items.add(new ActivityItem("LINKEDIN", "LinkedIn profile updated", linkedIn.getProfileUrl(),
                    linkedIn.getUpdatedAt()));
        }

        items.sort(Comparator.comparing(ActivityItem::occurredAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return items.stream().limit(15).toList();
    }

    private Long roadmapCareerIdSafe() {
        try {
            ReadinessResponse readiness = careerInsightService.myReadiness();
            return readiness.targetCareerId();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeItemTitle(RoadmapItemProgress p) {
        try {
            return p.getRoadmapItem() != null ? p.getRoadmapItem().getTitle() : "roadmap item";
        } catch (Exception e) {
            return "roadmap item";
        }
    }

    private String safeProjectTitle(ProjectProgress p) {
        try {
            return p.getProject() != null ? p.getProject().getTitle() : "project";
        } catch (Exception e) {
            return "project";
        }
    }

    /** Used by tests to avoid a second SecurityUtils lookup. */
    @Transactional(readOnly = true)
    public List<AttemptHistoryItem> submittedAttempts() {
        Long userId = SecurityUtils.currentUserId();
        return attemptRepository.findByUserIdAndStatusOrderByStartedAtDesc(userId, AttemptStatus.SUBMITTED)
                .stream().map(CareerInsightService::toHistoryItem).toList();
    }
}
