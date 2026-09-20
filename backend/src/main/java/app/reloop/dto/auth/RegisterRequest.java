package app.reloop.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters") String password,
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 20) String phone
) {}
