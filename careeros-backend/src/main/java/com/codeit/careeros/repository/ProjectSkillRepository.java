package com.codeit.careeros.repository;

import com.codeit.careeros.project.ProjectSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectSkillRepository extends JpaRepository<ProjectSkill, Long> {

    List<ProjectSkill> findByProjectId(Long projectId);
}
