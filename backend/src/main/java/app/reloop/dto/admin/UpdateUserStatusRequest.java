package app.reloop.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateUserStatusRequest(
        @NotBlank @Pattern(regexp = "ACTIVE|DISABLED", message = "Status must be ACTIVE or DISABLED") String status
) {}
