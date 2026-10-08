package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.SkillScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillScoreRepository extends JpaRepository<SkillScore, Long> {

    List<SkillScore> findByAttemptIdOrderByWeightPercentDesc(Long attemptId);
}
