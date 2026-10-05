package com.codeit.careeros.service;

import com.codeit.careeros.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Sprint 8: nightly purge of expired refresh sessions so the
 * {@code refresh_tokens} table cannot grow without bound. Revoked but
 * unexpired rows are kept until expiry (reuse detection depends on them)
 * and then removed here. Runs in every profile, including tests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpired() {
        long removed = refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
