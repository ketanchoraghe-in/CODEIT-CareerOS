package com.codeit.careeros.admin;

import com.codeit.careeros.dto.admin.AdminRoadmapTemplateResponse;
import com.codeit.careeros.repository.CareerRepository;
import com.codeit.careeros.repository.RoadmapItemRepository;
import com.codeit.careeros.repository.RoadmapPhaseRepository;
import com.codeit.careeros.roadmap.RoadmapItem;
import com.codeit.careeros.roadmap.RoadmapPhase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Admin read-only browser for roadmap templates (one per career). No mutations. */
@Service
@RequiredArgsConstructor
public class AdminRoadmapService {

    private final CareerRepository careerRepository;
    private final RoadmapPhaseRepository phaseRepository;
    private final RoadmapItemRepository itemRepository;

    @Transactional(readOnly = true)
    public List<AdminRoadmapTemplateResponse> listTemplates() {
        return careerRepository.findAll().stream()
                .sorted((a, b) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())))
                .map(career -> {
                    List<RoadmapPhase> phases = phaseRepository.findByCareerIdOrderByDisplayOrderAsc(career.getId());
                    List<AdminRoadmapTemplateResponse.TemplatePhase> templatePhases = phases.stream()
                            .map(phase -> {
                                List<AdminRoadmapTemplateResponse.TemplateItem> items = itemRepository
                                        .findByPhaseIdOrderByDisplayOrderAsc(phase.getId())
                                        .stream()
                                        .map(this::toItem)
                                        .toList();
                                return new AdminRoadmapTemplateResponse.TemplatePhase(
                                        phase.getId(),
                                        phase.getTitle(),
                                        phase.getDescription(),
                                        phase.getDisplayOrder(),
                                        phase.getDurationDays(),
                                        items);
                            })
                            .toList();
                    int itemCount = templatePhases.stream().mapToInt(p -> p.items().size()).sum();
                    int totalHours = templatePhases.stream()
                            .flatMap(p -> p.items().stream())
                            .mapToInt(AdminRoadmapTemplateResponse.TemplateItem::estimatedHours)
                            .sum();
                    return new AdminRoadmapTemplateResponse(
                            career.getId(),
                            career.getName(),
                            career.isPublished(),
                            templatePhases.size(),
                            itemCount,
                            totalHours,
                            templatePhases);
                })
                .toList();
    }

    private AdminRoadmapTemplateResponse.TemplateItem toItem(RoadmapItem item) {
        return new AdminRoadmapTemplateResponse.TemplateItem(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getLearningGoal(),
                item.getDisplayOrder(),
                item.getEstimatedHours(),
                item.getSkill() != null ? item.getSkill().getId() : null,
                item.getSkill() != null ? item.getSkill().getName() : null);
    }
}
