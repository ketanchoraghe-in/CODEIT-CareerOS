package com.codeit.careeros.repository;

import com.codeit.careeros.career.Career;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CareerRepository extends JpaRepository<Career, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Career> findByNameIgnoreCase(String name);

    Optional<Career> findByIdAndPublishedTrue(Long id);

    List<Career> findAllByPublishedTrueOrderByNameAsc();
}
