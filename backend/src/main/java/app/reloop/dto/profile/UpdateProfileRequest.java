package app.reloop.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Size(max = 20) String phone,
        @Size(max = 80) String city,
        @Size(max = 255) String addressLine
) {}
