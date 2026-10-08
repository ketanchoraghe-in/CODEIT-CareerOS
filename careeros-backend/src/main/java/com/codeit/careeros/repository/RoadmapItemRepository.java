package com.codeit.careeros.repository;

import com.codeit.careeros.roadmap.RoadmapItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadmapItemRepository extends JpaRepository<RoadmapItem, Long> {

    List<RoadmapItem> findByPhaseIdOrderByDisplayOrderAsc(Long phaseId);

    List<RoadmapItem> findByPhaseCareerIdOrderByDisplayOrderAsc(Long careerId);
}
