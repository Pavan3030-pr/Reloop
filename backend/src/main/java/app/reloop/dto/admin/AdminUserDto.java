package app.reloop.dto.admin;

import app.reloop.entity.User;
import app.reloop.security.Role;
import app.reloop.security.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserDto(
        UUID id,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified,
        String fullName,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static AdminUserDto from(User user, String fullName) {
        return new AdminUserDto(user.getId(), user.getEmail(), user.getRole(), user.getStatus(),
                user.isEmailVerified(), fullName, user.getCreatedAt(), user.getLastLoginAt());
    }
}
