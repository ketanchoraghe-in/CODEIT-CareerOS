package com.codeit.careeros.repository;

import com.codeit.careeros.cv.CvDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CvDocumentRepository extends JpaRepository<CvDocument, Long> {

    Optional<CvDocument> findByUserId(Long userId);
}
