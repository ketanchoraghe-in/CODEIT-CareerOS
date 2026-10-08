package com.codeit.careeros.repository;

import com.codeit.careeros.skill.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Skill> findByNameIgnoreCase(String name);

    List<Skill> findAllByOrderByNameAsc();
}
