package com.codeit.careeros.repository;

import com.codeit.careeros.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Sprint 8: backing query for the nightly expired-session purge.
     * Returns the number of rows removed.
     */
    long deleteByExpiresAtBefore(Instant now);
}