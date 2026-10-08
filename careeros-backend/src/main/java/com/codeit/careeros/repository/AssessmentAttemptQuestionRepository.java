package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.AssessmentAttemptQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssessmentAttemptQuestionRepository extends JpaRepository<AssessmentAttemptQuestion, Long> {

    List<AssessmentAttemptQuestion> findByAttemptIdOrderByDisplayOrderAsc(Long attemptId);

    boolean existsByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    boolean existsByQuestionId(Long questionId);

    long countByAttemptId(Long attemptId);

    /**
     * Ids of every question this student has already been served for the
     * given assessment (across all of their attempts, including the current
     * one). Used to prefer unseen questions when building a new attempt.
     */
    @Query("""
            select distinct aq.question.id from AssessmentAttemptQuestion aq
            where aq.attempt.user.id = :userId
              and aq.attempt.assessment.id = :assessmentId
            """)
    List<Long> findSeenQuestionIds(Long userId, Long assessmentId);
}
