package com.codeit.careeros.service;

import com.codeit.careeros.common.enums.Role;
import com.codeit.careeros.common.enums.UserStatus;
import com.codeit.careeros.dto.auth.AuthResponse;
import com.codeit.careeros.dto.auth.LoginRequest;
import com.codeit.careeros.dto.auth.RefreshRequest;
import com.codeit.careeros.dto.auth.RegisterRequest;
import com.codeit.careeros.dto.auth.UserResponse;
import com.codeit.careeros.entity.RefreshToken;
import com.codeit.careeros.entity.StudentProfile;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.mapper.UserMapper;
import com.codeit.careeros.repository.RefreshTokenRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.repository.UserRepository;
import com.codeit.careeros.security.JwtService;
import com.codeit.careeros.security.SecurityUtils;
import com.codeit.careeros.security.TokenHash;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            throw BusinessException.conflict("An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .role(Role.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        user = userRepository.save(user);

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .studentId(generateStudentId(user))
                .fullName(user.getFullName())
                .semester(1)
                .build();
        studentProfileRepository.save(profile);

        log.info("Registered student {} ({})", user.getEmail(), user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> BusinessException.forbidden("Account is disabled"));
        log.info("Logged in user {} ({})", user.getEmail(), user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = TokenHash.sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> BusinessException.unauthorized("Invalid refresh token"));

        if (stored.isRevoked()) {
            revokeAllForUser(stored.getUser().getId());
            throw BusinessException.unauthorized("Refresh token reuse detected. All sessions revoked.");
        }
        if (stored.isExpired()) {
            throw BusinessException.unauthorized("Refresh token has expired");
        }

        User user = stored.getUser();
        IssuedRefreshToken replacement = issueNewRefreshToken(user);
        stored.setRevoked(true);
        stored.setReplacedById(replacement.entity().getId());
        refreshTokenRepository.save(stored);

        return new AuthResponse(
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name()),
                replacement.raw(),
                "Bearer",
                jwtService.getAccessExpirationMs() / 1000,
                UserMapper.toResponse(user));
    }

    @Transactional
    public void logout(String refreshToken) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHash(TokenHash.sha256(refreshToken))
                .orElseThrow(() -> BusinessException.unauthorized("Invalid refresh token"));
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        log.info("Logged out user {}", stored.getUser().getId());
    }

    public UserResponse me() {
        User user = userRepository.findById(SecurityUtils.currentUserId())
                .orElseThrow(() -> BusinessException.notFound("User not found"));
        return UserMapper.toResponse(user);
    }

    private AuthResponse issueTokens(User user) {
        IssuedRefreshToken refresh = issueNewRefreshToken(user);
        return new AuthResponse(
                jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name()),
                refresh.raw(),
                "Bearer",
                jwtService.getAccessExpirationMs() / 1000,
                UserMapper.toResponse(user));
    }

    private IssuedRefreshToken issueNewRefreshToken(User user) {
        String raw = UUID.randomUUID().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().replace("-", "");
        RefreshToken entity = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenHash.sha256(raw))
                .expiresAt(Instant.now().plus(refreshExpirationMs, ChronoUnit.MILLIS))
                .build();
        entity = refreshTokenRepository.save(entity);
        return new IssuedRefreshToken(raw, entity);
    }

    private void revokeAllForUser(Long userId) {
        refreshTokenRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .forEach(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String generateStudentId(User user) {
        return "STU" + String.format("%06d", user.getId());
    }

    private record IssuedRefreshToken(String raw, RefreshToken entity) {
    }
}