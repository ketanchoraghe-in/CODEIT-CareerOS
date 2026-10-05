package com.codeit.careeros.mapper;

import com.codeit.careeros.dto.auth.UserResponse;
import com.codeit.careeros.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getCreatedAt());
    }
}