package com.codeit.careeros.service;

import com.codeit.careeros.common.enums.Role;
import com.codeit.careeros.common.enums.UserStatus;
import com.codeit.careeros.dto.auth.AuthResponse;
import com.codeit.careeros.dto.auth.LoginRequest;
import com.codeit.careeros.dto.auth.RegisterRequest;
import com.codeit.careeros.entity.User;
import com.codeit.careeros.exception.BusinessException;
import com.codeit.careeros.repository.RefreshTokenRepository;
import com.codeit.careeros.repository.StudentProfileRepository;
import com.codeit.careeros.repository.UserRepository;
import com.codeit.careeros.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StudentProfileRepository studentProfileRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604_800_000L);
    }

    @Test
    void register_createsStudentAndProfileAndEncodesPassword() {
        when(userRepository.existsByEmail("john@test.local")).thenReturn(false);
        User saved = User.builder().id(7L).email("john@test.local")
                .passwordHash("$2a$10$encoded").fullName("John Doe")
                .role(Role.STUDENT).status(UserStatus.ACTIVE).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(passwordEncoder.encode("Password1")).thenReturn("$2a$10$encoded");
        when(jwtService.generateAccessToken(any(), anyString(), anyString())).thenReturn("access-token");
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        RegisterRequest request = new RegisterRequest("John Doe", "john@test.local", "9876543210", "Password1");
        AuthResponse response = authService.register(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().role()).isEqualTo("STUDENT");
        assertThat(response.user().email()).isEqualTo("john@test.local");
        verify(passwordEncoder).encode("Password1");
        verify(studentProfileRepository).save(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("john@test.local")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("John Doe", "john@test.local", "9876543210", "Password1");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");
        verify(userRepository, never()).save(any());
        verify(studentProfileRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsTokens() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("john@test.local", "Password1"));
        when(userRepository.findByEmail("john@test.local")).thenReturn(
                java.util.Optional.of(User.builder().id(1L).email("john@test.local")
                        .fullName("John Doe").role(Role.STUDENT).status(UserStatus.ACTIVE).build()));
        when(jwtService.generateAccessToken(1L, "john@test.local", "STUDENT")).thenReturn("access-token");
        when(jwtService.getAccessExpirationMs()).thenReturn(900_000L);

        LoginRequest request = new LoginRequest("john@test.local", "Password1");
        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.user().fullName()).isEqualTo("John Doe");
    }

    @Test
    void login_wrongPassword_propagatesBadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        LoginRequest request = new LoginRequest("john@test.local", "WrongPass1");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(userRepository, never()).findByEmail(anyString());
    }
}