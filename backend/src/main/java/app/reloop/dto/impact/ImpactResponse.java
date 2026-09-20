package app.reloop.dto.impact;

import java.math.BigDecimal;
import java.util.List;

/**
 * Impact figures are computed ONLY from actual collected_waste records.
 * Environmental benefit numbers are estimates based on documented coefficients
 * (see docs/IMPACT.md) and are always labeled as estimates.
 */
public record ImpactResponse(
        BigDecimal totalCollectedKg,
        long completedPickups,
        boolean estimatesAreApproximations,
        String methodology,
        List<CategoryImpact> byCategory
) {
    public record CategoryImpact(
            String code,
            String name,
            String colorHex,
            BigDecimal kg,
            BigDecimal estimatedCo2eKgSaved
    ) {}
}
