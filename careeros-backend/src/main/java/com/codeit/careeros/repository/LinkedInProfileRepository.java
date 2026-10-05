package com.codeit.careeros.repository;

import com.codeit.careeros.linkedin.LinkedInProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkedInProfileRepository extends JpaRepository<LinkedInProfile, Long> {

    Optional<LinkedInProfile> findByUserId(Long userId);
}
