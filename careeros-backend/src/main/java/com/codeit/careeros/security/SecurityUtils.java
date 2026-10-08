package com.codeit.careeros.security;

import com.codeit.careeros.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails details)) {
            throw BusinessException.unauthorized("Authentication is required");
        }
        return details;
    }

    public static Long currentUserId() {
        return currentUser().getId();
    }
}