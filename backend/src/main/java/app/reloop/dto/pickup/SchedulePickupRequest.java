package app.reloop.dto.pickup;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SchedulePickupRequest(
        @NotNull Instant scheduledAt
) {}
