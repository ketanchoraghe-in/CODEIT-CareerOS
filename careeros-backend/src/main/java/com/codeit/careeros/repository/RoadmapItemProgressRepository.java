package com.codeit.careeros.repository;

import com.codeit.careeros.roadmap.RoadmapItemProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoadmapItemProgressRepository extends JpaRepository<RoadmapItemProgress, Long> {

    Optional<RoadmapItemProgress> findByUserIdAndRoadmapItemId(Long userId, Long roadmapItemId);

    List<RoadmapItemProgress> findByUserIdAndRoadmapItemPhaseCareerId(Long userId, Long careerId);
}
