package com.codeit.careeros.repository;

import com.codeit.careeros.assessment.AssessmentTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssessmentTestRepository extends JpaRepository<AssessmentTest, Long> {

    List<AssessmentTest> findAllByCareerIdOrderByCreatedAtDesc(Long careerId);

    Optional<AssessmentTest> findByIdAndPublishedTrue(Long id);

    List<AssessmentTest> findAllByOrderByCreatedAtDesc();

    long countByCareerIdAndPublishedTrue(Long careerId);

    boolean existsByCareerId(Long careerId);

    Optional<AssessmentTest> findByCareerIdAndTitle(Long careerId, String title);
}
