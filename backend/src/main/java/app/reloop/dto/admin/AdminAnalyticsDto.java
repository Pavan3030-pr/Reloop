package app.reloop.dto.admin;

import java.math.BigDecimal;
import java.util.Map;

public record AdminAnalyticsDto(
        long totalUsers,
        long verifiedCollectors,
        long pendingCollectorApplications,
        Map<String, Long> pickupsByStatus,
        long totalPickups,
        long completedCollections,
        BigDecimal totalCollectedKg,
        Map<String, BigDecimal> collectedKgByCategory
) {}
