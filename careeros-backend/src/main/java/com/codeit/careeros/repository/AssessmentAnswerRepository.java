package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.AssessmentAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, Long> {

    List<AssessmentAnswer> findByAttemptId(Long attemptId);

    Optional<AssessmentAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    long countByAttemptId(Long attemptId);

    boolean existsByQuestionId(Long questionId);
}
