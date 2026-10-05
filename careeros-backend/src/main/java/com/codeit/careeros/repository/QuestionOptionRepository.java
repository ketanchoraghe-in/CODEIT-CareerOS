package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {

    List<QuestionOption> findByQuestionIdOrderByDisplayOrderAsc(Long questionId);

    @Modifying
    @Transactional
    long deleteByQuestionId(Long questionId);
}
