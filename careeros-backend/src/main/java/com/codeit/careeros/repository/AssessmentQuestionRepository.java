package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Long> {

    List<AssessmentQuestion> findByAssessmentIdAndActiveTrueOrderByDisplayOrderAsc(Long assessmentId);

    List<AssessmentQuestion> findByAssessmentIdOrderByDisplayOrderAsc(Long assessmentId);

    long countByAssessmentIdAndActiveTrue(Long assessmentId);

    long countBySkillId(Long skillId);

    boolean existsByAssessmentIdAndQuestionText(Long assessmentId, String questionText);
}
