package com.codeit.careeros.repository;

import com.codeit.careeros.project.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByCareerIdAndPublishedTrueOrderByDisplayOrderAsc(Long careerId);

    boolean existsByCareerId(Long careerId);
}
