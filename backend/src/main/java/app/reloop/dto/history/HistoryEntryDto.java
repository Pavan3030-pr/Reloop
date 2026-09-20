package app.reloop.dto.history;

import java.math.BigDecimal;
import java.time.Instant;

public record HistoryEntryDto(
        String id,
        String pickupCode,
        String pickupStatus,
        String categoryCode,
        String categoryName,
        String categoryColor,
        BigDecimal quantityKg,
        Instant collectionDate,
        String collectorOrganization,
        String notes
) {}
