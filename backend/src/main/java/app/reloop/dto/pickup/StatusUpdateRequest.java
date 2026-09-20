package app.reloop.dto.pickup;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull String status,
        @Size(max = 500) String reason
) {}
