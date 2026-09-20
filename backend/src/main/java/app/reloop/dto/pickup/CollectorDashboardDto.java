package app.reloop.dto.pickup;

import java.math.BigDecimal;

public record CollectorDashboardDto(
        long availableRequests,
        long activeJobs,
        long completedJobs,
        long todayPickups,
        BigDecimal totalKgCollected,
        long totalCollections
) {}
