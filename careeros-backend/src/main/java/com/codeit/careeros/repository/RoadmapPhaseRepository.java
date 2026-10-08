package com.codeit.careeros.repository;

import com.codeit.careeros.roadmap.RoadmapPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoadmapPhaseRepository extends JpaRepository<RoadmapPhase, Long> {

    List<RoadmapPhase> findByCareerIdOrderByDisplayOrderAsc(Long careerId);

    boolean existsByCareerId(Long careerId);
}
