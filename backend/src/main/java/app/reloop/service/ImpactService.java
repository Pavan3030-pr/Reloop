package app.reloop.service;

import app.reloop.dto.impact.ImpactResponse;
import app.reloop.repository.CollectedWasteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImpactService {

    /**
     * Indicative kg CO2e avoided per kg of material recycled, based on widely cited
     * lifecycle-literature ranges (EPA WARM-style magnitudes) and deliberately CONSERVATIVE.
     * These are estimates, not measurements - see docs/IMPACT.md for methodology and sources.
     */
    static final Map<String, Double> CO2E_KG_PER_KG = Map.of(
            "PLASTIC", 1.5,
            "PAPER", 1.0,
            "CARDBOARD", 0.9,
            "METAL", 4.0,
            "GLASS", 0.3,
            "E_WASTE", 1.5,
            "ORGANIC", 0.5,
            "TEXTILE", 2.0,
            "HAZARDOUS", 0.0,
            "OTHER", 0.5
    );

    private final CollectedWasteRepository collectedWasteRepository;

    @Transactional(readOnly = true)
    public ImpactResponse impactForUser(UUID userId) {
        List<CollectedWasteRepository.CategoryTotal> byCategory =
                collectedWasteRepository.sumKgByCategoryForUser(userId);
        BigDecimal totalKg = collectedWasteRepository.sumKgForUser(userId, null, null, null, null);
        long completed = collectedWasteRepository.countByUserId(userId);

        List<ImpactResponse.CategoryImpact> categories = byCategory.stream()
                .map(t -> {
                    BigDecimal kg = scale(t.getKg());
                    BigDecimal co2e = kg.multiply(BigDecimal.valueOf(
                                    CO2E_KG_PER_KG.getOrDefault(t.getCode(), 0.0)))
                            .setScale(2, RoundingMode.HALF_UP);
                    return new ImpactResponse.CategoryImpact(t.getCode(), t.getName(), colorOf(t.getCode()),
                            kg, co2e);
                })
                .toList();

        BigDecimal estimatedTotal = categories.stream()
                .map(ImpactResponse.CategoryImpact::estimatedCo2eKgSaved)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ImpactResponse(scale(totalKg), completed, true,
                "Estimates use conservative literature-based kg CO2e per kg coefficients - see docs/IMPACT.md",
                categories);
    }

    private String colorOf(String code) {
        return switch (code) {
            case "PLASTIC" -> "#2D6A4F";
            case "PAPER" -> "#40916C";
            case "CARDBOARD" -> "#588157";
            case "METAL" -> "#52796F";
            case "GLASS" -> "#84A98C";
            case "E_WASTE" -> "#354F52";
            case "HAZARDOUS" -> "#9B2226";
            case "ORGANIC" -> "#76C893";
            case "TEXTILE" -> "#A5A58D";
            default -> "#6B705C";
        };
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }
}
