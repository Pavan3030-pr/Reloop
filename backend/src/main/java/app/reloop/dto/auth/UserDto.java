package app.reloop.dto.auth;

import app.reloop.entity.User;
import app.reloop.security.Role;
import app.reloop.security.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        String email,
        Role role,
        UserStatus status,
        boolean emailVerified,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail(), user.getRole(),
                user.getStatus(), user.isEmailVerified(), user.getCreatedAt());
    }
}
