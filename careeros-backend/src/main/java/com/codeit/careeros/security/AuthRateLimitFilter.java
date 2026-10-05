package com.codeit.careeros.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sprint 8 brute-force protection for the public auth endpoints
 * (login / register / token refresh). In-memory sliding-window limiter
 * keyed by client IP — no extra infrastructure, correct for a monolith.
 * Disabled in the {@code test} profile so MockMvc suites never trip it.
 */
@Slf4j
@Component
@Profile("!test")
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;

    private final int maxPerMinute;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(@Value("${app.auth.rate-limit-per-minute:60}") int maxPerMinute,
                               ObjectMapper objectMapper) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !("/api/v1/auth/login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/refresh".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = clientIp(request);
        Instant now = Instant.now();
        Deque<Instant> deque = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        boolean limited;
        synchronized (deque) {
            Instant cutoff = now.minusSeconds(WINDOW_SECONDS);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= maxPerMinute) {
                limited = true;
            } else {
                deque.addLast(now);
                limited = false;
            }
        }
        if (limited) {
            log.warn("Auth rate limit exceeded for client {}", key);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "success", false,
                    "message", "Too many attempts. Please wait a minute and try again.",
                    "data", null,
                    "errorCode", "RATE_LIMITED",
                    "timestamp", Instant.now().toString()));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}
