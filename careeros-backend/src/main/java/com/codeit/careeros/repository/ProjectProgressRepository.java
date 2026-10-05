package com.codeit.careeros.repository;

import com.codeit.careeros.project.ProjectProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectProgressRepository extends JpaRepository<ProjectProgress, Long> {

    Optional<ProjectProgress> findByUserIdAndProjectId(Long userId, Long projectId);

    List<ProjectProgress> findByUserIdAndProjectCareerId(Long userId, Long careerId);
}
