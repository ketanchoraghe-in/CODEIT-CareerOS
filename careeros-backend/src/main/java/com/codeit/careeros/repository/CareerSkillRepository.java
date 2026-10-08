package com.codeit.careeros.repository;

import com.codeit.careeros.career.CareerSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CareerSkillRepository extends JpaRepository<CareerSkill, Long> {

    List<CareerSkill> findByCareerIdOrderByWeightPercentDesc(Long careerId);

    boolean existsBySkillId(Long skillId);

    /**
     * Bulk delete, executed immediately. Must return the affected-row count:
     * a void derived delete is deferred as queued removals, which would run
     * AFTER the replacement inserts at flush time and trip the unique key.
     */
    @Modifying
    @Transactional
    long deleteByCareerId(Long careerId);

    long countByCareerId(Long careerId);
}
