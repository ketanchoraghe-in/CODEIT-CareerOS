package com.codeit.careeros.service;

import com.codeit.careeros.ai.AiChatSessionRepository;
import com.codeit.careeros.ai.CareerAssistantService;
import com.codeit.careeros.dto.ai.AiStatusResponse;
import com.codeit.careeros.dto.auth.UserResponse;
import com.codeit.careeros.dto.settings.ChangePasswordRequest;
import com.codeit.careeros.dto.settings.SettingsResponse;
import com.codeit.careeros.dto.student.StudentProfileResponse;
import com.codeit.careeros.entity.RefreshToken;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.StudentProfileMapper;
import com.codeit.careeros.mapper.UserMapper;
import com.codeit.careeros.repository.CvDocumentRepository;
import com.codeit.careeros.repository.LinkedInProfileRepository;
import com.codeit.careeros.repository.RefreshTokenRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.repository.UserRepository;
import com.codeit.careeros.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Student settings: account/profile snapshot (reusing existing mappers),
 * secure password change (current password required, BCrypt, session
 * revocation), AI conversation clearing and session overview.
 * Everything is strictly owner-scoped to the authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final CvDocumentRepository cvDocumentRepository;
    private final LinkedInProfileRepository linkedInProfileRepository;
    private final CareerAssistantService careerAssistantService;

    @Transactional(readOnly = true)
    public SettingsResponse mySettings() {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        UserResponse account = UserMapper.toResponse(user);
        StudentProfileResponse profile = studentProfileRepository.findByUserId(userId)
                .map(StudentProfileMapper::toResponse)
                .orElse(null);
        AiStatusResponse aiStatus = safeAiStatus();
        int activeSessions = (int) refreshTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .stream().filter(t -> !t.isRevoked() && !t.isExpired()).count();
        int aiConversations = aiChatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).size();
        boolean hasCv = cvDocumentRepository.findByUserId(userId).isPresent();
        boolean hasLinkedIn = linkedInProfileRepository.findByUserId(userId).isPresent();
        return new SettingsResponse(account, profile, aiStatus, activeSessions,
                aiConversations, hasCv, hasLinkedIn, false);
    }

    /**
     * Changes the password of the authenticated user only. Requires the
     * correct current password, encodes the new one with BCrypt and
     * revokes all other refresh sessions.
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Long userId = SecurityUtils.currentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw BusinessException.unauthorized("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw BusinessException.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        tokens.forEach(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
        log.info("Password changed for user {}; {} sessions revoked", userId, tokens.size());
    }

    /** Revokes all refresh sessions of the authenticated user except none (full logout everywhere). */
    @Transactional
    public int revokeAllSessions() {
        Long userId = SecurityUtils.currentUserId();
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        int active = 0;
        for (RefreshToken token : tokens) {
            if (!token.isRevoked() && !token.isExpired()) {
                active++;
            }
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        }
        log.info("Revoked {} sessions for user {}", tokens.size(), userId);
        return active;
    }

    /** Deletes all AI conversations of the authenticated student. */
    @Transactional
    public int clearAiHistory() {
        Long userId = SecurityUtils.currentUserId();
        var sessions = aiChatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        int count = sessions.size();
        sessions.forEach(session -> careerAssistantService.deleteSession(session.getId()));
        log.info("Cleared {} AI sessions for user {}", count, userId);
        return count;
    }

    private AiStatusResponse safeAiStatus() {
        try {
            return careerAssistantService.status();
        } catch (Exception e) {
            log.debug("AI status unavailable: {}", e.getMessage());
            return new AiStatusResponse(false, null, null);
        }
    }
}
